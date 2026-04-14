# Prompt: Entwicklungsplan für Claude Code – Ori:Dev

<role>
Du bist ein autonomer Claude Code Agent der als Senior Android-Architekt und DevOps-Engineer agiert. Du erstellst einen vollständigen, maschinenausführbaren Entwicklungsplan, den ein Claude Code Agent phasenweise autonom abarbeiten kann. Der Plan muss so präzise sein, dass jede Phase als eigenständiger Claude Code Task ohne Rückfragen ausführbar ist.
</role>

<project>
## Ori:Dev – 折り Dev (Falten + Entwicklung)

SCP/FTP/SSH File Manager & Terminal für Android Foldables mit Dual-Pane File Manager, SSH Terminal, Proxmox-Integration und Wear OS Companion App.
</project>

<features>

### Kernfeatures

- **Dual-Pane File Manager**: Split-Screen mit lokalem Dateisystem links und Remote-Server rechts (wie Total Commander / WinSCP)
- **Protokolle**: SCP, SFTP, FTP, FTPS, SSH Terminal
- **SSH Terminal**: Vollwertiges Terminal-Emulator mit Tastatur-Shortcuts. Layout-Verhalten: Automatische Bildschirmdrehung aktiviert. Im Landscape-Modus: Dual-Panel-Split – oberes Panel zeigt den Terminal-Output (scrollbar, selektierbar), unteres Panel zeigt eine custom Soft-Keyboard (optimiert für Terminal-Nutzung: Tab, Ctrl, Alt, Esc, Arrow Keys, Fn-Reihe, Pipe/Slash/Tilde schnell erreichbar). Im Portrait-Modus: Terminal fullscreen, System-Tastatur oder custom Keyboard als Bottom-Sheet. Die Split-Ratio (z.B. 60/40, 70/30) soll vom User per Drag anpassbar sein.
- **SSH Terminal – Copy & Paste**: Einfache und intuitive Möglichkeit zum Kopieren und Einfügen von Text und Befehlen. Long-Press-Selektion, Floating-Toolbar (Copy/Paste/Select All), Clipboard-Historie für zuletzt kopierte Befehle, Paste-Bestätigung bei mehrzeiligen Inhalten (Schutz vor versehentlichem Multi-Line-Paste).
- **Connection Manager**: Gespeicherte Server-Profile mit SSH-Key-Support (Ed25519, RSA), Passwort-Auth, Key-Agent
- **Datei-Operationen**: Upload, Download, Rename, Delete, Chmod, Symlinks, Drag & Drop zwischen Panels
- **Transfer-Queue**: Hintergrund-Transfers mit Fortschrittsanzeige, Pause/Resume, Retry
- **Bookmarks & Quick-Connect**: Favoriten, zuletzt verbundene Server

### Claude Code Integration – SSH Terminal

- **Session-Recorder**: Gesamte Terminal-Session als Markdown/Log exportierbar – ideal um Claude Code Output zu archivieren oder als Kontext in neue Prompts zu füttern
- **Output-Selektor mit "Send to Claude"**: Terminal-Output markieren → direkt als Prompt/Kontext an Claude API senden, Antwort inline oder als Overlay anzeigen
- **Snippet-Manager**: Häufig genutzte Claude Code Befehle (z.B. `claude -p "..."`, `claude code review`, Custom Slash-Commands) als Quick-Actions speichern und per Tap ausführen
- **Multi-Session Tabs**: Mehrere SSH-Sessions parallel – eine für Claude Code, eine für Git, eine für Logs – mit schnellem Tab-Wechsel
- **Auto-Detect Claude Code Output**: Erkennt Code-Blöcke in der Terminal-Ausgabe, bietet Syntax-Highlighting und Copy-as-Code an
- **Diff-Viewer inline**: Wenn Claude Code Dateiänderungen vorschlägt, direkt im Terminal einen Side-by-Side Diff rendern
- **Notification Hook**: Push-Notification wenn ein lang laufender Claude Code Task fertig ist (Integration mit PushHub)

