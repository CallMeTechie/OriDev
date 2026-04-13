# Secrets Setup — Ori:Dev Release Pipeline

Schritt-für-Schritt Anleitung für das Einrichten aller GitHub Secrets, die die
Release-Pipeline (`.github/workflows/release.yml`) benötigt. Der Release-Workflow
bricht **sofort** ab, wenn auch nur eines dieser Secrets fehlt — es werden keine
Schritte still übersprungen.

---

## Section 1: Keystore für App-Signing

Der Upload-Keystore wird für die Signierung jeder veröffentlichten AAB/APK
gebraucht. Play App Signing hält den eigentlichen Signier-Schlüssel in Googles
HSM — der hier erzeugte Keystore ist nur der **Upload-Key**.

### 1.1 Keystore lokal erzeugen

```bash
keytool -genkey -v -keystore ori-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 -alias ori
```

Während des interaktiven Prompts wirst du gefragt nach:

| Prompt                           | Empfehlung                                 |
|----------------------------------|--------------------------------------------|
| Keystore password                | Langes, zufälliges Passwort (Pass1)        |
| Re-enter keystore password       | Wiederholen                                |
| First and last name              | Dein Name / Projektname ("Ori Dev")        |
| Organizational unit              | z.B. "Release"                             |
| Organization                     | z.B. "Ori Dev"                             |
| City or Locality                 | Stadt                                      |
| State or Province                | Bundesland                                 |
| Country code (XX)                | `DE`                                       |
| Is the above correct?            | `yes`                                      |
| Key password for `<ori>`         | **Gleiches Passwort wie Keystore** (Pass2) |

> **Wichtig:** Beide Passwörter (Keystore-Password und Key-Password) sicher in
> einem Passwort-Manager speichern. Ohne den Keystore sind keine App-Updates
> auf Play Store mehr möglich — Google rotiert den Upload-Key nur auf Anfrage.

### 1.2 Keystore base64-encoden

```bash
base64 -w0 ori-release.keystore > keystore.base64
```

Der Inhalt von `keystore.base64` ist eine einzige lange Zeile. Diesen Inhalt
gleich in `KEYSTORE_BASE64` einfügen.

### 1.3 GitHub Secrets hinzufügen

Gehe zu **Settings → Secrets and variables → Actions → New repository secret**
und lege **vier** Secrets an:

| Secret Name         | Wert                                      |
|---------------------|-------------------------------------------|
| `KEYSTORE_BASE64`   | Inhalt von `keystore.base64`              |
| `KEYSTORE_PASSWORD` | Das Keystore-Passwort (Pass1)             |
| `KEY_ALIAS`         | `ori`                                     |
| `KEY_PASSWORD`      | Das Key-Passwort (Pass2)                  |

### 1.4 Keystore offline sichern

**Dringend empfohlen:**

```bash
# Verschlüsselt auf USB-Stick oder in Passwort-Manager ablegen
gpg -c ori-release.keystore  # -> ori-release.keystore.gpg
```

Lokale Kopie + verschlüsselte Offline-Kopie an zwei Orten behalten.
`ori-release.keystore` und `keystore.base64` NIEMALS committen
(beide sind in `.gitignore`).

---

## Section 2: Google Play Store Service Account

Der Release-Workflow lädt jede neue Version automatisch als **DRAFT** in den
**Internal Testing Track**. Dazu braucht er Zugriff über die Google Play
Developer API via Service Account.

### 2.1 Play Console App anlegen (einmalig)

1. <https://play.google.com/console> → **Neue App erstellen**
2. App-Name: `Ori:Dev`
3. Standardsprache: Deutsch
4. **Paket-Name:** `dev.ori.app` (muss mit `applicationId` in `app/build.gradle.kts`
   übereinstimmen — ändern nicht möglich nach erstem Upload!)
5. Store-Listing Pflichtfelder ausfüllen (Kurzbeschreibung, Screenshots, Grafik
   etc. — Play Store lässt sonst keinen Upload zu)

> **Wichtig:** Die **erste Version muss manuell** über die Play Console
> hochgeladen werden. Die Publisher API lehnt den allerersten Upload mit
> "Package not found" ab — erst ab Version 2 funktioniert automatischer Upload.

### 2.2 Service Account in Google Cloud erstellen

1. <https://console.cloud.google.com> → Projekt wählen (oder neues Projekt
   `oridev-release` anlegen)
