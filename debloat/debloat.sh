#!/usr/bin/env bash
# Usage: debloat/debloat.sh [--dry-run]   Undo: debloat/restore.sh
. "$(dirname "$0")/../lib/common.sh"

dry=""
[ "${1:-}" = "--dry-run" ] && dry=1
[ -n "$dry" ] || require_tv
run() { if [ -n "$dry" ]; then echo "would run: adb shell $*"; else tv shell "$@"; fi; }

[ -n "$dry" ] || "$ROOT/debloat/snapshot.sh"

for f in $(list_files); do
  read_list "$f" | while read -r pkg; do
    echo "disable $pkg: $(run pm disable-user --user 0 "$pkg" 2>&1 | tail -1)"
  done
done

read_list "$ROOT/debloat/lists/uninstall.txt" | while read -r pkg; do
  echo "uninstall $pkg: $(run pm uninstall -k --user 0 "$pkg" 2>&1 | tail -1)"
done

read_list "$ROOT/debloat/lists/background.txt" | while read -r pkg; do
  if [ -n "$dry" ]; then
    run cmd appops set "$pkg" RUN_ANY_IN_BACKGROUND ignore
  else
    run cmd appops set "$pkg" RUN_ANY_IN_BACKGROUND ignore && echo "background off: $pkg" || echo "skip $pkg (not installed)"
  fi
done

for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  run settings put global "$k" "$ANIMATION_SCALE"
done
echo "Animations set to $ANIMATION_SCALE."
[ -n "$dry" ] || echo "Done. Reboot the TV: adb -s $TV reboot"
