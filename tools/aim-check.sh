#!/usr/bin/env bash
# Learn my aim, on the emulator: a person who always lands 0.3 key right of E types "see" 25 times, which teaches
# Unicorn where they aim. A tap just across the E/R border should then type E instead of R. Learning starts at 40
# clean taps on a letter; the first word doesn't count here (the probe's R is still in the word when the field is
# cleared), so 24 words give 48 taps on E.
#
# Needs: the same setup as tools/size-check.sh, with the keys at the standard size and nothing learned yet
# (You > Look > Size and position > Forget how I tap). Usage: SERIAL=emulator-5556 tools/aim-check.sh
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb} -s ${SERIAL:-emulator-5556}"

tap() { $ADB shell input tap "$1" "$2"; sleep 0.3; }
dump() { $ADB shell uiautomator dump --windows /sdcard/aim.xml >/dev/null 2>&1; $ADB exec-out cat /sdcard/aim.xml | tr '>' '\n'; }
field() {
  local text
  text=$(dump | grep 'EditText' | head -1 | sed -n 's/.* text="\([^"]*\)".*/\1/p')
  [[ "$text" == "Message (multi-line)" ]] && text=""
  echo "$text"
}
bounds_of() { sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'; }
# Select all, then delete
clear_field() { $ADB shell input keycombination 113 29; $ADB shell input keyevent 67; sleep 0.3; }

# More than one view can carry the key view's id; the keys are the one the engine's debug log describes
log=$($ADB logcat -d -s UnicornGlide:D | grep " keys w=")
line=""
while read -r b; do
  [ -z "$b" ] && continue
  read -r x1 y1 x2 y2 <<< "$b"
  line=$(grep " keys w=$(( x2 - x1 )) h=$(( y2 - y1 )) " <<< "$log" | tail -1)
  [ -n "$line" ] && break
done <<< "$(dump | grep 'resource-id="ai.unicorn.app:id/keyboard_view"' | bounds_of)"
[ -z "$line" ] && { echo "keys not found"; exit 1; }
lx=$(sed -n 's/.* at=\([0-9-]*\),.*/\1/p' <<< "$line")
ly=$(sed -n 's/.* at=[0-9-]*,\([0-9-]*\).*/\1/p' <<< "$line")
key() { local xy; xy=$(tr ' ' '\n' <<< "$line" | grep "^$1=" | head -1 | cut -d= -f2); echo "$(( x1 + ${xy%,*} - lx )) $(( y1 + ${xy#*,} - ly ))"; }
read -r ex ey <<< "$(key e)"
read -r rx _ <<< "$(key r)"
read -r sx sy <<< "$(key s)"
read -r spx spy <<< "$(key space)"
pitch=$(( rx - ex ))
border=$(( (ex + rx) / 2 ))
probe=$(( border + pitch * 6 / 100 ))
failures=0

clear_field
tap "$probe" "$ey"
got="$(field)"
if [[ "${got,,}" == "r" ]]; then echo "ok    before learning, a tap just inside R types R"; else echo "FAIL  before learning: '$got' (expected r)"; failures=$((failures + 1)); fi

clear_field
aim=$(( ex + pitch * 30 / 100 ))
for _ in $(seq 1 25); do
  tap "$sx" "$sy"; tap "$aim" "$ey"; tap "$aim" "$ey"; tap "$spx" "$spy"
done
typed="$(field)"
words=$(grep -oi "see" <<< "$typed" | wc -l)
echo "      typed 'see' $words times (each e landing 0.3 key right of centre)"

clear_field
tap "$probe" "$ey"
got="$(field)"
if [[ "${got,,}" == "e" ]]; then echo "ok    after learning, the same tap types E"; else echo "FAIL  after learning: '$got' (expected e)"; failures=$((failures + 1)); fi
# The middle of R stays R
clear_field
tap "$rx" "$ey"
got="$(field)"
if [[ "${got,,}" == "r" ]]; then echo "ok    the middle of R still types R"; else echo "FAIL  middle of R: '$got'"; failures=$((failures + 1)); fi
clear_field
echo "$failures failure(s)"
exit $failures
