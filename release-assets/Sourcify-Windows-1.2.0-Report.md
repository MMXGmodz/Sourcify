# SOURCIFY WINDOWS REPORT

Version 1.2.0. Bestehendes Windows-Projekt erweitert; Android- und native
PS5-Quellen unverändert. Lokale Bibliotheken und Musikdateien werden nicht
gelöscht. **Dies ist ein Player-Release mit vorbereitetem Google-/Cloud-Client,
kein fertig eingerichteter plattformübergreifender Cloud-Service.**

| Bereich | Status | Nachweis / Grenze |
|---|---|---|
| Google Login | PARTIAL | Systembrowser, Code+PKCE, Loopback, state/nonce/JWKS-Prüfung implementiert und mit signierten Fixtures getestet; Desktop-Client-ID/Backend fehlen |
| Same Account as Mobile | BLOCKED | Mobile nutzt YouTube-Cookies; kein vorhandener Sourcify-Cloud-Account oder Google-OAuth-Client gefunden |
| Auto Login | PARTIAL | sichere Session-Wiederherstellung/Refresh implementiert; Live-Session und Windows-DPAPI-Hardwareprüfung fehlen |
| Cloud Sync | PARTIAL | Delta-Journal, Pull, Offline-Retry, Revisionen, Tombstones, Konflikte implementiert; gemeinsamer Server fehlt |
| Favorites Sync | PARTIAL | zwei Client-Fixtures inkl. Löschung geprüft; kein Android↔Windows-Livetest |
| Playlist Sync | PARTIAL | Tracks, Duplikate, Änderungen/Löschungen/Konflikte geprüft; kein Android↔Windows-Livetest |
| History Sync | PARTIAL | Events/Remote-Übernahme geprüft; tatsächliche Mobile-Anbindung fehlt |
| Device Management | PARTIAL | API-Client, Geräteliste/Entfernen/Session-Widerruf implementiert; reales Backend fehlt |
| Offline Mode | COMPLETE | bisherige lokale Wiedergabe, Favoriten, Queue, Playlists und Einstellungen funktionieren ohne Account |
| Fullscreen Player | COMPLETE | realer Electron-Test für Button/F11/ESC, dieselbe Audioinstanz, Seek/Volume/Queue/Favorite |
| Dynamic Background | COMPLETE | echte Cover-Farbextraktion, zwei Bild-Layer/Crossfade, Blur/Helligkeit/Animationseinstellungen; visuell geprüft |
| Mini Player | COMPLETE | echte zweite Electron-Oberfläche, sichere kleine IPC-Schnittstelle, Wiedergabe im Hauptfenster; kein zweiter Audio-Player |
| Windows Media Controls | PARTIAL | Chromium Media Session, Metadaten/Position/Play/Pause/Next/Previous und Windows-Taskbar-Steuerung implementiert; SMTC/Medientasten auf Windows noch testen |
| Canvas-Videoauswahl | COMPLETE | Suchvideo auswählen, Vorschau, manuelle Priorität vor Auto-Canvas; Such-UI mit Fixtures geprüft |
| Canvas-Ausschnitt | COMPLETE | Start frei wählbar, maximal 8 Sekunden, Begrenzung bei kurzen Videos; lokaler Electron-Test und Suchvideo-UI-Test |
| Canvas große Ansicht | COMPLETE | dunkler Hintergrund, Klick heller/groß, weiterer Klick oder ESC zurück; gleiches Videoelement |
| Performance / Cache | COMPLETE | begrenzte Such-/Bild-/Resolver-Caches, gemeinsame laufende Requests, weniger DOM-Neuaufbau, Listen zunächst 200 Einträge, 480p-Canvas-Default |
| Build | PASS | Windows-x64 ZIP/NSIS-Setup und ASAR-/Quellen-/Branding-Prüfung; aktuelle Artefakthashes im Release-Prüfbericht |
| Tests | PASS | 47 Node-Tests, 3 PS5-Bridge-Regressionstests, Browser- und echte Electron-Prüfungen; keine Behauptung eines Windows-/Google-/Android-Livetests |

`COMPLETE` bei Player-Funktionen bedeutet implementiert und im beschriebenen
Host-/Electron-Test geprüft. Das ist keine Zertifizierung beliebiger Windows-
Hardware, Codecs oder YouTube-Videos. Die Testumgebung ist Linux mit Electron;
der Release wird für Windows 10/11 x64 gebaut.

## Analyse des vorhandenen Codes

**Android:** Kotlin/Compose, Hilt, Room, DataStore, Media3, InnerTube/InnerTubeX.
`LoginScreen.kt` öffnet die echte Google/YouTube-Seite in einer WebView und
extrahiert YouTube-Cookies (`SAPISID`), VisitorData, DataSyncId und AuthUser.
`YouTube.accountInfo()` liefert Channel-/Profilinformationen. `SyncUtils.kt`
synchronisiert über die YouTube-Music-Schnittstelle, `MusicDatabase.kt`
verwaltet lokale Songs, Playlists, Events und Formate. Es gibt in den
vorliegenden Quellen kein Google-Sign-In-SDK/Firebase-Account-Backend,
keine Desktop-/Android-OAuth-Client-IDs und keine Sourcify-User-ID↔Google-sub-
Tabelle. `DataSyncId` ist kein Google-OIDC-`sub`.

