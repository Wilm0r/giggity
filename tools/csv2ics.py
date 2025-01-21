#!/usr/bin/env python3
# Copyright 2023, François Revol <revol@free.fr>
# MIT license
#
# csv2ics: convert a sensible schedule table to an icalendar program
# Initially wrote for https://2023.eurobsdcon.org/program/

import csv
from datetime import datetime, timedelta
import pytz
import re
import sys
from icalendar import Calendar, Event


# TODO: pass this as arguments
cal_name = "EuroBSDCon 2023"
timezone = "Europe/Lisbon"
day_filter = "(?P<event_type>.*)s:.*\((?P<day_str>.*)\)"
# You might need to run this with LC_TIME=C to parse English dates
day_format = "%d %B %Y"
time_filter = "(?P<start_time>\d{2}:\d{2})( *- *)?(?P<end_time>\d{2}:\d{2})?"

stamp = str(datetime.now().timestamp())


with open(sys.argv[1], newline='') as csvfile:
	reader = csv.reader(csvfile, delimiter=',', quotechar='"')
	cal = Calendar()
	cal.add('prodid', '-//csv2ics.py////')
	cal.add('version', '2.0')
	cal.add('name', cal_name)
	cal.add('x-wr-calname', cal_name)
	cal.add('timezone-id', timezone)
	cal.add('x-wr-timezone', timezone)
	tzinfo = pytz.timezone(timezone)
	day = None
	event_type = None
	locations = [] # aka Tracks for conferences
	last_time = None
	pending_events = []
	uidgen = 0
	for row in reader:
		if len(row[0].strip()) == 0:
			locations = row[1:]
			# flush pending for previous day, assume 1h
			for e in pending_events:
				print("pending pending: %s" % e)
				end_time = start_time + timedelta(hours=1)
				e.add('dtend', datetime.combine(day, end_time.time(), tzinfo=tzinfo))
				cal.add_component(e)
			pending_events = []

		m = re.match(day_filter, row[0])
		#if len(row[0]) and row[0][0].isdigit():
		if m is not None:
			event_type = m.group('event_type')
			day = datetime.strptime(m.group('day_str'), day_format)
			print(day)
			print(event_type)
		#m = re.match("(?P<start_time>\d{2}:\d{2})")
		m = re.match(time_filter, row[0])
		if m:
			start_time = m.group('start_time')
			end_time = m.group('end_time') or None
			start_time = datetime.strptime(start_time, "%H:%M")
			#start_time = timedelta(start_time)
			if end_time:
				end_time = datetime.strptime(end_time, "%H:%M")
				#end_time = timedelta(end_time)

			# flush pending
			for e in pending_events:
				print("pending pending: %s" % e)
				e.add('dtend', datetime.combine(day, start_time.time(), tzinfo=tzinfo))
				cal.add_component(e)
			pending_events = []
			print("##%s" % str(m.groups()))
			print("## %s %s" % (start_time, end_time))
			for i, entry in enumerate(row[1:]):
				# skip fields with just a NO-BREAK SPACE or BOM
				if entry in ['\u00a0', '\uFEFF']:
					entry = ""
				entry = entry.strip()
				print("#### %d %s" % (len(entry), entry))
				if len(entry) < 1:
					continue
				print("## %d %s, %s" % (i, locations[i], entry))
				lines = entry.split('\n')
				e = Event()
				e.add('summary', lines[0])
				e.add('dtstart', datetime.combine(day, start_time.time(), tzinfo=tzinfo))
				if len(lines) > 1:
					desc = "(%s)\n" % event_type
					desc += "\n".join(lines[1:]).strip('\n')
					e.add('description', desc)
				e.add('location', locations[i])
				e.add('uid', "%s-%d" % (stamp, uidgen))
				uidgen += 1
				if end_time is None:
					# we don't know the end time yet
					print("pending += %s" % str(e))
					pending_events.append(e)
				else:
					e.add('dtend', datetime.combine(day, end_time.time(), tzinfo=tzinfo))
					cal.add_component(e)
		print('|\n'.join(row))
	for e in pending_events:
		# Assume last events are an hour long
		print("pending pending: %s" % e)
		end_time = start_time + timedelta(hours=1)
		e.add('dtend', datetime.combine(day, end_time.time(), tzinfo=tzinfo))
		cal.add_component(e)

	with open(sys.argv[2], 'wb') as icsfile:
		icsfile.write(cal.to_ical())

