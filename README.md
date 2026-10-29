# Sourcify

Sourcify ist ein unabhängiger Musikplayer für Android auf Basis von [Metrolist](https://github.com/MetrolistGroup/Metrolist). Der aktuelle Stand erweitert den Player um Canvas-Videos, Lyrics, Künstlerinfos und Empfehlungen.

[Releases und APK](https://github.com/MamaxLEONBot/Sourcify/releases) · [Android bauen](docs/ANDROID.md) · [Projektstatus](docs/PROJECT_STATUS.md) · [Changelog](changelog.md)

## Download und Installation

1. Unter **Releases** die Datei **Sourcify.apk** herunterladen.
2. Auf Android öffnen und bei Bedarf die Installation aus dieser Quelle erlauben.
3. Die APK über die zuletzt in diesem Projekt gelieferte Installation installieren; eine Deinstallation ist dafür nicht nötig.

Android 8.0 oder neuer wird vorausgesetzt. Version **13.7.1** ist eine **Vorabversion**: Build und automatisierte Tests wurden geprüft, ein abschließender Gerätetest steht aus. Die veröffentlichte APK verwendet vorerst den FOSS-Debug-Build mit dem bisherigen Signierschlüssel.

Die sichtbare App heißt **Sourcify**. Die Paketkennung der veröffentlichten APK bleibt `io.sourcify.player.uitest`, damit bestehende Installationen und ihre Daten beim Update erhalten bleiben. Andere Paketkennungen oder Signierschlüssel sind getrennte Installationen.

## Im aktuellen Android-Stand enthalten

- Suche, Bibliothek, Favoriten, Playlists, Queue, Downloads und Cache aus der vorhandenen App.
- Lokales Umbenennen von Songs.
- Player mit scrollbar angeordneten Lyrics, Künstlerinfos, Entdecken-Karten und Songcredits.
- Canvas aus Videosuche oder Gerätespeicher mit einem Ausschnitt von bis zu acht Sekunden, Helligkeit, Zoom und eigenem Cache.
- Canvas bleibt im oberen Player-Bereich, scrollt mit ihm weg und geht nach unten weich in Schwarz über.
- Empfehlungen für Künstler oder Songs öffnen eine Liste zur Auswahl; der Ausgangstitel wird ausgeschlossen.
- Coverfarben für Hintergründe und Übergänge sowie eine einklappende Playlist-Kopfleiste mit angeheftetem Play-Knopf.

YouTube-Music-Daten bestimmen die verfügbaren Streams, Empfehlungen und Metadaten. Credits werden nur angezeigt, wenn die Quelle entsprechende Angaben liefert. Sourcify lädt keine offiziellen Spotify-Canvas-Dateien.

## Projektstruktur

```text
app/                 Android-App, UI, Player, Datenbank und Tests
innertube/           Anbindung an die vorhandene InnerTubeX-Bibliothek
platforms/windows/   Experimenteller Windows-Client 1.2.0
gradle/              Gradle-Wrapper und Versionskatalog
docs/                Build-Anleitung, Release-Ablauf und Plattformstatus
.github/workflows/   Android-Prüfung und manueller Release-Ablauf
assets/              Vorhandene Projektressourcen
fastlane/            Übernommene Android-Metadaten und Upstream-Ressourcen
```

## Schnell bauen

Mit JDK 21 und Android SDK 37:

```bash
chmod +x gradlew
./gradlew --no-configuration-cache :app:assembleFossDebug :app:testFossDebugUnitTest
```

Der lokale Standard-Build verwendet `io.sourcify.player`. Für ein Update der veröffentlichten APK müssen die Paketkennung und der private Signierschlüssel übereinstimmen. Details stehen in [docs/ANDROID.md](docs/ANDROID.md).

## Experimentelle Windows-Version

[Windows 1.2.0 herunterladen](https://github.com/MamaxLEONBot/Sourcify/releases/tag/windows-v1.2.0-experimental.1) · [Anleitung und Grenzen](docs/WINDOWS.md)

Der vorhandene Windows-Installer und sein Quellcode sind als separater experimenteller Stand erhalten. Die Windows-Quellen liegen unter `platforms/windows/`; sie teilen noch nicht automatisch alle aktuellen Android-Funktionen.

## Plattformen und Accounts

Dieses Repository veröffentlicht aktuell den vorhandenen **Android-Quellcode**. Windows ist als separater experimenteller 1.2.0-Stand enthalten. PS5 sowie ein gemeinsames Google-Account-/Cloud-Sync-Backend sind mit diesem Stand nicht als fertig oder unterstützt ausgewiesen. Vorhandene YouTube-Anmeldefunktionen sind kein eigener Sourcify-Cloud-Dienst. Siehe [Projektstatus](docs/PROJECT_STATUS.md).

## Lizenz und Herkunft

GPL-3.0; siehe [LICENSE](LICENSE) und [NOTICE](NOTICE). Die ursprünglichen Copyright-Hinweise und Lizenzdateien bleiben erhalten. Sourcify ist eine unabhängige Anpassung und keine offizielle Veröffentlichung von Metrolist, Spotify, Apple oder Google.
