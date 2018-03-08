#!/usr/bin/python3
# coding=utf-8

import codecs
import datetime
import hashlib
import icalendar
import os
import re
import subprocess
import tempfile
import time
import urllib3

from xml.etree.ElementTree import Element, SubElement, Comment, tostring


# Not needed I think?
#class TextElement(SubElement):
#	def __init__(self, parent, tag, text):
#		super(SubElement, self).__init__(parent, tag)
#		self.text = text


class GiggityObject(object):
	ids = set()

	def _idn(self, id, n):
		if n < 2:
			return id
		else:
			return "%s%d" % (id, n)	

	# On second thought, this one seems to work due to black magic.
	# The set() above is instantiated only once and shared by all
	# objects? Oh well..
	def makeid(self, name):
		id = re.sub("[^a-z0-9]+", "", name.lower())[:64]
		n = 1
		while self._idn(id, n) in self.ids:
			n += 1
		self.ids.add(self._idn(id, n))
		return self._idn(id, n)


class Schedule(GiggityObject):
	def __init__(self, title):
		self.title = title
		self.tents = []

	def xml(self):
		top = Element("schedule")
		top.attrib["title"] = self.title
		top.attrib["xmlns"] = "http://gaa.st/giggity#sched"
		
		for tent in self.tents:
			top.append(tent.xml())
		
		return top

	def frab(self):
		top = Element("schedule")
		conf = SubElement(top, "conference")
		SubElement(conf, "title").text = self.title
		SubElement(conf, "subtitle")
		SubElement(conf, "venue")
		SubElement(conf, "city")
		
		slot = self.timeslot_minutes()
		SubElement(conf, "timeslot_duration").text = (
			"00:%02d:00" % slot)

		index = 0
		for day in self.days():
			eod = day + datetime.timedelta(days=1)
			index += 1
			do = SubElement(top, "day", index="%d" % index,
			                date=day.strftime("%Y-%m-%d"))
			for tent in self.tents:
				to = tent.frab((day, eod))
				if to:
					do.append(to)

		return top

	def timeslot_minutes(self):
		ret = 3600
		for i in self.pile_items():
			while ret > 60 and ((i.start % ret) or (i.end % ret)):
				ret -= 60
		return ret / 60

	def days(self):
		ret = set()
		for i in self.pile_items():
			day = datetime.datetime.fromtimestamp(i.start)
			day = day.replace(hour=9, minute=0, second=0)
			if day.timestamp() > i.start:
				day -= datetime.timedelta(days=1)
			ret.add(day)
		return sorted(ret)
	
	def tentnames(self):
		return [tent.name for tent in self.tents]
	
	def gettent(self, name):
		for tent in self.tents:
			if tent.name == name:
				return tent
		tent = Tent(name)
		self.tents.append(tent)
		return tent

	def pile_items(self):
		for tent in self.tents:
			for i in tent.items:
				yield i
		

class Tent(GiggityObject):
	def __init__(self, name):
		self.name = name
		self.items = []
		self.id = self.makeid(name)
	
	def xml(self):
		top = Element("line")
		top.attrib["id"] = self.id
		top.attrib["title"] = self.name
		
		for item in self.items:
			top.append(item.xml())
		
		return top

	def frab(self, dayrange):
		day, eod = dayrange
		top = Element("room", name=self.name)
		
		for i in self.items:
			if not day.timestamp() <= i.start < eod.timestamp():
				continue
			top.append(i.frab())
		
		if not list(top):
			# Nothing for this date range
			return None
		return top
	
	def append(self, item):
		self.items.append(item)


