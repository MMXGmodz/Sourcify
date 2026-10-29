## Sourcify TV 13.7.1 — erste Android-TV-Vorschau

Eigene Oberfläche für Android TV/Google TV, auf Basis des bestehenden Sourcify-Android-Codes. Kein PC-Begleitserver.

### Enthalten

- Fernbedienungsnavigation mit sichtbarem Fokus und OK/Enter-Bedienung.
- Songsuche, Bibliothek, Favoriten, lokale Playlists und Downloads.
- Queue-Auswahl und Entfernen einzelner Vorkommen.
- Play/Pause, Vor/Zurück, Zeitsprünge, Shuffle, Repeat, App-Lautstärke und Medien-Tasten.
- Cover, automatisches Canvas, Helligkeit und Einstellungen.
- Bestehender Audio-, Datenbank-, Netzwerk- und Download-Code wird geteilt.

### Installation

**Sourcify-TV.apk** aus den Assets auf dem TV installieren. Android 8.0 oder neuer wird vorausgesetzt. Name: **Sourcify TV**; eigene Paketkennung `io.sourcify.player.tv`, daher getrennte lokale Daten gegenüber der Handy-App.

### Prüfung und Grenzen

**232 TV-Tests und 232 Handy-Regressionsprüfungen bestanden**, darunter native Fokus-/Fernbedienungsprüfungen. Beide Varianten gebaut; APK-Signatur und TV-Manifest geprüft.

Noch kein Test auf echter TV-Hardware. Bildschirmtastatur, Live-Streams, Audioausgabe und Fernbedienung bitte auf dem eigenen Gerät testen. Die APK ist eine Debug-/Vorabversion.

Die erste TV-Oberfläche bildet noch nicht jeden Handy-Dialog ab. Insbesondere manuelle Canvas-Auswahl/-Schnitt, Lyrics-/Künstler-/Album-Details, Playlist-Bearbeitung und Kontoanmeldung sind nicht speziell für TV umgesetzt. Gemeinsames Cloud-Backend fehlt weiterhin.

Weitere Details: `docs/ANDROID-TV.md` im Repository. APK, passender vollständiger Quellcode, Build-Bericht und Prüfsummen sind angehängt.