**Server:** `sourcify-online-server` ist der bestehende Go-/WebSocket-/bbolt-
Jam-Server. Raumteilnehmer, Gastnamen und kurzlebige Jam-Sessions sind keine
Cloud-Accounts. Dieser Server wurde nicht durch ein Windows-User-System ersetzt.

**Windows:** Electron 44.5.1, Node-Builtins, HTML/CSS/JavaScript, yt-dlp-
Resolver, HTMLAudio/Video, privates `sourcify-media`-Protokoll mit Range/Seek,
lokale atomare JSON-Bibliothek. Vorher waren Favoriten, Queue, Recents,
Downloads, Canvas, Jam und einfache Media Session vorhanden. Ein Cloud-/Login-
Backend, eine SQLite-User-DB und lokale Playlists gab es nicht.

Die vorhandenen API-, Player-, Import-, Download-, Canvas- und Jam-Einstiege
wurden weiterverwendet. Neue Auth-/Sync-/Account-Logik liegt unter `src/core/`;
Windows-spezifische Credential-/Fenster-/Tray-Bausteine unter
`src/platform/windows/`. Der gemeinsame API-Vertrag steht in
[CLOUD-CONTRACT.md](CLOUD-CONTRACT.md). Er muss an das echte gemeinsame Backend
angepasst werden, sobald dessen Repository/API vorliegt.

## Bedienung und Installation

Eine laufende ältere Version schließen, neues Setup ausführen oder die portable
Windows-ZIP vollständig entpacken und `Sourcify Player.exe` starten. Musikdateien
und die bestehende `%APPDATA%/Sourcify Player/library.json` bleiben erhalten.

- Fullscreen: Player-Button oder **F11**, zurück mit **ESC**.
- Canvas: Hintergrund einmal anklicken → heller und fensterfüllend; erneut
  anklicken oder ESC → zurück. Transport-/Favoritenbuttons beenden die Ansicht
  nicht versehentlich. Tastatur: Fokus auf Canvas, Enter/Leertaste.
- **Video aus Suche wählen**: Video suchen, Ergebnis anklicken, Ausschnitt
  einstellen und speichern. Eine manuelle Wahl wird durch Auto-Canvas nicht
  ersetzt; funktioniert auch bei deaktivierter automatischer Suche.
- **8 s bearbeiten**: aktuelles Canvas zuschneiden. Slider oder Videoposition
  verwenden, **Ausschnitt speichern**. Sehr kurze Videos nutzen ihre volle Dauer.
- Eigene Videodateien: bestehender **Eigenes Canvas**-Button; dieselbe Trim-
  Funktion. Die Datei bleibt auf dem PC, sie wird nicht automatisch kopiert.
- Mini-Player: Player-Button; Always On Top in Einstellungen umschalten.
- Tray/Autostart: Einstellungen. Tray ist optional. Windows-Autostart wird nur
  in der installierten Windows-App angeboten. `Beenden` schließt alle Fenster.
- Lyrics: Fullscreen-Button fragt LRCLIB anhand Titel/Künstler/Album ab;
  bei fehlenden Daten wird eine echte Nicht-verfügbar-Meldung angezeigt.
  Audioausgabe öffnet die tatsächlichen Windows-Soundeinstellungen.
- Account: **Continue with Google** oder **Offline fortfahren**. Ohne Backend-
  Konfiguration erscheint ein erklärter Fehler, kein angeblicher Login-Erfolg.

## Daten, Migration und Sicherheit

Lokale Bibliothek wird additiv um Playlists, Bibliothek, Events, Resume und neue
Preferences erweitert. Ältere JSON-Dateien werden ohne Löschen gelesen;
vorhandene Recents erhalten lokale Verlaufs-IDs. Keine Room-/SQL-Migration,
keine Änderung der Android-Datenbank. Defekte ursprüngliche Bibliotheken werden
weiterhin gesichert. Cloud-Profile liegen unter
`accounts/SHA256(Backend-Origin + User-ID + Google-sub)/`; auch ein irrtümlich
wiederverwendeter Backend-User-ID-Wert mischt unterschiedliche Google-Subjects
nicht in denselben lokalen Cache.

Der erste Login bietet **Zusammenführen**, **Cloud-Daten verwenden** oder
**Nur lokal behalten**. Die ursprüngliche Gastbibliothek bleibt bestehen.
Abmelden entfernt lokale sensible Sessions, stoppt Sync und aktive Downloads,
invalidiert Media-Tokens und wechselt zurück zur Gastbibliothek. Cloud-Daten
werden nicht gelöscht; Downloads bleiben auf diesem PC. Account-Profile werden
beim Wechsel nicht gegenseitig eingeblendet. Offline-Logout kann die lokale
Session sofort löschen; serverseitiger Widerruf braucht Verbindung und wird
bei Fehler als offen gemeldet.

