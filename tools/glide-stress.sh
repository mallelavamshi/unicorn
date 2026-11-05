#!/usr/bin/env bash
# Glide typing gate: runs the JVM accuracy harness, the emulator end-to-end test and a soak run, then writes
# docs/research/glide-typing-report.md. Exits non-zero if any part misses the bar. No person needed.
#
#   tools/glide-stress.sh                      build, install, test, soak 22 minutes
#   SOAK_MINUTES=0 tools/glide-stress.sh       skip the soak
#   SKIP_BUILD=1 tools/glide-stress.sh         use the APKs already built
#   APK=dist/unicorn-0.5.0.apk tools/glide-stress.sh
#   REUSE_SOAK=1 tools/glide-stress.sh         re-run the rest, keeping the last soak if it tested this same APK
#
# Needs: an emulator (SERIAL, default emulator-5556) with 3-button navigation, and JAVA_HOME for Gradle.
# Unicorn is selected as the input method and the test app (glidetest/) is installed by this script.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
ADB_BIN="${ADB:-adb}"
SERIAL="${SERIAL:-emulator-5556}"
ADB="$ADB_BIN -s $SERIAL"
SOAK_MINUTES="${SOAK_MINUTES:-22}"
APK="${APK:-app/build/outputs/apk/release/app-release.apk}"
TEST_APK=glidetest/build/outputs/apk/debug/glidetest-debug.apk
OUT=build/glide
REPORT=docs/research/glide-typing-report.md
IME=ai.unicorn.app/com.android.inputmethod.latin.LatinIME
mkdir -p "$OUT"
failures=()

echo "== JVM accuracy harness"
./gradlew :app:testDebugUnitTest --tests 'ai.unicorn.glide.*' --console=plain -q > "$OUT/jvm.log" 2>&1 || failures+=("JVM harness")
cp app/build/glide/jvm-report.md "$OUT/jvm-report.md" 2>/dev/null || echo "### JVM harness did not produce a report" > "$OUT/jvm-report.md"

if [ -z "${SKIP_BUILD:-}" ]; then
  echo "== Build"
  ./gradlew :app:assembleRelease :glidetest:assembleDebug --console=plain -q > "$OUT/build.log" 2>&1 || { echo "build failed, see $OUT/build.log"; exit 2; }
fi

echo "== Install on $SERIAL"
install() { # A just-booted emulator's package service can refuse a moment; try a few times
  for attempt in 1 2 3 4; do $ADB install -r "$1" > /dev/null && return 0; sleep 20; done
  return 1
}
install "$APK" || { echo "install failed"; exit 2; }
install "$TEST_APK" || { echo "test app install failed"; exit 2; }
$ADB shell ime enable $IME > /dev/null
$ADB shell ime set $IME > /dev/null
$ADB shell setprop log.tag.UnicornGlide DEBUG
$ADB shell settings put system accelerometer_rotation 0
# An install makes Android re-index apps for a few minutes; measure on a settled phone, as a person would use it
echo "== Waiting for the emulator to settle"
quiet=0
for i in $(seq 1 90); do
  idle=$($ADB shell top -b -n 1 -m 1 | grep -o '[0-9]*%idle' | tr -d '%idle')
  if [ "${idle:-0}" -ge 300 ]; then quiet=$((quiet + 1)); else quiet=0; fi
  [ $quiet -ge 3 ] && break
  sleep 10
done
version=$($ADB shell dumpsys package ai.unicorn.app | grep -m1 versionName | tr -d ' \r' | cut -d= -f2)
device="$($ADB shell getprop ro.product.model | tr -d '\r'), Android $($ADB shell getprop ro.build.version.release | tr -d '\r'), $($ADB shell wm size | tr -d '\r' | sed 's/Physical size: //')"

run_instrumentation() { # mode [extra args]
  local mode=$1; shift
  $ADB shell am instrument -w -e mode "$mode" "$@" ai.unicorn.glidetest/.GlideInstrumentation > "$OUT/$mode.out" 2>&1
  $ADB pull "/sdcard/Android/data/ai.unicorn.glidetest/files/glide-$mode.md" "$OUT/$mode.md" > /dev/null 2>&1 ||
    echo "The $mode run wrote no report: $(tail -3 "$OUT/$mode.out")" > "$OUT/$mode.md"
  grep -q "result=PASS" "$OUT/$mode.out"
}

