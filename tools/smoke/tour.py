#!/usr/bin/env python3
"""Bildschirm-Tour für den Starttest: richtet Flutlicht mit der Test-Playlist ein, läuft per
Fernbedienungstasten durch Menüs, Einstellungen, TV-Guide und Player und legt von jedem Schritt ein
Bildschirmfoto plus die sichtbaren Texte ab. So lassen sich abgeschnittene, überlappende oder
verdeckte Elemente ohne Fernseher prüfen.

Gesteuert wird nur mit Tasten (wie mit der Fernbedienung): `goto` sucht ein Element über den
UI-Baum und bewegt den Fokus mit dem Steuerkreuz dorthin.
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.jonathanxoxox.tvplayer"
OUT = sys.argv[1] if len(sys.argv) > 1 else "tour"
os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "index.txt"), "a", encoding="utf-8")
steps = []
counter = [0]
# Zeilen, die die Tour nie auslöst: sie löschen, setzen zurück oder verlassen die App.
DANGER = re.compile(
    r"Lösch|Zurücksetzen|Abmelden|Entfernen|Neustart|Neu starten|Beenden|Werkseinst|Alle Daten|Delete|Reset|Remove|"
    r"Log ?out|Restart|Exit|Wiederherstellen|Restore|Sicherung erstellen|Leeren|Clear|Update|Aktualisierung",
    re.I,
)


def adb(*args, check=False, timeout=60):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(r.stderr)
    return r.stdout


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def key(name, n=1, wait=0.6, long=False):
    for _ in range(n):
        sh(f"input keyevent {'--longpress ' if long else ''}KEYCODE_{name}")
        time.sleep(wait)


def bounds(node):
    m = re.findall(r"\d+", node.get("bounds", "[0,0][0,0]"))
    return tuple(int(v) for v in m)


def dump():
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1", timeout=40)
        xml = sh("cat /sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            try:
                return ET.fromstring(xml[xml.index("<hierarchy"):])
            except ET.ParseError:
                pass
        time.sleep(1)
    return ET.fromstring("<hierarchy/>")


def label(node):
    return (node.get("text") or node.get("content-desc") or "").strip()


def texts(root):
    return [(label(n), bounds(n)) for n in root.iter("node") if label(n)]


def focused(root):
    best = None
    for n in root.iter("node"):
        if n.get("focused") == "true":
            best = n  # innerstes fokussiertes Element
    return best


def focused_text(root):
    f = focused(root)
    if f is None:
        return ""
    own = label(f)
    if own:
        return own
    inner = [label(n) for n in f.iter("node") if label(n)]
    return " · ".join(inner[:3])


def shot(name):
    counter[0] += 1
    fn = f"{counter[0]:03d}-{re.sub(r'[^A-Za-z0-9äöüÄÖÜß_-]+', '_', name)[:60]}"
    time.sleep(0.8)
    root = dump()
    png = os.path.join(OUT, fn + ".png")
    with open(png, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    t = texts(root)
    w, h = 1920, 1080
    off = [s for s, b in t if b[0] < 0 or b[1] < 0 or b[2] > w or b[3] > h]
    entry = {"step": fn, "focus": focused_text(root), "texts": [s for s, _ in t][:80], "offscreen": off}
    steps.append(entry)
    log.write(json.dumps(entry, ensure_ascii=False) + "\n")
    log.flush()
    print(f"[shot] {fn}  focus={entry['focus']!r}", flush=True)
    return root


def find(root, pattern):
    rx = re.compile(pattern, re.I)
    hits = [n for n in root.iter("node") if label(n) and rx.search(label(n))]
    return hits[0] if hits else None


def goto(pattern, press=True, tries=24):
    """Fokus per Steuerkreuz auf das Element mit passendem Text bewegen und (optional) OK drücken."""
    last = None
    for _ in range(tries):
        root = dump()
        target = find(root, pattern)
        if target is None:
            print(f"[goto] {pattern!r} nicht gefunden; sichtbar: {[s for s, _ in texts(root)][:25]}", flush=True)
            return False
        tb = bounds(target)
        cx, cy = (tb[0] + tb[2]) // 2, (tb[1] + tb[3]) // 2
        f = focused(root)
        if f is not None:
            fb = bounds(f)
            if fb[0] <= cx <= fb[2] and fb[1] <= cy <= fb[3]:
                if press:
                    key("DPAD_CENTER", wait=1.2)
                return True
            fx, fy = (fb[0] + fb[2]) // 2, (fb[1] + fb[3]) // 2
            dx, dy = cx - fx, cy - fy
            d = ("DPAD_RIGHT" if dx > 0 else "DPAD_LEFT") if abs(dx) > abs(dy) else ("DPAD_DOWN" if dy > 0 else "DPAD_UP")
            if last == (fb, d):  # Fokus bewegt sich nicht mehr: andere Achse versuchen
                d = ("DPAD_DOWN" if dy > 0 else "DPAD_UP") if d in ("DPAD_LEFT", "DPAD_RIGHT") else ("DPAD_RIGHT" if dx > 0 else "DPAD_LEFT")
            last = (fb, d)
        else:
            d = "DPAD_DOWN"
        key(d, wait=0.4)
    print(f"[goto] {pattern!r}: Fokus nicht erreicht", flush=True)
    return False


def type_text(value):
    sh("input text " + "'" + value.replace("'", "") + "'")
    time.sleep(0.8)


def step(name, fn):
    try:
        fn()
    except Exception as e:  # die Tour läuft immer weiter, nur der Schritt fehlt dann
        print(f"[step] {name} fehlgeschlagen: {e}", flush=True)
    shot(name)


# ---------------------------------------------------------------- Einrichtung
def setup():
    shot("start")
    for pattern, name in [
        (r"^Los geht", "willkommen"),
        (r"^Weiter$", "anzeigegroesse"),
        (r"^Ich verstehe", "hinweis"),
        (r"^Neues Profil", "einrichtung"),
    ]:
        goto(pattern)
        shot(name)
    goto(r"^Profil erstellen")
    shot("profil")
    goto(r"^Hier eingeben")
    shot("quelle-formular")
    # Typ auf M3U stellen: Zeile "Typ" mit ◀ ▶
    if goto(r"^Typ$", press=False):
        key("DPAD_RIGHT")
    shot("quelle-m3u")
    if goto(r"^URL der Wiedergabeliste"):
        time.sleep(1.2)
        shot("tastatur")
        type_text("http://127.0.0.1:8080/playlist.m3u")
        key("ENTER", wait=1.0)
        shot("url-eingegeben")
        root = dump()
        if find(root, r"127\.0\.0\.1") is None:
            key("BACK", wait=1.0)
    root = shot("formular-fertig")
    if not goto(r"^Importieren starten"):
        key("DPAD_RIGHT")
        key("DPAD_CENTER", wait=1.0)
    for i in range(30):
        time.sleep(3)
        root = dump()
        if find(root, r"Jetzt synchronisieren|^Weiter$|Alles bereit|Fertig|fehlgeschlagen|failed"):
            break
    shot("import")
    for _ in range(6):
        root = dump()
        if find(root, r"Jetzt synchronisieren"):
            goto(r"Jetzt synchronisieren")
            time.sleep(8)
            shot("epg-sync")
        elif find(root, r"^Fertig$"):
            goto(r"^Fertig$")
        elif find(root, r"^Weiter$"):
            goto(r"^Weiter$")
        else:
            break
        time.sleep(2)
    time.sleep(4)
    shot("app-start")


# ---------------------------------------------------------------- Menü und Bereiche
def rail_tour():
    key("BACK", wait=1.0)
    key("DPAD_LEFT", n=4, wait=0.5)
    root = shot("menue-offen")
    seen = []
    for i in range(14):
        root = dump()
        ft = focused_text(root)
        if ft in seen:
            break
        seen.append(ft)
        if ft and not DANGER.search(ft):
            key("DPAD_CENTER", wait=2.5)
            shot(f"bereich-{ft}")
            # einmal in den Inhalt und zurück
            key("DPAD_RIGHT", wait=1.0)
            shot(f"bereich-{ft}-inhalt")
            key("DPAD_LEFT", n=6, wait=0.4)
            root = dump()
            if focused_text(root) != ft:
                goto(re.escape(ft) + "$", press=False)
        key("DPAD_DOWN", wait=0.8)
        shot(f"menue-{i}")
    log.write(json.dumps({"rail": seen}, ensure_ascii=False) + "\n")


def settings_tour():
    key("DPAD_LEFT", n=6, wait=0.4)
    if not goto(r"^Einstellungen$"):
        return
    time.sleep(2)
    shot("einstellungen")
    key("DPAD_RIGHT", wait=0.8)
    rows_seen = []
    for i in range(40):
        root = dump()
        ft = focused_text(root)
        if not ft or ft in rows_seen:
            key("DPAD_DOWN", wait=0.6)
            root = dump()
            ft = focused_text(root)
            if not ft or ft in rows_seen:
                break
        rows_seen.append(ft)
        if DANGER.search(ft):
            key("DPAD_DOWN", wait=0.6)
            continue
        key("DPAD_CENTER", wait=2.0)
        shot(f"einst-{ft}")
        # eine Ebene tiefer: die ersten Zeilen der Unterseite öffnen
        sub_seen = []
        for j in range(6):
            r2 = dump()
            st = focused_text(r2)
            if not st or st in sub_seen or st == ft:
                break
            sub_seen.append(st)
            if not DANGER.search(st):
                key("DPAD_CENTER", wait=1.5)
                shot(f"einst-{ft}-{st}")
                key("BACK", wait=1.2)
            key("DPAD_DOWN", wait=0.6)
        key("BACK", wait=1.5)
        r3 = dump()
        if focused_text(r3) != ft:
            goto(re.escape(ft[:40]), press=False)
        key("DPAD_DOWN", wait=0.6)
    log.write(json.dumps({"settings": rows_seen}, ensure_ascii=False) + "\n")


def player_tour():
    key("DPAD_LEFT", n=6, wait=0.4)
    goto(r"^(Live-?TV|TV)$")
    time.sleep(2)
    key("DPAD_RIGHT", wait=1.0)
    goto(r"Sky Sport Bundesliga 1")
    time.sleep(6)
    shot("player")
    key("DPAD_CENTER", wait=1.5)
    shot("player-ok-infoleiste")
    key("BACK", wait=1.0)
    key("DPAD_UP", wait=2.5)
    shot("player-hoch-zappen")
    key("DPAD_DOWN", wait=2.5)
    shot("player-runter-zappen")
    key("DPAD_RIGHT", wait=2.5)
    shot("player-rechts-letzter-sender")
    key("DPAD_LEFT", wait=1.5)
    shot("player-links-senderliste")
    key("DPAD_LEFT", wait=1.5)
    shot("player-links-gruppen")
    key("BACK", wait=1.0)
    key("BACK", wait=1.0)
    key("DPAD_CENTER", long=True, wait=1.5)
    shot("player-ok-lang-optionen")
    key("DPAD_DOWN", n=3, wait=0.5)
    shot("player-optionen-unten")
    key("BACK", wait=1.0)
    key("MENU", long=True, wait=1.5)
    shot("player-menue-lang-epg")
    key("BACK", wait=1.0)
    key("DPAD_UP", long=True, wait=1.5)
    shot("player-hoch-lang-verlauf")
    key("BACK", wait=1.0)
    key("DPAD_DOWN", long=True, wait=1.5)
    shot("player-runter-lang-suche")
    key("BACK", wait=1.0)
    key("BACK", wait=2.0)
    shot("player-zurueck")
    key("BACK", wait=2.0)
    shot("player-zurueck-2")


def guide_tour():
    key("DPAD_LEFT", n=6, wait=0.4)
    if not goto(r"^TV-Guide$"):
        return
    time.sleep(3)
    shot("guide")
    key("DPAD_RIGHT", n=2, wait=0.6)
    shot("guide-fokus")
    key("DPAD_DOWN", n=4, wait=0.5)
    key("DPAD_RIGHT", n=3, wait=0.5)
    shot("guide-weiter")
    key("PROG_GREEN", wait=1.5)
    shot("guide-gruen-senderoptionen")
    key("BACK", wait=1.0)
    key("INFO", wait=1.5)
    shot("guide-info")
    key("BACK", wait=1.0)
    key("DPAD_CENTER", long=True, wait=1.5)
    shot("guide-ok-lang")
    key("BACK", wait=1.0)


def main():
    step("einrichtung", setup)
    step("menue", rail_tour)
    step("guide", guide_tour)
    step("player", player_tour)
    step("einstellungen-tour", settings_tour)
    with open(os.path.join(OUT, "steps.json"), "w", encoding="utf-8") as f:
        json.dump(steps, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
