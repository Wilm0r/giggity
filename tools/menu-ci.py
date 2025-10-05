#!/usr/bin/env python3
# coding=utf-8
# (Honestly wondering whether to disable 99% OCD pylint instead.)
# pylint: disable=consider-using-f-string, missing-function-docstring, no-else-break, no-else-return, no-else-raise

"""
Giggity menu.json verifier.
Does basic verification of menu.json changes. Partially just JSON (schema)
validation, but also more thorough entry validation based on git diff.

Meant to be used with Github Actions for pull requests so I can stop
reviewing those updates manually.
"""

import argparse
import atexit
import base64
import datetime
import gzip
import json
import os
import re
import select
import shlex
import subprocess
import sys
import time
import zoneinfo

import jsonschema
import PIL.Image
import urllib3
import urllib.parse

import email.utils

import merge

from typing import Dict, Generator, List, Optional, Set


class MenuError(Exception):
	pass

class Log():
	def __init__(self, fn=None):
		self.errors = 0
		self._colour = sys.stdout.isatty()
		self.md = None
		if fn is not None:
			self.md = open(fn, "x")

	def C(self, text: str):  # Console-only, just for progress updates.
		print(text)

	def I(self, text: str):  # INFO
		if self._colour:
			print("\033[1m" + text + "\033[0m")
		elif text:
			print("* " + text)
		if self.md: self.md.write(text + "\n")

	def E(self, text: str):  # ERROR
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
	"--adb", help="Pointer at adb binary for schedule file validation by (headless) emulator.")
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
parser.add_argument(
	"--pr_title", default=None,
	help="Filename (must not yet exist) to write PR title update info to. (For Github Action handler.)")
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

	def fetch(self, url: str, img: bool=False, head: bool=False):
		"""Simple URL fetcher, will return bytes or a parsed image depending
		on what you ask for. Or raise FetchError if anything went wrong."""

		method = head and "HEAD" or "GET"
		try:
			LOG.C("Fetching %s" % url)
			u = self.o.request(
				method, url, redirect=False, preload_content=False, timeout=60, retries=False)
			if 300 <= u.status < 400:
				diff_protocol = ""
				if not u.get_redirect_location().startswith(
					# ... Wondering whether this is still true BTW.
					url.split("//", 1)[0]):
					diff_protocol = (" (Also, the Android HTTP "
						"fetcher does not allow http<>https redirects.)")
				raise FetchError(
					"URL redirected to %s, which is an unnecessary "
					"slowdown.%s" %
					(u.get_redirect_location(), diff_protocol))
			elif u.status != 200:
				raise FetchError("HTTP %d %s" % (u.status, u.reason))
			self.hcache[url] = {k.lower(): v for k, v in u.headers.iteritems()}
			self.hcache[url]["_ts"] = time.time()
			if not img:
				return u.read()
			else:
				return PIL.Image.open(u)
		except urllib3.exceptions.HTTPError as e:
			raise FetchError(e.args[-1]) from e

	def cache_sensible(self, url: str):
		"""Check whether it ever returns 304s. Yeah sure, this is a race. I
		don't care for a test that runs a few times a week at most. With
		Giggity's better caching (ETag support) I should improve/delete this."""
		if url not in self.hcache:
			return None
		if "last-modified" in self.hcache[url]:
			ims = self.hcache[url]["last-modified"]
		else:
			ims = email.utils.formatdate(self.hcache[url]["_ts"], localtime=False, usegmt=True)
		headers = {"If-Modified-Since": ims}
		if "etag" in self.hcache[url]:
			etag = self.hcache[url]["etag"].removeprefix("W/").removeprefix("\"").removesuffix("\"")
			headers["If-None-Match"] = etag
		# GET not HEAD because the CCC webserver is fucking retarded.
		u = self.o.request(
			"GET", url, headers=headers, redirect=False, preload_content=False)
		return u.status == 304


http = HTTP()


