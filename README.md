<p align="center">
  <img src="app/src/main/res/drawable-xhdpi/flutlicht_banner.png" alt="Mayo TV" width="480">
</p>

<p align="center">
  <b>IPTV-Player für Android TV, gebaut fürs Zappen und für Fußball</b><br>
  <sub>Bedienung wie ein klassischer IPTV-Player · Fußball im Launcher · eigene Playlist (M3U, Xtream, Stalker)</sub>
</p>

<p align="center">
  <a href="https://github.com/Jonathanxoxox/tvplayer/actions/workflows/tvplayer.yml"><img alt="APK" src="https://github.com/Jonathanxoxox/tvplayer/actions/workflows/tvplayer.yml/badge.svg"></a>
  <a href="https://github.com/Jonathanxoxox/tvplayer/actions/workflows/smoke.yml"><img alt="Starttest" src="https://github.com/Jonathanxoxox/tvplayer/actions/workflows/smoke.yml/badge.svg"></a>
  <img alt="Android TV" src="https://img.shields.io/badge/Android%20TV-ab%208.0-3DDC84?logo=android&logoColor=white">
  <img alt="Lizenz" src="https://img.shields.io/badge/Lizenz-GPLv3-blue">
</p>

---

Mayo TV ist ein IPTV-Player für die Android-TV-Box. Er ist auf schnelles Umschalten mit der
Fernbedienung ausgelegt: OK öffnet im Vollbild die Senderliste mit Programm, beim Zappen erscheint
unten eine Info-Leiste, Links/Rechts/Hoch/Runter tun, was man von einem IPTV-Player erwartet. Dazu
kommt, worum es eigentlich geht: **Fußball**. Mayo TV weiß, welche Teams und Ligen du verfolgst,
und zeigt laufende Spiele direkt im Launcher und auf einer eigenen Sport-Seite.

> ⚠️ Mayo TV ist nur ein Player. Er bringt keine Sender, Playlists oder Streams mit. Du fügst deine
> eigene Quelle hinzu und bist selbst dafür verantwortlich.

## 📥 Installieren

Die neueste APK liegt immer unter derselben Adresse:

```
https://github.com/Jonathanxoxox/tvplayer/releases/download/latest/MayoTV.apk
```

Bis Oktober 2026 hieß die App Flutlicht; der alte Link auf `Flutlicht.apk` liefert vorerst dieselbe
APK. Am Fernseher z. B. mit **Downloader** öffnen, „Apps aus unbekannten Quellen“ erlauben, installieren.
Danach aktualisiert sich Mayo TV selbst: Jeder Build erscheint zusätzlich als Release `v1.0.<Lauf>`,
das der Updater in der App findet (Einstellungen → App → Nach Updates suchen).

Die App installiert sich als `io.github.jonathanxoxox.tvplayer`. Seit Build v1.0.36 sind alle APKs mit
demselben Schlüssel signiert. Wer eine ältere Version hat, deinstalliert sie einmal, danach gehen
Updates einfach drüber.

## ✨ Was Mayo TV kann

### ⚽ Fußball
- **„Für dich“ im Launcher**: Eine Zeile auf dem Android-TV-Startbildschirm mit den Spielen deiner
  Teams und Ligen, die gerade laufen oder heute kommen. Ein Klick öffnet die Spielseite in der App.
- **Sport-Seite**: Spiele, Ligen und Tabellen deiner Teams mit den Sendern, die sie zeigen. Das Bild gleitet
  vom Spiel ins Vollbild und mit Zurück wieder zurück.
- **„Favoriten“ im Launcher**: deine Lieblingssender als zweite Zeile, ohne die App erst zu öffnen.
- **Gruppe „⚽ Fußball jetzt & heute“** ganz oben in Senderliste und TV-Guide, mit allen Sendern, die
  heute ein passendes Spiel zeigen. Laufende Spiele stehen vorn, Sender-Varianten (HD, UHD, Backup)
  eines Spiels stehen untereinander.
