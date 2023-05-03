#!/usr/bin/env python3
# coding=utf-8

"""
Giggity menu.json verifier.
Does basic verification of menu.json changes. Partially just JSON (schema)
validation, but also validation based on git diff.

Meant to be used with Github Actions for pull requests so I can stop
reviewing those updates manually.

Sadly I'll need to duplicate some things from Giggity's Java code I guess...
"""

import argparse
import datetime
import json
import os
import re
import sys
import time
import zoneinfo

import jsonschema
import PIL.Image
import urllib3

import email.utils

import merge

class MenuError(Exception):
	pass

class Log():
	def __init__(self, fn=None):
		self.errors = 0
		self._colour = sys.stdout.isatty()
		self.md = None
		if fn is not None:
			self.md = open(fn, "x")

	def C(self, text):  # Console-only, just for progress updates.
		print(text)

	def I(self, text):  # INFO
		if self._colour:
			print("\033[1m" + text + "\033[0m")
		elif text:
			print("* " + text)
		if self.md: self.md.write(text + "\n")

	def E(self, text):  # ERROR
		self.errors += 1
		if self._colour:
			print("\033[91m" + text + "\033[0m")
		else:
			print("ERROR: " + text)
		if self.md: self.md.write("**Error:** " + text + "\n")


SCHEMA = "tools/menu-schema.json"

parser = argparse.ArgumentParser(
	description="Validation of Giggity menu.json files based on JSON "
	            "schema and some other details.")
parser.add_argument(
	"--all", "-a", action="store_true",
	help="Validate all entries, not just the ones that changed.")
parser.add_argument(
	"--base", "-b",	default="HEAD", help="Base ref to diff against.")
parser.add_argument(
	"--load-new-from", help="Load new menu from specified path, and diff it against local menu.")
parser.add_argument(
	"--stderr", action="store_true",
	help="All output to stderr, useful when running inside a crappy GitHub Actions environment but you may want to *gasp* see some actual output.")
parser.add_argument(
	"--summary_comment", default=None,
	help="Filename (must not yet exist) to write markdown-formatted comment to. (For Github Action handler.)")
args = parser.parse_args()

if args.stderr:
	sys.stdout = sys.stderr

LOG = Log(args.summary_comment)

try:
	if args.load_new_from:
		new = merge.merge(merge.load_locally(args.load_new_from), "")
	else:
		new = merge.merge(merge.load_locally("menu"), "")
except Exception as e:
	LOG.E("Merge/parse failure while loading menu.")
	raise

base_ref = args.base
LOG.C("Base ref: %s" % base_ref)

try:
	if args.load_new_from:
		base = merge.merge(merge.load_locally("menu"), "")
	else:
		base = merge.merge(merge.load_git(".", base_ref), "")
except Exception as e:
	LOG.E("Merge/parse failure while loading baseline menu. Very confused now.")
	raise

schema = json.load(open(SCHEMA, "r"))
jsonschema.validate(new, schema)


class FetchError(Exception):
	pass


class HTTP():
	def __init__(self):
		certs = "/etc/ssl/certs/ca-certificates.crt"
		if os.path.exists(certs):
			self.o = urllib3.PoolManager(cert_reqs="CERT_REQUIRED", ca_certs=certs)
		else:
			LOG.I("HTTPS certificate verification disabled since %s seems to be missing." % certs)
			self.o = urllib3.PoolManager()

		self.hcache = {}

	def fetch(self, url, img=False, head=False):
		"""Simple URL fetcher, will return bytes or a parsed image depending
		on what you ask for. Or an exception (returned, not raised :-P)."""

		method = head and "HEAD" or "GET"
		try:
			LOG.C("Fetching %s" % url)
			u = self.o.request(
				method, url, redirect=False, preload_content=False)
			if 300 <= u.status < 400:
				diff_protocol = ""
				if not u.get_redirect_location().startswith(
					url.split("//", 1)[0]):
					diff_protocol = (" (Also, the Android HTTP "
						"fetcher does not allow http<>https redirects.)")
				return FetchError(
					"URL redirected to %s, which is an unnecessary "
					"slowdown.%s" %
					(u.get_redirect_location(), diff_protocol))
			elif u.status != 200:
				return FetchError("%d %s" % (u.status, u.reason))
			self.hcache[url] = {k.lower(): v for k, v in u.getheaders().iteritems()}
			self.hcache[url]["_ts"] = time.time()
			if not img:
				return u.read()
			else:
				return PIL.Image.open(u)
		except urllib3.exceptions.HTTPError as e:
			error = str(e.reason)
			return FetchError(error)

	def cache_sensible(self, url):
		"""Check whether it ever returns 304s. Yeah sure, this is a race. I
		don't care for a test that runs a few times a week at most."""
		if url not in self.hcache:
			return None
		if "last-modified" in self.hcache[url]:
			ims = self.hcache[url]["last-modified"]
		else:
			ims = email.utils.formatdate(self.hcache[url]["_ts"], localtime=False, usegmt=True)
		headers = {"If-Modified-Since": ims}
		# GET not HEAD because the CCC webserver is fucking retarded.
		u = self.o.request(
			"GET", url, headers=headers, redirect=False, preload_content=False)
		return u.status == 304


http = HTTP()


