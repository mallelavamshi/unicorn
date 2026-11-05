#!/usr/bin/env bash
# Throwaway proof of concept for docs/plan/demo-video.md: one scripted take of the Tone rewrite on emulator-5556,
# with a clean status bar. Take V:\nidi\unicorn\.shared.lock before running it and release it after.
#
#   bash docs/plan/demo-video/poc-tone.sh [take-number]
#
# Leaves raw/poc-tone-take<N>.webm next to this script. The status bar goes back to normal at the end.
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="/v/android-sdk/platform-tools/adb.exe -s emulator-5556"
HERE="$(cd "$(dirname "$0")" && pwd)"
TAKE="${1:-1}"
OUT="$HERE/raw/poc-tone-take$TAKE.mp4"
IME=ai.unicorn.app/com.android.inputmethod.latin.LatinIME
MESSY="hey sry cant make it tmrw nite, smthing came up at wrk. can we do sat instead??"
mkdir -p "$HERE/raw"

sh() { $ADB shell "$@"; }
demo() { sh am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

clean_bar_on() {
    # Gesture navigation looks current; switching restarts the system UI, so it goes first
    sh cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
    sleep 5
    sh settings put global sysui_demo_allowed 1
    demo enter
    demo clock -e hhmm 0941
    demo battery -e level 100 -e plugged false -e powersave false
    demo network -e wifi show -e level 4 -e fully true
    demo network -e mobile hide
    demo notifications -e visible false
    demo status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e zen hide -e mute hide \
        -e speakerphone hide -e tty hide -e eri hide -e sync hide -e cast hide -e hotspot hide -e managed_profile hide
    # The bare test app draws white status icons on white; an opaque bar keeps them readable
    demo bars -e mode opaque
    sh cmd notification set_dnd priority >/dev/null || true
}

clean_bar_off() {
    demo exit || true
    sh settings put global sysui_demo_allowed 0 || true
    sh cmd notification set_dnd off >/dev/null || true
    # Back to how this emulator was: both overlays on, three-button on top (its other checks expect three buttons)
    sh cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
    sh cmd overlay enable com.android.internal.systemui.navbar.gestural || true
    sh cmd overlay set-priority com.android.internal.systemui.navbar.threebutton highest || true
}
trap clean_bar_off EXIT

# Centre of the first on-screen node whose text matches $1, from a fresh accessibility dump (all windows, so the
# input method's toolbar is included). Prints "x y", or nothing.
find_text() {
    sh uiautomator dump --windows /sdcard/ui.xml >/dev/null 2>&1 || sh uiautomator dump /sdcard/ui.xml >/dev/null
    sh cat /sdcard/ui.xml | python -c '
import re, sys
xml, want = sys.stdin.read(), sys.argv[1]
for m in re.finditer(r"<node [^>]*>", xml):
    node = m.group(0)
    text = re.search(r" text=\"([^\"]*)\"", node)
    if text and re.search(want, text.group(1)):
        b = [int(v) for v in re.findall(r"-?\d+", re.search(r"bounds=\"([^\"]*)\"", node).group(1))]
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        break
' "$1"
}

# Same, but waits up to $2 seconds for the node to appear.
wait_text() {
    local until=$((SECONDS + ${2:-10})) at
    while [ $SECONDS -lt $until ]; do
        at="$(find_text "$1")"
        if [ -n "$at" ]; then echo "$at"; return 0; fi
        sleep 0.4
    done
    return 1
}

# --- Scene ---------------------------------------------------------------------------------------------------------
sh ime enable "$IME" >/dev/null
sh ime set "$IME" >/dev/null
sh settings put system screen_off_timeout 600000
sh input keyevent KEYCODE_WAKEUP
clean_bar_on
sh am start -W -S -n ai.unicorn.glidetest/.GlideTestActivity >/dev/null
sleep 2.5
# The messy text goes in before the recording starts; the shot opens on it already typed.
sh input text "$(printf '%s' "$MESSY" | sed 's/ /%s/g; s/?/\\?/g')"
sleep 1.5
TONE="$(wait_text 'Tone' 10)" || { echo "No Tone button (are Unicorn's AI tools resting?)" >&2; exit 1; }

# --- Take ----------------------------------------------------------------------------------------------------------
# The emulator's own recorder (host side) keeps every frame the screen draws: about 28 distinct frames a second while
# the panel opens, against 13 for `adb shell screenrecord`, which drops frames on this busy host.
OUT="${OUT%.mp4}.webm"
rm -f "$OUT"
$ADB emu screenrecord start --fps 30 --bit-rate 16000000 --time-limit 30 "$(cygpath -m "$OUT")" >/dev/null
START=$(date +%s.%N)
mark() { echo "$1 $(python -c "print(round($(date +%s.%N) - $START, 2))")" >> "${OUT%.webm}.times"; }
rm -f "${OUT%.webm}.times"
sleep 2.0                                   # hold on the messy text
mark tone
sh input tap $TONE                          # 🎭 Tone: the panel opens and asks for a Rewrite right away
CARD="$(wait_text 'Saturday|saturday|Sat' 25)" || { echo "No rewrite came back" >&2; $ADB emu screenrecord stop; exit 1; }
mark answer
sleep 1.8                                   # let the answer be read
mark pick
sh input tap $CARD                          # the rewrite replaces the text in the field
sleep 2.5                                   # hold on the clean text
mark end
$ADB emu screenrecord stop >/dev/null
sleep 2                                     # the recorder finishes writing the file
echo "$OUT"
