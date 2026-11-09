#!/usr/bin/env bash
# Usage: debloat/snapshot.sh [--apks]   Saves the TV state to backups/<date>/ (never committed).
. "$(dirname "$0")/../lib/common.sh"
require_tv

out="$ROOT/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
tv shell pm list packages -f > "$out/packages-all.txt"
tv shell pm list packages -3 > "$out/packages-3rdparty.txt"
tv shell pm list packages -d > "$out/packages-disabled.txt"
for ns in global secure system; do
  tv shell settings list "$ns" > "$out/settings-$ns.txt"
done
tv shell getprop > "$out/getprop.txt"

if [ "${1:-}" = "--apks" ]; then
  sed 's/^package://' "$out/packages-3rdparty.txt" | while read -r pkg; do
    mkdir -p "$out/apks/$pkg"
    tv shell pm path "$pkg" | sed 's/^package://' | while read -r apk; do
      adb -s "$TV" pull "$apk" "$out/apks/$pkg/" </dev/null >/dev/null
    done
  done
fi

echo "Snapshot saved to ${out#$ROOT/}. It holds device IDs: keep it private."