### Claude Code Integration – File Manager

- **Code-Preview mit Syntax-Highlighting**: Dateien direkt im File Manager mit Highlighting anzeigen (nicht nur Plaintext)
- **Code-Editor**: Vollwertiger integrierter Code-Editor mit Syntax-Highlighting (Multi-Language: PHP, Kotlin, Python, JS/TS, Bash, YAML, JSON, XML, Markdown etc.), Zeilennummern, Suchen & Ersetzen, Auto-Indent, Touch-optimiert
- **Git-Status Overlay**: Dateiliste zeigt Git-Status (modified, untracked, staged) als farbige Badges
- **review.md Viewer**: Dedizierter Viewer für `review.md` Dateien mit Markdown-Rendering – direkt aus dem Dateibaum öffnen
- **CLAUDE.md Quick-Access**: Pinned/Favorit für CLAUDE.md im Projekt-Root, mit Markdown-Editor zum schnellen Anpassen
- **Bulk-Operations mit Pattern**: Dateien per Glob-Pattern selektieren (z.B. `*.php`, `src/**/*.ts`)
- **File Watcher**: Live-Aktualisierung der Dateiliste wenn Claude Code remote Dateien ändert

### Claude Code Integration – Übergreifend

- **Context-Bridge**: Datei im File Manager auswählen → öffnet sich als Kontext im Terminal mit `claude -p "review this file" < datei.php`
- **Project-Mode**: Projekt-Ordner als Scope setzen – Terminal startet automatisch im Projektverzeichnis, File Manager zeigt nur den Projektbaum
- **Connection-Profile mit Claude Code Config**: Pro Server speichern welche Claude Code Version, welches Model, welche CLAUDE.md gilt

### Wear OS Companion App (Smartwatch)

**Verbindungs-Monitoring:**
- Live-Status aller aktiven SSH/FTP-Verbindungen (Connected/Disconnected mit farbigen Indikatoren)
- Quick-Disconnect: Per Tap eine oder alle Verbindungen trennen
- Reconnect-Button direkt am Handgelenk

**Transfer-Übersicht:**
- Aktive Transfers mit Fortschrittsbalken auf dem Zifferblatt
- Pause/Resume einzelner Transfers per Wisch-Geste
- Notification bei abgeschlossenem oder fehlgeschlagenem Transfer mit Retry-Option

**Quick-Commands:**
- Gespeicherte SSH-Befehle per Tap ausführen (z.B. Server-Restart, Service-Status, Disk-Check)
- Letzte 5 ausgeführte Befehle als History
- Befehlsausgabe als kompakte Textansicht auf der Uhr (scrollbar)
- Vordefinierte Command-Sets pro Server-Profil

**Claude Code auf der Watch:**
- Notification wenn Claude Code Task fertig ist (PushHub Integration)
- Quick-Action: "Approve" oder "Reject" für Claude Code Vorschläge direkt von der Watch

**Server Health Dashboard:**
- Complication für das Watchface: CPU/RAM/Disk eines Lieblingsservers als Tile
- Farbcodiert: Grün/Gelb/Rot je nach Auslastung
- Alert-Vibration wenn ein Server einen Schwellwert überschreitet

**Sicherheit:**
- Watch als 2FA-Device: Verbindungsfreigabe auf dem Foldable muss per Watch bestätigt werden
- Panic-Button: Alle Verbindungen sofort trennen mit einem Tap

**Tiles & Complications:**
- Wear OS Tile mit Übersicht: Aktive Verbindungen, laufende Transfers, letzter Befehl
- Complications für Watchfaces: Verbindungsanzahl, Transfer-Status, Server-Health

**Kommunikation Foldable ↔ Watch:**
- Data Layer API (Wearable Data Layer) für Echtzeit-Sync
- MessageClient für Quick-Commands
- Falls Foldable nicht erreichbar: Standalone-Modus mit direkter WiFi-SSH-Verbindung (falls Watch im selben Netz)

### Proxmox VM-Erstellung