host_load() { powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average" 2>/dev/null | tr -d '\r'; }
load_e2e=$(host_load)
echo "== Emulator end-to-end"
$ADB logcat -c
run_instrumentation e2e || failures+=("emulator end-to-end")

soak_note=""
apk_sha=$(sha256sum "$APK" | cut -c1-64)
soak_reused=""
if [ -n "${REUSE_SOAK:-}" ] && [ -f "$OUT/soak-apk.sha256" ] && [ "$(cat "$OUT/soak-apk.sha256")" = "$apk_sha" ]; then
  echo "== Soak: keeping the last run's, which tested this same APK"
  soak_note=$(cat "$OUT/soak-note.md")
  soak_reused=$(cat "$OUT/soak-time.txt")
  grep -q "result=PASS" "$OUT/soak.out" || failures+=("soak")
  [ -s "$OUT/soak-failures.txt" ] && while read -r f; do failures+=("$f"); done < "$OUT/soak-failures.txt"
elif [ "$SOAK_MINUTES" != "0" ]; then
  echo "== Soak, $SOAK_MINUTES minutes"
  $ADB logcat -c
  $ADB logcat -b crash -c
  $ADB logcat -b events -c
  : > "$OUT/memory.csv"
  (
    while true; do
      mem=$($ADB shell dumpsys meminfo ai.unicorn.app 2>/dev/null | tr -d '\r')
      pss=$(echo "$mem" | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / && !p {print $2; p=1}' | head -1)
      java=$(echo "$mem" | awk '/Java Heap:/ {print $3; exit}')
      native=$(echo "$mem" | awk '/Native Heap:/ {print $3; exit}')
      echo "$(date +%s),$($ADB shell pidof ai.unicorn.app | tr -d '\r'),${pss:-0},${java:-0},${native:-0}" >> "$OUT/memory.csv"
      sleep 60
    done
  ) &
  sampler=$!
  run_instrumentation soak -e minutes "$SOAK_MINUTES" || failures+=("soak")
  kill $sampler 2>/dev/null
  crashes=$($ADB logcat -d -b crash | grep -c "ai.unicorn.app")
  anrs=$($ADB logcat -d -b events | grep -c "am_anr.*ai.unicorn.app")
  anrs2=$($ADB logcat -d | grep -c "ANR in ai.unicorn.app")
  anrs=$((anrs + anrs2))
  pids=$(cut -d, -f2 "$OUT/memory.csv" | sort -u | grep -c .)
  # Memory growth: median of the first three samples after two minutes' warm-up against the last three
  read -r first last <<< "$(awk -F, 'NR>2 && $3>0 {v[++n]=$3} END {
    if (n < 6) { print 0, 0; exit }
    a[1]=v[1]; a[2]=v[2]; a[3]=v[3]; b[1]=v[n-2]; b[2]=v[n-1]; b[3]=v[n]
    asort(a); asort(b); print a[2], b[2] }' "$OUT/memory.csv")"
  growth_kb=$(( last - first ))
  [ "$crashes" -eq 0 ] || failures+=("crashes during soak")
  [ "$anrs" -eq 0 ] || failures+=("ANRs during soak")
  [ "$pids" -le 1 ] || failures+=("Unicorn restarted during soak")
  [ "$growth_kb" -lt 20480 ] || failures+=("memory grew ${growth_kb} KB during soak")
  soak_note="| Crashes (crash log) | $crashes | 0 |
| ANRs | $anrs | 0 |
| Unicorn processes seen | $pids | 1 |
| Memory (PSS) after warm-up → end | $((first / 1024)) MB → $((last / 1024)) MB ($((growth_kb / 1024)) MB) | under 20 MB growth |"
  # Kept so REUSE_SOAK can stand on this run for the same APK
  echo "$apk_sha" > "$OUT/soak-apk.sha256"
  echo "$soak_note" > "$OUT/soak-note.md"
  date -u '+%Y-%m-%d %H:%M UTC' > "$OUT/soak-time.txt"
  : > "$OUT/soak-failures.txt"
  for f in "${failures[@]}"; do case "$f" in *soak*) echo "$f" >> "$OUT/soak-failures.txt" ;; esac; done
