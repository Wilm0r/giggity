#!/usr/bin/python2.4

import datetime
import re
import time

from xml.etree.ElementTree import Element, SubElement, Comment, tostring

html = file("times.html", "r").read()
html = html.replace("&amp;", "&")
html = unicode(html, "utf-8")

class GiggityObject(object):
	ids = set()

	def _idn(self, id, n):
		if n < 2:
			return id
		else:
			return "%s%d" % (id, n)	

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

	def xml(self):
		top = Element("schedule")
		top.attrib["id"] = self.id
		top.attrib["title"] = self.title
		top.attrib["xmlns"] = "http://deoxide.gaast.net/#sched"
		
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
		self.links = []
		self.id = self.makeid(name)
	
	def xml(self):
		top = Element("item")
		top.attrib["id"] = self.id
		top.attrib["title"] = self.name
		top.attrib["startTime"] = str(int(time.mktime(self.start.timetuple())))
		top.attrib["endTime"] = str(int(time.mktime(self.end.timetuple())))
		
		return top

def maketime(time):
	h, m = time.split(":")
	return datetime.datetime(2011, 8, 6, int(h), int(m))

sched = Schedule("nl.dancevalley.2011", "Dance Valley 2011")
for thtml in html.split("<strong>"):
	lines = [line.strip() for line in re.split("<.*?>", thtml) if line.strip()]
	if lines:
		tent = Tent(lines[0])
		sched.tents.append(tent)
		laststart = None
		for line in lines[1:]:
			m = re.search("^((\d+:\d+)[- ]+(\d+:\d+))? *(.*)$", line)
			_, start, end, name = m.groups()
			
			if not start or not end:
				pass
				#print "TODO: No time info :-("
			else:
				item = Item(name)
				item.start = maketime(start)
				item.end = maketime(end)
				
				if laststart and (start < laststart):
					tent = Tent("%s (MC)" % lines[0])
					sched.tents.append(tent)
				
				tent.items.append(item)
				
				laststart = start

file("dancevalley.xml", "w").write(tostring(sched.xml()))