`auth/session.bin` enthält ausschließlich `safeStorage`-verschlüsselte Session-
Daten. Auf Windows nutzt Electron DPAPI. Ein Linux-`basic_text`-Fallback wird
abgelehnt. Kein Token ist in `library.json`, der Oberfläche, URL-Queries oder
Logs. Kein Google-/Sourcify-Passwortfeld und kein Passwort-Reset-System.
ID-Token-Prüfung im Client ersetzt die erforderliche Backend-Prüfung nicht.

Persistenter Bild-Cache: `cache/images/` je Profil, maximal 32 MiB/256 Bilder,
4 MiB pro Download, SHA256-Dateinamen, begrenzte Metadaten und MIME-Typen,
TTL 30 Tage. Such-Cache: 64 Queries, 3 Minuten. Resolver: maximal 200 Quellen
unter Berücksichtigung signierter URL-Expiry. Keine Tokens/Streaming-URLs auf
Disk cachen. Unreferenzierte Such-Metadaten werden auf 500 begrenzt; Favoriten,
History, Bibliothek, Queue, lokale Dateien und ausgewählte Canvas bleiben.
**Cache leeren** entfernt keine Musikdownloads oder manuelle Canvas-Auswahl.
Canvas pausiert bei pausierter Musik, verborgenem Fenster und beim Bearbeiten.
480p/H.264 wird bevorzugt; 360p/720p sind wählbar.

## Build und Tests

```bash
npm ci
npm test
npm run build:win
node scripts/verify-package.cjs
```

Keine neuen npm-/nativen Runtime-Dependencies. Verwendet werden bestehendes
Electron, Node `crypto/http/fs` und öffentliche Google/LRCLIB-Schnittstellen.
Google-OAuth erfordert öffentliche Desktop-Client-ID und gemeinsame HTTPS-
Cloud-Adresse. Weitere benötigte Client-IDs/Redirects: siehe Cloud-Vertrag.
Der Client enthält keine produktiven Client-IDs oder privaten Secrets.

Zusätzliche Checks: `test/renderer.py`, `test/canvas-renderer.py`,
`test/electron.py`, `test/windows-enhancements.py`. Die Electron-Prüfungen
benötigen einen gestarteten `test/launch-electron.cjs` mit isoliertem Profil
und lokalem Debug-Port 9223. Die Testvideos sind geometrische Fixtures und
gehören nicht zur produktiven App-Bibliothek. `test/auth-sync.test.cjs` prüft
Loopback, signierte Test-JWTs, Cancel/Error, sichere Storage-Abstraktion,
Refresh, Logout, Namespace-Trennung, Offline-Journal, Live-Edit während Upload,
Conflicts, Deletes und Checkpoint-Recovery; kein echter Google-Login.

## Noch notwendige Tests und nächste Schritte

1. Windows 10/11: Setup/Upgrade, Sound, SMTC/Medientasten, Taskbar, Tray,
   Autostart, Always On Top und echte Mehrmonitor-Wechsel prüfen. Geometrie-
   Berechnung bei Monitorentfernung/negativen Koordinaten ist unitgetestet.
2. Echtes gemeinsames Backend/API und öffentliche Google-Client-IDs bereitstellen.
   Adapter an dessen Schema anpassen und serverseitige Validierung/Audience-
   Allowlist, Session-Rotation, Idempotenz, Quotas und Tombstones prüfen.
3. Android erhält denselben Google-/Cloud-Flow zusätzlich zur bestehenden
   YouTube-Musiksession. Nicht beide Identitäten gleichsetzen. PS5 später mit
   einem für diese Plattform geeigneten Login-/Token-Storage-Adapter anbinden.
4. Realtest Account A: Android Song X/Playlist Gaming → Windows sichtbar;
   Windows Song Y → Android sichtbar. Danach Logout/Account B und Rückwechsel
   sowie abgelaufene Sessions, Offline-Start und Migration mit echten Daten.
5. Live-YouTube, verschiedene Canvas-Codecs/Netzwerkabbrüche und LRCLIB prüfen.
   Der Host-Proxy blockiert Live-YouTube; Fixture-Ergebnisse sind kein Nachweis
   für beliebige aktuelle YouTube-Streams. Android-/PS5-Cloud-Sync bleibt bis
   zu den beschriebenen Live-Tests unvollständig.

Geänderte/neue Dateien: [WINDOWS-CHANGED-FILES.txt](WINDOWS-CHANGED-FILES.txt).
Backend-Code-Änderungen: **keine**. Keine zweite User-DB angelegt. Der fehlende
Backend-Service wird weder durch UI-Platzhalter noch durch Fixture-Daten
als funktionsfähig ausgegeben.