class ADB:
	def __init__(self):
		self.on = False
		self._adb: subprocess.Popen = None
		self._buf = b""
		self._dev = ()
		if args.adb:
			self.on = True
			if not os.getenv("ANDROID_SERIAL"):
				for dev in self.call("devices").splitlines()[1:]:
					# Insist on using only emulators to avoid accidentally erasing data on a real device!
					if dev.startswith("emulator-"):
						dev = dev.split()[0]
						LOG.C("Connecting to adb emulator device %r" % dev)
						self._dev = ("-s", dev)
						break
					else:
						if dev:
							LOG.I("Not an emulator: %s" % dev)

				if not self._dev:
					self.on = False
					LOG.E("No Android emulator available?")
					return
			
			self.call("logcat", "-c")
			p = subprocess.Popen((args.adb,) + self._dev + ("logcat",), stdout=subprocess.PIPE)
			os.set_blocking(p.stdout.fileno(), False)
			self._adb = p
			atexit.register(self._adb.kill)
			self.on = True

	def call(self, *cmd) -> Optional[str]:
		if not self.on:
			return None
		p = subprocess.Popen((args.adb,) + self._dev + cmd, stdout=subprocess.PIPE, encoding="utf-8")
		out, _ = p.communicate()
		assert p.returncode == 0
		return out

	def lines(self, timeout) -> Generator[str, None, None]:
		if not self._adb:
			return
		end = time.time() + timeout
		while True:
			t = min(timeout, end - time.time())
			if t <= 0:
				break
			have, _, _ = select.select([self._adb.stdout], [], [], t)
			if len(have) == 0:
				break
			self._buf += self._adb.stdout.read(4096)
			while self._buf:
				nl = self._buf.find(b"\n")
				if nl >= 0:
					ret: bytes
					ret, self._buf = self._buf[0:nl], self._buf[nl+1:]
					yield ret.decode("utf-8")
				else:
					break


adb = ADB()
adb.call("shell", "am", "force-stop", "net.gaast.giggity")
# adb.call("shell", "pm", "clear", "net.gaast.giggity")


def validate_url(url: str) -> List[str]:
	if not url.startswith("http:"):
		return []
	https = re.sub("^http:", "https:", url)
	try:
		http.fetch(https)
		return ["Use URL %s instead of non-TLS HTTP?" % https]
	except FetchError as err:
		return ["URL %s is insecure (but no TLS version available? (%s))" % (url, err)]


def validate_entry(e):
	ret = []
	
	# Forced to use GET not HEAD even though I don't need the data because for
	# example the retarded CCC server refuses HEAD requests.
	try:
		http.fetch(e["url"])
		ret += validate_url(e["url"])
	except FetchError as err:
		ret.append("Could not fetch %s %s: %s" % (e["title"], e["url"], err))

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
			try:
				url = md["c3nav_base"] + "/api/locations/?format=json"
				c3nav = http.fetch(url)
				c3nav = json.loads(c3nav)
				c3_by_slug = {v["slug"]: v for v in c3nav}
				ret += validate_url(url)
			except FetchError as err:
				ret.append("Could not fetch %s %s: %s" % (e["title"], e["url"], err))

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
			icon_errors = []
			try:
				img = http.fetch(md["icon"], img=True)
				ret += validate_url(md["icon"])
				if "A" not in img.mode:
					icon_errors.append("%s image has no alpha layer" % img.mode)
				if img.width != img.height:
					icon_errors.append("%d×%d image is not square" % (img.width, img.height))
				if img.width < 64:
					icon_errors.append("%d×%d image is too small" % (img.width, img.height))
				elif img.width > 512:
					icon_errors.append("%d×%d image is too large" % (img.width, img.height))
			except FetchError as err:
				icon_errors.append(str(err))
			if icon_errors:
				ret.append("Icon for %s seems bad: %s" % (e["title"], ", ".join(icon_errors)))
		
		if "links" in md:
			if len(md["links"]) > 4:
				ret.append("%d links exceeds the recommended maximum of 4 to ensure the nav drawer fits on most screens with no scrolling" % len(md["links"]))
			for link in md["links"]:
				if link["url"].startswith("geo:"):
					continue
				try:
					http.fetch(link["url"])
					ret += validate_url(link["url"])
				except FetchError as err:
					ret.append("Link \"%s\" appears broken: %s" % (link["title"], err))

	return ret

