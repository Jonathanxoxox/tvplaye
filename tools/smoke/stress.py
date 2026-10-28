#!/usr/bin/env python3
"""Härtetest im Emulator: richtet Mayo TV wie die Bildschirm-Tour ein und quält die App dann so,
wie es ein echter Fernsehabend tut. Stream-Server fällt weg, Internet ist aus, die Playlist kommt
kaputt an, Android beendet den Prozess im Hintergrund, die Schriftgröße ändert sich (die Activity wird
neu gebaut), Tasten kommen schneller als die App zeichnet, und am Ende drückt monkey wild herum.

Nach jedem Teil wird geprüft, ob die App noch läuft, ob es einen Absturz (Java oder nativ) oder ein
ANR gab. Ergebnis: <out>/summary.txt, dazu Fotos und Texte wie bei der Tour.
"""
import os
import random
import re
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tour as T  # noqa: E402  (nutzt sys.argv[1] als Ausgabeordner, wie wir)

PKG = T.PKG
OUT = T.OUT
FX = os.environ.get("STRESS_FX", "fx")
results = []


def pid():
    return T.sh(f"pidof {PKG}").strip()


def crash_count():
    buf = T.adb("logcat", "-d", "-b", "crash")
    return buf.count(f"Process: {PKG}") + buf.count(f">>> {PKG} <<<")


def anr_count():
    return len(re.findall(rf"ANR in {re.escape(PKG)}", T.adb("logcat", "-d", "-b", "main,system")))


def launch():
    T.sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(10)


def check(name, fn):
    """Einen Teil laufen lassen und danach: noch am Leben, neuer Absturz, neues ANR?"""
    c0, a0 = crash_count(), anr_count()
    note = ""
    try:
        fn()
    except Exception as e:  # der Test läuft immer weiter
        note = f"Schritt-Fehler: {e}"
    time.sleep(2)
    crashed, anr, alive = crash_count() > c0, anr_count() > a0, bool(pid())
    state = "ABSTURZ" if crashed else "ANR" if anr else "OK" if alive else "BEENDET"
    line = f"{state:8} {name}" + (f"  ({note})" if note else "")
    results.append(line)
    print(f"[stress] {line}", flush=True)
    T.shot(f"{name}-ende")
    if not alive:
        launch()


# ---------------------------------------------------------------- Teile
def play_first_channel():
    """Live-TV: ▶ in die Senderzeilen, OK spielt in der Vorschau, das zweite OK geht ins Vollbild."""
    T.open_section("Live-TV")
    T.key("DPAD_RIGHT", wait=1.0)
    T.key("DPAD_RIGHT", wait=1.0)
    T.key("DPAD_CENTER", wait=5)
    if not T.in_fullscreen():
        T.key("DPAD_CENTER", wait=5)


def http_server(on):
    if on:
        subprocess.Popen(
            ["python3", "-m", "http.server", "8080", "--directory", FX],
            stdout=open("http-stress.log", "a"), stderr=subprocess.STDOUT,
        )
        time.sleep(2)
        T.adb("reverse", "tcp:8080", "tcp:8080")
    else:
        subprocess.run(["pkill", "-f", "http.server 8080"])
        time.sleep(1)


def stream_loss():
    """Sender läuft, dann ist der Stream-Server weg: zappen, Menü, zurück, Server wieder da."""
    play_first_channel()
    T.shot("stream-laeuft")
    http_server(False)
    time.sleep(20)
    T.shot("stream-weg")
    T.key("DPAD_UP", wait=3)
    T.key("DPAD_DOWN", wait=3)
    T.key("DPAD_CENTER", wait=2)
    T.shot("stream-weg-zappen")
    T.key("BACK", wait=2)
    T.key("BACK", wait=2)
    for section in ["Sport", "Startseite", "Live-TV"]:
        T.open_section(section)
        time.sleep(2)
    T.shot("stream-weg-menue")
    http_server(True)
    play_first_channel()
    time.sleep(8)
    T.shot("stream-wieder-da")
    T.key("BACK", wait=2)


def airplane(on):
    T.sh(f"cmd connectivity airplane-mode {'enable' if on else 'disable'}")
    radio = "disable" if on else "enable"
    T.sh(f"svc wifi {radio}; svc data {radio}")
    time.sleep(4)


