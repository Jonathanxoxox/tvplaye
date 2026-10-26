#!/usr/bin/env python3
"""Testdaten für die Bildschirm-Tour: Playlist und TV-Guide rund um die aktuelle Uhrzeit.

Die Sender zeigen alle auf den lokalen Teststream (stream/index.m3u8), den die Tour per ffmpeg
erzeugt. Lange deutsche Namen sind Absicht: sie zeigen, wo Text abgeschnitten wird.
"""
import datetime as dt
import pathlib
import sys

out = pathlib.Path(sys.argv[1])
base = "http://127.0.0.1:8080"
groups = {
    "Fußball": [
        "Sky Sport Bundesliga 1 HD", "Sky Sport Bundesliga 2 HD", "DAZN 1 HD", "DAZN 2 HD",
        "Sky Sport Top Event UHD", "Sportschau Live", "TV 2 Sport (DK)",
    ],
    "Deutschland": [
        "Das Erste HD", "ZDF HD", "RTL HD", "SAT.1 HD", "ProSieben HD", "VOX HD", "kabel eins HD",
        "NDR Fernsehen Hamburg HD", "WDR Fernsehen Köln HD", "Bayerisches Fernsehen Süd HD",
    ],
    "Nachrichten": ["tagesschau24 HD", "n-tv HD", "WELT HD", "phoenix HD"],
    "Dokumentationen und Wissen extra langer Gruppenname": ["arte HD", "3sat HD", "ZDFinfo HD", "Discovery Channel HD"],
}
shows = {
    "Fußball": [
        ("Bundesliga: Hamburger SV – Borussia Dortmund", "Live aus dem Volksparkstadion. Vorberichte, Spiel und Analyse."),
        ("UEFA Champions League: FC Liverpool – FC Barcelona", "Das Spitzenspiel der Gruppenphase in voller Länge."),
        ("Bundesliga – Die Konferenz am Samstagnachmittag", "Alle Spiele, alle Tore."),
    ],
    "default": [
        ("Tagesschau", "Nachrichten des Tages."),
        ("Ein ausgesprochen langer Sendungstitel, der in keine Zelle passt – Teil 2", "Beschreibung mit vielen Wörtern, damit der Text umbricht."),
        ("Tatort: Der Fall Flutlicht", "Krimi, Deutschland 2026."),
    ],
}
lines = [f'#EXTM3U url-tvg="{base}/epg.xml"']
programmes = []
now = dt.datetime.now(dt.timezone.utc).replace(minute=0, second=0, microsecond=0)
n = 0
for group, names in groups.items():
    for name in names:
        n += 1
        cid = f"ch{n}"
        lines.append(f'#EXTINF:-1 tvg-id="{cid}" tvg-name="{name}" group-title="{group}",{name}')
        lines.append(f"{base}/stream/index.m3u8")
        pool = shows["Fußball" if group == "Fußball" else "default"]
        t = now - dt.timedelta(hours=3)
        for i in range(16):
            title, desc = pool[(i + n) % len(pool)]
            end = t + dt.timedelta(minutes=45 if i % 2 else 90)
            programmes.append(
                f'<programme start="{t:%Y%m%d%H%M%S} +0000" stop="{end:%Y%m%d%H%M%S} +0000" channel="{cid}">'
                f"<title>{title}</title><desc>{desc}</desc></programme>"
            )
            t = end
out.mkdir(parents=True, exist_ok=True)
(out / "playlist.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")
channels = "".join(f'<channel id="ch{i}"><display-name>ch{i}</display-name></channel>' for i in range(1, n + 1))
(out / "epg.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?><tv>' + channels + "".join(programmes) + "</tv>\n", encoding="utf-8"
)
print(f"{n} Sender, {len(programmes)} Sendungen")
