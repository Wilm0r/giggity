#!/usr/bin/python3

import json
import os
import re

all = json.load(open("app/src/main/res/raw/menu.json", "r"))

latlon1line = re.compile(r"(\"latlon\": \[)\s+(-?[0-9.]+),\s+(-?[0-9.]+)\s+(\])")

for s in all["schedules"]:
	t = re.sub("[^a-z0-9]+", "_", s["title"].lower()).strip("_")

	indented = json.dumps(s, indent="\t", ensure_ascii=False)
	formatted = latlon1line.sub(r"\1\2, \3\4", indented)
	
	open(os.path.join("menu", t + ".json"), "w").write(formatted + "\n")
