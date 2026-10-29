# Gemeinsame Sourcify-Account-API: Adaptervertrag v1

Dieser Vertrag dokumentiert den implementierten Windows-Client. **Er ist keine
Dokumentation eines vorhandenen oder bereitgestellten Cloud-Servers.** Im
Workspace existiert nur der Jam-Server. Seine Raum-/Gast-Sessions sind keine
Google-Accounts. Android verwendet YouTube-Cookies und die YouTube-Music-API,
nicht diese Account-API. Der echte Backend-Adapter muss nach Bereitstellung des
gemeinsamen Backends an dessen vorhandene Struktur angepasst werden.

Es wird kein Windows-User-Backend, keine E-Mail-Registrierung und kein
Sourcify-Passwort eingeführt. Android, Windows und später PS5 müssen denselben
Backend-Account nutzen. Backend-Identität: eindeutiger Provider `google` +
validiertes Google-`sub`; E-Mail ist nur Profildaten. Die Backend-User-ID wird
von allen Geräten verwendet. Lokale Windows-Verzeichnisse sind Caches dieser
Identität, keine zweite serverseitige Benutzerdatenbank.

## Google-Konfiguration

Windows benötigt einen eigenen **OAuth-Client vom Typ Desktop application**.
Der Systembrowser verwendet Authorization Code + PKCE S256 und
`scope=openid email profile`. Google-URLs sind fest im Auth-Modul definiert.
Der Listener bindet nur temporär an
`http://127.0.0.1:{zufälliger_port}/oauth/callback` und schließt nach Erfolg,
Abbruch, Fehler oder Timeout. Diesen Flow nicht mit einem Web-Client und einer
Liste fester Ports ersetzen. Die Loopback-Port-Variation gehört zum Google-
Desktop-Flow; kein eigener URI-Scheme-Handler wird installiert.

Google dokumentiert `client_secret` bei Installed-App-Token-Requests als
optional; der Client enthält kein Secret. Sollte die tatsächlich eingerichtete
Konfiguration einen anderen Flow erfordern, muss sie geprüft werden, statt
ein Web-Client-Secret in die App einzubauen.

Für Android fehlen im vorliegenden Projekt OAuth-/Credential-Manager-
Konfiguration und Cloud-Anbindung. Bei deren Ergänzung werden die Android-
Paketkennung und Signing-SHA-1 benötigt; gegebenenfalls verwendet Credential
Manager eine Web-/Server-Client-ID als ID-Token-Audience. **Nicht einfach die
Desktop-ID in die vorhandene WebView-Anmeldung einsetzen.** Das gemeinsame
Backend muss die tatsächlich vorgesehenen Audiences explizit zulassen.

Referenz: <https://developers.google.com/identity/protocols/oauth2/native-app>

## Auth-Endpunkte

HTTPS-Origin wird in der Account-Seite konfiguriert. Keine Redirects für
authentifizierte Requests, 20 Sekunden Timeout, begrenzte JSON-Antwortgröße,
keine Tokens in Renderer, URL-Query oder Logs.

| Endpunkt | Methode | Request / Response |
|---|---|---|
| `/v1/auth/google/challenge` | POST | `{platform:"windows"}` → `{challengeId,nonce}` |
| `/v1/auth/google` | POST | `{idToken,challengeId,device}` → Session |
| `/v1/auth/refresh` | POST | `{refreshToken}` → rotierte Session |
| `/v1/auth/logout` | POST | Bearer-Session widerrufen; kein Account-/Cloud-Daten-Löschen |

Session:

```json
{
  "accessToken": "opaque-backend-token",
  "refreshToken": "opaque-rotating-backend-refresh-token",
  "expiresAt": 1790000000000,
  "user": {
    "id": "stable-backend-user-id",
    "provider": "google",
    "googleSubject": "123456789",
    "email": "user@example.com",
    "displayName": "Name",
    "avatarUrl": "https://lh3.googleusercontent.com/..."
  }
}
```

Das Backend muss Googles JWKS/Signatur, Issuer, zulässige Audience/azp, Expiry
und die **serverseitig erstellte, kurzlebige, einmalig verbrauchte Nonce**
prüfen. Ein vom Client behauptetes `sub` oder eine dekodierte, ungeprüfte JWT
reichen nicht aus. Windows prüft das ID-Token zusätzlich lokal und verlangt,
dass `user.googleSubject` dem validierten Token entspricht. Gleiches `sub`
muss unabhängig von Plattform/E-Mail-Änderung zum gleichen Account führen.
Backend-Refresh-Sessions müssen widerrufbar und pro Gerät zugeordnet sein.