**Workflow:** Auf dem Proxmox-Server werden VM-Templates vorkonfiguriert. Die App ruft diese Templates über die Proxmox REST API ab. Der User wählt auf dem Foldable nur:
1. Template auswählen (aus der Liste der verfügbaren Templates auf dem Proxmox-Node)
2. VM-ID vergeben
3. VM-Name vergeben
4. Modus auswählen (z.B. Full Clone / Linked Clone)
5. Optional: IP-Adresse und MAC-Adresse anpassen
6. Tap → VM wird geklont und automatisch gestartet

**Technische Umsetzung:**
- Proxmox REST API (Token-Auth, API-Credentials im Android Keystore)
- Multi-Node Support: mehrere Proxmox-Cluster verwalten
- Auto-Connect Flow: Nach VM-Start pollt die App den SSH-Port → sobald erreichbar, wird automatisch ein Server-Profil angelegt und das Terminal geöffnet
- VM-Lifecycle auf dem Foldable: Start/Stop/Restart/Delete mit Bestätigungs-Dialog
- VM-Lifecycle auf der Watch: Start/Stop/Restart per Quick-Action

### Hintergrund-Betrieb & Persistent Connections

- **Foreground Service**: Aktive SSH/FTP-Verbindungen müssen bestehen bleiben wenn der Bildschirm ausgeschaltet wird oder die App in den Hintergrund geht. Implementierung als Foreground Service mit persistenter Notification (zeigt aktive Verbindungen + Transfer-Status).
- **Wake Lock**: Partial Wake Lock während aktiver Transfers und Terminal-Sessions, automatisches Release wenn idle.
- **Reconnect-Logik**: Automatisches Reconnect bei Verbindungsabbruch (Netzwerkwechsel, kurzer Signalverlust) mit konfigurierbarer Retry-Strategie.
- **Battery-Optimization Whitelist**: Nutzer-Hinweis beim ersten Start, die App von der Akku-Optimierung auszunehmen, um Hintergrund-Kills zu vermeiden.
- **Android 14+ Kompatibilität**: Foreground Service Type korrekt deklarieren (dataSync, connectedDevice), Exact Alarm Permissions für geplante Transfers.

### Monetarisierung

**Free-Tier (mit Werbung):**
- Banner-Ad am unteren Bildschirmrand (niemals im aktiven Terminal-Betrieb, niemals während laufender Transfers)
- Ads nur in Idle-Screens: Connection-Manager, Dateiliste, Settings
- Max 2 gespeicherte Server-Profile
- Max 3 gleichzeitige Terminal-Tabs
- Transfer-Queue auf 5 parallele Transfers limitiert
- Kein Session-Recorder Export
- Kein File Watcher
- Code-Editor nur Read-Only Preview (kein Editieren)
- Basis Syntax-Highlighting (5-6 Sprachen)

**Premium (zwei Optionen):**
- 7 Tage kostenlose Testversion
- Einmalkauf: 139,98 €
- Abo: 6,99 €/Monat oder 69,99 €/Jahr
- Komplett werbefrei
- Unbegrenzte Server-Profile
- Unbegrenzte Terminal-Tabs
- Unbegrenzte parallele Transfers
- Alle Claude Code Features freigeschaltet (Session-Recorder, Send to Claude, Diff-Viewer, Context-Bridge, File Watcher)
- Vollwertiger Code-Editor mit allen Sprachen
- Biometrie-Unlock
- Custom Themes / Farbschemata

**Ad-Netzwerk:** Google AdMob (keine Tracking-Ads im Terminal-Bereich – Privacy-sensible Zielgruppe)

**UX-Regeln:** Klarer sichtbarer Upgrade-Button, kein Dark-Pattern. Niemals Ads die den Workflow unterbrechen.

</features>

<tech_stack>
### Technische Anforderungen

