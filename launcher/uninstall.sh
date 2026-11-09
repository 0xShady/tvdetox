#!/usr/bin/env bash
. "$(dirname "$0")/../lib/common.sh"
require_tv
tv shell settings delete secure enabled_accessibility_services
tv shell settings put secure accessibility_enabled 0
tv uninstall local.tvhome || true
tv shell input keyevent KEYCODE_HOME
echo "Stock home screen restored."
