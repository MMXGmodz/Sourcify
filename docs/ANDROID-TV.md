# Sourcify für Android TV — Vorschau

[TV-APK und Quellcode](https://github.com/MMXGmodz/Sourcify/releases/tag/android-tv-v13.7.1-preview.1)

Eine eigene TV-Variante des bestehenden Android-Projekts. Audio-Service, Suche über InnerTubeX, Room-Datenbank, Favoriten, Playlists, Download-Service, Einstellungen und Canvas-Komponenten werden weiterverwendet. Es ist kein PC-Begleitserver nötig.

## Installation

Voraussetzung ist ein Android-TV-/Google-TV-Gerät mit Android 8.0 oder neuer. Die aktuelle Vorschau wurde noch nicht auf einem echten TV-Gerät getestet; daraus folgt keine Zusage für jedes Modell oder Fire TV.

`Sourcify-TV.apk` aus dem Release auf den Fernseher übertragen und dort installieren. Je nach Gerät muss die Installation aus der verwendeten Quelle erlaubt werden. Wenn ADB bereits eingerichtet ist, ist auch `adb install -r Sourcify-TV.apk` möglich.

Die App erscheint als **Sourcify TV** im TV-Launcher. Paketkennung: `io.sourcify.player.tv`. Die normale Handy-App wird nicht überschrieben. Lokale Bibliothek und Downloads sind aufgrund der getrennten Installation nicht automatisch mit der Handy-App geteilt.

## Bedienung

- Steuerkreuz: Fokus bewegen; hellblaue Fläche und weißer Rahmen markieren den Fokus.
- OK/Enter: fokussiertes Element auswählen.
- Zurück: eine ausgewählte Playlist verlassen beziehungsweise zur Suche zurück; dort übernimmt Android die Zurück-Aktion.
- Medien-Tasten: Play/Pause, Play, Pause, nächster/vorheriger Titel und Stop.
- Suche: Suchfeld mit der Fernbedienung fokussieren, TV-Tastatur verwenden und Suche auslösen.
- Songs: Titel zum Abspielen auswählen, zur Queue hinzufügen oder herunterladen.
- Bibliothek, Favoriten, Playlists und Downloads: vorhandene lokale Daten anzeigen und abspielen.
- Queue: bestimmte Vorkommen auswählen oder entfernen; doppelte Titel bleiben über ihre Queue-Position unterscheidbar.
- Player-Leiste: Play/Pause, Vor/Zurück, Zeitsprünge, Shuffle, Repeat und App-Lautstärke.
- Wiedergabe-Seite: Cover beziehungsweise automatisches Canvas, Song-/Albuminfos, Favorit und Download.
- Einstellungen: Canvas, automatisches Canvas, Helligkeit und Filter für explizite Inhalte.

Die TV-Tastatur und einige Medien-/Lautstärketasten hängen zusätzlich vom Fernseher und seiner Fernbedienung ab.

## Grenzen dieser ersten TV-Oberfläche

Die Oberfläche bietet aktuell Songsuche und lokale Playlists. Sie bildet noch nicht jeden Handy-Dialog ab: manuelle Canvas-Videosuche/-Schnitt, Künstler-/Album-Detailseiten, Lyrics-Detaildialoge, Playlist-Bearbeitung und Kontoanmeldung sind noch nicht speziell für TV umgesetzt. Dafür gibt es keine leeren Schaltflächen.

Ein gemeinsamer Sourcify-Google-/Cloud-Dienst ist nicht eingerichtet. Online-Musik hängt von der YouTube-Music-Verfügbarkeit und den jeweiligen Zugangsvoraussetzungen ab. Offline-Wiedergabe setzt zuvor vorhandene Downloads voraus. Streams oder Canvas können bei Netz-/Quellenfehlern ausfallen; die Oberfläche zeigt Fehler beziehungsweise das Cover.

## Build

JDK 21 und Android SDK 37.0 wie im Android-Projekt. Die Flavor-spezifische Oberfläche liegt unter `app/src/main/kotlin/com/metrolist/music/ui/tv/`, das Manifest unter `app/src/tv/`.

```bash
./gradlew --no-configuration-cache --max-workers=1 :app:assembleTvDebug :app:testTvDebugUnitTest
```

APK: `app/build/outputs/apk/tv/debug/app-tv-debug.apk`.

Der normale Android-Build bleibt `:app:assembleFossDebug`. TV verwendet dieselben vorhandenen FOSS-Cast-Abstraktionen; Google-Cast-Senderfunktionen werden nicht als TV-Funktion angezeigt.

Die veröffentlichte APK ist vorerst ein signierter Debug-/Vorschau-Build. Ein eigener Schlüssel kann wie im Android-Build über `METROLIST_DEBUG_KEYSTORE_PATH` angegeben werden. Eigene APKs mit anderen Schlüsseln können die veröffentlichte Installation nicht aktualisieren.

## Prüfung

- TV- und normale Android-Variante gebaut.
- Jeweils 232 Unit-Tests erfolgreich.
- Native Robolectric-Prüfung von Fokus, OK/Enter, Links/Rechts und vertikaler Navigation in einer scrollenden TV-Menüliste.
- Medien-Tasten-Zuordnung und begrenzte Zeitsprünge separat geprüft.
- Paketkennung, App-Name, TV-Launcher, Leanback-Anforderung und APK-Signatur geprüft.
- Kein echter TV-/Fernbedienungs-/Wiedergabetest und kein Live-Streaming-Nachweis für jedes Gerät.

Vor einem stabilen Release auf Hardware prüfen: Installation, TV-Launcher, Bildschirmtastatur, alle Fokuswege, Netzverlust, Streamstart, Audio-Fokus/App-Wechsel, Medien-Tasten, Downloads, Queue, Lautstärke, Canvas und Neustart. Besonders kleine TV-Oberflächen und erhöhte Schriftgröße prüfen.
