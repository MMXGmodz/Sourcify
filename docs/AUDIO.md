# Audioquellen, lokale Dateien und Offline-Cover

## Eigene Audiodateien

Auf dem Handy: **Bibliothek → Lokale Audiodateien · FLAC / WAV → Audio importieren**.

Sourcify prüft die tatsächliche Audiospur und die Decoder-Unterstützung des Geräts. Unterstützt sind FLAC, PCM WAV, MP3, AAC und Ogg/Opus/Vorbis, soweit Android sie extrahieren und decodieren kann. Videos und unbekannte Codierungen werden abgelehnt. Pro Datei sind bis zu 1 GiB möglich; beim Kopieren bleibt eine Speicherreserve frei.

Die Datei wird ohne Umwandlung in den privaten App-Speicher kopiert. Tags und vorhandene eingebettete Cover werden übernommen. Die Originaldatei außerhalb der App bleibt unverändert. „Lokale Kopie entfernen“ entfernt auch den lokalen Bibliothekseintrag und Queue-Vorkommen. Die Kopie bleibt bei normalen App-Updates erhalten; das Löschen von App-Daten oder die Deinstallation entfernt sie.

FLAC und PCM werden als **verlustfreie Codierung** angezeigt. Das ist keine Aussage darüber, ob eine Datei früher aus verlustbehaftetem Material erzeugt wurde. Geräteausgabe, Bluetooth, Lautstärkeverarbeitung und DSP können das ausgegebene Signal verändern; eine bitgenaue oder Hi-Res-Ausgabe wird nicht zugesagt. ALAC wird in dieser Version nicht als unterstützter Import beworben.

Der Importdialog ist Teil der Handy-Oberfläche. Der gemeinsame TV-Audiocode wird mitgebaut und getestet; ein eigener TV-Dateibrowser ist hier nicht enthalten. Lokale Audiodateien werden nicht auf andere Geräte oder in die Cloud übertragen. Beim Wechsel zu lokalen Dateien endet eine aktive Listen-Together-Verbindung, da die Gäste diese Datei nicht besitzen. Jam-LAN-Gäste sehen höchstens Titel, keine Dateipfade.

## Qualität anzeigen

Im Player **⋯ → Details** stehen Quelle, Codierung, Codec, Abtastrate und bekannte Dateidaten. Die Details bleiben für gespeicherte Formatinformationen auch ohne YouTube-Abfrage sichtbar.

YouTube Music liefert in dieser App AAC/Opus-Quellen. Die Einstellung „Hoch“ wählt die verfügbare Quellqualität. Eine Umwandlung dieser Daten in FLAC würde keine verlorenen Details zurückbringen und ist deshalb nicht als Lossless-Funktion eingebaut. Downloads behalten beim Qualitätswechsel ihre ursprünglichen Audiodaten.

## Songcredits

Beim Öffnen des vollständigen Songinfo-Sheets ergänzt MusicBrainz fehlende Credit-Gruppen. Titel und Künstler müssen übereinstimmen, die bekannte Dauer darf höchstens zwei Sekunden abweichen und der Suchtreffer muss eindeutig sein. Die Detailantwort wird erneut geprüft. Bestehende Credits aus der Primärquelle bleiben erhalten. MusicBrainz wird mit Quelle verlinkt; Ladefehler und fehlende Treffer sind sichtbar. Die Abfragen sind serialisiert, begrenzt, abbrechbar und im Arbeitsspeicher zwischengespeichert. Kein API-Schlüssel ist nötig.

## Cover ohne Internet

Cover von Downloads liegen getrennt vom normalen Bildcache. Das Deaktivieren oder Leeren dieses Caches entfernt sie nicht. Für bereits vorhandene Downloads muss Sourcify einmal mit Internetzugang laufen, um noch fehlende Bilder zu ergänzen. Ohne zuvor gespeichertes Bild kann die App kein Cover aus dem Netz rekonstruieren.

Die Speicherung ist auf 4 MiB je Bild und 512 MiB insgesamt begrenzt. Bereits gespeicherte Download-Cover werden bei Erreichen der Grenze nicht automatisch verdrängt. Das Entfernen eines Downloads gibt nicht mehr verwendete Bilder frei.

## Canvas und Datenverbrauch

Unter **Einstellungen → Canvas → Allgemein** beschränkt „Canvas nur über WLAN/LAN“ Online-Videos auf ungetaktete WLAN-/Ethernet-Netze. Mobilfunk und getaktetes WLAN bleiben gesperrt; selbst ausgewählte lokale Videos funktionieren weiter. Die Audioqualität und Audiowiedergabe sind davon unabhängig.

## Quellen und Prüfungen

- Inspiration und Qualitätsabgrenzung: [spotDL](https://github.com/spotDL/spotify-downloader), [Spotui](https://github.com/Spotui/Spotui), [myspoti](https://github.com/kota-zo/myspoti).
- Metadaten: [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API), [Abrufregeln](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting).
- Wiedergabe: [Media3-Formate](https://developer.android.com/media/media3/exoplayer/supported-formats).

Automatisierte Tests lesen selbst erzeugte FLAC-/WAV-Testdateien durch die lokale Datenquelle und die gemeinsamen Media3-Extractor, prüfen Netzwerk-Bypass, Seeking, Dateispeicherung und Cover. Sie bestätigen keine physische Geräteausgabe. Die Testtöne wurden mit FFmpeg aus `sine=frequency=440:sample_rate=44100:duration=0.25` erzeugt; WAV ist PCM 16 Bit, FLAC daraus ohne Verluste codiert.
