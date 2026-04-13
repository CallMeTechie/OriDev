# Ori:Dev — Play Store Release Reference

Alle Informationen, die du für die Veröffentlichung von Ori:Dev im Google Play Store brauchst. Bilingual (Deutsch / English).

**Related files:**
- `store/listing.md` — Langfassung der Store-Texte (EN+DE)
- `store/privacy-policy.md` — Datenschutzerklärung
- `store/screenshots/README.md` — Screenshot-Spezifikationen
- `store/feature-graphic/README.md` — Feature-Graphic-Vorgaben
- `docs/SECRETS_SETUP.md` — Keystore + Service Account Setup

---

## 1. Quick Reference

| Feld | Wert |
|------|------|
| **App Name** | Ori:Dev |
| **Package Name / Application ID** | `com.ori.dev` |
| **Wear Package Name** | `com.ori.dev.wear` |
| **Current Version** | `0.3.3` (versionCode: `5`) |
| **Signing** | GitHub Actions Release Workflow (stable keystore via `KEYSTORE_BASE64` Secret) |
| **Keystore SHA-1** | `50:C6:DF:80:58:68:EC:FC:13:D9:F3:DE:8B:6F:CF:BA:6A:CC:EA:5D` |
| **Key Alias** | `oridev` |
| **Keystore Backup** | `/root/.oridev-signing/oridev-release.keystore` + GitHub Secret |
| **App Signing** | Google Play App Signing (empfohlen, Google verwaltet Signing Key) |
| **Minimum SDK** | Android 14 (API 34) |
| **Target SDK** | Android 15 (API 36) |
| **Supported Devices** | Phone, Foldables (Pixel Fold optimized), Wear OS 4+ |

---

## 2. App Listing (Store Metadata)

### 2.1 App Title

- **EN:** `Ori:Dev — SSH, SFTP & Terminal`
- **DE:** `Ori:Dev — SSH, SFTP & Terminal`

_Max 30 Zeichen — beide Varianten sind 30 chars exakt._

### 2.2 Short Description (max 80 chars)

- **EN:** `SSH, SFTP & FTP file manager and terminal — built for foldables.`
- **DE:** `SSH, SFTP & FTP Dateimanager und Terminal — gemacht für Faltgeräte.`

### 2.3 Long Description (max 4000 chars each)

#### English

```
Ori:Dev (折り Dev) is a developer-grade SSH/SFTP/FTP client and terminal for
Android, designed from the ground up for the Pixel Fold and other foldable
devices.

Key features:

• Multi-protocol remote access: SSH, SFTP, FTP, FTPS with public-key or
  password authentication
• Full xterm-256 terminal emulation with mouse support, scrollback, tmux/
  screen compatibility and a customizable soft-keyboard row
• Dual-pane file manager on the foldable inner display, single-pane on the
  outer cover — automatic layout switching based on posture
• Built-in code editor powered by Sora-Editor with syntax highlighting for
  80+ languages, line numbers, soft wrap, search/replace, and an inline git
  diff viewer
• Optional Claude AI integration: select code in the editor and ask for
  refactors, explanations or fixes — snippets are only sent when you
  explicitly ask, never in the background
• Transfer manager: queued uploads/downloads with pause, resume, retry on
  drop, and progress notifications
• Proxmox dashboard: list VMs and LXC containers, start/stop/reboot/snapshot
  with a tap
• Wear OS companion app: monitor active connections, send quick commands,
  receive 2-factor prompts and trigger panic-disconnect from your watch
• Security: credentials encrypted at rest via Android Keystore (AES-256-GCM),
  SSH host keys pinned on first use (TOFU), biometric unlock, clipboard
  auto-clear, no plaintext password storage
• 100% open source on GitHub, no tracking, no ads, no premium gates

Ori:Dev is the SSH tool you wish you had when your laptop stayed at home.
```

#### Deutsch

