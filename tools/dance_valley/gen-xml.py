#!/usr/bin/python

import datetime
import hashlib
import os
import re
import subprocess
import tempfile
import time
import urllib2

from xml.etree.ElementTree import Element, SubElement, Comment, tostring
from xml.sax.saxutils import unescape

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
	h, m = time.split(":")
	return datetime.datetime(2011, 8, 6, int(h), int(m))

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

def dehtml(html):
	fn = tempfile.mktemp(suffix=".html")
	file(fn, "w").write(html)
	w3m = subprocess.Popen(["/usr/bin/w3m", "-cols", "100000", "-dump", fn], stdout=subprocess.PIPE)
	text = unicode(w3m.stdout.read(), "utf-8")
	w3m.communicate()
	os.unlink(fn)
	return text

def googlelinks(item):
	ret = []
	httpname = urllib2.quote(item.name.encode("utf-8")).replace("%20", "+")
	url = "http://www.google.com/search?num=20&q=%s" % httpname.encode("utf-8")
	html = fetch(url)
	
	for link in re.findall(r'<a href="([^"]*)" class=l\b', html):
		if link.startswith("http"):
			ret.append(link.replace("&amp;", "&"))
	
	return ret

def findlinks(item):
	ret = []
	
	httpname = urllib2.quote(item.name.encode("utf-8")).replace("%20", "+")
	url = "http://www.last.fm/music/" + httpname.encode("utf-8")
	html = fetch(url)
	if html:
		ret.append(Link(url, "lastfm"))
	
	misctypes = [("wikipedia", "^en\.wikipedia\.org/"),
	             ("myspace", "^myspace\.com/"),
	             ("discogs", "^discogs\.com/"),
	             ]
	
	for type, regex in misctypes:
		for link in googlelinks(item):
			m = re.match("^https?://(www\.)?(.*)", link)
			short = m.group(2)
			if re.match(regex, short):
				ret.append(Link(link, type))
				break
	
	# And in case all the other links suck..
	ret.append(Link("http://www.google.com/search?q=%s" % httpname, "google"))

	return ret

def finddesc(item):
	desc = None
	url = None
	tags = []
	
	for link in item.links:
		if link.type == "lastfm":
			url = link.url
			break
	
	if url:
		html = fetch(url)
		m = re.search("<div id=\"wikiAbstract\">(.*?)<div class=\"wikiOptions\">", html, re.S)
		if m:
			desc = dehtml(m.group(1))
		else:
			print "No decent description for %s, removing last.fm link.." % item.name
			item.links = [link for link in item.links if link.url != url]
		
		m = re.search("Popular tags:(.*?)</div>", html, re.S)
		if m:
			for tag in re.findall("<a.*?>(.*?)</a>", m.group(1)):
				if '<' not in tag:
					tags.append(tag)
			
		if desc and tags:
			desc += "\nTags: %s" % ", ".join(tags)
	
	return desc

html = file("times.html", "r").read()
html = html.replace("&amp;", "&")
html = unicode(html, "utf-8")

#sched = Schedule("nl.dancevalley.2011", "Dance Valley 2011")
sched = Schedule("90428e2c6f63e2661cf2c086e465195785613d44", "Dance Valley 2011")
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
				tent.items[-1].name += " %s," % name
				pass
			else:
				item = Item(name)
				item.start = maketime(start)
				item.end = maketime(end)
				
				item.links += findlinks(item)
				item.description = finddesc(item)
				
				if laststart and (start < laststart):
					tent = Tent("%s (MC)" % lines[0])
					sched.tents.append(tent)
				laststart = start
				
				tent.items.append(item)

sched.linktypes.append(LinkType("google", "http://wilmer.gaa.st/deoxide/google.png"))
sched.linktypes.append(LinkType("lastfm", "http://wilmer.gaa.st/deoxide/lastfm.png"))
sched.linktypes.append(LinkType("wikipedia", "http://wilmer.gaa.st/deoxide/wikipedia.png"))
sched.linktypes.append(LinkType("myspace", "http://wilmer.gaa.st/deoxide/myspace.png"))
sched.linktypes.append(LinkType("discogs", "http://wilmer.gaa.st/deoxide/discogs.png"))

file("dancevalley.xml", "w").write(tostring(sched.xml()))
