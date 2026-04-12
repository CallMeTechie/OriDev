# Ori:Dev — Store Screenshots Checklist

Eight captures are required for the Play Store listing. Capture in the order below; export PNGs straight from `adb` (no recompression). Keep status bar visible, time set to **09:41**, battery at **100 %**, no notifications.

## Capture procedure

```bash
# 1. Set demo mode (clean status bar)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4

# 2. Take a screenshot and pull it
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png ./store/screenshots/<filename>.png
adb shell rm /sdcard/shot.png

# 3. Exit demo mode
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

For the Wear OS captures, target the watch via:

```bash
adb -s <watch-serial> shell screencap -p /sdcard/shot.png
adb -s <watch-serial> pull /sdcard/shot.png ./store/screenshots/<filename>.png
```

## Required resolutions

| Device                  | Resolution | Notes                            |
| ----------------------- | ---------- | -------------------------------- |
| Pixel 8 (phone)         | 1080x2400  | crop status bar OFF for Play     |
| Pixel Fold inner        | 1840x2208  | unfolded, landscape orientation  |
| Pixel Fold outer        | 1080x2092  | folded, portrait                 |
| Pixel Watch 2           | 450x450    | round, no insets                 |

> Play Store legacy minimum is 1080x1920, but submit the native resolutions above for sharper rendering on modern devices. Keep aspect ratio between 16:9 and 9:16.

## Capture list

### #1 — Pixel 8: Connection list with 3 saved servers
- **File:** `01-pixel8-connections.png`
- **Resolution:** 1080x2400
- **Setup:** Three connection cards visible — `homelab.lan` (Proxmox icon, online), `prod-edge-01` (SSH, online), `nas.local` (SFTP, offline). FAB visible, top app bar showing search icon.
- **Caption (EN):** "All your servers, one tap away."
- **Caption (DE):** "Alle deine Server, einen Tap entfernt."

### #2 — Pixel 8: Terminal with htop
- **File:** `02-pixel8-terminal-htop.png`
- **Resolution:** 1080x2400
- **Setup:** SSH session into `prod-edge-01`, `htop` running, ~12 processes visible, CPU bars colourful, footer keybar visible (F1..F10), session tab named "edge-01".
- **Caption (EN):** "A real xterm-256color terminal."
- **Caption (DE):** "Ein echtes xterm-256color Terminal."

### #3 — Pixel 8: File manager browsing /var/log
- **File:** `03-pixel8-filemanager-varlog.png`
- **Resolution:** 1080x2400
- **Setup:** Single-pane file manager on `/var/log`, showing 8-10 entries (auth.log, syslog, nginx/, journal/, …). Breadcrumb bar visible. One file selected to show the contextual action bar.
- **Caption (EN):** "Browse, edit, transfer — anywhere."
- **Caption (DE):** "Durchsuchen, bearbeiten, übertragen — überall."

### #4 — Pixel Fold (inner): Dual-pane file manager + editor
- **File:** `04-fold-inner-dualpane.png`
- **Resolution:** 1840x2208 (unfolded landscape)
- **Setup:** Left pane = remote `/etc/nginx/`, right pane = Sora editor with `nginx.conf` open and a 6-line server block highlighted. Drag handle between panes visible.
- **Caption (EN):** "Unfold to a true dual-pane workstation."
- **Caption (DE):** "Aufklappen für echte Zwei-Spalten-Workstation."

### #5 — Pixel Fold (inner): Editor with diff view
- **File:** `05-fold-inner-editor-diff.png`
- **Resolution:** 1840x2208
- **Setup:** Editor showing inline git diff for `nginx.conf` (3 lines red, 4 lines green). Right rail shows Claude AI prompt input.
- **Caption (EN):** "Inline diff. Optional Claude AI."
- **Caption (DE):** "Inline-Diff. Optionale Claude-KI."

### #6 — Pixel Fold (outer): Connection list compact
- **File:** `06-fold-outer-connections.png`
- **Resolution:** 1080x2092 (folded portrait)
- **Setup:** Same connection list as #1 but rendered for the cover screen — denser cards, larger touch targets.
- **Caption (EN):** "Folded? Same power, smaller screen."
- **Caption (DE):** "Geklappt? Gleiche Power, kleineres Display."

### #7 — Pixel Watch 2: Connection monitor tile
- **File:** `07-watch2-monitor.png`
- **Resolution:** 450x450
- **Setup:** Watch tile showing 2 active sessions (`prod-edge-01`, `homelab.lan`) with green status dots and last-activity timestamps.
- **Caption (EN):** "Monitor sessions from your wrist."
- **Caption (DE):** "Sessions am Handgelenk überwachen."

### #8 — Pixel Watch 2: Panic disconnect screen
- **File:** `08-watch2-panic.png`
- **Resolution:** 450x450
- **Setup:** Big red "Disconnect all" confirm screen with the Indigo accent ring and "2 sessions" subtext.
- **Caption (EN):** "Panic disconnect. One tap. Done."
- **Caption (DE):** "Panic Disconnect. Ein Tap. Erledigt."

## Post-processing

- Do NOT scale screenshots — submit native resolution.
- Do NOT add device frames; Play Store adds its own.
- If a banner overlay is required for marketing, store it under `screenshots/overlays/` and never overwrite the source PNG.
- Run `pngcrush -rem alla -reduce` on each file before committing.
