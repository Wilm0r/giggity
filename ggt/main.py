#!/usr/bin/env python3

import hashlib
import hmac
import json
import jsonschema
import os
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


@app.route('/update-menu-cache', methods=["GET", "POST"])
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

	path = git_pull(REPO)

	rj = request.json or {}
	# The wonderfully up-to-date docs can't make their minds up on head/after field name.
	head = rj.get("head", rj.get("after", request.args.get("rev", "HEAD")))
	for rev in set([head, "HEAD"]):
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

	return Response("OK, updated to %s" % head)


@app.route('/menu.json')
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


if __name__ == '__main__':
	app.run(host='127.0.0.1', port=8080, debug=True)