2. **IAM & Admin → Service Accounts → Create Service Account**
3. Name: `oridev-play-publisher`
4. Beschreibung: "CI/CD Play Store Internal Upload"
5. **Rolle: _keine_** auf GCP-Ebene auswählen — der Zugriff wird über die
   Play Console erteilt, nicht über GCP IAM.
6. **Done**
7. In der Service-Account-Liste auf den neu erstellten Account klicken →
   Tab **Keys → Add Key → Create new key → JSON → Create**
8. Die JSON-Datei wird heruntergeladen — **gut aufbewahren!**

### 2.3 Service Account in Play Console freigeben

1. <https://play.google.com/console> → **Setup → API access**
2. Falls noch nicht geschehen: Google Cloud Projekt verknüpfen
3. Unter "Service accounts" den neu erstellten Account finden → **Grant access**
4. **App permissions:** Ori:Dev App auswählen
5. **Account permissions:**
   - `Releases to testing tracks (internal, alpha, beta)` ✓
   - `View app information (read-only)` ✓
   - Optional: `Releases to production` (für spätere Automatisierung)
6. **Invite user → Send invite**
7. Warte 5-10 Minuten, bis die Berechtigungen aktiv sind.

### 2.4 JSON-Key in GitHub Secret eintragen

1. Heruntergeladene JSON-Datei öffnen
2. **Gesamten Inhalt** (inklusive der äußeren geschweiften Klammern) kopieren
3. GitHub → **Settings → Secrets and variables → Actions → New repository secret**
4. Name: `PLAY_SERVICE_ACCOUNT_JSON`
5. Value: JSON-Inhalt einfügen
6. **Add secret**

### 2.5 Erste Version manuell hochladen

Bevor der automatische Upload funktioniert, **muss** einmalig eine Version
per Hand eingespielt werden:

```bash
# Lokal mit Keystore-Env-Vars signiert bauen
export KEYSTORE_PATH=$PWD/ori-release.keystore
export KEYSTORE_PASSWORD="dein-keystore-pw"
export KEY_ALIAS=ori
export KEY_PASSWORD="dein-key-pw"
./gradlew :app:bundleRelease
# AAB liegt dann unter app/build/outputs/bundle/release/app-release.aab
```

Dann in der Play Console → **Testing → Internal testing → Create new release →
Upload** und die AAB hochladen. Warte, bis der Status `Draft` oder `Published`
erreicht ist (ca. 15-30 Minuten).

**Erst danach** funktioniert `./gradlew :app:publishReleaseBundle` aus der CI.

---

## Section 3: ACRA Backend (optional)

Nur relevant, wenn ein selbst gehostetes Acrarium-Backend für Crash Reports
verwendet wird. Ohne diese Secrets wird die App mit einem Dummy-Endpoint
(`https://acra.invalid`) gebaut — Crashes werden dann lokal verworfen.

| Secret                      | Beschreibung                            |
|-----------------------------|-----------------------------------------|
| `ACRA_BACKEND_URL`          | Ingest-URL deines Acrarium              |
| `ACRA_BASIC_AUTH_LOGIN`     | HTTP Basic Auth Login                   |
| `ACRA_BASIC_AUTH_PASSWORD`  | HTTP Basic Auth Passwort                |

Diese Secrets werden beim Build via `-Pacra.backend.url=...` etc. eingespielt
(siehe `buildConfigField` in `app/build.gradle.kts`).

---

## Section 4: Versioning Convention

Die Pipeline leitet die nächste Version **automatisch aus den Commit-Messages**
ab (Conventional Commits). `.github/workflows/auto-tag.yml` läuft nach jedem
erfolgreichen Build auf master und entscheidet:

| Commit-Prefix                      | Bump   | Beispiel           |
|------------------------------------|--------|--------------------|
| `feat: neue funktion`              | MINOR  | `0.2.0` → `0.3.0`  |
| `feat(editor): ...`                | MINOR  | mit Scope erlaubt  |
| `fix: bug gefixt`                  | PATCH  | `0.2.0` → `0.2.1`  |
| `fix(wear): ...`                   | PATCH  | mit Scope erlaubt  |
| `feat!: breaking redesign`         | MAJOR  | `0.2.0` → `1.0.0`  |
| `fix!: ...` oder `refactor!: ...`  | MAJOR  | Breaking via `!`   |
| Commit-Body mit `BREAKING CHANGE:` | MAJOR  |                    |
| `chore:`, `docs:`, `ci:`, `build:` | keiner | **kein** Release   |
| `refactor:`, `test:`, `style:`     | keiner |                    |
| `perf:`                            | keiner | Standardmäßig kein |

