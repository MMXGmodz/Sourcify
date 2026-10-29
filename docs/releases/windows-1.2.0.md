## Sourcify Windows 1.2.0 — experimentell

Der vorhandene Windows-x64-Stand vom **5. Oktober 2026** wird hier unverändert wiederveröffentlicht. Es handelt sich nicht um einen neuen Build oder um vollständige Funktionsgleichheit mit Android 13.7.1.

### Download

**Sourcify-Player-Windows-1.2.0-x64-Setup.exe** aus den Assets herunterladen. Windows 10/11 x64; eine laufende ältere Instanz vorher schließen. Der ursprüngliche Installer ist nicht digital signiert.

### Enthalten

- Fullscreen-Player mit F11/ESC, Mini-Player und vorhandener Windows-Medienanbindung.
- Canvas-Auswahl aus Videosuche oder lokaler Datei, bis zu acht Sekunden lange Clips, große Canvas-Ansicht und Cache.
- Lokale Musik, Bibliothek, Playlists, Favoriten, Verlauf und vorhandener Jam-Modus.
- Vorbereitete Google-/Cloud-Clients; OAuth-Konfiguration und gemeinsames Account-Backend fehlen weiterhin.

### Nachweise und Grenzen

- **47 vorhandene Node-Tests** unter Linux erneut bestanden, auch nach dem Quellenimport.
- Installer-SHA256 stimmt exakt mit dem ursprünglichen Build-Bericht überein.
- ZIP-Integrität und Dateiprüfsummen geprüft.
- Kein neuer Test von Installation, DPAPI, Medientasten oder Live-Streams auf Windows-Hardware.

Die Windows-Quellen sind im Repository unter `platforms/windows/` zu finden. Das unveränderte vollständige Quellcode-ZIP enthält zusätzlich historische PS5-Quellen; diese werden nicht als getestet oder unterstützt ausgewiesen.

### Assets

- Installer EXE.
- Vollständiges ursprüngliches Quellcode-ZIP.
- Ursprünglicher Entwicklungsbericht (historisch).
- `publication-report.json` mit den Prüfungen dieser Wiederveröffentlichung.
- `SHA256SUMS` zur Dateiprüfung.
