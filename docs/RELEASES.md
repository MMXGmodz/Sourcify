# Release-Ablauf

## Aktuelle Veröffentlichung

- Version: **13.7.1**, Android-Versioncode **154**.
- Tag: **v13.7.1-preview.1**.
- Sichtbarer App-Name und Download-Datei: **Sourcify**, **Sourcify.apk**.
- Paket: `io.sourcify.player.uitest`, gleicher Schlüssel wie die bisherigen APKs.
- Status: Vorabversion, FOSS-Debug-Build; Hardwareprüfung noch offen.

## Dateien im Release

- `Sourcify.apk`: installierbares APK.
- `Sourcify-13.7.1-source.zip`: vollständiger passender Quellcode ohne private Schlüssel oder Build-Verzeichnisse.
- `build-report.json`: Versionen, Build-/Testergebnisse und Signaturfingerabdruck.
- `SHA256SUMS`: Prüfsummen der Dateien.

## GitHub Actions

`Android` baut und testet den Quellcode bei Änderungen an `main`, bei Pull Requests sowie bei manuellem Start. CI-APKs verwenden die getrennte Paketkennung `io.sourcify.player.ci` und aktualisieren die veröffentlichte App nicht.

`Android release` wird ausschließlich manuell gestartet. Vorher muss das Repository-Secret **SOURCIFY_SIGNING_KEYSTORE_B64** den bestehenden privaten Keystore in Base64 enthalten. Der Workflow bricht vor dem Build ab, wenn der Schlüssel fehlt. Das Secret niemals als Datei ins Repository aufnehmen.

Der Workflow testet, baut mit der veröffentlichten Paketkennung, prüft die Signatur und erstellt zunächst einen Draft. Nach vollständigem Asset-Upload wird der Release als Vorabversion veröffentlicht. Der eingegebene Tag muss zur Android-Version passen, etwa `v13.7.1-preview.1`. Ein vorhandener Release wird nicht überschrieben.

Ein stabiler Release setzt einen echten Gerätetest voraus. Ein späterer Wechsel auf optimierte Release-Builds muss dieselbe Paketkennung und Signatur verwenden.