`VERSION_CODE` wird bei **jedem** Bump (auch Patch) um 1 hochgezählt —
Play Store verlangt strikte Monotonie.

### Single Source of Truth

`/version.properties` ist die einzige Stelle, wo die aktuelle Version steht:

```properties
VERSION_MAJOR=0
VERSION_MINOR=2
VERSION_PATCH=0
VERSION_CODE=1
```

Sowohl `app/build.gradle.kts` als auch `wear/build.gradle.kts` lesen diese
Datei beim Konfigurationslauf. **Nicht manuell editieren** — `auto-tag.yml`
überschreibt sie.

---

## Section 5: Release-Ablauf

1. Entwickler pusht `feat: ...` oder `fix: ...` Commit auf `master`.
2. **Build & Test** Workflow läuft → grün.
3. **Auto Tag** Workflow triggert via `workflow_run`:
   - Analysiert Commits seit letztem Tag.
   - Bestimmt Bump-Typ.
   - Schreibt neue `version.properties`.
   - Commit `chore(release): v0.3.0`.
   - Erstellt annotated Tag `v0.3.0`.
   - Pusht Commit + Tag auf master.
4. **Release** Workflow triggert via `push: tags: ['v*']`:
   - Pre-Release Verification (detekt, lint, test).
   - Decode Keystore.
   - Build signed AAB + APK (phone + wear), R8 minifiziert.
   - `apksigner verify` (v2/v3 required).
   - Changelog aus Commits seit vorherigem Tag.
   - GitHub Release mit allen Artefakten.
   - Upload zu Play Store Internal Track (DRAFT).
5. Operator öffnet Play Console, prüft Draft, promoviert zu "Internal Testing"
   oder direkt zu "Production".

**Keine manuellen Versions-Bumps mehr nötig.** Der Commit-Typ bestimmt alles.

---

## Section 6: Troubleshooting

### `Missing required signing secrets: KEYSTORE_BASE64`
Einer der vier Keystore-Secrets fehlt oder ist leer. Prüfe
**Settings → Secrets and variables → Actions** und vergleiche mit Section 1.3.

### `Keystore was tampered with, or password was incorrect`
`KEYSTORE_PASSWORD` stimmt nicht mit dem beim `keytool -genkey` gesetzten
Passwort überein. Keystore lokal mit
`keytool -list -keystore ori-release.keystore` prüfen.

### `PLAY_SERVICE_ACCOUNT_JSON secret not set`
Secret fehlt — siehe Section 2.4. Der JSON-Inhalt muss **vollständig** inkl.
`{` und `}` eingefügt werden, sonst kann Gradle ihn nicht parsen.

### `Package dev.ori.app does not exist in Play Console`
Erste Version wurde noch nicht manuell hochgeladen. Siehe Section 2.5.

### `No releases found for track internal`
Gleiche Ursache wie oben. Die Publisher API kann keine brand-new App
anlegen — erster Upload muss per Hand passieren.

### `The caller does not have permission` / `403 Forbidden`
Service Account ist nicht in Play Console freigegeben oder hat zu wenig
Rechte. Siehe Section 2.3 — stelle sicher, dass "Releases to testing tracks"
aktiv ist und die App-Zuordnung existiert.

### `Version code 1 has already been used`
`VERSION_CODE` in `version.properties` wurde nicht hochgezählt. Passiert nur
bei manuellem Eingriff — die Auto-Tag-Pipeline verhindert das normalerweise.
Lösung: `version.properties` anpassen, `chore(release): v0.x.y` committen,
Tag manuell erstellen und pushen.

### `apksigner verify` schlägt fehl
APK ist nicht mit v2/v3 signiert. `enableV2Signing = true` und
`enableV3Signing = true` müssen in `app/build.gradle.kts` aktiviert sein
(ist der Fall im aktuellen Setup).

---

## Referenzen

- **Play Developer API Getting Started:**
  <https://developers.google.com/android-publisher/getting_started>
- **gradle-play-publisher Docs:**
  <https://github.com/Triple-T/gradle-play-publisher>
- **Conventional Commits:**
  <https://www.conventionalcommits.org/>
- **Play App Signing:**
  <https://support.google.com/googleplay/android-developer/answer/9842756>
