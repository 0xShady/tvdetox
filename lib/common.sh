#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -f "$ROOT/tv.env" ]; then
  set -a
  . "$ROOT/tv.env"
  set +a
fi

TV="${TV:-}"
ANIMATION_SCALE="${ANIMATION_SCALE:-0.5}"
DEBLOAT_LISTS="${DEBLOAT_LISTS:-google-tv}"
STOCK_SCREENSAVER="${STOCK_SCREENSAVER:-com.google.android.apps.tv.dreamx/.service.Backdrop}"

die() { echo "error: $*" >&2; exit 1; }

require_tv() {
  command -v adb >/dev/null || die "adb not found. Install Android platform-tools."
  [ -n "$TV" ] || die "TV is not set. Copy tv.env.example to tv.env and set TV=<ip>:5555, or run with TV=<ip>:5555."
  adb connect "$TV" >/dev/null
  adb -s "$TV" get-state >/dev/null 2>&1 || die "cannot reach $TV. Is network debugging on, and did you accept the prompt on the TV?"
}

tv() { adb -s "$TV" "$@" </dev/null; }

read_list() {
  grep -vE '^\s*(#|$)' "$1" | sed 's/[[:space:]]*#.*//' | awk '{print $1}'
}

list_files() {
  local name
  for name in $DEBLOAT_LISTS; do
    [ -f "$ROOT/debloat/lists/$name.txt" ] || die "no list debloat/lists/$name.txt"
    echo "$ROOT/debloat/lists/$name.txt"
  done
}

find_sdk() {
  local c
  for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$c" ] && [ -d "$c/build-tools" ] && { echo "$c"; return; }
  done
  die "Android SDK not found. Set ANDROID_HOME."
}

build_apk() {
  local name="$1"
  local sdk bt jar javac keystore
  sdk="$(find_sdk)"
  bt="$sdk/build-tools/${BUILD_TOOLS:-$(ls "$sdk/build-tools" | sort -V | tail -1)}"
  jar="$sdk/platforms/android-${PLATFORM:-34}/android.jar"
  [ -f "$jar" ] || die "missing $jar. Install it with: sdkmanager \"platforms;android-${PLATFORM:-34}\""
  if [ -n "${JAVA_HOME:-}" ]; then
    javac="$JAVA_HOME/bin/javac"
  elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17+ >/dev/null 2>&1; then
    javac="$(/usr/libexec/java_home -v 17+)/bin/javac"
  else
    javac="$(command -v javac)" || die "javac not found. Install JDK 17 or newer."
  fi
  keystore="$HOME/.android/debug.keystore"
  if [ ! -f "$keystore" ]; then
    mkdir -p "$HOME/.android"
    "$(dirname "$javac")/keytool" -genkeypair -keystore "$keystore" -storepass android -keypass android \
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
  fi

  rm -rf build && mkdir -p build/classes build/dex build/gen/drawable-nodpi
  local wallpaper="${2:-${WALLPAPER:-assets/wallpaper.jpg}}"
  case "$wallpaper" in /*) ;; *) wallpaper="$ROOT/$wallpaper" ;; esac
  [ -f "$wallpaper" ] || die "wallpaper not found: $wallpaper"
  local ext
  ext="$(echo "${wallpaper##*.}" | tr '[:upper:]' '[:lower:]')"
  cp "$wallpaper" "build/gen/drawable-nodpi/wallpaper.$ext"
  mkdir -p build/gen/values
  {
    echo '<?xml version="1.0" encoding="utf-8"?>'
    echo '<resources>'
    echo "<string name=\"latitude\">${LATITUDE:-}</string>"
    echo "<string name=\"longitude\">${LONGITUDE:-}</string>"
    echo "<string name=\"temp_unit\">${TEMP_UNIT:-celsius}</string>"
    echo '</resources>'
  } > build/gen/values/config.xml
  if [ -n "${FAVORITES:-}" ]; then
    mkdir -p build/gen/values
    {
      echo '<?xml version="1.0" encoding="utf-8"?>'
      echo '<resources><string-array name="favorites">'
      for pkg in $FAVORITES; do echo "<item>$pkg</item>"; done
      echo '</string-array></resources>'
    } > build/gen/values/favorites.xml
  fi
  local res_args=()
  if [ -d res ]; then
    "$bt/aapt2" compile --dir res -o build/res.zip
    res_args+=(-R build/res.zip)
  fi
  "$bt/aapt2" compile --dir build/gen -o build/gen.zip
  res_args+=(-R build/gen.zip --auto-add-overlay)
  "$bt/aapt2" link -I "$jar" --manifest AndroidManifest.xml "${res_args[@]}" \
    --min-sdk-version 28 --target-sdk-version 29 -o build/unsigned.apk
  "$javac" -source 11 -target 11 -nowarn -Xlint:-options -classpath "$jar" -d build/classes $(find src "$ROOT/common/src" -name '*.java')
  "$bt/d8" --release --min-api 28 --lib "$jar" --output build/dex $(find build/classes -name '*.class')
  (cd build/dex && zip -q ../unsigned.apk classes.dex)
  "$bt/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
  "$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --out "build/$name.apk" build/aligned.apk
  echo "Built build/$name.apk ($(du -h "build/$name.apk" | cut -f1))"
}
