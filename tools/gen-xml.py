#!/usr/bin/python
# coding=utf-8

import codecs
import datetime
import hashlib
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
	
	def tentnames(self):
		return [tent.name for tent in self.tents]
	
	def gettent(self, name):
		for tent in self.tents:
			if tent.name == name:
				return tent

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
		print "Downloading %s" % url
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
	codecs.open(fn, "w", encoding="utf-8").write(html)
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
	
	#for link in re.findall(r'<a href="([^"]*)" class=l\b', html):
	for link in re.findall(r'href="/url\?q=(.*?)&amp;', html):
		if link.startswith("http"):
			ret.append(urllib2.unquote(HTMLParser.unescape.__func__(HTMLParser, link)))
	
	return ret

def findlinks(item):
	ret = []
	name = re.sub(r" *\([a-zA-Z]+\)$", "", item.name)
	name = re.sub(" LIVE$", "", name)
	
	httpname = urllib2.quote(name.encode("utf-8")).replace("%20", "+")
	url = "http://www.last.fm/music/" + httpname.encode("utf-8")
	html = fetch(url)
	if html:
		ret.append(Link(url, "lastfm"))
	
	def wpmusictest(url, m):
		url = "http://en.wikipedia.org/w/index.php?title=%s&action=edit" % m.group(1)
		html = fetch(url)
		return "{{infobox musical artist" in html.lower()
	
	misctypes = [("wikipedia", "^en\.wikipedia\.org/wiki/(.*)", wpmusictest),
	             ("myspace", "^myspace\.com/", None),
	             ("soundcloud", "^soundcloud\.com/", None),
	             ("discogs", "^discogs\.com/", None),
	             ]
	
	for type, regex, func in misctypes:
		for link in googlelinks(item):
			m = re.match("^https?://(www\.)?(.*)", link)
			short = m.group(2)
			m = re.match(regex, short)
			if m and (not func or func(link, m)):
				ret.append(Link(link, type))
				break
	
	# And in case all the other links suck..
	if len(ret) < 4:
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
		m = re.search("<div[^>]*id=\"wikiAbstract\"[^>]*>(.*?)<div[^>]*class=\"wiki-options\"[^>]*>", html, re.S)
		if m:
			desch = unicode(m.group(1), "utf-8")
			desc = dehtml(desch)
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
	
	key = re.sub(r" *\([a-zA-Z]+\)", "", item.name)
	key = re.sub(r"[^a-z0-9]", "", key.lower())
	if key not in descs:
		key += "live"
	if key in descs:
		desc = descs[key]['desc']
		if "yt" in descs[key]:
			item.links.append(Link(descs[key]['yt'], 'youtube'))
	elif not desc:
		print "No decent description for %s :-(" % item.name
	return desc


class FosdemEventParser(HTMLParser):
	def __init__(self):
		self.cur = []
		self.e = {}
		HTMLParser.__init__(self)
	
	def handle_starttag(self, tag, atts):
		self.cur.append((tag, sorted(atts)))
	
	def handle_endtag(self, tag):
		if self.cur[-1][0] != tag:
			print "WTF? %r </%s>" % (self.cur, tag)
		else:
			self.cur.pop()
	
	def handle_data(self, data):
		if not self.cur:
			return
		
		if self.cur[-1] == ("li", [("class", "active")]):
			self.e["title"] = self.e.get("title", "") + data
		if (("ul", [("class", "side-box")]) in self.cur and
		    self.cur[-1][0] == "a" and self.cur[-1][1][0][0] == "href"):
			href = self.cur[-1][1][0][1]
			if href.startswith("/2013/schedule/"):
				bits = href.split("/")
				self.e[bits[3]] = self.e.get(bits[3], "") + data


BASE_URL = "https://fosdem.org/2013/schedule/event/"
html = fetch(BASE_URL)

sched = Schedule("org.fosdem.2013", "FOSDEM 2013")
#sched = Schedule("nl.dancevalley.2011", "Dance Valley 2011")
#sched = Schedule("nl.lovelandfestival.2012", "Loveland Festival 2012")
#sched = Schedule("nl.luminosity.2012", "Luminosity Beach Festival 2012")
#sched = Schedule("uk.blocweekend.2012", "Bloc.2012")
tent = None
dates = {
	"Saturday": (2013, 2, 2),
	"Sunday": (2013, 2, 3),
}
today = (2013, 2, 2)
for line in html.splitlines():

	line = line.strip()
	m = re.search(r"<img src=\"/icons/folder.gif\".*?href=\"(.*?)\"", line)
	#m = re.search("^((\d+\:\d+)) *(.*?)$", line)
	
	if m:
		evurl = BASE_URL + m.group(1)
		ev = fetch(evurl)
		p = FosdemEventParser()
		p.feed(ev)
		print p.e
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
		

sched.linktypes.append(LinkType("google", "http://wilmer.gaa.st/deoxide/google.png"))
sched.linktypes.append(LinkType("lastfm", "http://wilmer.gaa.st/deoxide/lastfm.png"))
sched.linktypes.append(LinkType("wikipedia", "http://wilmer.gaa.st/deoxide/wikipedia.png"))
sched.linktypes.append(LinkType("myspace", "http://wilmer.gaa.st/deoxide/myspace.png"))
sched.linktypes.append(LinkType("soundcloud", "http://wilmer.gaa.st/deoxide/soundcloud.png"))
sched.linktypes.append(LinkType("discogs", "http://wilmer.gaa.st/deoxide/discogs.png"))
sched.linktypes.append(LinkType("youtube", "http://wilmer.gaa.st/deoxide/youtube.png"))

file("loveland2012.xml", "w").write(tostring(sched.xml()))
