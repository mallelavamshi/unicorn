#!/usr/bin/env bash
# Demo video (docs/plan/demo-video-x.md): a clean status bar on emulator-5556 for recording, and back again.
#
#   bash tools/demo/clean-bar.sh on     gesture navigation, 9:41, full battery and Wi-Fi, no icons, Do Not Disturb
#   bash tools/demo/clean-bar.sh off    the emulator as it was (three-button navigation on top, live status bar)
#
# Take V:\nidi\unicorn\.shared.lock first. Never run this against emulator-5554.
set -uo pipefail
export MSYS_NO_PATHCONV=1
ADB="/v/android-sdk/platform-tools/adb.exe -s emulator-5556"
sh() { $ADB shell "$@"; }
demo() { sh am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

case "${1:-on}" in
on)
    # Switching navigation restarts the system UI, which forgets demo mode, so it goes first
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
    sh cmd notification set_dnd priority >/dev/null
    sh settings put system screen_off_timeout 1800000
    sh input keyevent KEYCODE_WAKEUP
    ;;
off)
    demo exit
    sh settings put global sysui_demo_allowed 0
    sh cmd notification set_dnd off >/dev/null
    sh cmd overlay enable com.android.internal.systemui.navbar.threebutton
    sh cmd overlay enable com.android.internal.systemui.navbar.gestural
    sh cmd overlay set-priority com.android.internal.systemui.navbar.threebutton highest
    ;;
esac
