#!/usr/bin/env python3

### This is years old experimentation with storing selections in Firebase. I'm never going to finish it because meh @ firebase.

import datetime
import hashlib
import hmac
import json
import jsonschema
import os
import re
import tempfile
import time
import uuid

from flask import Flask, Response
from flask import request

import dulwich
from dulwich.repo import Repo
from dulwich import porcelain

from google.cloud import storage

#import firebase_admin
#from firebase_admin import credentials
#from firebase_admin import firestore

from ggt_tools import merge


app = Flask(__name__)


UTC = datetime.timezone.utc

storage_client = storage.Client()
bucket = storage_client.get_bucket("giggity.appspot.com")

REPO = "https://github.com/Wilm0r/giggity"
MASTER_REF = "refs/heads/master"

# Couldn't really find a better way to store secrets. I would've been
# fine with just a password to check against a hash, shithub. :<
GITHUB_SECRET = bucket.blob("github-secret").download_as_string().strip() # actually bytes?

MENU_JSON_SCHEMA = json.load(open("ggt_tools/menu-schema.json", "r"))

#cred = credentials.Certificate(os.getenv("GOOGLE_APPLICATION_CREDENTIALS"))
#firebase_admin.initialize_app(cred)
#fdb = firestore.client()


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


def id_encode(raw_id):
	if "/" in raw_id or len(raw_id) > 33:
		return "_" + hashlib.md5(raw_id.encode("utf-8")).hexdigest()
	return raw_id


@app.route("/sync", methods=["POST"])
def sync():
	msg = request.get_json()
	if "/" in msg["user"] or "/" in msg["login"] or "/" in msg["s_id"]:
		return Response("Bad data", 400)
	
	sched_ref = fdb.document("schedules/%s" % msg["s_id"])
	user_ref = fdb.document("users/%s" % msg["user"])
	#items_ref = fdb.collection_group("items")
	items_ref = fdb.collection("users/%s/items" % msg["user"])
	login_ref = fdb.document("users/%s/logins/%s" % (msg["user"], msg["login"]))
	if not login_ref.get().exists:
		return Response("User or login does not exist", 403)

	items_ref = (items_ref.where("schedule", "==", sched_ref)
	                      .where("sts", ">", datetime.datetime.fromtimestamp(msg["last_sync"], UTC)))

	raw_items = {}
	ret = {}
	warnings = []
	for item in items_ref.stream():
		d = item.to_dict()
		id = d["i_id"]
		raw_items[id] = item
		if item.exists:
			ret[id] = {
				"remind": d["remind"],
				"hidden": d["hidden"],
			}

	for id, item in msg["items"].items():
		item_ref = fdb.document("schedules/%s/items/%s" % (msg["s_id"], id_encode(id)))
		ts = datetime.datetime.fromtimestamp(item["ts"], UTC)
		if id in ret:
			if ts < raw_items[id].get("cts"):
				warnings.append("Item %s overwritten by more recent entry in database." % id)
				continue
			else:
				ret.pop(id)

		fdb.collection("users/%s/items" % msg["user"]).document(id_encode(id)).set({
			"i_id": id,
			"sts": firestore.SERVER_TIMESTAMP,
			"cts": ts,
			"last_login": login_ref,
			"schedule": sched_ref,
			"item": item_ref,
			"remind": item["remind"],
			"hidden": item["hidden"],
		})

	if msg["my_time"] > time.time() + 60:
		warnings.append("Client time ahead of server's.")
	elif msg["my_time"] < time.time() - 3600:
		warnings.append("Client time more than an hour behind on server's.")

	return {"items": ret, "warnings": warnings, "timestamp": time.time()}


KEY = b'\x03\xaa\x85\xce^\xb7M!\xa6\xab\xf2\xd6R\xc8\xc4\xc0'


@app.route("/login", methods=["POST"])
def login():
	msg = request.get_json()
	if "/" in msg["user"] or "/" in msg.get("login", "") or "name" not in msg:
		return Response("Bad data", 400)
	
	user_ref = fdb.document("users/%s" % msg["user"])

	new_id = uuid.uuid4().hex
	new_ref = fdb.document("users/%s/logins/%s" % (msg["user"], new_id))

	if msg.get("login"):
		# user = username, login = existing login
		# Adding another login for an existing user.
		login_ref = fdb.document("users/%s/logins/%s" % (msg["user"], msg["login"]))
		if not login_ref.get().exists:
			return Response("User or login does not exist", 403)

		new_ref.set({"name": msg["name"]})
		return {"login": new_id}
	
	u = user_ref.get().to_dict()
	if not u.get("mail"):
		return Response("No existing login given, and no e-mail address on account", 403)

	return {
		"new_id": new_id,
		"sig": hmac.HMAC(KEY, new_id.encode("utf-8"), "sha224").hexdigest(),
	}


if __name__ == "__main__":
	#app.run(host="127.0.0.1", port=8080, debug=True)
	app.run(host="0.0.0.0", port=8080, debug=True)