fi

status="PASS"
[ ${#failures[@]} -eq 0 ] || status="FAIL: $(IFS=', '; echo "${failures[*]}")"
{
  echo "# Glide typing report"
  echo
  echo "Generated by \`tools/glide-stress.sh\` on $(date -u '+%Y-%m-%d %H:%M UTC'). Unicorn $version on $device."
  echo "The computer running the emulator was ${load_e2e:-?}% busy when the end-to-end test started (other emulators and builds share it), which inflates wall-clock times."
  echo
  echo "**Gate: $status**"
  echo
  echo "## How it works"
  echo
  echo "Glide typing is decoded by \`ai.unicorn.glide.GlideDecoder\` (the AOSP native gesture decoder is an empty stub)."
  echo "It is shape-and-location template matching after SHARK2 (Kristensson and Zhai, UIST 2004): the swipe and each candidate"
  echo "word's ideal path over the real key centers are resampled and compared point by point, after scaling to a unit box,"
  echo "and with an elastic alignment, plus a check that every key of the word was visited in order. Candidates come from"
  echo "the words whose first and last keys are near where the swipe started and ended and whose path length fits. The"
  echo "evidence is weighed with the word's frequency in the engine's English dictionary, the previous word (a next-word table"
  echo "from Tatoeba's English sentences, CC BY 2.0 FR, plus what the user typed before), and the user's own words."
  echo
  echo "Synthetic swipes (\`app/src/glidesynth\`) follow a word's keys with the mistakes people make, in millimeters: realistic is"
  echo "1.6 mm aim at each key (2 mm at the first and last), corners cut by up to 2.5 mm, 30% overshoot at sharp turns,"
  echo "3 mm rounding, 0.25 mm sensor jitter, a wiggle on 35% of double letters and 0.7 to 1.5 times normal speed; sloppy is"
  echo "2.4 mm aim, 4 mm cuts, 50% overshoot of up to 4 mm and up to 2.2 times the speed. A phone key is about 7 mm wide."
  echo
  echo "## Results"
  echo
  sed 's/^### /### /' "$OUT/jvm-report.md"
  echo
  cat "$OUT/e2e.md"
  echo
  if [ -n "$soak_note" ]; then
    [ -n "$soak_reused" ] && echo "The soak below ran at $soak_reused on this same APK (SHA-256 ${apk_sha:0:16}…); this run repeated the rest." && echo
    cat "$OUT/soak.md"
    echo
    echo "| Watched from outside | Measured | Needed |"
    echo "|---|---:|---|"
    echo "$soak_note"
    echo
    echo "Memory samples (one a minute, KB): $(awk -F, '{printf "%s%s", (NR>1?", ":""), $3}' "$OUT/memory.csv")"
    echo
  fi
  echo "## Known limits"
  echo
  echo "- Words whose keys lie on one straight line share a path (\"for\" and \"four\", \"to\" and \"too\", \"its\" and \"it's\")."
  echo "  The previous word usually picks the right one; otherwise it is the first alternative in the suggestions."
  echo "- Synthetic swipes are not people. The emulator test injects them as real touches, but a real hand on a real phone"
  echo "  is the final check."
  echo "- Two-letter words on neighboring keys (\"we\", \"as\", \"ok\") are quicker to tap. A swipe that short often isn't"
  echo "  recognized as one before the key's long press opens its accents or number (the engine's gesture detection, as in"
  echo "  AOSP), so the sentence test taps them, as people do."
  echo "- Short words that double back (\"wow\", \"here\" as a message's first word) can lose to a more common word with"
  echo "  nearly the same path; it is then in the suggestions."
  echo "- English only, like the dictionary."
} > "$REPORT"

echo "== $status"
echo "Report: $REPORT"
[ ${#failures[@]} -eq 0 ]