def validate_url(url):
	if not url.startswith("http:"):
		return []
	https = re.sub("^http:", "https:", url)
	if http.fetch(https):
		return ["Use URL %s instead of non-TLS HTTP?" % https]
	else:
		return ["URL %s is insecure (but no TLS version available?)" % url]


def validate_entry(e):
	ret = []
	
	# Forced to use GET not HEAD even though I don't need the data because for
	# example the retarded CCC server refuses HEAD requests.
	sf = http.fetch(e["url"])
	ret += validate_url(e["url"])
	if isinstance(sf, FetchError):
		ret.append("Could not fetch %s %s: %s" % (e["title"], e["url"], str(sf)))

	if e["end"] < e["start"]:
		ret.append("Conference ends (%(end)s) before it starts (%(start)s)?" % e)
	if e["end"] < datetime.datetime.now().strftime("%Y-%m-%d"):
		ret.append("Conference already ended (%(end)s)?" % e)

	if "timezone" in e:
		try:
			zoneinfo.ZoneInfo(e["timezone"])
		except zoneinfo._common.ZoneInfoNotFoundError:
			ret.append("Timezone does not exist: %s" % e["timezone"])
	elif e["version"] >= 2021091100:
		ret.append("The timezone property is now required.")

	md = e.get("metadata")
	if md:
		c3_by_slug = {}
		if "c3nav_base" in md:
			c3nav = http.fetch(md["c3nav_base"] + "/api/locations/?format=json")
			ret += validate_url(md["c3nav_base"] + "/api/locations/?format=json")
			if isinstance(c3nav, FetchError):
				ret.append("Could not fetch %s %s: %s" % (e["title"], e["url"], str(c3nav)))
			else:
				c3nav = json.loads(c3nav)
				c3_by_slug = {v["slug"]: v for v in c3nav}

		for room in md.get("rooms", []):
			# Forgot why I wrote this check initially, it's now also in the schema already..
			if not set(room) & {"latlon", "c3nav_slug"}:
				ret.append("Missing latlon entry in %s→%s" % (e["title"], room.get("show_name", room.get("name"))))
			
			if "c3nav_slug" in room and room["c3nav_slug"] not in c3_by_slug:
				ret.append("c3nav room %s not listed in /api/locations/" % room["c3nav_slug"])

			try:
				re.compile(room.get("name"))
			except re.error as err:
				ret.append("Room name %r not a valid regular expression: %s" % (room.get("name"), err))
	
		if "icon" in md:
			img = http.fetch(md["icon"], img=True)
			ret += validate_url(md["icon"])
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
				ret.append("Icon for %s seems bad: %s" % (e["title"], ", ".join(icon_errors)))
		
		if "links" in md:
			for link in md["links"]:
				if link["url"].startswith("geo:"):
					continue
				d = http.fetch(link["url"])
				ret += validate_url(link["url"])
				if isinstance(d, FetchError):
					ret.append("Link \"%s\" appears broken: %s" % (link["title"], str(d)))

	# Check schedule file, id for [xi]cal submissions
	# Title match would be nice but that would require duplicating parsing :-(
	# Maybe check URLs and types? Why not
	# Hrmm, checks for 304/I-M-S support would be nice..
	return ret

maxver = max(e["version"] for e in new["schedules"])
todayver = int(datetime.datetime.now().strftime("%Y%m%d99"))
if new["version"] < maxver:
	LOG.E("File version (%d) number must be ≥ %d (highest version in file)" % (new["version"], maxver))
elif new["version"] > todayver:
	LOG.E("File version (%d) number must be ≤ %d" % (new["version"], todayver))

changed = []
base_entries = {e["url"]: e for e in base.get("schedules", [])}
seen = set()
for e in new["schedules"]:
	if e["url"] in seen:
		LOG.E("Duplicate URL, unable to diff: %r %s" %
		      ([x["title"] for x in new["schedules"] if x["url"] == e["url"]], e["url"]))
		continue
	seen.add(e["url"])
	if e["url"] in base_entries:
		if e == base_entries[e["url"]]:
			LOG.C("Unchanged: %s" % e["title"])
			base_entries.pop(e["url"])
			if not args.all:
				continue
		else:
			LOG.I("Changed: %s" % e["title"])
			if e["version"] <= base_entries[e["url"]]["version"]:
				LOG.E("Version number for %r must be updated" % e["title"])
			base_entries.pop(e["url"])
			changed.append(e)
	else:
		LOG.I("New: %s" % e["title"])
		changed.append(e)
	
	entry_issues = validate_entry(e)
	if entry_issues:
		for err in entry_issues:
			LOG.E(err)
		LOG.I("")

if changed and base:
	if new["version"] <= base["version"]:
		LOG.E("File version number must be > %d (previous version)" % base["version"])
	for e in changed:
		# Not in validate_entry() because that function shouldn't itself
		# assume changes were made relative to the base version.
		if e["version"] <= base["version"]:
			LOG.E("Schedule %s version number must be > %d (previous version)" % (e["title"], base["version"]))

for e in changed:
	if e.get("refresh_interval", 86400) < 86400 and not http.cache_sensible(e["url"]):
		LOG.E("Schedule %s refresh_interval set below 1d while server never sends HTTP 304s" % e["title"])

for e in base_entries.values():
	LOG.I("Removed: %s" % e["title"])

if LOG.errors:
	LOG.E("File validation failed! :-( Please correct the issues listed above.")
else:
	LOG.I("File passed validation! \\o/")
sys.exit(min(LOG.errors, 1))
