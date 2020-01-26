#!/usr/bin/env python3

import json
import pprint
import re
import sys

def strip(name):
	return re.sub("[^a-z0-9]", "", name.replace("Assembly:", "").lower(), flags=re.I)

mappings = json.load(open("mappings.35c3.json", "r"))
mappings.update(json.load(open("mappings.36c3.json", "r")))
mappings.update({
	"Art-and-Play Installation": "ap-stage",
	"Assembly:Anarchist Village": "anarchist-assembly",
	"Assembly:Art-and-Play": "artandplay",
	"Assembly:C-base / CCC-B / xHain": "xhain",
	"Assembly:Foodhackingbase": "fhb",
	"Assembly:WikipakaWG": "wikipaka-living",
	"DLF- und Podcast-Bühne": "dlf-sendezentrum",
	"OIO Solder Area": "oio-workshop",
	"OIO Workshop Dome": "oio-workshop-dome",
	"Vintage Computing Cluster": "vintage",
})
smappings = {strip(k): v for k, v in mappings.items()}

c3_byname = {}
c3_locs = json.loads(open("locations.json", "r").read())
for l in c3_locs:
	stript = strip(l["title"])
	if stript in c3_byname:
		#print(stript, l, c3_byname[stript])
		pass
	else:
		c3_byname[stript] = l

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
