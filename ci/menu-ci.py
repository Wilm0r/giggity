#!/usr/bin/python3
# coding=utf-8

"""
Giggity menu.json verifier.
Does basic verification of menu.json changes. Partially just JSON (schema)
validation, but also validation based on git diff.

Meant to be used with travis-ci.org for Github pull requests so I can stop
reviewing those updates manually.

Sadly I'll need to duplicate some things from Giggity's Java code I guess...
"""


import json
import os
import re
import subprocess
import sys

# version check. This script requires at least python 3.6
# Currently the only 3.6 feature that is used in this script is `encoding` in
# Popen.
if sys.version_info[:2] < (3, 6):
    raise RuntimeError("at least python 3.6 is required")

import jsonschema
import PIL.Image
import urllib3


class MenuError(Exception):
	pass

MENU = "app/src/main/res/raw/menu.json"
SCHEMA = "ci/menu-schema.json"

raw = open(MENU, "r").read()
try:
	new = json.loads(raw)
except ValueError as e:
	raise MenuError("JSON parse failure %r" % e)

try:
	env = os.environ
	travis_vars = sorted("%s=%s" % (k, v) for k, v in env.items() if k.startswith("TRAVIS_"))
	if travis_vars:
		print("\n".join(travis_vars))
	if env.get("TRAVIS_EVENT_TYPE") == "pull_request":
		base_ref = env["TRAVIS_BRANCH"]
	elif "TRAVIS_COMMIT_RANGE" in env:
		base_ref = env["TRAVIS_COMMIT_RANGE"].split(".")[0]
	else:
		base_ref = "master"
	print("Base ref: %s" % base_ref)
	g = subprocess.Popen(["git", "show", "%s:%s" % (base_ref, MENU)], stdout=subprocess.PIPE, encoding="utf-8")
	base_raw, _ = g.communicate()
	base = None
	if g.returncode == 0:
		base = json.loads(base_raw)
	else:
		print("Failed to read base version, going to skip diffing.")
except ValueError as e:
	raise MenuError("JSON parse failure (in baseline!) %r" % e)

schema = json.load(open(SCHEMA, "r"))

jsonschema.validate(new, schema)

errors = []


class FetchError(Exception):
	pass


def fetch(url, img=False):
	"""Simple URL fetcher, will return text or a parsed image depending
	on what you ask for. Or an exception (returned, not raised :-P)."""
	o = urllib3.PoolManager()
	try:
		print("Fetching %s" % url)
		u = o.request("GET", url, preload_content=False)
		if u.status != 200:
			# Hmm, any way to make urllib3 throw an exception
			# on non-success responses?
			return FetchError("%d %s" % (u.status, u.reason))
		if not img:
			return u.read()
		else:
			return PIL.Image.open(u)
	except urllib3.exceptions.HTTPError as e:
		error = str(e.reason)
		return FetchError(error)


def validate_entry(e):
	sf = fetch(e["url"])
	if isinstance(sf, FetchError):
		errors.append("Could not fetch %s %s: %s" % (e["title"], e["url"], str(sf)))

	md = e.get("metadata")
	if md:
		if "c3nav_base" in md:
			c3nav = fetch(md["c3nav_base"] + "/api/locations/?format=json")
			if isinstance(c3nav, FetchError):
				errors.append("Could not fetch %s %s: %s" % (e["title"], e["url"], str(c3nav)))
				c3_by_slug = {}
			else:
				c3nav = json.loads(c3nav)
				c3_by_slug = {v["slug"]: v for v in c3nav}

		for room in md.get("rooms", []):
			# Forgot why I wrote this check initially, it's now also in the schema already..
			if not set(room) & {"latlon", "c3nav_slug"}:
				errors.append("Missing latlon entry in %s→%s" % (e["title"], room.get("show_name", room.get("name"))))
			
			if "c3nav_slug" in room and room["c3nav_slug"] not in c3_by_slug:
				errors.append("c3nav room %s not listed in /api/locations/" % room["c3nav_slug"])
	
		if "icon" in md:
			img = fetch(md["icon"], img=True)
			icon_errors = []
			if isinstance(img, FetchError):
				icon_errors.append(str(img))
			else:
				if 'A' not in img.mode:
					icon_errors.append("%s image has no alpha layer" % img.mode)
				if img.width != img.height:
					icon_errors.append("%d×%d image is not square" % (img.width, img.height))
				if img.width < 64:
					icon_errors.append("%d×%d image is too small" % (img.width, img.height))
				elif img.width > 512:
					icon_errors.append("%d×%d image is too large" % (img.width, img.height))
			if icon_errors:
				errors.append("Icon for %s seems bad: %s" % (e["title"], ", ".join(icon_errors)))
		
		if "links" in md:
			for link in md["links"]:
				d = fetch(link["url"])
				if isinstance(d, FetchError):
					errors.append("%s link for %s appears broken: %s" % (link["title"], e["title"], str(d)))

	# Check schedule file, id for [xi]cal submissions
	# Title match would be nice but that would require duplicating parsing :-(
	# Maybe check URLs and types? Why not
	# Hrmm, checks for 304/I-M-S support would be nice..


if re.search(r"^\t* ", raw, flags=re.M):
	errors.append("File must be tab-indented")

maxver = max(e["version"] for e in new["schedules"])
if new["version"] < maxver:
	errors.append("File version number must be ≥ %d" % maxver)

base_entries = {e["url"]: e for e in base["schedules"]}
for e in new["schedules"]:
	if e["url"] in base_entries:
		if e == base_entries[e["url"]]:
			print("Unchanged: %s" % e["title"])
			base_entries.pop(e["url"])
			continue
		else:
			print("Changed: %s" % e["title"])
			if e["version"] <= base_entries[e["url"]]["version"]:
				errors.append("Version number for %r must be updated" % e["title"])
			base_entries.pop(e["url"])
	else:
		print("New: %s" % e["title"])
	validate_entry(e)

for e in base_entries.values():
	print("Removed: %s" % e["title"])

if errors:
	print("There were some problems with this file!")
	print()
	print("\n".join(errors))
	os._exit(1)