- Teams und Ligen frei wählbar unter **Einstellungen → Sport**. Ab Werk: HSV, BVB, Bayern, Liverpool,
  Barça, Bundesliga, Champions League, deutsche und dänische Nationalmannschaft.

### 📺 Fernsehen
- **Vollbild-Bedienung wie bei klassischen IPTV-Playern**: Senderliste mit Programm und Vorschau,
  Info-Leiste mit EPG, Verlauf und letzten Sendern, Optionen auf OK gedrückt halten.
- **Live-TV ist der TV-Guide**: Sender als Zeilen mit Zeitleiste, oben links die Vorschau mit Ton.
  Drei Ebenen (Menü → Kategorien → Guide), ◀ und Zurück gehen genau eine Ebene zurück. Das Bild
  gleitet aus der Vorschau ins Vollbild und wieder zurück. Programmdaten aus XMLTV oder, wenn die
  Playlist keins mitbringt, direkt vom Anbieter.
- **Catch-up**, Aufnahmen, Multiview, Favoriten, Verlauf und Suche.
- Zwei Wiedergabe-Engines (mpv und ExoPlayer) mit automatischem Wechsel, wenn ein Stream hakt.
- Quellen: **M3U** (URL oder Datei), **Xtream** und **Stalker/Ministra**, auch bequem vom Handy aus
  per QR-Code eingerichtet.

### 🎬 Filme & Serien
- Kataloge mit Postern und Infos von TMDB, links eine Kategorien-Spalte, oben „Weiterschauen“.
  Gezeigt werden nur Titel, die deine Playlist wirklich abspielen kann.
- „Im Abo bei“ zeigt, auf welchen Streamingdiensten ein Titel außerdem läuft.

### 🔎 Suche
- Eine Suche für Sender, Filme und Serien, auch nach Schauspielern und nach Beschreibung
  („der Film mit dem Hai“).
- **Mikrofon-Knopf** an jedem Suchfeld: Die App nimmt selbst auf und schreibt mit, auch wenn die
  Sprachsuche der Box ausgeschaltet ist.

### 🎮 Fernbedienung
Jede Taste ist im Vollbild frei belegbar, **kurz drücken und gedrückt halten getrennt**
(Einstellungen → Layout → Fernbedienungs-Kurzbefehle). Ab Werk:

| Taste | Kurz | Gedrückt halten |
|------|------|------|
| OK | Info-Leiste | Optionen |
| Menü | Optionen | TV-Guide |
| ◀ Links | Senderliste | – |
| ▶ Rechts | Letzter Sender | Info-Leiste |
| ▲ Hoch / ▼ Runter | Sender weiter / zurück | Verlauf / Suche |
| Info, Gelb | Info-Leiste | – |
| Guide, Rot, Grün | TV-Guide | – |
| Blau | Optionen | – |

Zurück im Player führt zur Senderliste, Zurück gedrückt halten aus jedem Menü zum letzten Sender.

### 🎨 Look
- **Cyan Blau** als ruhiges Grundthema: Petrol-Flächen, Cyan-Akzent, heller Fokusbalken.
- Weitere **Farbthemen** in den Einstellungen, von schlichtem Graphit und
  Schiefer über Königsblau und Rasengrün bis Neon und Flutlicht. Alle Themen sind ruhig: keine
  bewegten Hintergründe, keine Pop-ups, nur kurze Übergänge beim Wechsel zwischen Ansichten.
- Eine feste Bedienung statt vieler Layout-Varianten: schmale Menüleiste links, die beim Fokus
  aufklappt.
- Eigenes App-Icon, Banner und Startbildschirm.

## 📸 Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/senderliste.jpg" alt="Senderliste im Vollbild"><br><sub>Senderliste im Vollbild, Gruppe Fußball</sub></td>
    <td align="center"><img src="docs/screenshots/tv-guide.jpg" alt="TV-Guide mit Menü"><br><sub>TV-Guide mit aufgeklapptem Menü</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="2"><img src="docs/screenshots/tasten.jpg" alt="Tastenbelegung" width="50%"><br><sub>Fernbedienungs-Kurzbefehle</sub></td>
  </tr>
</table>

