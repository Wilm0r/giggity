#!/bin/sh

json="$1"
if ! cat "$json" > /dev/null; then
	echo "Missing argument or could not read file?" >&2
	exit 1
fi

url="$(jq -r .url < "$json")"
if [ -z "$url" ]; then
	echo "Could not parse JSON" >&2
	echo "Maybe: sudo apt-get install jq" >&2
	exit 1
fi

echo https://ggt.gaa.st#url="${url}"\&json="$(cat "$json" | jq -Mc "" | gzip -9 | base64 -w0 | tr +/ -_)"