```
Ori:Dev (折り Dev) ist ein SSH/SFTP/FTP-Client und Terminal in Entwickler-
Qualität für Android — von Grund auf für das Pixel Fold und andere Faltgeräte
entworfen.

Hauptfunktionen:

• Multi-Protokoll Remote-Zugriff: SSH, SFTP, FTP, FTPS mit Public-Key- oder
  Passwort-Authentifizierung
• Vollständige xterm-256 Terminal-Emulation mit Maus-Support, Scrollback,
  tmux/screen-Kompatibilität und anpassbarer Soft-Keyboard-Leiste
• Dual-Pane Dateimanager auf dem inneren Foldable-Display, Single-Pane auf
  dem Cover — automatischer Layout-Wechsel je nach Posture
• Integrierter Code-Editor auf Basis von Sora-Editor mit Syntax-Highlighting
  für 80+ Sprachen, Zeilennummern, Soft-Wrap, Suchen/Ersetzen und Inline-
  Git-Diff-Viewer
• Optionale Claude-KI-Integration: Code im Editor markieren und nach
  Refactoring, Erklärungen oder Fixes fragen — Snippets werden nur gesendet
  wenn du es explizit verlangst, niemals im Hintergrund
• Transfer-Manager: Queued Uploads/Downloads mit Pause, Fortsetzen,
  automatischem Retry bei Verbindungsabbruch und Fortschritts-Notifications
• Proxmox Dashboard: VMs und LXC-Container listen, mit einem Tap starten/
  stoppen/rebooten/snapshotten
• Wear OS Companion App: aktive Verbindungen monitoren, Quick-Commands
  senden, 2-Faktor-Prompts empfangen und Panic-Disconnect von der Uhr aus
  auslösen
• Sicherheit: Credentials verschlüsselt im Android Keystore (AES-256-GCM),
  SSH Host Keys bei erstem Kontakt gepinnt (TOFU), biometrischer Unlock,
  automatisches Clipboard-Leeren, keine Klartext-Passwort-Speicherung
• 100% Open Source auf GitHub, kein Tracking, keine Werbung, keine Premium-
  Sperren

Ori:Dev ist das SSH-Tool das du gerne gehabt hättest wenn der Laptop zuhause
geblieben ist.
```

---

## 3. Release Notes (max 500 chars per language, per release)

> Play Store zeigt die "What's new" Notes prominent. Halte sie kurz, aktions-
> orientiert und auf den User fokussiert. Kein "bumped gradle version".

### 3.1 v0.3.3 — Initial Public Release

#### EN (497 chars)
```
First public release of Ori:Dev.

• SSH, SFTP, FTP and FTPS with public-key or password auth
• Full xterm-256 terminal with mouse and scrollback
• Dual-pane file manager optimised for foldables
• Code editor with syntax highlighting, git diff and optional Claude AI
• Queued transfer manager with pause/resume
• Proxmox VM/LXC dashboard
• Wear OS companion for remote monitoring and panic disconnect
• Credentials encrypted via Android Keystore, TOFU host-key pinning

Thanks for trying the beta — feedback welcome on GitHub.
```

#### DE (496 chars)
```
Erste öffentliche Version von Ori:Dev.

• SSH, SFTP, FTP und FTPS mit Public-Key oder Passwort-Auth
• Vollständiges xterm-256 Terminal mit Maus und Scrollback
• Dual-Pane Dateimanager für Faltgeräte optimiert
• Code-Editor mit Syntax-Highlighting, Git-Diff und optionaler Claude-KI
• Transfer-Manager mit Pause/Fortsetzen
• Proxmox VM/LXC Dashboard
• Wear OS Companion für Remote-Monitoring und Panic-Disconnect
• Credentials im Android Keystore verschlüsselt, TOFU Host-Key-Pinning

Danke fürs Testen der Beta — Feedback auf GitHub willkommen.
```

### 3.2 Template für zukünftige Releases

Verwende für jeden neuen Release dieses Format. Die Pipeline generiert automatisch einen technischen Changelog — für Play Store musst du daraus **user-fokussierte** Notes formulieren:

```markdown
#### EN (max 500 chars)
• [Neue Funktion in user-sprache]
• [Fix der spürbar ist]
• [Verbesserung der UX / Performance]

#### DE (max 500 chars)
• [Neue Funktion in Nutzer-Sprache]
• [Fix der spürbar ist]
• [Verbesserung der UX / Performance]
```

Technischer Changelog aus `git log` landet automatisch im **GitHub Release Body** — NICHT 1:1 in Play Store übernehmen.

---

## 4. Categorization

| Feld | Wert |
|------|------|
| **App Category** | Tools |
| **App Sub-Category** | Developer Tools |
| **Tags** | ssh, sftp, ftp, terminal, file manager, developer, sysadmin, devops, foldable |
| **Content Rating** | Everyone / Alle Altersgruppen (IARC Questionnaire: kein Gewalt, kein Sex, kein Glücksspiel, keine Drogen, kein User-generated Content) |
| **Target Audience** | 18+ (Entwickler, SysAdmins) — kann über Data Safety als "Not primarily directed at children" deklariert werden |

