#!/usr/bin/python2.4

# Generic ics parser at the moment, may make some Lowlands specific changes
# later (fetching descriptions, links, etc.).

import cgi
import gdbm
import re
import sys
import time

descs = gdbm.open('descriptions.pag')

def date_mess(key, value):
	# So fuzzy, brrr
	if k.endswith('VALUE=DATE-TIME'):
		m = re.match('(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})', v)
		if m:
			#date = '%s-%s-%sT%s:%s:%s' % m.groups()
			tup = tuple([int(x) for x in m.groups()] + [0, 0, -1])
			date = int(time.mktime(tup))
		else:
			print 'Can\'t parse timestamp: ' + (k, v)
	else:
		print 'Can\'t parse timestamp: ' + (k, v)
		date = 'GRRR'

	return date

lines = sys.stdin.readlines()

locorder = list()
perloc = dict()
ev = dict()
for l in lines:
	l = l.rstrip()
	if l.startswith("BEGIN:VEVENT"):
		# Flush all the info we saved and start collecting info for
		# a new event.
		ev = dict()
	elif l.startswith("END:VEVENT"):
		# Store the event info in the per-location dict.
		if ev['location'] not in locorder:
			locorder.append(ev['location'])
			perloc[ev['location']] = list()
		perloc[ev['location']].append(ev)
	else:
		(k, v) = l.split(':', 1)
		if k.startswith('DTSTART'):
			ev['startTime'] = date_mess(k, v)
		elif k.startswith('DTEND'):
			ev['endTime'] = date_mess(k, v)
		else:
			ev[k.lower()] = v

print '<schedule id="nl.lowlands.foo" title="Lowlands" xmlns="http://deoxide.gaast.net/#sched">'
print '\t<linkType id="www" icon="http://wilmer.gaast.net/deoxide/konq.png" />'
print '\t<linkType id="lastfm" icon="http://wilmer.gaast.net/deoxide/lastfm.png" />'

for loc in locorder:
	print '\t<line id="%s" title="%s">' % (cgi.escape(loc), cgi.escape(loc))
	for ev in sorted(perloc[loc], lambda x, y: cmp(x['startTime'], y['startTime'])):
		if ev['startTime'] > 1218873600:
			continue
		print '\t\t<item id="%s" title="%s" startTime="%s" endTime="%s">' % (
			cgi.escape(ev['uid']), cgi.escape(ev['summary']),
			ev['startTime'], ev['endTime'])
		try:
			print '\t\t\t<itemDescription>' + descs['desc/'+ev['summary'].lower()] + '</itemDescription>'
		except:
			pass
		try:
			print '\t\t\t<itemLink type="www" href="' + descs['www/'+ev['summary'].lower()] + '" />'
		except:
			pass
		try:
			print '\t\t\t<itemLink type="lastfm" href="' + descs['lastfm/'+ev['summary'].lower()] + '" />'
		except:
			pass
		print '\t\t</item>'

	print '\t</line>'

print '</schedule>'
