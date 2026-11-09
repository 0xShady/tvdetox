#!/usr/bin/env bash
# Usage: screensaver/add-photos.sh photo.jpg [more.jpg ...]   or   screensaver/add-photos.sh --clear
. "$(dirname "$0")/../lib/common.sh"
require_tv
dir=/sdcard/Pictures/Screensaver
tv shell mkdir -p "$dir"
if [ "${1:-}" = "--clear" ]; then
  tv shell rm -f "$dir/*"
  echo "Removed all screensaver photos."
else
  [ $# -gt 0 ] || die "pass one or more images, or --clear"
  for f in "$@"; do
    [ -f "$f" ] || die "not a file: $f"
    adb -s "$TV" push "$f" "$dir/" </dev/null >/dev/null 2>&1
    echo "Added $(basename "$f")"
  done
fi
tv shell am force-stop local.lightdream
