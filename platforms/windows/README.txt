Sourcify Player für Windows – Release 1.2.0

Neu: Fullscreen (F11/ESC), Mini-Player, optionale Tray-/Autostart-Steuerung,
Playlists/Verlauf, dunkles Canvas mit heller großer Klickansicht,
manuelle Canvas-Videos aus der Suche und wählbare 8-Sekunden-Ausschnitte.
Begrenzte Such-/Bild-Caches und sparsame Canvas-Qualität.

Google-/Cloud-Client ist vorbereitet, aber kein gemeinsames Account-Backend
oder Google-Client-ID ist im vorhandenen Projekt eingerichtet. Android nutzt
bisher eine YouTube-Websession. Die neue Windows-App bleibt ohne Account nutzbar.
Details und tatsächlicher Status: resources/SOURCIFY-WINDOWS-REPORT.md.

SOURCIFY PLAYER FÜR WINDOWS — 1.1.0

System: Windows 10/11, 64 Bit (x64). Rund 330 MB freier Speicher.
Installation: Sourcify-Player-Windows-1.1.0-x64-Setup.exe öffnen.
Der Installer ist nicht digital signiert.

Beim ersten Online-Zugriff wird yt-dlp (17 MB) automatisch von der offiziellen
GitHub-Veröffentlichung geladen und vor der Nutzung mit SHA256 geprüft.
Dafür ist Internetzugriff nötig; eigene Musikdateien funktionieren sofort.
Kein Python muss auf dem PC installiert werden.

Musik suchen: Suchfeld oben. Songtitel anklicken zum Abspielen.
Herz: Favorit. Plus: Warteschlange. Pfeil nach unten: Download.
Downloads werden standardmäßig unter Musik\Sourcify gespeichert;
in Einstellungen lässt sich der Ordner ändern oder öffnen.
Kein MP3-Konverter: M4A bzw. WebM/Opus wird im Originalformat gespeichert.
YouTube kann einzelne Streams oder Suchanfragen ablehnen. Die App zeigt dann
entsprechende Fehlermeldungen. Eine Konto-Anmeldung ist nicht implementiert.
Eigene Musik über das Ordnersymbol importieren (MP3, M4A, WAV, FLAC u.a.).
Importierte Dateien bleiben an ihrem ursprünglichen Speicherort.

Canvas: automatische Suche nach einem passenden Musikvideo. Eine stumme
8-Sekunden-Schleife aus der Mitte läuft neben der Musik. Gibt es keinen
passenden Treffer, bleibt das Cover sichtbar. Standardzoom: 135 Prozent.
Eigenes Canvas über den Button rechts importieren (MP4 oder WebM empfohlen).
Canvas und Zoom lassen sich in Einstellungen ändern. Eigenes Canvas kann dort
für den aktuellen Song zurückgesetzt werden. Videos pausieren bei pausierter
Musik und minimiertem Fenster; die Musik kann im Hintergrund weiterspielen.

JAM IM SELBEN WLAN
In der Seitenleiste Jam öffnen, Namen eingeben, Jam starten, Link kopieren.
Den vollständigen Link an Freunde im selben WLAN weitergeben. Gäste öffnen
ihn auf Smartphone oder PC im Browser, geben ihren Namen ein und treten mit
dem vorausgefüllten sechsstelligen Sitzungscode bei. Keine Gast-App nötig.
Die Musik spielt über den PC des Hosts. Es gibt keine Internet-Sitzungen oder
synchronisierte Audioausgabe auf mehreren Geräten in dieser Version.
Gäste können online nach Songs suchen und sie hinzufügen, bereits sichtbare
Titel erneut einreihen sowie ihre eigenen noch wartenden Titel entfernen.
Der Host kann die Reihenfolge ändern, Gäste entfernen, neue Beitritte sperren
und Play/Pause/Titelwechsel für Gäste freigeben. Standard: nur Host-Steuerung.
Nach dem Entfernen eines Gasts werden neue Beitritte automatisch gesperrt;
der Host kann die Sperre danach wieder aufheben. Höchstens 20 Gäste.
Jam beenden oder die App schließen beendet den Netzwerkdienst. Einladung,
Teilnehmer und Zugriffsrechte werden nicht über einen App-Neustart gespeichert.
Die gemeinsame Song-Warteschlange bleibt lokal in deiner Bibliothek erhalten.
Es werden nur die Jam-Warteschlange, Titelinfos und Teilnehmernamen geteilt.
Lokale Musikdateien werden nicht an Gäste übertragen.
Wenn Windows beim Start von Jam fragt, den Netzwerkzugriff für private
Netzwerke erlauben. Ein Gast-WLAN mit Geräteisolierung funktioniert nicht.
Bei mehreren Netzwerkadaptern den passenden WLAN-Einladungslink auswählen.

Leertaste: Play/Pause. Medien-Tasten werden unterstützt.
Favoriten, Warteschlange und Einstellungen bleiben beim Schließen erhalten.
Keine Übertragung der Android-Bibliothek; separate Windows-Datenspeicherung.
Eine Deinstallation lässt Bibliothek und Musikdateien bestehen.

Validiert: Node-Funktionstests, Chromium-Oberflächentests, echter Electron-
Prozess unter Linux mit lokalen Audio-/Video-Dateien, Spulen und IPC.
Jam zusätzlich mit zwei unabhängigen Browser-Gästen am echten Electron-Host
geprüft: gemeinsame Queue, Besitzrechte, Play/Pause, Sperren und Entfernen.
Windows-x64-Paket und Installer wurden unter Linux kompiliert und geprüft.
Kein Laufzeittest auf einem echten Windows-PC. Live-YouTube-Zugriff wurde
von der Netzwerkumgebung abgelehnt und konnte hier nicht bestätigt werden.

QUELLCODE BAUEN
Node.js 22 oder neuer; Python 3 nur zum Herunterladen der Wiedergabe-Komponente.
1. npm ci
2. python scripts/fetch-engine.py (oder python3 ...)
3. npm test
4. npm run build:win
Das erzeugt ZIP und Setup-EXE in dist/. Kein Python auf dem Ziel-PC erforderlich.
Windows: npm start für die Entwicklung. Für Linux-Entwicklung siehe Testnotizen.
Tests: test/core.test.cjs, test/renderer.py, test/electron.py und test/README.txt.

Credits: MMXG · Vibecoded.
Lizenz: GPL-3.0-only; Hinweise zu Drittsoftware in THIRD-PARTY.txt.
