#!/usr/bin/env bash
# Usage: screensaver/build-and-install.sh   Undo: screensaver/uninstall.sh
. "$(dirname "$0")/../lib/common.sh"
cd "$(dirname "$0")"
build_apk LightSlideshow "${SCREENSAVER_WALLPAPER:-assets/screensaver.jpg}"
require_tv
tv install -r build/LightSlideshow.apk
tv shell pm grant local.lightdream android.permission.READ_EXTERNAL_STORAGE
tv shell mkdir -p /sdcard/Pictures/Screensaver
tv shell settings put secure screensaver_components local.lightdream/.SlideshowDream
echo "Installed and set as screensaver. Add pictures: adb -s $TV push photo.jpg /sdcard/Pictures/Screensaver/"