## Geräte

`device = {id: UUID, name: string(max80), platform: "windows"}`.
Client-ID und Gerätename sind keine Authentifizierung.

| Endpunkt | Methode | Verhalten |
|---|---|---|
| `/v1/devices` | GET | `{devices:[{id,name,platform,lastSeen}]}`; Zeit in Millisekunden |
| `/v1/devices/{UUID}` | DELETE | Gerät und dessen Sessions widerrufen |
| `/v1/devices/revoke-others` | POST | `{deviceId}`; alle anderen Sessions widerrufen |

Alle Geräte-/Sync-Endpunkte müssen die Backend-Session validieren und Zugriff
auf andere Account-IDs verhindern. `lastSeen` stammt vom Backend, nicht von
einer erfundenen Online-Anzeige.

## Inkrementeller Sync

`POST /v1/sync`, Bearer-Session:

```json
{
  "protocolVersion": 1,
  "deviceId": "device-uuid",
  "cursor": 23,
  "operations": [{
    "id": "operation-uuid",
    "key": "favorite:abcdefghijk",
    "value": true,
    "deleted": false,
    "baseRevision": 0,
    "timestamp": 1790000000000
  }]
}
```

Response:

```json
{
  "cursor": 24,
  "acknowledged": ["operation-uuid"],
  "changes": [{"key":"favorite:abcdefghijk","value":true,"deleted":false,"revision":24}],
  "conflicts": [],
  "hasMore": false
}
```

Revisionen sind pro Account monoton steigend. Operation-ID ist idempotent;
Wiederholung nach verloren gegangener Antwort darf nichts duplizieren. Eigene
bestätigte Änderungen müssen mit Revision in `changes` enthalten sein.
Cursor darf erst vorrücken, wenn alle entsprechenden Änderungen ausgeliefert
sind. Maximal 100 Operations je Request, 1.000 Changes je Response; große
initiale Bibliotheken werden über `hasMore` paginiert. Keine ständigen
Komplettdatenbank-Uploads. Backend muss Größen-/Schema-/Quota-Grenzen,
Atomizität und Account-Isolation selbst erzwingen.

Konflikt: `{operationId,remote:{key,value,deleted,revision}}`. Beide Versionen
bleiben im Journal, bis der Benutzer lokal oder remote auswählt. Ein lokaler
Edit während eines laufenden Uploads wird nicht durch das ältere ACK entfernt.
Tombstones müssen über einen definierten Aufbewahrungszeitraum für offline
gebliebene Geräte verfügbar sein. Bei Cursor-Ablauf ist später ein expliziter
Snapshot-/Rebase-Flow nötig; kein stilles Löschen lokaler Daten.

Entities: `track:{YouTubeID}`, `favorite:{YouTubeID}`, `library:{YouTubeID}`,
`playlist:{UUID}` (Name, geordnete Tracks inkl. Duplikate), `history:{UUID}`
(Track-ID, Zeitpunkt), `setting:{erlaubterSchlüssel}`, `resume:{GeräteUUID}`
(Track-ID, Position, Zeitpunkt), `download:{GeräteUUID}:{YouTubeID}`
(Metadaten). Dateipfade, Audio-Dateien, Canvas-Dateien, Streaming-URLs,
Google-Cookies und Auth-Tokens werden nicht synchronisiert. Lokale Dateien
bleiben gerätebezogen; lokale Playlist-Titel werden nicht still entfernt,
wenn ein Cloud-Playlist-Update eintrifft.

Offline-Änderungen sind atomar im Account-Journal gespeichert, ein Checkpoint
erkennt auch eine Bibliotheksänderung vor dem nächsten Journal-Schreibvorgang.
Nach Verbindung werden die Deltas übertragen. Netzwerkfehler: Retry nach
15 Sekunden; remote Pull im Leerlauf alle 30 Sekunden. Kein Audio-Thread wird
für HTTP oder Sync verwendet.

## Bereits durchgeführte und fehlende Tests

Lokale signierte JWT-/Loopback-/Transport-Fixtures, Delta-Sync zwischen zwei
Client-Instanzen, Konflikte, Logout, Account-Isolation und Refresh wurden
getestet. **Das ist kein Android↔Windows-Livetest.** Dafür fehlen ein reales
gemeinsames Backend, Client-IDs und eine Android-OAuth-/Cloud-Anbindung.
PS5 hat in diesem Release weiterhin keine Google-Account-Anbindung.
