# Release-Ablauf

## Aktuelle Veröffentlichung

- Version: **13.7.2**, Android-Versioncode **155**.
- Tag: **v13.7.2-preview.1**.
- Sichtbarer App-Name und Download-Datei: **Sourcify**, **Sourcify.apk**.
- Paket: `io.sourcify.player.uitest`, gleicher Schlüssel wie die bisherigen APKs.
- Status: Vorabversion, FOSS-Debug-Build; Hardwareprüfung noch offen.

## Dateien im Release

- `Sourcify.apk`: installierbares APK.
- `Sourcify-13.7.2-source.zip`: vollständiger passender Quellcode ohne private Schlüssel oder Build-Verzeichnisse.
- `build-report.json`: Versionen, Build-/Testergebnisse und Signaturfingerabdruck.
- `SHA256SUMS`: Prüfsummen der Dateien.

## GitHub Actions

`Android` baut und testet den Quellcode bei Änderungen an `main`, bei Pull Requests sowie bei manuellem Start. CI-APKs verwenden die getrennte Paketkennung `io.sourcify.player.ci` und aktualisieren die veröffentlichte App nicht.

`Android release` wird ausschließlich manuell gestartet. Vorher muss das Repository-Secret **SOURCIFY_SIGNING_KEYSTORE_B64** den bestehenden privaten Keystore in Base64 enthalten. Der Workflow bricht vor dem Build ab, wenn der Schlüssel fehlt. Das Secret niemals als Datei ins Repository aufnehmen.

Der Workflow testet, baut mit der veröffentlichten Paketkennung, prüft die Signatur und erstellt zunächst einen Draft. Nach vollständigem Asset-Upload wird der Release als Vorabversion veröffentlicht. Der eingegebene Tag muss zur Android-Version passen, etwa `v13.7.2-preview.1`. Ein vorhandener Release wird nicht überschrieben.

Ein stabiler Release setzt einen echten Gerätetest voraus. Ein späterer Wechsel auf optimierte Release-Builds muss dieselbe Paketkennung und Signatur verwenden.

## Android-TV-Vorschau

Tag: `android-tv-v13.7.1-preview.1`, APK: `Sourcify-TV.apk`, Paket: `io.sourcify.player.tv`.
Build/Test: `:app:assembleTvDebug :app:testTvDebugUnitTest`.
Paketierung nach erfolgreichem Build und vollständigen Tests:

```bash
python3 scripts/package-release.py --tv --tag android-tv-v13.7.1-preview.1 --output release-files
```

TV und Handy besitzen unterschiedliche Tag-Präfixe; das Release-Werkzeug prüft Flavor, Paketkennung, TV-Launcher und Signatur. TV-CI-Builds verwenden die getrennte Kennung `io.sourcify.player.ci.tv` und werden nicht als signierte Release-APKs veröffentlicht. Der private Schlüssel bleibt außerhalb des Repositories.