---

## 5. Contact & Legal Info

| Feld | Wert |
|------|------|
| **Developer Name** | CallMeTechie |
| **Developer Website** | https://github.com/CallMeTechie/OriDev |
| **Support Email** | _(bei Play Console eintragen — Pflichtfeld)_ |
| **Privacy Policy URL** | https://github.com/CallMeTechie/OriDev/blob/master/store/privacy-policy.md |
| **Terms of Service** | Apache 2.0 License (Repo: `LICENSE`) |

> **Hinweis:** Play Console verlangt eine **öffentlich erreichbare** Privacy Policy URL. Die GitHub-URL auf `master` funktioniert, aber **friert ein** wenn das Repo private geht oder der Pfad sich ändert. Für Production besser eine eigene Domain verwenden.

---

## 6. Data Safety Declaration

Play Console zwingt dich, für jeden Datenpunkt zu deklarieren, ob er gesammelt, geteilt oder nur lokal verarbeitet wird. Hier die Antworten für Ori:Dev:

### 6.1 Data collection summary

| Data Type | Collected? | Shared? | Optional? | Purpose |
|-----------|------------|---------|-----------|---------|
| **Personal info** (name, email, user IDs) | ❌ No | ❌ No | — | — |
| **Financial info** | ❌ No | ❌ No | — | — |
| **Health & fitness** | ❌ No | ❌ No | — | — |
| **Messages** | ❌ No | ❌ No | — | — |
| **Photos & videos** | ❌ No | ❌ No | — | — |
| **Audio files** | ❌ No | ❌ No | — | — |
| **Files & docs** | ⚠️ User-initiated transfer only (SFTP/FTP) | ❌ No | — | Transferred between user's device and user's own server; not sent to Ori:Dev developers |
| **Calendar** | ❌ No | ❌ No | — | — |
| **Contacts** | ❌ No | ❌ No | — | — |
| **App activity** | ❌ No | ❌ No | — | — |
| **Web browsing** | ❌ No | ❌ No | — | — |
| **App info & performance** (crash logs) | ⚠️ Optional | ⚠️ Opt-in to self-hosted ACRA backend | ✅ Yes (default OFF) | Bug diagnosis; PII-scrubbed (paths + hostnames redacted); user opt-in in Settings |
| **Device or other IDs** | ❌ No | ❌ No | — | — |

### 6.2 Security practices

- ✅ **Data is encrypted in transit** (TLS/SSL/SSH for all remote protocols)
- ✅ **Data is encrypted at rest** (Android Keystore AES-256-GCM for credentials, EncryptedSharedPreferences)
- ✅ **Users can request data deletion** (Uninstall clears DataStore + Keystore entries)
- ✅ **Committed to Play Families Policy** (no inappropriate content)
- ✅ **Independent security review** — code is 100% open source for audit

### 6.3 Wichtige Data-Safety Antworten im Detail

**"Does your app collect or share any user data?"**
→ **Yes** (wegen optionalem Crash Reporting)

**"Is data collection required?"**
→ **No** — Crash Reporting ist opt-in, Default OFF

**"Do you provide a way for users to request their data be deleted?"**
→ **Yes** — App-Deinstallation löscht alle lokalen Credentials und Preferences

---

## 7. Screenshots & Graphics (Requirements Summary)

| Asset | Anzahl | Format | Resolution | Quelle |
|-------|--------|--------|------------|--------|
| **Phone Screenshots** | min. 2, empfohlen 4-8 | PNG/JPEG, <8 MB | min. 320px, max. 3840px | `store/screenshots/` |
| **7" Tablet Screenshots** | optional | PNG/JPEG | ^ | Foldable inner display captures |
| **10" Tablet Screenshots** | optional | PNG/JPEG | ^ | ^ |
| **Wear OS Screenshots** | min. 1 | PNG/JPEG | 384x384 (round) oder 454x454 | `store/screenshots/wear/` |
| **Feature Graphic** | 1 | PNG/JPEG, <15 MB | 1024x500 | `store/feature-graphic/` |
| **App Icon** | 1 | PNG (32-bit, alpha) | 512x512 | `app/src/main/res/mipmap-*` |

> Details siehe `store/screenshots/README.md` und `store/feature-graphic/README.md`

---

## 8. Publishing Checklist (Step-by-Step)

### 8.1 Einmaliges Setup (nur beim ersten Release)

