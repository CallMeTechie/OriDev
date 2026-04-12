# Datenschutzerklärung — Ori:Dev

_Stand: 12. April 2026_

Ori:Dev (折り Dev) ist eine Open-Source-Anwendung für Android, entwickelt von CallMeTechie. Der Schutz deiner Daten ist ein zentrales Designziel dieser App. Diese Datenschutzerklärung beschreibt vollständig, welche Daten Ori:Dev verarbeitet — und welche nicht.

## Kurzfassung

**Ori:Dev sammelt keine personenbezogenen Daten.** Es gibt keine Analytics, keine Werbe-IDs, keine Drittanbieter-Tracker und kein Telemetrie-Reporting im Hintergrund.

## 1. Server-Zugangsdaten

Alle Verbindungsprofile (Hostnamen, Benutzernamen, Passwörter, SSH-Schlüssel) werden **ausschließlich lokal** auf deinem Gerät gespeichert. Passwörter und private Schlüssel werden im **Android Keystore** verschlüsselt mit **AES-256-GCM**. Sie verlassen dein Gerät niemals — außer als Teil der von dir aktiv aufgebauten SSH-/SFTP-/FTP-/FTPS-Verbindung zum jeweiligen Zielhost.

## 2. SSH-Host-Keys

SSH-Host-Keys werden nach dem **Trust-on-First-Use**-Prinzip lokal gespeichert. Sie werden weder an CallMeTechie noch an Dritte übertragen. Bei einer Abweichung wird die Verbindung hart abgelehnt.

## 3. Crash-Reports (ACRA)

Crash-Reports sind **standardmäßig deaktiviert** und können in den Einstellungen optional aktiviert werden. Wenn du sie aktivierst:

- werden **keine** Logcat-Daten übertragen,
- wird **keine** Geräte-ID übertragen,
- werden **keine** Hostnamen oder Pfade aus Stack Traces übertragen (diese werden vor dem Versand bereinigt),
- werden ausschließlich übertragen: App-Version, Android-Version, Telefonmodell und der bereinigte Stack Trace.

Crash-Reports werden über eine selbstgehostete ACRA-Instanz auf `crash.ori.dev` empfangen und nach 90 Tagen automatisch gelöscht.

## 4. Claude-KI-Integration

Wenn du die Claude-KI-Integration im Editor aktivierst und einen Code-Ausschnitt für eine KI-Anfrage markierst, wird **nur dieser Ausschnitt** an die Anthropic-API gesendet. Es erfolgt **keine Hintergrundnutzung**. Es werden keine Dateien automatisch übermittelt. Anthropics eigene Datenschutzerklärung gilt für diese Anfragen: https://www.anthropic.com/privacy.

## 5. Proxmox-API-Aufrufe

Aufrufe an deinen Proxmox-VE-Server gehen **direkt** von deinem Gerät an den von dir konfigurierten Server. Es gibt **kein Proxying** über CallMeTechie- oder Drittanbieter-Server.

## 6. Wear-OS-Companion

Die Kommunikation zwischen Telefon und Pixel Watch nutzt die **Google Play Services Wearable Data Layer**. Diese Verbindung wird vom Betriebssystem verschlüsselt. Es werden ausschließlich aktive Sitzungs-Metadaten (Hostalias, Status, letzte Aktivität) übertragen — niemals Passwörter oder Schlüssel.

## 7. Berechtigungen

| Berechtigung           | Zweck                                                |
| ---------------------- | ---------------------------------------------------- |
| `INTERNET`             | SSH/SFTP/FTP/FTPS-Verbindungen, Proxmox-API          |
| `USE_BIOMETRIC`        | Optionales biometrisches Entsperren des Keystores    |
| `POST_NOTIFICATIONS`   | Transfer-Status, Wear-Befehle                        |
| `FOREGROUND_SERVICE`   | Laufende Transfers während Bildschirm-Aus            |
| `WAKE_LOCK`            | Verhindern von Doze während aktiver Transfers        |

## 8. Kontakt

Bei Fragen oder Anliegen zum Datenschutz öffne bitte ein Issue:
**https://github.com/CallMeTechie/OriDev/issues**

---

# Privacy Policy — Ori:Dev (English)

_Last updated: 12 April 2026_

Ori:Dev (折り Dev) is an open-source Android application built by CallMeTechie. Protecting your data is a core design goal. This document fully describes what data Ori:Dev processes — and what it does not.

## TL;DR

**Ori:Dev does not collect personal data.** No analytics, no advertising IDs, no third-party trackers, no background telemetry.

## 1. Server credentials

All connection profiles (hostnames, usernames, passwords, SSH keys) are stored **locally** on your device only. Passwords and private keys are encrypted in the **Android Keystore** using **AES-256-GCM**. They never leave your device — except as part of the SSH / SFTP / FTP / FTPS connection you actively initiate to the target host.

## 2. SSH host keys

SSH host keys are stored locally using the **Trust on First Use** model. They are never transmitted to CallMeTechie or to any third party. A mismatch results in a hard connection refusal.

## 3. Crash reports (ACRA)

Crash reports are **opt-in** and **disabled by default**. If you enable them:

- **no** logcat data is transmitted,
- **no** device identifier is transmitted,
- **no** hostnames or file paths from stack traces are transmitted (they are scrubbed before submission),
- only the following are transmitted: app version, Android version, phone model, and the scrubbed stack trace.

Crash reports are received by a self-hosted ACRA instance at `crash.ori.dev` and automatically deleted after 90 days.

## 4. Claude AI integration

If you enable the Claude AI integration in the editor and select a code excerpt for an AI request, **only that excerpt** is sent to the Anthropic API. There is **no background usage**. No files are uploaded automatically. Anthropic's own privacy policy applies to those requests: https://www.anthropic.com/privacy.

## 5. Proxmox API calls

Calls to your Proxmox VE server go **directly** from your device to the server you configured. There is **no proxying** through CallMeTechie or any third-party server.

## 6. Wear OS companion

Communication between phone and Pixel Watch uses the **Google Play Services Wearable Data Layer**, which is encrypted by the operating system. Only active-session metadata (host alias, status, last activity) is transmitted — never passwords or keys.

## 7. Permissions

| Permission             | Purpose                                              |
| ---------------------- | ---------------------------------------------------- |
| `INTERNET`             | SSH / SFTP / FTP / FTPS connections, Proxmox API    |
| `USE_BIOMETRIC`        | Optional biometric unlock of the Keystore            |
| `POST_NOTIFICATIONS`   | Transfer status, Wear command acknowledgements       |
| `FOREGROUND_SERVICE`   | Long-running transfers while the screen is off      |
| `WAKE_LOCK`            | Prevent Doze during active transfers                 |

## 8. Contact

For privacy questions, please open an issue:
**https://github.com/CallMeTechie/OriDev/issues**
