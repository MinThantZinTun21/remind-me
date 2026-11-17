# What Tecno HiOS does to background apps and alarms

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me v1](../map.md)

## Question

What does HiOS on a Tecno Camon 40 (Android 16) do, beyond stock Android, that can stop a scheduled notification or alarm from firing, and which settings turn that off?

Establish:

- The HiOS features that kill or freeze apps (autostart management, battery or power-saving modes, app-launch or background management, "Phone Master" style cleaners) and where each setting lives.
- Whether swiping an app away from recents on HiOS force-stops it (which cancels its alarms) or only removes the task.
- Whether alarms set with `setAlarmClock` are treated differently from other exact alarms on Transsion devices.
- Whether boot-completed broadcasts are delivered to apps that are not on the autostart list.
- What dontkillmyapp.com, Transsion's own documentation and developer reports say, noting how recent each source is and which HiOS version it covers.
- Whether any of these settings can be read or opened from an app (deep links to the settings screens) or checked over adb, so a prototype can verify them.

Evidence here is thinner and less official than for stock Android; mark each claim as documented, reported, or unverified. End with the checklist of settings the owner must change once, and the specific behaviours the reliability prototype must test on the real phone.

## Answer

Researched 2026-11-17. Recorded by the charting session from the research agent's committed findings, with the owner's go-ahead (the agent could not write outside its worktree).

Evidence is low to moderate: nothing was observed on a Camon 40, Transsion publishes no developer documentation, and every mechanism below is `reported` by third parties on other Transsion phones (four of them on Android 16). The dontkillmyapp.com Tecno page dates from 2024 and Android 11-era firmware.

- HiOS has its own freezer ("Hiber", log tag `Usf_Hiber`), separate from Doze and from the stock battery setting. It is reported to freeze an app about 5 to 6 seconds after it leaves the screen, foreground service or not, and to withhold the app's scheduled alarms and broadcasts until it is unfrozen. Which owner setting exempts an app is not established; Auto-start plus unrestricted battery worked in one Android 16 report, and battery exemption plus a recents lock did not in another.
- `setAlarmClock` is reported to get no special treatment from that freezer. The reported exemption is a package-name allowlist (name contains `alarm`, `clock` or `calendar`); one decompilation report plus one alarm developer's experience on Android 14, unverified on HiOS 16.
- Swiping from recents is reported to kill the process, including a ringing alarm's sound and notification, but not to force-stop the package (`stopped=false`, exit reason "One-key clean"), so scheduled alarms stay registered. Locking the card in recents prevents the kill.
- The boot broadcast is reported to arrive, but about 11.5 minutes after boot on a Tecno with Android 15, with or without Auto-start; older reports say it never arrives without Auto-start. Unverified on HiOS 16.
- An app can open the Auto-start screen (`com.transsion.phonemaster/com.cyin.himgr.autostart.AutoStartActivity`) but cannot read whether it is on. Over adb, freezer state is readable through logcat and `dumpsys`; the commands that change it are reported blocked on retail firmware.

Settings the owner changes once (names vary by HiOS version; use Settings search, and recheck after every HiOS update):

1. App battery page (Settings, App management, App list, Remind Me, Battery): allow background activity, set Unrestricted or "No restrictions", turn off "Stop app when idle".
2. Phone Master, Auto-start management (also shown as App Launch): switch Remind Me on.
3. Phone Master, Settings, Protected apps: add Remind Me, if the list exists.
4. Lock the Remind Me card in recents.
5. Turn off Power Saving Management For Apps if present, sleep mode optimisation, automatic power saving, and the old Power Marathon items if present.
6. Never add Remind Me to the Freezer; keep it out of Phone Master's cleaner.

Behaviours the reliability prototype must test on the phone:

1. Whether and how soon `Usf_Hiber` freezes the app, and whether scheduled alarms at 2 minutes, 30 minutes and overnight fire on time while frozen (check `dumpsys alarm`).
2. Two builds differing only in application ID, neutral against one containing `alarm`; and `setAlarmClock` against `setExactAndAllowWhileIdle` under the neutral name.
3. Each checklist setting alone from a fresh install, to find the smallest set that works.
4. Swipe from recents, clear-all and Phone Master clean, with and without the recents lock: `stopped=` state, whether the task still fires, and whether a ringing alarm is silenced and can recover.
5. Whether the ring keeps playing for minutes with the screen locked, and whether the full-screen alarm appears from a frozen or killed app.
6. Reboot: time to `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED` with Auto-start off and on, and a task due minutes after boot.
7. Battery saver and the strongest power-saving mode; repeat rule rescheduling before the app refreezes; force-stop as a control.
8. Whether the Auto-start deep link opens, and which read-only Hiber queries and exit reasons are available for diagnostics.

Findings: branch `research/hios-background-restrictions` (commit `2492f77`), file `docs/research/hios-background-restrictions.md`. Read it with `git show research/hios-background-restrictions:docs/research/hios-background-restrictions.md`.