- [ ] Play Console Developer Account ($25 one-time fee) aktiv
- [ ] Neue App im Play Console anlegen mit Paket-Name `com.ori.dev`
- [ ] Google Play App Signing aktivieren (Default bei neuen Apps)
- [ ] Erste Version `oridev-0.3.3.aab` aus GitHub Release herunterladen
- [ ] AAB in Internal Testing Track hochladen — Play Console locked den Upload-Key-Fingerprint
- [ ] SHA-1 Fingerprint im Play Console verifizieren (sollte `50:C6:DF:80:...` sein)
- [ ] Store Listing komplett ausfüllen (Texte aus Abschnitt 2 + 3)
- [ ] Screenshots hochladen (mindestens 2 Phone, 1 Wear)
- [ ] Feature Graphic hochladen
- [ ] App Icon (512x512) hochladen
- [ ] Categorization setzen (Tools / Developer Tools)
- [ ] Content Rating Questionnaire ausfüllen → "Everyone"
- [ ] Privacy Policy URL eintragen
- [ ] Support Email eintragen
- [ ] Data Safety Form ausfüllen (Antworten aus Abschnitt 6)
- [ ] Target Audience auswählen ("18+ Developers")
- [ ] Ads-Declaration: **No ads**
- [ ] Release Notes EN+DE aus Abschnitt 3.1 eintragen
- [ ] Internal Testing Track: Tester-Liste anlegen (mindestens deine eigene E-Mail)
- [ ] Release ausrollen

### 8.2 Jeder weitere Release

- [ ] Conventional Commit auf master pushen (`feat:` / `fix:`)
- [ ] CI/CD Pipeline warten (~10 min) → neuer `vX.Y.Z` Tag + GitHub Release
- [ ] Falls `PLAY_SERVICE_ACCOUNT_JSON` Secret gesetzt → automatischer Upload zu Internal Track
- [ ] Falls nicht → manuell: AAB aus GitHub Release herunterladen und hochladen
- [ ] Release Notes EN+DE im Play Console Track eintragen (Template aus Abschnitt 3.2)
- [ ] Release aus Internal Testing → Closed Testing → Open Testing → Production promoten (je nach Reife)

### 8.3 Bei Problemen

| Fehler | Ursache | Lösung |
|--------|---------|--------|
| "Package name already exists" | Anderer Developer oder alter Draft | Paket-Name prüfen, alten Draft löschen |
| "Upload key mismatch" | AAB mit falschem Keystore signiert | CI-Pipeline Logs prüfen; `KEYSTORE_BASE64` Secret muss zum hinterlegten Fingerprint passen |
| "App Bundle format is invalid" | Corrupted AAB | CI-Run rerun |
| "Missing native debug symbols" | R8 Obfuscation ohne `mapping.txt` | Mapping-Datei aus GitHub Release zusätzlich hochladen |
| "Privacy Policy URL not accessible" | GitHub Raw URL gibt 404 | Verwende `https://github.com/.../blob/master/...` (rendered), nicht `raw.githubusercontent.com` |

---

## 9. Rollout Strategy (empfohlen)

| Phase | Track | Audience | Dauer | Ziel |
|-------|-------|----------|-------|------|
| **1** | Internal Testing | Bis 100 Tester (Email-Liste) | 1-2 Wochen | Smoke test, eigene Geräte |
| **2** | Closed Testing | Tester-Gruppe (Email oder Google Group) | 2-4 Wochen | Beta-Feedback, Crash-Monitoring via ACRA |
| **3** | Open Testing | Opt-in via Play Store Link | 4+ Wochen | Breitentest, Performance-Daten |
| **4** | Production | Alle | Final | Staged Rollout: 10% → 25% → 50% → 100% über 1 Woche |

---

## 10. Quick Copy-Paste Block (für Play Console Forms)

**Title:**
```
Ori:Dev — SSH, SFTP & Terminal
```

**Short Description (EN):**
```
SSH, SFTP & FTP file manager and terminal — built for foldables.
```

**Short Description (DE):**
```
SSH, SFTP & FTP Dateimanager und Terminal — gemacht für Faltgeräte.
```

**Category / Tags:**
```
Tools / Developer Tools / ssh sftp ftp terminal developer foldable
```

**Privacy Policy URL:**
```
https://github.com/CallMeTechie/OriDev/blob/master/store/privacy-policy.md
```

**Package Name:**
```
com.ori.dev
```

---

_Last updated: 2026-04-13 · Version covered: v0.3.3_