def offline():
    """Kein Internet (der lokale Teststream läuft weiter): jeder Bereich, Suche, Kaltstart offline."""
    airplane(True)
    try:
        for section in ["Startseite", "Sport", "Filme", "Serien", "Suche", "Live-TV"]:
            if T.open_section(section):
                T.key("DPAD_RIGHT", wait=1.5)
                T.shot(f"offline-{T.slug(section)}")
        # Ein Film offline öffnen: TMDB, „Im Abo bei“ und die Suche nach Beschreibung gehen nicht.
        if T.open_section("Filme"):
            T.key("DPAD_RIGHT", n=2, wait=1.0)
            T.key("DPAD_CENTER", wait=4)
            T.shot("offline-film")
            T.key("BACK", wait=1.5)
        if T.open_section("Suche"):
            T.key("DPAD_RIGHT", wait=1.0)
            T.key("DPAD_CENTER", wait=1.5)
            T.type_text("ein spannender Krimi am Hafen")
            T.key("ENTER", wait=6)
            T.shot("offline-suche-beschreibung")
            T.key("BACK", wait=1.0)
            T.key("BACK", wait=1.0)
        T.sh(f"am force-stop {PKG}")
        launch()
        time.sleep(8)
        T.shot("offline-kaltstart")
    finally:
        airplane(False)
    time.sleep(6)
    T.shot("wieder-online")


BROKEN = {
    "404": None,
    "html": "<html><body><h1>502 Bad Gateway</h1></body></html>\n",
    "leer": "",
    "kaputt": "\n".join([
        "#EXTM3U url-tvg=\"http://127.0.0.1:8080/epg.xml\"",
        "#EXTINF:-1 tvg-id=\"x1\" group-title=\"Kaputt\",Ohne URL danach",
        "#EXTINF:-1 tvg-id=\"x2\" group-title=\"Kaputt\"",
        "http://127.0.0.1:8080/stream/index.m3u8",
        "#EXTINF:abc tvg-logo=\"http://127.0.0.1:9/nichts.png\" group-title=\"\",",
        "not-a-url",
        "#EXTINF:-1 group-title=\"Kaputt\"," + "Sehr langer Name " * 120,
        "http://127.0.0.1:8080/stream/index.m3u8",
        "#EXTINF:-1 tvg-id=\"x3\" group-title=\"😀 Emoji ünd Ümlaute\",⚽ HSV – BVB \u0000 �",
        "http://127.0.0.1:8080/fehlt.m3u8",
        "#EXTINF:-1 type=\"movie\" group-title=\"Filme | Kaputt\",(0)",
        "rtsp://127.0.0.1:9/x",
        "#EXTINF:-1 type=\"series\" group-title=\"Serien | Kaputt\",Serie S99E999999",
        "http://127.0.0.1:8080/stream/index.m3u8",
        "#EXTINF",
    ]) + "\n",
}
BROKEN_EPG = '<?xml version="1.0" encoding="UTF-8"?><tv><channel id="ch1"><display-name>ch1</display-name>' \
    '<programme start="kaputt" stop="20991231000000 +0000" channel="ch1"><title>Ohne Ende'


def resync():
    """Einstellungen › Quellen und Programm › Wiedergabelisten › die Playlist neu synchronisieren."""
    if not T.open_section("Mehr"):
        return
    T.key("DPAD_RIGHT", wait=0.8)
    if not T.goto(r"^Einstellungen$"):
        return
    time.sleep(1.5)
    T.key("DPAD_RIGHT", wait=0.8)
    T.key("DPAD_DOWN", wait=0.8)
    if not T.goto(r"^Quellen und Programm$", xmin=600):
        return
    time.sleep(1.2)
    if not T.goto(r"^Wiedergabelisten$"):
        return
    time.sleep(1.5)
    T.shot("wiedergabelisten")
    if T.goto(r"^Meine Wiedergabeliste$"):
        time.sleep(1.5)
        T.shot("playlist-optionen")
        T.goto(r"ynchronisier|Aktualisier|Neu laden|Sync")
        time.sleep(1.5)
        # Die Rückfrage „… erneut synchronisieren“: ihr Knopf hat schon den Fokus.
        if T.goto(r"^Jetzt erneut synchronisieren$"):
            time.sleep(1.5)
    time.sleep(12)
    T.shot("nach-sync")
    for _ in range(4):
        T.key("BACK", wait=1.0)


def broken_sources():
    good_m3u, good_epg = os.path.join(FX, "playlist.m3u"), os.path.join(FX, "epg.xml")
    shutil.copy(good_m3u, good_m3u + ".gut")
    shutil.copy(good_epg, good_epg + ".gut")
    try:
        for name, body in BROKEN.items():
            if body is None:
                os.remove(good_m3u)
            else:
                with open(good_m3u, "w", encoding="utf-8") as f:
                    f.write(body)
            if name == "kaputt":
                with open(good_epg, "w", encoding="utf-8") as f:
                    f.write(BROKEN_EPG)
            check(f"playlist-{name}", lambda: (resync(), T.open_section("Live-TV"), T.key("DPAD_RIGHT", wait=2)))
    finally:
        shutil.copy(good_m3u + ".gut", good_m3u)
        shutil.copy(good_epg + ".gut", good_epg)
    resync()


