#!/usr/bin/python3

import json
import re
import sys

if len(sys.argv) > 1:
	sys.stdin = open(sys.argv[1], "r")

p = json.loads(sys.stdin.read())

latlon1line = re.compile(r"(\"latlon\": \[)\s+([0-9.]+),\s+([0-9.]+)\s+(\])")
print(latlon1line.sub(r"\1\2, \3\4", json.dumps(p, indent="\t", ensure_ascii=False)))

