# TV Kanäle

Begleit-App für Android TV, die zeigt, was auf deinen IPTV-Sendern läuft, und passende Sendungen
vorschlägt, direkt auf dem Startbildschirm (Projectivity Launcher, Google TV, Android-TV-Home).
Ein Klick auf eine Kachel startet den Sender in TiviMate, im eingebauten Player oder in einem anderen Player.

TiviMate selbst bleibt unverändert. Die App liest dieselbe Playlist und denselben EPG wie TiviMate.

## Was die App macht

| Zeile auf dem Startbildschirm | Inhalt |
| --- | --- |
| **Für dich** | Sendungen, die gerade laufen oder bald beginnen und zu deinen Interessen passen, bestes Spiel zuerst. Läuft dasselbe Spiel auf mehreren Sendern, erscheint nur der passendste. |
| **Favoriten** | Deine Lieblingssender mit der laufenden Sendung und dem, was danach kommt. |
| **Als Nächstes ansehen** | Laufende Spiele deiner Teams (abschaltbar). |

Jede Kachel wird selbst gezeichnet: Senderlogo, Titel, LIVE oder Startzeit, Fortschrittsbalken.
Aktualisiert wird alle 15 Minuten; Playlist und EPG werden alle 6 Stunden neu geladen.

### Wie die Vorschläge entstehen

Grundlage ist der EPG. Jede Sendung bekommt Punkte:

- **Teams** (100 Punkte, beide Teams eines Spiels +50): eingebaute Schreibvarianten, z. B. zählt
  „FC Barcelona“ auch als „Barça“, „Barca“, „Barcelona“. Unbekannte Teams gelten wörtlich.
- **Stichworte** (60), z. B. „Clásico“.
- **Ligen** (40), z. B. „LaLiga“ auch als „La Liga“ oder „Primera División“.
- **Andere Fußballspiele** (15), wenn eingeschaltet.
- **LIVE** im Titel (+20), **läuft gerade** (+50), **beginnt in 15 Minuten** (+40).
- **Ausschließen**: steht z. B. „Highlights“ oder „Wdh“ im Titel, fällt die Sendung raus.

Alles davon lässt sich in der App ändern. Die App zeigt die aktuellen Vorschläge mit Punkten auch
selbst an, so lässt sich prüfen, ob die Einstellungen greifen.

## Installieren

1. APK laden: Releases → **Neueste Version** → `TVKanaele.apk`
   (am Fernseher z. B. mit der App *Downloader*).
2. App „TV Kanäle“ öffnen, unten die Playlist eintragen (M3U-Link oder Xtream-Zugang, wie in TiviMate),
   oben die Interessen anpassen, **Speichern und aktualisieren**.
3. **Favoriten wählen** (Suche nach Namen oder Gruppe).
4. Projectivity Launcher: Einstellungen → Kanäle → „Für dich“ und „Favoriten“ von „TV Kanäle“ hinzufügen.
   Google-TV/Android-TV-Home: Button **Kanäle einblenden**.

## TiviMate und der richtige Sender

TiviMate hat keine offizielle Schnittstelle, um von außen einen bestimmten Sender zu öffnen.
Die App bietet deshalb vier Wege, einstellbar unter „Beim Klick abspielen mit“:

1. **TiviMate, falls es den Sender annimmt** (Standard): Die App schickt TiviMate den Stream-Link
   wie an jeden Videoplayer. Ob TiviMate das annimmt, steht oben in der App. Falls nicht, springt
   automatisch der eingebaute Player ein.
2. **Nur TiviMate öffnen**: öffnet TiviMate auf dem zuletzt gesehenen Sender.
3. **Eingebauter Player** (Media3/ExoPlayer): spielt den gewählten Sender sicher ab. Hoch/Runter schaltet
   durch die Favoriten, OK zeigt, was läuft.
4. **Anderer Player**: VLC, Just Player, MX Player … über die Systemauswahl.

## Bauen

GitHub Actions baut bei jedem Push die APK (`.github/workflows/build.yml`), führt vorher die Tests aus
und hängt die APK an das Release `latest`. Lokal: `./gradlew assembleRelease` (Android SDK nötig).

Die APK ist mit dem Schlüssel `app/sideload.keystore` signiert, der absichtlich im Repo liegt,
damit sich neue Versionen über alte installieren lassen. Er ist nur für privates Sideloading gedacht.

## Aufbau

- `core/`: reine Kotlin-Logik ohne Android, mit Tests
  - `M3uParser.kt`, `Xtream.kt`: Playlist lesen
  - `XmltvParser.kt`: EPG als Datenstrom lesen (auch große, gepackte Dateien)
  - `Matcher.kt`, `Aliases.kt`: Interessen bewerten, Schreibvarianten von Teams und Ligen
  - `Suggestions.kt`: Sender dem EPG zuordnen, Vorschläge berechnen, doppelte Sender zusammenfassen
  - `GuideStore.kt`: gefilterte Daten speichern
- `Sync.kt`, `SyncWorker.kt`: Laden, Filtern, Veröffentlichen; alle 15 Minuten per WorkManager
- `ChannelPublisher.kt`: schreibt die Zeilen in den TvProvider
- `TileRenderer.kt`: zeichnet die Kacheln und gibt sie über einen ContentProvider an den Launcher
- `Links.kt`: Klick aus dem Launcher → TiviMate, eingebauter oder anderer Player
- `PlayerActivity.kt`, `MainActivity.kt`, `ChannelPickerActivity.kt`: Oberfläche
