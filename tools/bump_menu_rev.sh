#!/bin/sh
sed -e "2s/\(^\t\"version\": \).*$/\1$(date '+%Y%m%d01'),/;" -i app/src/main/res/raw/menu.json && git add app/src/main/res/raw/menu.json && git commit -m "Bump menu revision"