<sub>Aufgenommen im Emulator mit Test-Playlist, daher das Testbild.</sub>

## 🛠️ Bauen

Gebaut wird über GitHub Actions, bei jedem Push auf `main` und auf Arbeitszweige:

| Workflow | Was er macht |
|------|------|
| [`tvplayer.yml`](.github/workflows/tvplayer.yml) | baut die signierte Release-APK und veröffentlicht sie als `latest` und `v1.0.<Lauf>` |
| [`smoke.yml`](.github/workflows/smoke.yml) | JVM-Tests und Starttest im Emulator |
| [`i18n.yml`](.github/workflows/i18n.yml) | prüft bei Pull Requests, dass keine neuen festen Texte im Code stehen (`tools/i18n/`) |
| [`screens.yml`](.github/workflows/screens.yml) | nur von Hand: fährt im Emulator per Fernbedienung durch die App und legt Screenshots auf den Zweig `screens/<Zweig>` |
| [`android.yml`](.github/workflows/android.yml) | nur von Hand: alle Unit-Tests und Lint |

Läuft ein Workflow für einen Push, den ein neuerer Push auf demselben Zweig schon überholt hat,
überspringt er den Rest und endet grün. Die Läufe werden nicht abgebrochen, weil jeder abgebrochene
Lauf eine Mail verschickt.

Signiert wird mit dem Repository-Secret `KEYSTORE_BASE64`. Fehlt es, bekommt jeder Build einen
Einmal-Schlüssel und lässt sich nicht über eine bestehende Installation updaten.

Lokal: Repository in Android Studio öffnen, Build-Variante `standard` (echte Boxen) oder `x86_64`
(Emulator) wählen, starten. Auf der Kommandozeile `./gradlew assembleStandardRelease`. Die
Kernbibliothek (Datenbank, Parser, EPG, Player-Engines) lädt Gradle als fertiges Maven-Paket.
Mindestens Android 8.0 (API 26).

### Wo was liegt

```
app/src/main/java/tv/own/owntv/
├── launcher/   Launcher-Zeilen „Für dich“ und „Favoriten“, Fußball-Erkennung, Fußball-Gruppe
├── player/     Vollbild-Player, Info-Leiste, Senderliste, Tastenbelegung
├── features/
│   ├── shell/  der Rahmen der App: Menüleiste, welche Seite gerade zu sehen ist, Bild-Ebene
│   ├── epg/    Live-TV als TV-Guide
│   ├── sports/ Sport-Seite und Spielseite
│   ├── movies/ series/ vod/   Filme und Serien (TMDB)
│   ├── search/ voice/         Suche, Mikrofon, Suche nach Beschreibung
│   ├── settings/ update/      Einstellungen, Updater
│   └── …       Startseite, Multiview, Aufnahmen, Profile
└── ui/         Farbthemen, Stage-Bausteine (Leisten, Listen, Fokus), Komponenten
app/src/main/res/values/strings.xml   eigene deutsche Texte (Präfix tvplayer_)
app/src/test/                         JVM-Tests
tools/smoke/                          Starttest und Screenshot-Tour
tools/i18n/                           Prüfung auf feste Texte im Code
```

## 🙏 Dienste

Film- und Seriendaten kommen von [TMDB](https://www.themoviedb.org/). This product uses the TMDB API
but is not endorsed or certified by TMDB. Die Untertitelsuche nutzt
[OpenSubtitles.com](https://www.opensubtitles.com/) mit deinem eigenen Konto. Wiedergabe über
[mpv](https://mpv.io/)/[FFmpeg](https://ffmpeg.org/) und [Media3/ExoPlayer](https://github.com/androidx/media).

## 📄 Lizenz

GNU General Public License v3.0, siehe [LICENSE](LICENSE). Mayo TV ist eine veränderte Fassung von
[OwnTV](https://github.com/ahXN00/OwnTV) (© ahXN00, GPLv3). Geändert wurden ab Oktober 2026 unter
anderem Name und Branding, die Vollbild-Bedienung, die Launcher-Zeilen und alle Fußball-Funktionen.
Die Kernbibliothek stammt unverändert von dort.
