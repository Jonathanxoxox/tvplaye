#!/usr/bin/env python3
"""Bildschirm-Tour für den Starttest: richtet Mayo TV mit der Test-Playlist ein, läuft per
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
    # Erst das Bild, dann der UI-Baum: der Dump dauert Sekunden, kurze Einblendungen wären sonst weg.
    png = os.path.join(OUT, fn + ".png")
    with open(png, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    root = dump()
    t = texts(root)
    w, h = 1920, 1080
    off = [s for s, b in t if b[0] < 0 or b[1] < 0 or b[2] > w or b[3] > h]
    entry = {"step": fn, "focus": focused_text(root), "texts": [s for s, _ in t][:80], "offscreen": off}
    steps.append(entry)
    log.write(json.dumps(entry, ensure_ascii=False) + "\n")
    log.flush()
    print(f"[shot] {fn}  focus={entry['focus']!r}", flush=True)
    return root


def find(root, pattern, xmin=0, xmax=99999):
    rx = re.compile(pattern, re.I)
    hits = [n for n in root.iter("node") if label(n) and rx.search(label(n)) and xmin <= bounds(n)[0] <= xmax]
    return hits[0] if hits else None


def goto(pattern, press=True, tries=24, xmin=0, xmax=99999):
    """Fokus per Steuerkreuz auf das Element mit passendem Text bewegen und (optional) OK drücken."""
    last = None
    for _ in range(tries):
        root = dump()
        target = find(root, pattern, xmin, xmax)
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
            # Erst die Zeile wechseln, dann seitlich: ◀ ▶ auf einer Stepper-Zeile verstellt sonst den Wert.
            th = max(20, (fb[3] - fb[1]) // 2)
            d = ("DPAD_DOWN" if dy > 0 else "DPAD_UP") if abs(dy) > th else ("DPAD_RIGHT" if dx > 0 else "DPAD_LEFT")
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
    # Ohne Namen bleibt "Profil erstellen" gesperrt: Name-Zeile (erster Fokus) öffnen und tippen.
    # Der Name ist der erste Fokus; OK öffnet die Tastatur direkt in der Zeile.
    key("DPAD_CENTER", wait=1.5)
    shot("profil-tastatur")
    type_text("Jonathan")
    key("ENTER", wait=1.2)
    shot("profil-name")
    goto(r"^Profil erstellen")
    shot("profil")
    goto(r"^Hier eingeben")
    shot("quelle-formular")
    # Typ auf M3U stellen: Zeile "Typ" mit ◀ ▶
    if goto(r"^Typ$", press=False):
        key("DPAD_RIGHT")
    shot("quelle-m3u")
    # Die Eingabezeilen tauchen im UI-Baum nicht mit Text auf: von "Typ" aus zwei nach unten (Name, URL).
    key("DPAD_DOWN", n=2, wait=0.7)
    if True:
        key("DPAD_CENTER", wait=1.5)
        shot("tastatur")
        type_text("http://127.0.0.1:8080/playlist.m3u")
        key("ENTER", wait=1.0)
        shot("url-eingegeben")
        root = dump()
        if find(root, r"127\.0\.0\.1") is None:
            key("BACK", wait=1.0)
    root = shot("formular-fertig")
    # "Importieren starten" sitzt im Feld rechts und ist von jeder Zeile mit ▶ erreichbar.
    if not goto(r"^Importieren starten"):
        key("DPAD_RIGHT", wait=1.0)
        shot("import-knopf")
        if find(dump(), r"^Importieren starten") is not None:
            key("DPAD_CENTER", wait=1.0)
    for i in range(30):
        time.sleep(3)
        root = dump()
        if find(root, r"Jetzt synchronisieren|^Weiter$|Alles bereit|Fertig|fehlgeschlagen|failed"):
            break
    shot("import")
    # Die EPG-Abfrage hat den Fokus schon auf "Jetzt synchronisieren".
    if steps and steps[-1]["focus"].startswith("Jetzt synchronisieren"):
        key("DPAD_CENTER", wait=8)
        shot("epg-sync")
    # EPG-Abfrage, "Alles bereit" und Co. wegdrücken, bis die Startseite mit dem Menü steht.
    for _ in range(12):
        root = dump()
        print(f"[setup] Dump: {len(list(root.iter('node')))} Knoten, Fokus {focused_text(root)!r}", flush=True)
        if find(root, r"Jetzt synchronisieren"):
            goto(r"Jetzt synchronisieren")
            time.sleep(8)
            shot("epg-sync")
        elif find(root, r"^Fertig$"):
            goto(r"^Fertig$")
        elif find(root, r"^Weiter$"):
            shot("fertig")
            goto(r"^Weiter$")
        elif find(root, r"^(Live-TV|Startseite)$") is not None:
            break
        time.sleep(2)
    time.sleep(4)
    shot("app-start")


# ---------------------------------------------------------------- Menü und Bereiche
RAIL = re.compile(r"^(Suche|Live-TV|Programmübersicht|Sport|Filme|Serien|Downloads|Startseite|Mehr)$")
# Menüpunkte liegen ganz links; der Guide-Titel heißt ebenfalls „Live-TV“.
RAIL_XMAX = 160
SETTINGS_CARDS = [
    "Schnellzugriff", "Profil", "Quellen und Programm", "Darstellung", "Layout", "Inhalte und Metadaten",
    "Player", "Bild", "Ton und Untertitel", "Live-TV", "Wiedergabe und Aufnahme", "App",
]


def slug(text):
    return re.sub(r"[^\wäöüÄÖÜß-]+", "_", text)[:40]


def dismiss_exit(root):
    """Zurück auf dem Menü fragt "Mayo TV beenden?": immer Abbrechen."""
    if find(root, r"^Mayo TV beenden") is not None:
        goto(r"^Abbrechen$")
        return True
    return False


def to_rail():
    """Fokus ins linke Menü: ◀, und wo ◀ nichts mehr bewegt, Zurück."""
    for _ in range(10):
        root = dump()
        if dismiss_exit(root):
            continue
        before = focused_text(root)
        if RAIL.match(before or ""):
            return True
        key("DPAD_LEFT", wait=0.6)
        root = dump()
        if dismiss_exit(root):
            continue
        now = focused_text(root)
        if RAIL.match(now or ""):
            return True
        if now == before:
            key("BACK", wait=1.2)
    print("[rail] Menü nicht erreicht", flush=True)
    return False


def open_section(name):
    if not to_rail():
        return False
    ok = goto("^" + re.escape(name) + "$", xmax=RAIL_XMAX)
    time.sleep(2.5)
    return ok


def rail_tour():
    to_rail()
    shot("menue-offen")
    for name in ["Startseite", "Suche", "Live-TV", "Sport", "Filme", "Serien", "Downloads"]:
        if not open_section(name):
            continue
        shot(f"bereich-{slug(name)}")
        key("DPAD_RIGHT", wait=1.0)
        shot(f"bereich-{slug(name)}-inhalt")
        key("DPAD_DOWN", n=3, wait=0.5)
        shot(f"bereich-{slug(name)}-unten")


def more_tour():
    for item in ["Favoriten", "Wiedergabeverlauf", "Sicherung", "Lokale Synchronisierung", "Fehlerprotokoll", "Über"]:
        if not open_section("Mehr"):
            return
        if item == "Favoriten":
            shot("mehr")
        key("DPAD_RIGHT", wait=0.8)
        if goto("^" + re.escape(item)):
            time.sleep(1.5)
            shot(f"mehr-{slug(item)}")
            key("BACK", wait=1.2)


def guide_tour():
    # Live-TV ist der Guide (wie „TV“ bei TiviMate), eine eigene Programmübersicht gibt es nicht mehr.
    if not open_section("Live-TV"):
        return
    time.sleep(2)
    shot("guide")
    key("DPAD_RIGHT", n=2, wait=0.6)
    shot("guide-fokus")
    key("DPAD_DOWN", n=4, wait=0.5)
    key("DPAD_RIGHT", n=3, wait=0.5)
    shot("guide-weiter")
    key("PROG_GREEN", wait=1.5)
    shot("guide-gruen")
    key("BACK", wait=1.0)
    key("INFO", wait=1.5)
    shot("guide-info")
    key("BACK", wait=1.0)
    key("DPAD_CENTER", long=True, wait=1.5)
    shot("guide-ok-lang")
    key("BACK", wait=1.0)
    # Das Menü über dem Guide: die Seite rückt zur Seite und wird dunkler
    if to_rail() and goto(r"^Live-TV$", press=False, xmax=RAIL_XMAX):
        shot("guide-menue-offen")
        key("DPAD_RIGHT", wait=1.2)
        shot("guide-gruppen")
        key("DPAD_DOWN", n=2, wait=0.6)
        shot("guide-gruppen-runter")
        key("DPAD_RIGHT", wait=1.2)
        shot("guide-nach-gruppen")
        # Drei Ebenen: ◀ aus dem Guide zu den Kategorien, ◀ noch einmal zum Menü, Zurück eine Ebene zurück.
        key("DPAD_LEFT", wait=1.2)
        shot("guide-links-kategorien")
        key("DPAD_LEFT", wait=1.2)
        shot("guide-links-menue")
        key("DPAD_RIGHT", wait=1.2)
        shot("guide-menue-rechts-kategorien")
        key("DPAD_RIGHT", wait=1.2)
        key("BACK", wait=1.2)
        shot("guide-zurueck-kategorien")
        key("DPAD_RIGHT", wait=1.2)


def in_fullscreen():
    """Vollbild: kein Menü und keine Senderliste mit Fokus."""
    root = dump()
    return not RAIL.match(focused_text(root) or "") and find(root, r"^Fußball$") is None


def player_tour():
    if not open_section("Live-TV"):
        return
    shot("live")
    # Live-TV ist immer der Guide; der Sender wird per Namen in den Zeilen gesucht.
    key("DPAD_RIGHT", wait=1.0)
    shot("live-guide")
    if not goto(r"^Sky Sport Bundesliga 1"):
        return
    time.sleep(5)
    shot("live-vorschau")
    # Erstes OK spielt in der Vorschau, das zweite geht ins Vollbild
    if not in_fullscreen():
        key("DPAD_CENTER", wait=5)
    shot("player")
    # Zurück: das Bild gleitet in die Vorschau-Ecke des Guides und läuft dort weiter; OK holt es zurück.
    key("BACK", wait=0.15)
    shot("player-zurueck-gleitet")
    time.sleep(2)
    shot("player-zurueck-im-guide")
    key("DPAD_CENTER", wait=4)
    shot("player-wieder-vollbild")
    key("DPAD_CENTER", wait=1.5)
    shot("player-ok-infoleiste")
    key("BACK", wait=1.0)
    key("DPAD_UP", wait=1.2)
    shot("player-hoch-zappen")
    key("DPAD_DOWN", wait=1.2)
    shot("player-runter-zappen")
    key("DPAD_RIGHT", wait=1.2)
    shot("player-rechts-letzter-sender")
    key("DPAD_LEFT", wait=1.5)
    shot("player-links-senderliste")
    key("DPAD_LEFT", wait=1.5)
    shot("player-links-gruppen")
    key("BACK", wait=1.0)
    key("BACK", wait=1.0)
    shot("player-nach-liste")
    for code, name in [
        ("DPAD_CENTER", "ok-lang-optionen"), ("MENU", "menue"), ("MENU", "menue-lang-epg"),
        ("DPAD_UP", "hoch-lang-verlauf"), ("DPAD_DOWN", "runter-lang-suche"), ("PROG_RED", "rot"),
        ("PROG_BLUE", "blau"), ("INFO", "info"),
    ]:
        key(code, long=name.endswith(("lang-optionen", "lang-epg", "lang-verlauf", "lang-suche")), wait=1.8)
        shot(f"player-{name}")
        if name == "ok-lang-optionen":
            key("DPAD_DOWN", n=3, wait=0.5)
            shot("player-optionen-unten")
        key("BACK", wait=1.2)
        if dismiss_exit(dump()):
            break
    key("BACK", wait=1.5)
    shot("player-zurueck")
    key("BACK", wait=1.5)
    shot("player-zurueck-2")


def vod_section(name, layout):
    """Filme oder Serien: Raster, Hero, Kategorien und die Detailseite eines Titels."""
    if not open_section(name):
        return
    time.sleep(4)  # Metadaten (TMDB) für den ersten Titel nachladen lassen
    tag = f"{slug(name)}-{layout}"
    shot(f"vod-{tag}")
    key("DPAD_RIGHT", wait=1.0)
    time.sleep(3)
    shot(f"vod-{tag}-titel")
    key("DPAD_RIGHT", n=2, wait=0.8)
    time.sleep(3)
    shot(f"vod-{tag}-titel-3")
    key("DPAD_DOWN", wait=1.0)
    time.sleep(2)
    shot(f"vod-{tag}-zeile-2")
    key("DPAD_UP", wait=0.8)
    key("DPAD_LEFT", n=6, wait=0.6)
    shot(f"vod-{tag}-links")
    key("DPAD_DOWN", n=3, wait=0.8)
    time.sleep(2)
    shot(f"vod-{tag}-kategorie")
    # OK auf einem Film spielt ihn ab (auch ein langes OK über adb); eine Serie öffnet ihre Seite.
    if name == "Serien":
        key("DPAD_RIGHT", wait=1.0)
        key("DPAD_CENTER", wait=3.0)
        time.sleep(3)
        shot(f"vod-{tag}-serie")
        key("DPAD_DOWN", n=2, wait=0.8)
        shot(f"vod-{tag}-serie-unten")
        key("BACK", wait=1.5)


def vod_details():
    """Filme: OK halten auf einem Poster öffnet die Optionen, darin die TMDB-Detailseite."""
    if not open_section("Filme"):
        return
    time.sleep(3)
    # ▶ erreicht die Kategorien-Spalte, zweimal ▶ das zweite Poster (wie in vod_section).
    key("DPAD_RIGHT", wait=1.0)
    key("DPAD_RIGHT", n=2, wait=0.8)
    # Ein langes OK über adb spielt den Film ab; die Menü-Taste öffnet die Optionen wie OK halten.
    key("MENU", wait=1.5)
    shot("vod-filme-optionen")
    if goto(r"details", tries=12):
        time.sleep(4)
        shot("vod-filme-details")
        key("DPAD_DOWN", n=2, wait=0.8)
        shot("vod-filme-details-unten")
        key("BACK", wait=1.5)
    if in_fullscreen():
        key("BACK", n=2, wait=1.5)


def search_tour():
    """Suche: ein Titel, ein Schauspieler (TMDB) und eine Beschreibung (Jev), jeweils Liste und erste Zeile."""
    if not open_section("Suche"):
        return
    # Die Suche setzt den Fokus selbst ins Feld; ▶ wäre schon das Mikrofon.
    time.sleep(1.0)
    if RAIL.match(focused_text(dump()) or ""):
        key("DPAD_RIGHT", wait=1.0)
    shot("suche-leer")
    for q, tag in [("top gun", "titel"), ("Tom Cruise", "schauspieler"), ("gruseliger Vampirfilm", "beschreibung")]:
        key("DPAD_CENTER", wait=1.5)
        type_text(q.replace(" ", "%s"))
        key("ENTER", wait=1.0)
        time.sleep(12)  # TMDB und Jev antworten nicht sofort
        shot(f"suche-{tag}")
        key("DPAD_DOWN", n=2, wait=0.8)
        time.sleep(3)
        shot(f"suche-{tag}-zeile")
        # Zurück leert die Suche und setzt den Fokus wieder ins Feld.
        key("BACK", wait=1.5)


def vod_tour():
    # Filme und Serien haben nur noch das filmische Layout.
    vod_section("Filme", "filmisch")
    vod_section("Serien", "filmisch")
    vod_details()
    search_tour()


def settings_tour():
    if not open_section("Mehr"):
        return
    key("DPAD_RIGHT", wait=0.8)
    if not goto(r"^Einstellungen$"):
        return
    time.sleep(2)
    shot("einstellungen")
    # Von der Leiste aus: ▶ landet in der Suchzeile, ▼ auf der ersten Kachel
    key("DPAD_RIGHT", wait=0.8)
    key("DPAD_DOWN", wait=0.8)
    shot("einstellungen-raster")
    for card in SETTINGS_CARDS:
        if not goto("^" + re.escape(card) + "$", xmin=600):
            continue
        time.sleep(1.5)
        shot(f"einst-{slug(card)}")
        key("DPAD_DOWN", n=6, wait=0.4)
        shot(f"einst-{slug(card)}-mitte")
        key("DPAD_DOWN", n=10, wait=0.4)
        shot(f"einst-{slug(card)}-ende")
        # Die Tastenbelegung ist die letzte Zeile von Layout
        if card == "Layout" and goto(r"^Fernbedienungs-Kurzbefehle"):
            time.sleep(1.5)
            shot("einst-tasten")
            key("DPAD_DOWN", wait=0.6)
            shot("einst-tasten-zeile")
            key("DPAD_CENTER", wait=1.5)
            shot("einst-tasten-auswahl")
            key("BACK", wait=1.0)
            key("DPAD_DOWN", n=12, wait=0.4)
            shot("einst-tasten-unten")
            key("BACK", wait=1.5)
        key("BACK", wait=1.5)
        root = dump()
        if dismiss_exit(root) or find(root, r"^Alle Einstellungen durchsuchen") is None:
            key("BACK", wait=1.5)


SCENES = ["Basalt", "Taupe", "Salbei", "Pflaume", "Neon", "Abendrot", "Flutlicht", "Gletscher"]


def open_scene_row():
    """Mehr › Einstellungen › Darstellung › Glas & Hintergrund, Fokus auf die Zeile „Farbthema“."""
    if not open_section("Mehr"):
        return False
    key("DPAD_RIGHT", wait=0.8)
    if not goto(r"^Einstellungen$"):
        return False
    time.sleep(2)
    key("DPAD_RIGHT", wait=0.8)
    key("DPAD_DOWN", wait=0.8)
    if not goto(r"^Darstellung$", xmin=600):
        return False
    time.sleep(1.5)
    if not goto(r"^Glas & Hintergrund$"):
        return False
    time.sleep(1.5)
    return goto(r"^Farbthema$", press=False)


def scenes_tour():
    """Jedes Farbthema einstellen (Vorschau im Feld rechts), dann Startseite und Live-TV davor."""
    for name in SCENES:
        if not open_scene_row():
            shot(f"szene-{slug(name)}-zeile-fehlt")
            return
        for _ in range(20):
            if find(dump(), "^" + re.escape(name) + "$") is not None:
                break
            key("DPAD_RIGHT", wait=1.0)
        time.sleep(2)
        shot(f"szene-{slug(name)}-einstellung")
        # Erst mit Zurück raus: ◀ auf der Szenen-Zeile würde die Szene wieder umstellen.
        key("BACK", wait=1.5)
        key("BACK", wait=1.5)
        key("BACK", wait=1.5)
        for section in ["Startseite", "Live-TV"]:
            if open_section(section):
                time.sleep(2)
                shot(f"szene-{slug(name)}-{slug(section)}")
        key("DPAD_RIGHT", wait=1.5)
        shot(f"szene-{slug(name)}-live-liste")


def main():
    step("einrichtung", setup)
    # Ohne fertige Einrichtung würden die weiteren Schritte nur im Assistenten herumdrücken.
    if find(dump(), r"Los geht|Wer schaut gerade|Angenehm lesbar|Mayo TV einrichten|Playlist hinzufügen|Hier eingeben|Importieren starten") is not None:
        print("[tour] Einrichtung nicht abgeschlossen, Tour endet hier", flush=True)
        return
    # TOUR_PARTS=vod,scenes,player,… läuft nur durch diese Teile (schneller beim Arbeiten an einem Bereich).
    parts = [p for p in os.environ.get("TOUR_PARTS", "all").replace(" ", "").split(",") if p]
    for part, name, fn in [
        ("menu", "menue", rail_tour), ("vod", "filme-serien", vod_tour), ("more", "mehr-tour", more_tour),
        ("guide", "guide", guide_tour), ("player", "player", player_tour),
        ("settings", "einstellungen-tour", settings_tour), ("scenes", "szenen", scenes_tour),
    ]:
        if "all" in parts or part in parts:
            step(name, fn)
    with open(os.path.join(OUT, "steps.json"), "w", encoding="utf-8") as f:
        json.dump(steps, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
