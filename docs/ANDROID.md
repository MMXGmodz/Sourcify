# Android bauen und testen

## Voraussetzungen

- JDK 21.
- Android SDK mit `platform-tools`, `platforms;android-37`, `build-tools;37.0.0` und `build-tools;36.0.0`.
- Internetzugang für Gradle-, Maven- und JitPack-Abhängigkeiten.
- `ANDROID_HOME` oder eine lokale, nicht versionierte `local.properties` mit dem SDK-Pfad.

Der Gradle-Wrapper ist enthalten. Die genaue Abhängigkeitsversionierung steht in `gradle/libs.versions.toml`.

## Lokaler Build

```bash
chmod +x gradlew
./gradlew --no-configuration-cache --max-workers=2 :app:assembleFossDebug
```

Ergebnis: `app/build/outputs/apk/foss/debug/app-foss-debug.apk`.

## Tests

```bash
./gradlew --no-configuration-cache --max-workers=2 :app:testFossDebugUnitTest
```

Enthalten sind unter anderem Queue-/Datenbank-, Metadaten-, Canvas-, Empfehlungs- und UI-Render-Prüfungen. Native Robolectric-Vorschauen entstehen unter `app/build/ui-previews/`. Solche Render-Prüfungen ersetzen keine Tests auf einem Smartphone.

## Paketkennung und Signatur

Für die veröffentlichte Installationslinie werden diese Variablen gesetzt:

```bash
export METROLIST_APPLICATION_ID=io.sourcify.player.uitest
export METROLIST_APP_NAME=Sourcify
export METROLIST_DEBUG_KEYSTORE_PATH=/absoluter/pfad/zum/privaten/signierschluessel.jks
./gradlew --no-configuration-cache :app:assembleFossDebug
```

Der vorhandene Schlüssel verwendet die Standard-Alias-/Passwortwerte der bisherigen Debug-Signierung. Abweichende Werte lassen sich über `METROLIST_DEBUG_KEY_ALIAS`, `METROLIST_DEBUG_KEYSTORE_PASSWORD` und `METROLIST_DEBUG_KEY_PASSWORD` übergeben. Schlüssel und Passwörter gehören nicht in Git, APK-Assets oder Build-Protokolle.

Ohne den ursprünglichen privaten Schlüssel lässt sich die veröffentlichte Installation nicht mit einem selbst gebauten APK aktualisieren. Ein neuer Standard-Build kann separat unter `io.sourcify.player` installiert werden.

## Vor dem nächsten stabilen Release auf Hardware prüfen

- Update ohne Datenverlust, Start und Wiedergabe.
- Canvas bei Songwechsel, Pause, Loop-Ende, Zurückscrollen und App-Wechsel.
- Schwarzer Übergang unterhalb des Canvas und Bedienung der unteren Karten.
- Künstler-/Songempfehlungen, Auswahl, Shuffle, Offline-Fehler und Retry.
- Lyrics, Credits, Favoriten, Downloads und Playlist-Scroll-Übergang.
- Kleine Displays, vergrößerte Schrift, Querformat und Systemleisten.

## Netzwerk und Konfiguration

HTTPS-Prüfungen bleiben aktiv. Optional vorhandene Last.fm-Parameter werden über lokale Konfiguration oder Umgebungsvariablen bezogen; für den FOSS-Build sind sie nicht zwingend. Keine Zugangsdaten oder Tokens in Fehlerberichte kopieren.
