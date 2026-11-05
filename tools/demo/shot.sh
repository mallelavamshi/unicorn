#!/usr/bin/env bash
# Demo video (docs/plan/demo-video-x.md): records one shot on emulator-5556.
#
#   bash tools/demo/shot.sh tone            plays tools/demo/shots/tone.txt and keeps video/takes/tone.webm
#   bash tools/demo/shot.sh tone 2          the same, as take 2 (tone-2.webm)
#
# The steps run inside the glidetest app's instrumentation (see glidetest/.../DemoShots.kt), which taps, types and
# glides on Unicorn's real keys. The recording is the emulator's own (host side), which keeps every frame. The
# shot's step log (device uptime per step) lands next to the take as <name>.log, for timing the cut.
# Take V:\nidi\unicorn\.shared.lock and run clean-bar.sh on first.
set -uo pipefail
export MSYS_NO_PATHCONV=1
ADB="/v/android-sdk/platform-tools/adb.exe -s emulator-5556"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAME="$1"
TAKE="${2:+-$2}"
STEPS="$ROOT/tools/demo/shots/$NAME.txt"
OUT="$ROOT/video/takes/$NAME$TAKE"
mkdir -p "$ROOT/video/takes"
[ -f "$STEPS" ] || { echo "No steps file $STEPS" >&2; exit 1; }

# A host-side "pre" block (lines starting with "#!host ") runs first, off camera: app data resets, settings...
grep '^#!host ' "$STEPS" | sed 's/^#!host //' | while read -r cmd; do $ADB shell "$cmd" </dev/null; done

$ADB shell setprop log.tag.UnicornGlide DEBUG
$ADB push "$(cygpath -m "$STEPS")" /data/local/tmp/demo-shot.txt >/dev/null
$ADB logcat -c
rm -f "$OUT.webm"
# setup-* step files only prepare the app off camera, so they aren't recorded
case "$NAME" in setup-*) RECORD=0 ;; *) RECORD=1 ;; esac
[ $RECORD = 1 ] && $ADB emu screenrecord start --fps 30 --bit-rate 10000000 --time-limit 180 "$(cygpath -m "$OUT.webm")" >/dev/null
START=$($ADB shell cat /proc/uptime | cut -d' ' -f1)
RESULT=$($ADB shell am instrument -w -e mode shot -e file /data/local/tmp/demo-shot.txt ai.unicorn.glidetest/.GlideInstrumentation)
sleep 1
[ $RECORD = 1 ] && { $ADB emu screenrecord stop >/dev/null; sleep 2; }
# Step times as seconds into the recording
$ADB logcat -d -s DemoShot:I DemoShot:W | python -I -c "
import re, sys
start = float(sys.argv[1]) * 1000
for line in sys.stdin:
    m = re.search(r'(step|done) (\d+) ?(.*)', line)
    if m: print(f'{(int(m.group(2)) - start) / 1000:7.2f}  {m.group(3) or m.group(1)}')
    elif 'failed' in line: print('FAILED ', line.split('failed:', 1)[1].strip())
" "$START" > "$OUT.log"
cat "$OUT.log"
case "$RESULT" in *"CODE: -1"*) echo "OK $OUT.webm" ;; *) echo "SHOT FAILED: $RESULT" >&2; exit 1 ;; esac
