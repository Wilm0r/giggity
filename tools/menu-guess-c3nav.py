#!/usr/bin/env python3

import json
import pprint
import re
import sys
import xml.etree.ElementTree as ET

# mappings = json.load(open("mappings.35c3.json", "r"))
# mappings.update(json.load(open("mappings.36c3.json", "r")))
# mappings.update({
# 	"Art-and-Play Installation": "ap-stage",
# 	"Assembly:Anarchist Village": "anarchist-assembly",
# 	"Assembly:Art-and-Play": "artandplay",
# 	"Assembly:C-base / CCC-B / xHain": "xhain",
# 	"Assembly:Foodhackingbase": "fhb",
# 	"Assembly:WikipakaWG": "wikipaka-living",
# 	"DLF- und Podcast-Bühne": "dlf-sendezentrum",
# 	"OIO Solder Area": "oio-workshop",
# 	"OIO Workshop Dome": "oio-workshop-dome",
# 	"Vintage Computing Cluster": "vintage",
# })
# smappings = {strip(k): v for k, v in mappings.items()}

out = []
# https://37c3.c3nav.de/api/v2/auth/session/ for key
# wget -O- --header "X-API-Key: session:yb6g7n0a07yvsllk4obh6f3v207wklwt" https://37c3.c3nav.de/api/v2/map/locations/?searchable=true | jq | less
c3_locs = json.loads(open("locations.json", "r").read())
search = {}
for l in c3_locs:
	for t in l.get("add_search", "").split():
		search[t] = l["slug"]

sch = ET.parse('/tmp/schedule.xml')
for d in sch.getroot():
	if d.tag == "day":
		for r in d:
			guid = r.attrib.get("guid", "#*#@(#@*&#@)")
			if guid in search:
				print(r.attrib["name"], search[guid])
				out.append({
					"name": re.escape(r.attrib["name"]).replace(r"\ ", " "),
					"show_name": r.attrib["name"],
					"c3nav_slug": search[guid],
				})
				del search[guid]
			else:
				print("No: " + r.attrib["name"])


out.sort(key=lambda x: x["show_name"])
print(json.dumps(out, indent="\t"))
oldfile = json.load(open(sys.argv[1], "r"))

orig = oldfile["metadata"]["rooms"]

new = []
for room in open("rooms.txt", "r"):
	room = room.strip()
	out = {}
	for o in orig:
		if re.search("^%s$" % o["name"], room):
			out.update(o)
			break
	slug = None
	if room in mappings:
		slug = mappings[room]
	elif strip(room) in smappings:
		slug = smappings[strip(room)]
	elif strip(room) in c3_byname:
		slug = c3_byname[strip(room)]["slug"]
	if not slug:
		print("No clue: %s %s" % (room, strip(room)))
		continue
	out.update({
		"name": re.escape(room),
		"c3nav_slug": slug,
	})
	#print (room, slug)
	new.append(out)

newfile = oldfile  # yeah not a deepcopy..
newfile["metadata"]["rooms"] = new
open(sys.argv[1], "w").write(json.dumps(newfile, indent="\t", ensure_ascii=False))
