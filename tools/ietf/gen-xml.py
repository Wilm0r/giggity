#!/usr/bin/python
# coding=utf-8

import codecs
import datetime
import hashlib
import html5lib
import os
import re
import subprocess
import tempfile
import time
import urllib2

from HTMLParser import HTMLParser
from xml.etree.ElementTree import Element, SubElement, Comment, tostring

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
	def __init__(self, id, title):
		self.id = id
		self.title = title
		self.tents = []
		self.linktypes = []

	def xml(self):
		top = Element("schedule")
		top.attrib["id"] = self.id
		top.attrib["title"] = self.title
		top.attrib["xmlns"] = "http://gaa.st/giggity#sched"
		
		for type in self.linktypes:
			top.append(type.xml())
		for tent in self.tents:
			top.append(tent.xml())
		
		return top

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

class Link(GiggityObject):
	def __init__(self, url, type="www"):
		self.url = url
		self.type = type
	
	def xml(self):
		top = Element("itemLink")
		top.attrib["type"] = self.type
		top.attrib["href"] = self.url
		return top

class LinkType(GiggityObject):
	def __init__(self, id, icon):
		self.id = id
		self.icon = icon

	def xml(self):
		top = Element("linkType")
		top.attrib["id"] = self.id
		top.attrib["icon"] = self.icon
		return top

def maketime(time):
	global day
	h, m = (time[0:2], time[2:4])
	return datetime.datetime(2011, 11, day, int(h), int(m))

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
		except urllib2.HTTPError, e:
			f.write("%d\n\n" % (e.code,))
			return None
		
		body = u.read()
		f.write("%d\n\n%s" % (u.code, body))
	
	return body

def htmlfind(tree, tag, attrs={}, maxlevel=-1):
	ret = []
	
	for ch in tree.childNodes:
		if ch.name == tag:
			for attr, val in attrs.iteritems():
				if ch.attributes.get(attr, None) != val:
					break
			else:
				ret.append(ch)
		
		if maxlevel != 0:
			ret += htmlfind(ch, tag, attrs, maxlevel - 1)
	
	return ret

def getinside(node):
	# FFFFUUUUU - am I just doing it wrong? Maybe I need a different tree class..
	return re.sub("\s+", " ", node.toxml().split('>', 1)[1].rsplit('<', 1)[0].strip())

def getroom(node):	
	links = htmlfind(node, "a")
	for link in links:
		href = link.attributes.get("href", "")
		if "/?room=" in href:
			return getinside(link)
	return None

html = file("agenda.html", "r").read()
html = unicode(html, "utf-8")
html = html5lib.parse(html)

tbl = htmlfind(html, "table", {"id": "agenda"})
rows = htmlfind(tbl[0], "tr", maxlevel=2)
rooms = {}

sched = Schedule("org.ietf.82", "IETF 82 Taipei")

for row in rows:
	rclass = row.attributes.get("class", None)
	if rclass == "meeting-date":
		date = htmlfind(row, "h2")
		date = getinside(date[0])
		m = re.search("(\d+),", date)
		day = int(m.group(1))
	elif rclass == "grouprow":
		room = getroom(row)
		cols = htmlfind(row, "td", maxlevel=2)
		colt = [getinside(col) for col in cols]
		area = colt[1]
		wg = re.sub("<.*?>", "", colt[2])
		full = htmlfind(row, "a", {"title": "session agenda"})
		if not full:
			# This seems to happen with canceled events..
			continue
		fullname = getinside(full[0])
		
		wga = htmlfind(cols[2], "a")
		links = []
		if wga:
			links.append(Link("https://datatracker.ietf.org" + wga[0].attributes["href"], "link"))
		
		ag_url = full[0].attributes["href"]
		agenda = None
		if ag_url.endswith(".txt"):
			try:
				agenda = unicode(fetch(ag_url), "utf-8")
			except UnicodeDecodeError, e:
				agenda = unicode(fetch(ag_url), "iso8859-1")
		else:
			links.append(Link(ag_url, "link"))
		
		if room in rooms:
			roomo = rooms.get(room, None)
		else:
			roomo = Tent(room)
			sched.tents.insert(0, roomo)
			rooms[room] = roomo
		
		item = Item("%s - %s (%s)" % (wg, fullname, area))
		item.id = row.attributes["id"]
		item.start = slot_start
		item.end = slot_end
		item.description = agenda
		item.links = links
		roomo.items.append(item)
		
		pass
	elif rclass == "groupagenda":
		pass
	else:
		td = htmlfind(row, "td")[0]
		room = getroom(td)
		text = re.sub("<.*?>", "", getinside(td))
		
		desc = None
		if len(text) > 128:
			bold = htmlfind(row, "b")
			text = getinside(bold[0])
			desc = re.sub("<.*?>", "", td.toxml())
		
		times = re.search("(\d{4})-(\d{4})", text)
		if times:
			slot_start = maketime(times.group(1))
			slot_end = maketime(times.group(2))
			text = text.replace(times.group(0), "")

		if room:
			text = text.replace(room, "")

		text = text.strip(" -")
		
		if room and times:
			if room in rooms:
				roomo = rooms.get(room, None)
			else:
				roomo = Tent(room)
				sched.tents.append(roomo)
				rooms[room] = roomo
			
			item = Item(text)
			item.start = slot_start
			item.end = slot_end
			if desc:
				item.description = desc
			roomo.items.append(item)
		
		print (room, slot_start, text)

sched.linktypes.append(LinkType("link", "http://wilmer.gaa.st/deoxide/konq.png"))

sortlist = [rooms[r] for r in sorted(rooms.iterkeys())]
sched.tents = sortlist
print sorted(rooms.iterkeys())

file("ietf82.xml", "w").write(tostring(sched.xml()))
