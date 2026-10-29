# Sourcify Windows — experimentell 1.2.0

[Windows-Release und Installer](https://github.com/MamaxLEONBot/Sourcify/releases/tag/windows-v1.2.0-experimental.1)

Dieser Stand wurde aus den vorhandenen Dateien vom 5. Oktober 2026 wiederhergestellt. Der Installer wird unverändert übernommen; dies ist kein neu gebauter Windows-Release und keine Übernahme aller neuen Android-Änderungen.

## Installation und Bedienung

Windows 10/11 x64. `Sourcify-Player-Windows-1.2.0-x64-Setup.exe` herunterladen und öffnen. Der vorhandene Installer ist nicht digital signiert. Eine laufende ältere Sourcify-Instanz vorher schließen. Musikdateien und vorhandene Bibliotheksdaten werden durch die Quellenübernahme nicht verändert.

Enthalten sind unter anderem Fullscreen (F11/ESC), Mini-Player, Canvas-Auswahl und bis zu acht Sekunden lange Ausschnitte, Cache, lokale Playlists sowie der vorhandene Jam-Modus. Details: [ursprüngliche Bedienungsanleitung](../platforms/windows/README.txt).

Google-/Cloud-Clients sind vorbereitet; gemeinsames Backend und OAuth-Konfiguration sind nicht eingerichtet. Damit wird kein funktionierender plattformübergreifender Sourcify-Cloud-Dienst behauptet. Offline-Nutzung bleibt möglich.

## Quellcode und Build

Die Windows-relevanten Quellen liegen unter `platforms/windows/`. Das vollständige ursprüngliche Quellcode-ZIP im Release enthält zusätzlich ältere PS5-Quellen und Drittanbieterarchive; daraus folgt keine getestete PS5-Unterstützung.

```bash
cd platforms/windows
npm ci
npm test
npm run build:win
```

Electron und Build-Werkzeuge sind im Lockfile festgehalten. Der Build benötigt Netzwerkzugriff und die plattformspezifischen Electron-/NSIS-Werkzeuge. Ein neu gebauter Installer wurde bei dieser Veröffentlichung nicht erzeugt.

## Prüfung dieser Veröffentlichung

- 47 vorhandene Node-Tests unter Linux erfolgreich erneut ausgeführt.
- ZIP-Integrität, Installer-Dateiformat und Dateiprüfsummen geprüft.
- Der ursprüngliche Entwicklungsbericht bleibt als historischer Bericht enthalten, nicht als neuer Testnachweis.
- Noch kein neuer Installer-/Wiedergabe-/Medientasten-/DPAPI-Test auf Windows-Hardware.
- Live-Google-Login und Android-/Windows-Cloud-Sync weiterhin nicht bestätigt.

Der separate GitHub-Workflow führt die vorhandenen Node-Tests aus. Er ist kein Windows-Hardwaretest.
