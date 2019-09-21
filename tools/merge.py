#!/usr/bin/env python3

import argparse
import datetime
import json
import operator
import os
import re

from dulwich.repo import Repo
from dulwich import porcelain

parser = argparse.ArgumentParser(
	description="Merge json fragments into a single menu.json file.")
parser.add_argument(
	"--revision", "-r", type=str, nargs="?",
	help="git revision to read from instead of live files")
parser.add_argument(
	"--weeks", "-w", type=int, nargs="?",
	help="max range in weeks of items to preserve (counting from last/most future one)")

args = parser.parse_args()

def check_indents(lines, fn):
	indent = None
	for num, line in enumerate(lines.splitlines()):
		if not line[0].isspace():
			continue
		if not indent:
			indent = line[0]
		elif indent != line[0]:
			raise SyntaxError(
				"Inconsistent indentation: %r on %s line %d" % (line[0], fn, num + 1))
		if not re.search(r"^\t* *\S", line):
			raise SyntaxError(
				"Bad mix of whitespace on %s line %d" % (fn, num + 1))

all = {}
if not args.revision:
	for p, _, flist in os.walk("menu"):
		for fn in flist:
			text = open(os.path.join(p, fn), "r", encoding="utf-8").read()
			check_indents(text, fn)
			all[fn] = json.loads(text)
else:
	# Thanks to Jelmer Vernooij for spelling this one out for me :-D
	repo = Repo('.')
	rev = args.revision.encode("ascii")
	for r in repo.get_walker():
		if r.commit.id.startswith(rev):
			rev = r.commit.id
			break
	menu = porcelain.get_object_by_path(repo, "menu", rev)
	for name, mode, object_id in menu.iteritems():
		text = str(repo[object_id].data, "utf-8")
		check_indents(text, name)
		all[name] = json.loads(text)

if args.weeks:
	dates = [datetime.datetime.strptime(e["start"], "%Y-%m-%d") for e in all.values()]
	# Using max(dates) instead of just today's date so we're a
	# little more deterministic.
	last = max(dates)
	first = datetime.datetime.strftime(last - datetime.timedelta(weeks=args.weeks), "%Y-%m-%d")
else:
	first = ""

out = {
	"version": 0,
	"schedules": [],
}

for s in sorted(all.values(), key=operator.itemgetter("start", "end", "title")):
	if s["start"] < first:
		# Too long ago, don't include. Maybe write a purge script
		# some day.
		continue
	out["version"] = max(out["version"], s["version"])
	out["schedules"].append(s)

indented = json.dumps(out, indent="\t", ensure_ascii=False)
latlon1line = re.compile(r"(\"latlon\": \[)\s+(-?[0-9.]+),\s+(-?[0-9.]+)\s+(\])")
formatted = latlon1line.sub(r"\1\2, \3\4", indented)

print(formatted)