maxver = max(e["version"] for e in new["schedules"])
todayver = int(datetime.datetime.now().strftime("%Y%m%d99"))
if new["version"] < maxver:
	LOG.E("File version (%d) number must be ≥ %d (highest version in file)" % (new["version"], maxver))
elif new["version"] > todayver:
	LOG.E("File version (%d) number must be ≤ %d" % (new["version"], todayver))

changed = []
pr_title = []
base_entries: Dict[str, Dict] = {e["id"]: e for e in base.get("schedules", [])}
seen_urls: Set[str] = set()
seen_ids: Set[str] = set()
for e in new["schedules"]:
	url: str = e["url"]
	if url in seen_urls:
		LOG.E("Duplicate URL, unable to diff: %r %s" %
		      ([x["title"] for x in new["schedules"] if x["url"] == url], url))
		continue
	seen_urls.add(url)
	eid: str = e["id"]
	if eid in seen_ids:
		# Really mustn't happen BTW since this is derived from unique filename?
		LOG.E("Duplicate ID, unable to diff: %r %s" %
		      ([x["title"] for x in new["schedules"] if x["id"] == eid], eid))
		continue
	seen_ids.add(eid)

	if eid in base_entries:
		if e == base_entries[eid]:
			LOG.C("Unchanged: %s" % e["title"])
			base_entries.pop(eid)
			if not args.all:
				continue
		else:
			LOG.I("Changed: %s" % e["title"])
			if e["version"] <= base_entries[eid]["version"]:
				LOG.E("Version number for %r must be updated" % e["title"])
			base_entries.pop(eid)
			changed.append(e)
	else:
		LOG.I("New: %s" % e["title"])
		changed.append(e)
	
	pr_title.append({
		"tag": e["start"],
		"prefix": "[%s] " % e["start"],
	})

	entry_issues = validate_entry(e)
	if entry_issues:
		for err in entry_issues:
			LOG.E(err)
		LOG.I("")
	
	if adb.on:
		for _ in adb.lines(.1): pass  # Flush backlog
		entry_url = base64.urlsafe_b64encode(gzip.compress(json.dumps(e).encode("utf-8"))).decode("ascii")
		intent_url = "https://ggt.gaa.st#url=" + urllib.parse.quote(url) + "&json=" + entry_url
		adb.call("shell", "am", "start", "-n", "net.gaast.giggity/.ScheduleViewActivity",
		         "-a",  "android.intent.action.VIEW", "-d", shlex.quote(intent_url))
		load_log = []
		menu_entry = {}
		for line in adb.lines(10):
			MARKER = "giggity.Schedule: successfully loaded, suggested menu JSON:"
			m = line.find(MARKER)
			if m > 0:
				menu_entry = json.loads(line[m+len(MARKER):])
				break
			else:
				load_log.append(line)
		if menu_entry:
			for k, v in menu_entry.items():
				rep = LOG.E
				if k == "title":
					rep = LOG.I
				if e[k] != v:
					rep("Entry mismatch: %s=%r in entry ≠ %r in schedule?" % (k, e[k], v))
		else:
			LOG.E("Schedule file doesn't seem to load in Giggity successfully. (See CI logs for details?)")
			LOG.C("\n".join(load_log))  # Won't end up in markdown but should be in verbose logs.

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

if args.pr_title and len(pr_title) == 1:
	open(args.pr_title, "x").write(json.dumps(pr_title[0]))

if LOG.errors:
	LOG.E("File validation failed! :-( Please correct the issues listed above.")
else:
	LOG.I("File passed validation! \\o/")
sys.exit(min(LOG.errors, 1))
