#!/usr/bin/env python3
# coding=utf-8

"""
Giggity menu.json verifier.
Does basic verification of menu.json changes. Partially just JSON (schema)
validation, but also validation based on git diff.

Meant to be used with travis-ci.org for Github pull requests so I can stop
reviewing those updates manually.

Sadly I'll need to duplicate some things from Giggity's Java code I guess...
"""

import argparse
import datetime
import json
import os
import re
import subprocess
import sys

# version check. This script requires at least python 3.6
# Currently the only 3.6 feature that is used in this script is `encoding` in
# Popen.
if sys.version_info[:2] < (3, 6):
	raise RuntimeError("at least python 3.6 is required, you have %s" %
	                   sys.version)

import jsonschema
import PIL.Image
import urllib3


class MenuError(Exception):
	pass

SCHEMA = "ci/menu-schema.json"

parser = argparse.ArgumentParser(
	description="Validation of Giggity menu.json files based on JSON "
	            "schema and some other details.")
parser.add_argument(
	"--all", "-a", action="store_true",
	help="Validate all entries, not just the ones that changed.")
args = parser.parse_args()

g = subprocess.Popen(["tools/merge.py"],
                     stdout=subprocess.PIPE, encoding="utf-8")
raw, _ = g.communicate()
if g.returncode != 0:
	raise MenuError("Menu merger script failed")
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
		base_ref = "HEAD"
	print("Base ref: %s" % base_ref)
	g = subprocess.Popen(["tools/merge.py", "-r", base_ref],
	                     stdout=subprocess.PIPE, encoding="utf-8")
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

	certs = "/etc/ssl/certs/ca-certificates.crt"
	# Kind of ugly. Should I even check, or just not support machines
	# that don't have this file?
	kwargs = {}
	if os.path.exists(certs):
		kwargs.update({
			"cert_reqs": "CERT_REQUIRED",
			"ca_certs": certs,
		})

	o = urllib3.PoolManager(**kwargs)
	try:
		print("Fetching %s" % url)
		u = o.request("GET", url, redirect=False,
		              preload_content=False)
		if 300 <= u.status < 400:
			return FetchError(
				"URL redirected to %s, which is an unnecessary "
				"slowdown. (Also, the Android HTTP fetcher does not "
				"allow http<>https redirects.)" %
				(u.get_redirect_location()))
		elif u.status != 200:
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

	IDBUG = " https://github.com/Wilm0r/giggity/issues/134"
	if "id" not in e and e["url"].endswith(".ics"):
		errors.append(
		    ".ics entries need an \"id\" field for now "
		    "(containing the ics' X-WR-CALNAME value)." + IDBUG)

	if "id" in e and e["url"].endswith(".xml"):
		errors.append(
		    ".xml entries should not have an \"id\" field." + IDBUG)

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
todayver = int(datetime.datetime.now().strftime("%Y%m%d99"))
if new["version"] < maxver:
	errors.append("File version (%d) number must be ≥ %d (highest version in file)" % (new["version"], maxver))
elif new["version"] > todayver:
	errors.append("File version (%d) number must be ≤ %d" % (new["version"], todayver))

changed = []
base_entries = {e["url"]: e for e in base["schedules"]}
for e in new["schedules"]:
	if e["url"] in base_entries:
		if e == base_entries[e["url"]]:
			print("Unchanged: %s" % e["title"])
			base_entries.pop(e["url"])
			if not args.all:
				continue
		else:
			print("Changed: %s" % e["title"])
			if e["version"] <= base_entries[e["url"]]["version"]:
				errors.append("Version number for %r must be updated" % e["title"])
			base_entries.pop(e["url"])
			changed.append(e)
	else:
		print("New: %s" % e["title"])
		changed.append(e)
	validate_entry(e)

if changed and base:
	if new["version"] <= base["version"]:
		errors.append("File version number must be > %d (previous version)" % base["version"])
	for e in changed:
		# Not in validate_entry() because that function shouldn't itself
		# assume changes were made relative to the base version.
		if e["version"] <= base["version"]:
			errors.append("Schedule %s version number must be > %d (previous version)" % (e["title"], base["version"]))

for e in base_entries.values():
	print("Removed: %s" % e["title"])

if errors:
	print("There were some problems with this file!")
	print()
	print("\n".join(errors))
	os._exit(1)
