#!/usr/bin/env bash
. "$(dirname "$0")/../lib/common.sh"
require_tv
tv shell settings put secure screensaver_components "$STOCK_SCREENSAVER"
tv uninstall local.lightdream || true
echo "Stock screensaver restored. Run debloat/restore.sh too if you disabled it."
