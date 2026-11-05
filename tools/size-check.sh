#!/usr/bin/env bash
# Size and position checks: the ⋯ menu, one-handed docking and its side buttons, the resize handles, edge taps,
# per-orientation memory, and tapping and glide typing at each size. Saves a screenshot of every size.
#
# Needs: an emulator at 1080x2400 (420 dpi), Unicorn selected as the input method, and the harness app (harness/,
# package com.openai.chatgpt). Usage: SERIAL=emulator-5556 tools/size-check.sh [screenshot dir]
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb} -s ${SERIAL:-emulator-5556}"
OUT="${1:-build/size-check}"
mkdir -p "$OUT"
DP=2.625

failures=0
pass() { echo "ok    $*"; }
fail() { echo "FAIL  $*"; failures=$((failures + 1)); }
dp() { awk -v v="$1" -v d="$DP" 'BEGIN { printf "%d", v * d + 0.5 }'; }
tap() { $ADB shell input tap "$1" "$2"; sleep 0.35; }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; }

field() {
  local text
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  text=$($ADB exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep 'EditText' | head -1 | sed -n 's/.* text="\([^"]*\)".*/\1/p')
  # An empty field reports its hint
  [[ "$text" == "Message (multi-line)" ]] && text=""
  echo "$text"
}
# Select all, then delete: works in both orientations and leaves the keys up
clear_field() { $ADB shell input keycombination 113 29; $ADB shell input keyevent 67; sleep 0.3; }

# Where the keys are: W H AX AY from the key view's bounds on screen, and KX_<key> KY_<key>. The engine's debug log
# gives each key's place within the key view (its "at=" can be from before a move, so only the offsets are used).
read_keys() {
  local line="" bounds x1 y1 x2 y2 lx ly log
  for _ in $(seq 1 15); do
    sleep 0.4
    $ADB shell uiautomator dump --windows /sdcard/keys.xml >/dev/null 2>&1
    log=$($ADB logcat -d -s UnicornGlide:D | grep " keys w=")
    # More than one view can carry the key view's id; the keys are the one the log describes
    while read -r bounds; do
      [ -z "$bounds" ] && continue
      read -r x1 y1 x2 y2 <<< "$bounds"
      line=$(grep " keys w=$(( x2 - x1 )) h=$(( y2 - y1 )) " <<< "$log" | tail -1)
      [ -n "$line" ] && break
    done <<< "$($ADB exec-out cat /sdcard/keys.xml | tr '>' '\n' | grep 'resource-id="ai.unicorn.app:id/keyboard_view"' \
      | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')"
    [ -n "$line" ] && break
  done
  [ -z "$line" ] && { echo "keys not found on screen"; return 1; }
  AX=$x1; AY=$y1; W=$(( x2 - x1 )); H=$(( y2 - y1 ))
  lx=$(sed -n 's/.* at=\([0-9-]*\),.*/\1/p' <<< "$line")
  ly=$(sed -n 's/.* at=[0-9-]*,\([0-9-]*\).*/\1/p' <<< "$line")
  for tok in $line; do
    case "$tok" in
      [a-z]*=*,*) local name=${tok%%=*} xy=${tok#*=}; eval "KX_$name=$(( AX + ${xy%,*} - lx )); KY_$name=$(( AY + ${xy#*,} - ly ))" ;;
    esac
  done
}
# Rotating recreates the app, which closes the keys: rotate, then tap the field until they are up again
show_keys() {
  for _ in 1 2 3 4 5; do
    $ADB shell input tap 540 155
    sleep 2
    $ADB shell dumpsys input_method | grep -q "mInputShown=true" && return 0
  done
  return 1
}
rotate() { $ADB shell wm user-rotation lock "$1"; sleep 2.5; show_keys; }
# Runs a command that changes the keys, then reads where they went
after() { "$@"; sleep 1.5; read_keys; }

