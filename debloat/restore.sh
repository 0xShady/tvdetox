#!/usr/bin/env bash
# Usage: debloat/restore.sh [--apks backups/<date>]   Re-enables everything in debloat/lists/.
. "$(dirname "$0")/../lib/common.sh"
require_tv

for f in "$ROOT"/debloat/lists/*.txt; do
  case "$(basename "$f")" in uninstall.txt|background.txt) continue ;; esac
  read_list "$f" | while read -r pkg; do
    echo "enable $pkg: $(tv shell pm enable "$pkg" 2>&1 | tail -1)"
  done
done

read_list "$ROOT/debloat/lists/uninstall.txt" | while read -r pkg; do
  echo "reinstall $pkg: $(tv shell cmd package install-existing "$pkg" 2>&1 | tail -1)"
done

read_list "$ROOT/debloat/lists/background.txt" | while read -r pkg; do
  tv shell cmd appops set "$pkg" RUN_ANY_IN_BACKGROUND allow || true
done

for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  tv shell settings put global "$k" 1.0
done
tv shell settings put secure screensaver_components "$STOCK_SCREENSAVER"

if [ "${1:-}" = "--apks" ]; then
  dir="${2:?pass the snapshot folder, e.g. backups/20260101-120000}"
  for d in "$dir"/apks/*/; do
    adb -s "$TV" install-multiple -r "$d"*.apk </dev/null || echo "failed: $d"
  done
fi

echo "Done. Reboot the TV: adb -s $TV reboot"