def lifecycle():
    """Android räumt auf: Prozess im Hintergrund beendet, Speicher knapp, Activity neu gebaut."""
    play_first_channel()
    T.key("HOME", wait=3)
    T.sh(f"am kill {PKG}")
    time.sleep(2)
    launch()
    T.shot("nach-prozess-tod")
    T.sh(f"am send-trim-memory {PKG} RUNNING_CRITICAL")
    time.sleep(2)
    T.key("HOME", wait=2)
    T.sh(f"am send-trim-memory {PKG} COMPLETE")
    launch()
    T.shot("nach-speicher-knapp")
    # Schriftgröße ist keine der configChanges: die Activity wird mitten im Bild neu gebaut.
    T.sh("settings put system font_scale 1.3")
    time.sleep(6)
    T.shot("schrift-gross")
    T.sh("settings put system font_scale 1.0")
    time.sleep(6)
    # Kaltstarts hintereinander, Tasten schon während des Starts.
    for _ in range(3):
        T.sh(f"am force-stop {PKG}")
        T.sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
        for k in ["DPAD_DOWN", "BACK", "DPAD_LEFT", "DPAD_CENTER"]:
            T.key(k, wait=0.15)
        time.sleep(6)
    T.shot("kaltstarts")


def key_storm():
    """Schneller als die App zeichnet: Zappen im Vollbild, Fokus quer durch den Guide."""
    play_first_channel()
    for k in ["DPAD_UP"] * 25 + ["DPAD_DOWN"] * 25:
        T.sh(f"input keyevent KEYCODE_{k}")
    time.sleep(3)
    T.shot("zapp-sturm")
    T.key("BACK", wait=0.3)
    seq = ["DPAD_DOWN", "DPAD_RIGHT", "DPAD_DOWN", "DPAD_LEFT", "DPAD_UP", "DPAD_RIGHT", "BACK", "DPAD_LEFT"] * 8
    T.sh("input keyevent " + " ".join(f"KEYCODE_{k}" for k in seq))
    time.sleep(3)
    T.shot("fokus-sturm")


RANDOM_KEYS = (
    ["DPAD_UP", "DPAD_DOWN"] * 5 + ["DPAD_LEFT", "DPAD_RIGHT"] * 4 + ["DPAD_CENTER", "BACK"] * 2
    + ["MENU", "CHANNEL_UP", "CHANNEL_DOWN", "PROG_RED", "PROG_GREEN", "PROG_YELLOW", "INFO", "MEDIA_PLAY_PAUSE"]
)


def monkey(seed, keys=1500, batch=15):
    """Zufällige Fernbedienungstasten wie monkey, aber in der App: wer sie verlässt, wird zurückgeholt,
    und auf Löschen/Zurücksetzen fällt kein OK (dann Zurück)."""
    rnd = random.Random(seed)
    walk = open(os.path.join(OUT, f"monkey-{seed}.txt"), "w", encoding="utf-8")
    for _ in range(keys // batch):
        if T.DANGER.search(T.focused_text(T.dump()) or ""):
            T.key("BACK", wait=0.5)
        seq = [rnd.choice(RANDOM_KEYS) for _ in range(batch)]
        walk.write(" ".join(seq) + "\n")
        T.sh("input keyevent " + " ".join(f"KEYCODE_{k}" for k in seq))
        if PKG not in T.sh("dumpsys activity activities | grep -m1 mResumedActivity"):
            walk.write("-- App verlassen, neu gestartet\n")
            launch()
    walk.close()


def still_set_up():
    """Nach allem: Profil und Playlist sind noch da (kein Einrichtungs-Assistent)."""
    T.sh(f"am force-stop {PKG}")
    launch()
    time.sleep(6)
    root = T.dump()
    if T.find(root, r"Los geht|Mayo TV einrichten|Playlist hinzufügen|Hier eingeben") is not None:
        raise RuntimeError("Einrichtung erscheint wieder: Profil/Playlist verloren")


def main():
    T.step("einrichtung", T.setup)
    if T.find(T.dump(), r"Los geht|Mayo TV einrichten|Playlist hinzufügen|Hier eingeben|Importieren starten") is not None:
        results.append("FEHLT    Einrichtung nicht abgeschlossen, Härtetest endet hier")
    else:
        parts = [p for p in os.environ.get("STRESS_PARTS", "all").replace(" ", "").split(",") if p]
        for part, fn in [
            ("stream", stream_loss), ("offline", offline), ("playlist", broken_sources),
            ("lifecycle", lifecycle), ("keys", key_storm),
            ("monkey", lambda: (monkey(7), monkey(42))), ("persist", still_set_up),
        ]:
            if "all" in parts or part in parts:
                if part == "playlist":
                    broken_sources()  # prüft jede Variante selbst
                else:
                    check(part, fn)
    crash = T.adb("logcat", "-d", "-b", "crash")
    with open(os.path.join(OUT, "summary.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(results) + "\n")
        if crash.strip():
            f.write("\n===== Absturz-Puffer =====\n" + crash[-20000:])
    print("\n===== Härtetest =====\n" + "\n".join(results), flush=True)


if __name__ == "__main__":
    main()
