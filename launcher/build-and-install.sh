#!/usr/bin/env bash
# Usage: launcher/build-and-install.sh   Undo: launcher/uninstall.sh
. "$(dirname "$0")/../lib/common.sh"
cd "$(dirname "$0")"
build_apk TvHome
require_tv
tv install -r build/TvHome.apk
tv shell pm grant local.tvhome android.permission.ACCESS_FINE_LOCATION
tv shell settings delete secure enabled_accessibility_services >/dev/null
tv shell settings put secure enabled_accessibility_services local.tvhome/local.tvhome.HomeKeyService
tv shell settings put secure accessibility_enabled 1
tv shell am start -n local.tvhome/.HomeActivity >/dev/null
echo "Installed and opened. The Home button now opens it."
