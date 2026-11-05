#!/usr/bin/env bash
# Autocorrect golden checks, run before every release.
#
# Needs: an emulator at 1080x2400 with 3-button navigation, Unicorn selected as the keyboard, and the harness app
# (harness/, package com.openai.chatgpt) installed. It types through the on-screen keys, like a person, and compares
# what lands in the field. Usage: SERIAL=emulator-5556 tools/autocorrect_check.sh
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb} -s ${SERIAL:-emulator-5556}"

# Key centres for the Classic layout at 1080x2400
row1="qwertyuiop"; row2="asdfghjkl"; row3="zxcvbnm"
tap() { $ADB shell input tap "$1" "$2"; }
type_keys() {
  local text="$1" c idx
  for (( i=0; i<${#text}; i++ )); do
    c="${text:$i:1}"
    if [[ "$c" == " " ]]; then tap 540 2178
    elif [[ "$row1" == *"$c"* ]]; then idx=$(expr index "$row1" "$c"); tap $(( (45 + (idx-1)*90) * 12 / 10 )) 1686
    elif [[ "$row2" == *"$c"* ]]; then idx=$(expr index "$row2" "$c"); tap $(( (90 + (idx-1)*90) * 12 / 10 )) 1849
    elif [[ "$row3" == *"$c"* ]]; then idx=$(expr index "$row3" "$c"); tap $(( (180 + (idx-1)*90) * 12 / 10 )) 2014
    fi
  done
}
backspace() { tap 1004 2014; }
clear_field() { tap 48 1556; sleep 0.4; tap 48 1556; sleep 0.4; }
field() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null
  $ADB exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep 'EditText' | head -1 | sed -n 's/.* text="\([^"]*\)".*/\1/p'
}

$ADB shell am force-stop com.openai.chatgpt
$ADB shell monkey -p com.openai.chatgpt -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
# The first tap after a launch can come before the field is ready; tap until the keys are up
for attempt in 1 2 3 4 5; do
  tap 540 155
  sleep 2
  $ADB shell dumpsys input_method | grep -q "mInputShown=true" && break
done

failures=0
check() {
  local typed="$1" expected="$2" got
  clear_field
  type_keys "$typed"
  sleep 0.6
  got="$(field)"
  if [[ "$got" == "$expected" ]]; then echo "ok    '$typed' -> '$got'"
  else echo "FAIL  '$typed' -> '$got' (expected '$expected')"; failures=$((failures + 1)); fi
}

# Contractions and plain typos are fixed
check "im going " "I'm going "
check "dont know " "Don't know "
check "itll work " "It'll work "
check "youre right " "You're right "
check "cant wait " "Can't wait "
check "teh end " "The end "
check "becuase it " "Because it "
# Correct words are left alone
check "well done " "Well done "
check "lets go " "Let's go "
# Backspace right after a correction brings the typed word back. Undoing a correction also teaches Unicorn the
# word, so each run takes the next typo from this list (remove learned ones in You > Your words to reuse them).
typos=(wprld hpuse freind brwad plnae)
typo="${typos[$(( $(date +%s) / 60 % ${#typos[@]} ))]}"
clear_field
type_keys "$typo "
sleep 0.4
backspace
sleep 0.6
got="$(field)"
expected="$(tr '[:lower:]' '[:upper:]' <<< "${typo:0:1}")${typo:1}"
if [[ "$got" == "$expected" ]]; then echo "ok    backspace undoes the correction ($typo)"
else echo "FAIL  backspace after correcting '$typo' -> '$got' (expected '$expected')"; failures=$((failures + 1)); fi

echo "$failures failure(s)"
exit $failures
