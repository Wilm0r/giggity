#!/usr/bin/env python3

import argparse
import datetime
import json
import operator
import os
import re
import sys

import dulwich
from dulwich.repo import Repo
from dulwich import porcelain

def check_indents(lines, fn):
	indent = None
	for num, line in enumerate(lines.splitlines()):
		if not line or not line[0].isspace():
			continue
		if not indent:
			indent = line[0]
		elif indent != line[0]:
			raise SyntaxError(
				"Inconsistent indentation: %r on %s line %d" % (line[0], fn, num + 1))
		if not re.search(r"^\t* *\S", line):
			raise SyntaxError(
				"Bad mix of whitespace on %s line %d" % (fn, num + 1))

def load_locally(path):
	all = {}
	for p, _, flist in os.walk(path):
		for fn in flist:
			text = open(os.path.join(p, fn), "r", encoding="utf-8").read()
			check_indents(text, fn)
			all[fn] = json.loads(text)

	return all

def load_git(path, revision):
	# Thanks to Jelmer Vernooij for spelling this one out for me :-D
	repo = Repo(path)
	rev = revision.encode("ascii")
	for r in repo.get_walker():
		if r.commit.id.startswith(rev):
			rev = r.commit.id
			break
	menu = porcelain.get_object_by_path(repo, "menu", rev)
	all = {}
	for name, mode, object_id in menu.iteritems():
		text = str(repo[object_id].data, "utf-8")
		check_indents(text, name)
		all[name] = json.loads(text)
	
	return all

def load_github(url):
	# Wrote this to get around GAE constraints ... then I realised
	# the Python3 environment *does* allow local fs access. :<
	import urllib3
	import tarfile
	pm = urllib3.PoolManager()

	all = {}
	u = pm.request("GET", url, preload_content=False)
	with tarfile.open(fileobj=u, mode="r|gz") as tf:
		for f in tf:
			p = f.path.split("/")
			if len(p) != 3 or p[1] != "menu" or not p[2]:
				continue
			all[p[2]] = json.loads(tf.extractfile(f).read())
	return all

def start_date(all, weeks=None):
	if not weeks:
		return ""
	dates = [datetime.datetime.strptime(e["start"], "%Y-%m-%d") for e in all.values()]
	# Using max(dates) instead of just today's date so we're a
	# little more deterministic.
	last = max(dates)
	return datetime.datetime.strftime(last - datetime.timedelta(weeks=weeks), "%Y-%m-%d")

def merge(all, first):
	out = {
		"version": 0,
		"schedules": [],
	}

	sortkey = lambda kv: operator.itemgetter("start", "end", "title")(kv[1])
	for fn, s in sorted(all.items(), key=sortkey):
		if s["start"] < first:
			# Too long ago, don't include. Maybe write a purge script
			# some day.
			print("Too old, skipped: %s" % fn, file=sys.stderr)
			continue
		out["version"] = max(out["version"], s["version"])
		out["schedules"].append(s)

	return out

def format_file(menu_json):
	indented = json.dumps(menu_json, indent="\t", ensure_ascii=False)
	latlon1line = re.compile(r"(\"latlon\": \[)\s+(-?[0-9.]+),\s+(-?[0-9.]+)\s+(\])")
	formatted = latlon1line.sub(r"\1\2, \3\4", indented)

	return formatted

if __name__ == "__main__":
	parser = argparse.ArgumentParser(
		description="Merge json fragments into a single menu.json file.")
	parser.add_argument(
		"--revision", "-r", type=str, nargs="?",
		help="git revision to read from instead of live files")
	parser.add_argument(
		"--weeks", "-w", type=int, nargs="?",
		help="max range in weeks of items to preserve (counting from last/most future one)")

	args = parser.parse_args()

	if args.revision:
		all = load_git(".", args.revision)
	else:
		all = load_locally("menu")

	print(format_file(merge(all, start_date(all, args.weeks))))