- **Sprache**: Kotlin (kein Java)
- **Min SDK**: 34 (Android 14), **Target SDK**: 36 (optimiert für Android 16) – App ist primär für Foldables konzipiert
- **Architektur**: MVVM + Clean Architecture (Domain/Data/Presentation Layer)
- **DI**: Hilt
- **UI**: Jetpack Compose mit Material 3, Dual-Pane Layout optimiert für Foldables (WindowSizeClass, Jetpack WindowManager für Fold-Erkennung und Hinge-Awareness, nahtloser Übergang zwischen gefaltet/entfaltet)
- **Lokale DB**: Room (für Connection Profiles, Transfer History, Bookmarks)
- **Networking/Protokolle**: JSch oder SSHJ für SSH/SCP/SFTP, Apache Commons Net für FTP/FTPS
- **Security**: Android Keystore für Credentials, EncryptedSharedPreferences, Biometrie-Unlock
- **Hintergrund-Transfers**: WorkManager + Foreground Service mit Notification
- **Testing**: JUnit 5, MockK, Turbine (Flow-Testing), Compose UI Tests, Espresso für E2E
- **CI/CD**: GitHub Actions mit folgenden Workflows:
  - PR-Check: Lint (ktlint/detekt), Unit Tests, UI Tests (Emulator via reactivecircus/android-emulator-runner)
  - Build: Debug + Release APK/AAB
  - Release: Tag-basiert → signierter Release-Build → GitHub Release mit Changelog-Generierung → optional F-Droid/Play Store Upload
  - Dependency Check: Dependabot + License Compliance
- **Code Quality**: detekt, ktlint, SonarQube/SonarCloud optional
- **Signing**: Keystore in GitHub Secrets, Release-Signing automatisiert
</tech_stack>

<claude_code_constraints>
### Claude Code Ausführungsregeln

Jede Phase des Plans MUSS folgende Kriterien erfüllen, damit ein Claude Code Agent sie autonom abarbeiten kann:

1. **Atomic Tasks**: Jede Phase ist in einzelne, abgeschlossene Tasks unterteilt. Ein Task = ein Claude Code Aufruf. Jeder Task hat ein klar definiertes Ergebnis (Datei erstellt, Test grün, Build erfolgreich).
2. **Keine Ambiguität**: Keine Formulierungen wie "z.B.", "optional", "könnte man". Jede Anweisung ist eine konkrete Handlung.
3. **Verifikation pro Task**: Jeder Task endet mit einem ausführbaren Verifikationsschritt (z.B. `./gradlew test`, `./gradlew detekt`, `./gradlew assembleDebug`).
4. **Dependency Chain**: Tasks innerhalb einer Phase haben eine klare Reihenfolge. Abhängigkeiten zwischen Phasen sind explizit benannt.
5. **File-Level Präzision**: Der Plan benennt exakt welche Dateien erstellt/geändert werden, in welchem Pfad, mit welchem Inhalt (oder struktureller Beschreibung).
6. **Conventional Commits**: Jeder Task endet mit einem Commit. Commit-Messages folgen Conventional Commits (feat:, fix:, chore:, docs:, test:, ci:).
7. **CLAUDE.md**: Phase 0 erstellt eine vollständige CLAUDE.md im Projekt-Root mit allen Konventionen, Architektur-Entscheidungen und Coding-Standards für alle nachfolgenden Phasen.
8. **Fehlerbehandlung**: Jeder Task definiert was bei Fehlschlag passiert (Retry-Strategie, Fallback, Abbruchbedingung).
9. **Kein menschlicher Input nötig**: Der Plan darf an keiner Stelle eine manuelle Entscheidung oder Eingabe durch einen Menschen voraussetzen. Alle Entscheidungen sind im Plan vorweggenommen.
10. **Kontextfenster-Bewusstsein**: Große Phasen werden in Sub-Tasks von max. 500 Zeilen Code pro Task aufgeteilt, damit Claude Code nicht den Kontext verliert.
</claude_code_constraints>

<output_format>
### Erwartetes Output-Format

Liefere den Plan als strukturiertes Markdown-Dokument mit folgenden Abschnitten:

#### 1. CLAUDE.md (vollständig ausformuliert)
Die CLAUDE.md-Datei die in Phase 0 ins Projekt-Root geschrieben wird. Enthält: Projektbeschreibung, Architektur, Coding-Standards, Package-Konventionen, Commit-Konventionen, Test-Konventionen, alle technischen Entscheidungen.

#### 2. Projektstruktur
Vollständiger Verzeichnisbaum als Tree-Output. Multi-Module: `:app`, `:core`, `:feature-filemanager`, `:feature-terminal`, `:feature-connections`, `:feature-proxmox`, `:data`, `:domain`, `:wear`. Jedes Modul mit Package-Layout bis auf Datei-Ebene.

#### 3. Design Mockups (vor Entwicklungsstart)
Textuelle UI-Spezifikationen für alle Hauptscreens die als Grundlage für die Compose-Implementierung dienen:
- Dual-Pane File Manager (Portrait + Landscape, gefaltet + entfaltet)
- SSH Terminal (Portrait + Landscape mit Custom Keyboard)
- Connection Manager
- Transfer Queue
- Code Editor
- Snippet Manager
- Proxmox VM-Ersteller
- Watch Screens (Tiles, Complications, Quick-Commands)
- Settings + Paywall/Premium Screen
Jeder Screen: Layout-Beschreibung, Compose-Komponenten-Hierarchie, Navigation, Interaktionen.

#### 4. Phasen-Roadmap
8-12 Phasen. Jede Phase enthält:
```
## Phase N: [Name]
Abhängigkeiten: Phase X, Y
Geschätzter Aufwand: X Claude Code Tasks

### Task N.1: [Name]
Beschreibung: [Was genau passiert]
Dateien: [Exakte Pfade die erstellt/geändert werden]
Verifikation: [Ausführbarer Befehl]
Commit: [Conventional Commit Message]
Bei Fehlschlag: [Konkrete Aktion]

### Task N.2: ...
```

#### 5. Tech-Stack Matrix
Tabelle: Library | Version | Zweck | Modul | Gradle-Dependency-String

#### 6. CI/CD Pipeline
Vollständige GitHub Actions YAML-Dateien (nicht Beschreibungen, sondern der tatsächliche YAML-Code) für:
- `.github/workflows/pr-check.yml`
- `.github/workflows/build.yml`
- `.github/workflows/release.yml`
- `.github/dependabot.yml`
Inklusive aller benötigten Secrets (als Platzhalter-Namen).

#### 7. Testplan
Pro Modul: Welche Test-Klassen werden erstellt, was testen sie, welche Mocks werden verwendet. Konkrete Beispiel-Testmethoden als Pseudo-Code.

#### 8. Security-Konzept
Credentials-Flow, Key-Storage, Biometrie-Integration, Proxmox API Token Handling – als implementierbare Spezifikation.

#### 9. Datenmodell
Room Entities als Kotlin Data Classes (vollständiger Code). Alle DAOs als Interfaces. Migrations-Strategie.

#### 10. Risiken & Mitigations
Bekannte Probleme mit konkreten Workarounds (nicht "könnte problematisch sein" sondern "Problem X → Lösung Y implementieren").

#### 11. Monetarisierungs-Implementierung
Google Play Billing Library Integration, AdMob Setup, Feature-Flags für Free/Premium, Trial-Logik – als Task-Liste.
</output_format>

<critical_reminders>
### Wichtige Hinweise

- Der gesamte Plan wird von Claude Code Agents ausgeführt, NICHT von einem menschlichen Entwickler. Schreibe entsprechend präzise und maschinenlesbar.
- Priorisierung: Phase 1-4 = MVP (File Manager + Terminal + Connections + CI/CD), Phase 5-8 = Claude Code Integration + Proxmox, Phase 9+ = Watch App + Monetarisierung.
- Jede Phase muss einen kompilierbaren, testbaren Zustand hinterlassen. Kein "work in progress" ohne grüne Tests.
- Verwende die aktuellsten stabilen Library-Versionen (Stand: April 2026).
- Wear OS Companion ist ein separates Modul (`:wear`) im selben Gradle-Projekt.
</critical_reminders>
