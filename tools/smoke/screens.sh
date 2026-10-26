#!/usr/bin/env bash
# Läuft im Emulator-Schritt: Deutsch und 1080p einstellen, App installieren, Tour starten.
set -x
adb root; sleep 3; adb wait-for-device
adb shell setprop persist.sys.locale de-DE
adb shell setprop ctl.restart zygote
sleep 25; adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell wm size 1920x1080
adb shell wm density 320
adb shell settings put system user_rotation 1 || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
pgrep -f "http.server 8080" >/dev/null || (nohup python3 -m http.server 8080 --directory fx > http.log 2>&1 &)
adb reverse tcp:8080 tcp:8080
adb install -r dist/app.apk
adb logcat -c
adb shell monkey -p io.github.jonathanxoxox.tvplayer -c android.intent.category.LAUNCHER 1
sleep 20
python3 tools/smoke/tour.py tour
adb logcat -d -b crash > tour/crash.txt || true
adb logcat -d '*:E' | tail -n 400 > tour/errors.txt || true
exit 0
