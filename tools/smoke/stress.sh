#!/usr/bin/env bash
# Läuft im Emulator-Schritt des Härtetests: wie screens.sh einrichten, dann stress.py statt der Tour.
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
adb logcat -G 16M || true
mkdir -p stress
adb shell monkey -p io.github.jonathanxoxox.tvplayer -c android.intent.category.LAUNCHER 1
sleep 20
python3 tools/smoke/stress.py stress
adb logcat -d -b crash > stress/crash.txt || true
adb logcat -d '*:E' | tail -n 600 > stress/errors.txt || true
adb logcat -d -b main,system | grep -E "ANR in|FATAL|AndroidRuntime" | tail -n 200 > stress/anr-fatal.txt || true
adb pull /data/anr stress/anr 2>/dev/null || true
exit 0