# The toolbar sits above the suggestions (40 dp, 36 dp in landscape); ⋯ is its last button (40 dp wide)
toolbar_y() { echo $(( AY - $(dp "$STRIP") - $(dp 22) )); }
strip_y() { echo $(( AY - $(dp "$STRIP") / 2 )); }
# The one-handed side buttons: switch sides beside the toolbar, full width beside the suggestions, centred in the gap
gap_x() { if (( AX > 1 )); then echo $(( AX / 2 )); else echo $(( AX + W + (SCREEN_W - AX - W) / 2 )); fi; }
more_menu() { tap $(( AX + W - $(dp 20) )) "$(toolbar_y)"; sleep 0.4; }
# Menu pills, found by their text in the toolbar
menu_item() {
  $ADB shell uiautomator dump --windows /sdcard/ime.xml >/dev/null 2>&1 || $ADB shell uiautomator dump /sdcard/ime.xml >/dev/null 2>&1
  local bounds
  bounds=$($ADB exec-out cat /sdcard/ime.xml | tr '>' '\n' | grep "text=\"$1\"" | head -1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  if [ -n "$bounds" ]; then
    read -r x1 y1 x2 y2 <<< "$bounds"
    tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
    return 0
  fi
  return 1
}

type_word() {
  local word="$1" c
  for (( i=0; i<${#word}; i++ )); do
    c="${word:$i:1}"
    eval "tap \$KX_$c \$KY_$c"
  done
}
glide_word() {
  local word="$1" c x y
  c="${word:0:1}"; eval "x=\$KX_$c; y=\$KY_$c"
  $ADB shell input motionevent DOWN "$x" "$y"
  for (( i=1; i<${#word}; i++ )); do
    c="${word:$i:1}"; eval "x=\$KX_$c; y=\$KY_$c"
    $ADB shell input motionevent MOVE "$x" "$y"
  done
  $ADB shell input motionevent UP "$x" "$y"
  sleep 0.8
}

check_field() {
  local label="$1" expected="$2" got
  sleep 0.5
  got="$(field)"
  if [[ "${got,,}" == "${expected,,}" ]]; then pass "$label -> '$got'"; else fail "$label -> '$got' (expected '$expected')"; fi
}

check_size() {
  local label="$1" width="$2" left="$3"
  if (( W >= width - 3 && W <= width + 3 && AX >= left - 3 && AX <= left + 3 )); then pass "$label: keys ${W}px wide at x=$AX"
  else fail "$label: keys ${W}px wide at x=$AX (expected ${width} at ${left})"; fi
}

# Typing and glide typing work at this size
typing_works() {
  local label="$1"
  clear_field; type_word hello; check_field "$label: tapped hello" "hello"
  clear_field; glide_word hello; local got; got="$(field)"
  if [[ "${got,,}" == hello* ]]; then pass "$label: glided hello -> '$got'"; else fail "$label: glided hello -> '$got'"; fi
}

# --- Start: the harness chat app, keys up, standard size ---------------------------------------------------------
IME=ai.unicorn.app/com.android.inputmethod.latin.LatinIME
SCREEN_W=1080
$ADB shell ime enable "$IME" >/dev/null
$ADB shell ime set "$IME" >/dev/null
$ADB shell setprop log.tag.UnicornGlide DEBUG
$ADB shell wm user-rotation lock 0
STRIP=40
$ADB shell am force-stop com.openai.chatgpt
$ADB logcat -c
$ADB shell monkey -p com.openai.chatgpt -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
for attempt in 1 2 3 4 5; do
  tap 540 155
  sleep 2
  $ADB shell dumpsys input_method | grep -q "mInputShown=true" && break
done
read_keys || exit 1
if (( W != 1080 )); then
  # A size left over from an earlier run: reset it with the handles' Reset button
  more_menu; menu_item "↔  Size and position"; sleep 0.8
  tap $(( AX + $(dp 40) )) "$(toolbar_y)"; sleep 1; read_keys
  tap $(( 1080 - $(dp 40) )) "$(toolbar_y)"; sleep 0.5
fi
check_size "standard" 1080 0
shot 1-standard
typing_works "standard"

# --- One-handed from the ⋯ menu: docks right ------------------------------------------------------------------
after more_menu
after menu_item "One-handed"
check_size "one-handed right" 886 194
shot 2-one-handed-right
reach=$(( (W / 10) * 3 / 4 ))
clear_field; tap $(( AX - 12 )) "$KY_q"; check_field "edge tap beside Q" "q"
clear_field; tap $(( AX - reach + 6 )) "$KY_a"; check_field "edge tap at the far end of the strip beside A" "a"
clear_field; tap $(( AX - reach - 30 )) "$KY_q"; check_field "tap further out is ignored" ""
clear_field; tap $(( AX + 3 )) "$KY_q"; check_field "tap on Q's own edge" "q"
typing_works "one-handed right"

# --- Side buttons: switch to the left, then back to full width ---------------------------------------------------
after tap "$(gap_x)" "$(toolbar_y)"
check_size "switched to the left" 886 0
shot 3-one-handed-left
clear_field; tap $(( AX + W + 12 )) "$KY_p"; check_field "edge tap beside P" "p"
clear_field; tap $(( AX + W + 12 )) "$KY_l"; check_field "edge tap beside L" "l"
typing_works "one-handed left"
after tap "$(gap_x)" "$(strip_y)"
check_size "full width button" 1080 0

# --- Resize handles: narrower from the left, taller, lifted ------------------------------------------------------
more_menu
menu_item "↔  Size and position" || fail "Size and position is in the ⋯ menu"
sleep 0.8
shot 4-handles
mid_y=$(( AY + H / 2 ))
after $ADB shell input swipe 2 "$mid_y" 162 "$mid_y" 700
check_size "left edge dragged in" 920 160
old_h=$H
after $ADB shell input swipe 540 "$(toolbar_y)" 540 $(( $(toolbar_y) - 120 )) 700
if (( H >= old_h + 100 && H <= old_h + 125 )); then pass "top edge dragged up: rows ${old_h} -> ${H}px"; else fail "top edge: rows ${old_h} -> ${H}px"; fi
old_bottom=$(( AY + H ))
mid_y=$(( AY + H / 2 ))
after $ADB shell input swipe 620 "$mid_y" 620 $(( mid_y - 90 )) 700
if (( old_bottom - (AY + H) >= 80 && old_bottom - (AY + H) <= 100 )); then pass "lifted by $(( old_bottom - AY - H ))px"; else fail "lift: bottom ${old_bottom} -> $(( AY + H ))"; fi
shot 5-resized-editing
tap $(( AX + W - $(dp 40) )) "$(toolbar_y)"
sleep 0.8
shot 6-resized
clear_field; tap $(( AX - 12 )) "$KY_q"; check_field "edge tap beside Q after resizing" "q"
clear_field; tap 540 $(( AY + H + 40 )); check_field "a tap in the lifted space types nothing" ""
typing_works "resized"

# --- Landscape remembers its own size ----------------------------------------------------------------------------
STRIP=36; after rotate 1
sleep 1.5; read_keys
# The input window avoids the side navigation bar and the camera cutout, so "full" is narrower than the screen
LAND_W=$W; LAND_X=$AX
if (( W >= 1900 )); then pass "landscape keeps the full width: ${W}px at x=$AX"; else fail "landscape width ${W}px at x=$AX"; fi
shot 7-landscape
typing_works "landscape"
after more_menu
after menu_item "One-handed"
one_w=$(( LAND_W * 82 / 100 ))
check_size "landscape one-handed right" "$one_w" $(( LAND_X + LAND_W - one_w ))
shot 8-landscape-one-handed
clear_field; tap $(( AX - 12 )) "$KY_q"; check_field "landscape edge tap beside Q" "q"
typing_works "landscape one-handed"
STRIP=40; after rotate 0
sleep 1.5; read_keys
check_size "portrait kept its own size" 920 160
STRIP=36; after rotate 1
sleep 1.5; read_keys
check_size "landscape kept its own size" "$one_w" $(( LAND_X + LAND_W - one_w ))
# Back to full width in landscape with the side button, then portrait
after tap $(( LAND_X + (AX - LAND_X) / 2 )) "$(strip_y)"
check_size "landscape full width button" "$LAND_W" "$LAND_X"
STRIP=40; after rotate 0
sleep 1.5; read_keys

# --- Reset ---------------------------------------------------------------------------------------------------------
more_menu
menu_item "↔  Size and position"
sleep 0.8
after tap $(( AX + $(dp 40) )) "$(toolbar_y)"
tap $(( 1080 - $(dp 40) )) "$(toolbar_y)"
check_size "Reset" 1080 0
typing_works "after reset"

$ADB shell wm user-rotation free
echo "$failures failure(s); screenshots in $OUT"
exit $failures
