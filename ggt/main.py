#!/usr/bin/env python3

import hashlib
import hmac
import json
import jsonschema
import os
import re
import tempfile

from flask import Flask, Response
from flask import request

import dulwich
from dulwich.repo import Repo
from dulwich import porcelain

from google.cloud import storage

from ggt_tools import merge


app = Flask(__name__)


storage_client = storage.Client()
bucket = storage_client.get_bucket("giggity.appspot.com")

REPO = "https://github.com/Wilm0r/giggity"
MASTER_REF = "refs/heads/master"

# Couldn't really find a better way to store secrets. I would've been
# fine with just a password to check against a hash, shithub. :<
GITHUB_SECRET = bucket.blob("github-secret").download_as_string().strip() # actually bytes?

MENU_JSON_SCHEMA = json.load(open("ggt_tools/menu-schema.json", "r"))


def git_pull(path, local_path="/tmp/giggity.git"):
	try:
		porcelain.pull(local_path, path)
		return local_path
	except dulwich.errors.NotGitRepository:
		t = tempfile.mkdtemp(prefix=local_path)
		repo = porcelain.clone(path, bare=True, target=t, checkout=False)
		try:
			os.rename(t, local_path)
			return local_path
		except OSError:
			# Guess there may have been a race. All we know
			# is the one we just created should work.
			return t


# json_get (shortcut to dig through json nested dicts..)
def jg(nested_dict, *args):
	while args and nested_dict:
		nested_dict = nested_dict.get(args[0], {})
		args = args[1:]
	return nested_dict


def bytes_if_not(src):
	if isinstance(src, bytes):
		return src
	else:
		return src.encode("utf-8")


@app.route("/update-menu-cache", methods=["GET", "POST"])
def update():
	req_digest = request.headers.get("X-Hub-Signature")
	if req_digest:
		# Same here, manual says request.data returns a string but it's actually a byte
		# array (which thankfully I actually prefer right here). It'll screw me some day..
		digest = hmac.new(GITHUB_SECRET, request.data or b"", hashlib.sha1).hexdigest()
		# Headers *are* a string already as is hexdigest(), I'm so lucky.
		if req_digest.split("=") != ["sha1", digest]:
			return Response("Incorrect HMAC", 403)
	elif request.headers.get("X-Appengine-Cron", "") == "true":
		print("Access granted for cron-scheduled update")
	else:
		return Response("HMAC header missing", 403)

	rj = request.json or {}
	if rj.get("ref", MASTER_REF) != MASTER_REF:
		return Response("Not a master commit, won't update cache", 200)

	todo = set()
	if "rev" in request.args:
		todo.add(request.args["rev"])
	for commit in rj.get("commits", []):
		files = commit.get("added", []) + commit.get("modified", []) + commit.get("removed", [])
		if any(re.search(r"^(ggt|menu|tools)/", fn) for fn in files):
			todo.add(commit["id"])
	
	if not todo:
		return Response("No interesting commits, won't update cache", 200)
	
	path = git_pull(REPO)

	# The only name normally actually fetched from cache.
	todo.add("HEAD")
	for rev in todo:
		cached = bucket.blob("menu-cache/%s" % rev)
		items = merge.load_git(path, rev)
		raw_json = merge.merge(items, merge.start_date(items, 60))
		formatted = merge.format_file(raw_json)
		try:
			jsonschema.validate(json.loads(formatted), MENU_JSON_SCHEMA)
			cached.upload_from_string(formatted)
		except (json.decoder.JSONDecodeError, jsonschema.ValidationError) as inval:
			return Response("Generated invalid JSON:\n\n%s\n\n%s" % (str(inval), formatted), 500, headers={
				"Content-Type": "text/plain"
			})

	return Response("OK, updated revisions %s" % " ".join(sorted(todo)))


@app.route("/menu.json")
def menu_json():
	rev = request.args.get("rev", "HEAD")
	cached = bucket.blob("menu-cache/%s" % rev)
	if cached.exists():
		return Response(cached.download_as_string(), headers={
			"Content-Type": "application/json",
		})
	else:
		return Response("Don't have that version cached", 404)
		# Redirect to old URL?
		pass


@app.route("/_ah/warmup")
def warmup():
	rev = "HEAD"
	if bucket.blob("menu-cache/%s" % rev).exists():
		return "", 204
	else:
		return ":-(", 500


if __name__ == "__main__":
	app.run(host="127.0.0.1", port=8080, debug=True)