class Item(GiggityObject):
	def __init__(self, name):
		self.name = name
		self.start = None
		self.end = None
		self.description = None
		self.links = []
		self.id = self.makeid(name)
	
	def xml(self):
		top = Element("item")
		top.attrib["id"] = self.id
		top.attrib["title"] = self.name
		top.attrib["startTime"] = str(int(time.mktime(self.start.timetuple())))
		top.attrib["endTime"] = str(int(time.mktime(self.end.timetuple())))
		
		if self.description:
			desc = SubElement(top, "itemDescription")
			desc.text = self.description
		
		for link in self.links:
			top.append(link.xml())
		
		return top

	def frab(self):
		ev = Element("event", id=self.id)
		SubElement(ev, "start").text = self.start_dt().strftime("%H:%M")
		#SubElement(ev, "end").text = self.end_dt().strftime("%H:%M")
		d = self.duration_td()
		SubElement(ev, "duration").text = "%02d:%02d" % (
			d.seconds // 3600, (d.seconds // 60) % 60)
		SubElement(ev, "description").text = self.description
		return ev

	def start_dt(self):
		return datetime.datetime.fromtimestamp(self.start)

	def end_dt(self):
		return datetime.datetime.fromtimestamp(self.end)

	def duration_td(self):
		return self.end_dt() - self.start_dt()


class Link(GiggityObject):
	def __init__(self, url):
		self.url = url
	
	def xml(self):
		top = Element("itemLink")
		top.attrib["href"] = self.url
		return top


def maketime(time, today=(2008, 10, 23)):
	h, m = time.split(":")
	ret = datetime.datetime(today[0], today[1], today[2], int(h), int(m))
	if int(h) < 8:
		ret += datetime.timedelta(1)
	return ret


def fetch(url):
	hash = hashlib.md5(url).hexdigest()
	fn = os.path.join("_cache", hash)
	if os.path.exists(fn):
		cont = file(fn, "r").read()
		head, body = cont.split("\n\n", 1)
		f_url, code = head.split("\n")[0:2]
		if (f_url != url):
			raise Exception("URL mismatch in cache (%s %s)" % (hash, url))
		if int(code) != 200:
			return None
	
	else:
		f = file(fn, "w")
		f.write("%s\n" % (url,))
		opener = urllib2.build_opener()
		opener.addheaders = [('User-agent', 'Giggity/0.9')]
		try:
			u = opener.open(url)
		except urllib2.HTTPError as e:
			f.write("%d\n\n" % (e.code,))
			return None
		
		body = u.read()
		f.write("%d\n\n%s" % (u.code, body))
	
	return body


sched = Schedule("LCA 2018")

ic = icalendar.Calendar.from_ical(open("/home/wilmer/Desktop/conference-new.ics", "r").read())
for ev in ic.walk("VEVENT"):
	t = sched.gettent(ev["LOCATION"])
	e = Item(ev["SUMMARY"])
	e.id = ev["UID"]
	e.description = ev["DESCRIPTION"]
	e.start = ev["DTSTART"].dt.timestamp()
	e.end = ev["DTEND"].dt.timestamp()
	t.append(e)

print(tostring(sched.frab(), encoding="unicode"))


os._exit(0)


for line in html.splitlines():

	line = line.strip()
	m = re.search(r"<img src=\"/icons/folder.gif\".*?href=\"(.*?)\"", line)
	#m = re.search("^((\d+\:\d+)) *(.*?)$", line)
	
	if m:
		evurl = BASE_URL + m.group(1)
		ev = fetch(evurl)
		p = FosdemEventParser()
		p.feed(ev)
		continue
		_, start, end, name = m.groups()
		#_, start, name = m.groups()
		
		if len(tent.items) > 0:
			tent.items[-1].end = maketime(start, today)
		
		if name == "Close":
			continue
		
		item = Item(name)
		item.start = maketime(start, today)
		# Start with a guess.
		if end:
			item.end = maketime(end, today)
		else:
			item.end = maketime(start, today) + datetime.timedelta(hours=1)
		
		item.links += findlinks(item)
		item.description = finddesc(item)
		
		tent.items.append(item)
	
	elif line in dates:
		today = dates[line]

	elif line:
		tent = sched.gettent(line)
		if not tent:
			tent = Tent(line)
			sched.tents.append(tent)
		

file("loveland2012.xml", "w").write(tostring(sched.xml()))
