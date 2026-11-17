# What Tecno HiOS does to background apps and scheduled alarms

Research for ticket `.scratch/remind-me/issues/04-hios-background-restrictions.md`.
Researched 2026-11-17. Target: Tecno Camon 40 (CM5), Android 16, HiOS 16.

Scope: what HiOS adds on top of stock Android. Stock rules (exact-alarm permission, Doze, full-screen intent, force-stop semantics) belong to ticket 03 and are cited here only where a HiOS finding depends on them.

Wording: in this file "scheduled alarm" means an Android `AlarmManager` entry, the mechanism that fires both delivery styles. "Alarm" on its own keeps its glossary meaning (the full-screen ring).

## Read this first

- **Nothing here was observed on a Camon 40.** No source found covers that model on HiOS 16. The closest are developer reports from 2026 on other Transsion phones running Android 16 (Tecno Pova 7, Infinix X6853, X6886 and Note 50x). Tecno, Infinix and itel share one framework, so these are the best available proxy, but they remain a proxy.
- **Transsion publishes nothing for developers** about its background management. The only official text found is two user-facing notes about the Freezer and the Auto-start list. Everything else is community or developer reporting.
- **Labels used below.**
  - `documented`: stated by Tecno, Carlcare (Transsion's service arm) or Google.
  - `reported`: stated by a named third party from a device or from decompiled firmware. The device, OS version and date are given. One report is not proof.
  - `unverified`: follows from a report but nobody is known to have checked it, or sources disagree.

## Short answer

1. HiOS has a vendor freezer, called **Hiber** in firmware and logs, that is separate from Doze and from the stock battery setting. It is reported to freeze a third-party app's processes about five seconds after the app leaves the screen, foreground service or not, and to withhold that app's scheduled alarms and broadcasts while it is frozen. `reported`
2. `setAlarmClock` is reported to get **no special treatment** from Hiber. The reported exemption is a package-name allowlist: any package whose name contains `alarm`, `clock` or `calendar`. One alarm developer independently found that alarms on a Tecno became reliable only after renaming the package to end in `.alarm`. `reported`, two sources, neither on Android 16.
3. Swiping the app from recents is reported to **kill the process but not force-stop it**. The scheduled alarms stay registered. It does kill a ringing alarm's sound, service and notification, and restarts can be blocked unless the app is on the Auto-start list. Locking the app card in recents prevents the kill. `reported`
4. The boot broadcast is delivered, but one test on a Tecno with Android 15 measured it arriving **about 11.5 minutes after boot**, with or without Auto-start. Older reports say it is not delivered at all without Auto-start. `reported`, sources disagree.
5. The settings that matter are the per-app battery page, the Auto-start list in Phone Master, the recents lock, and the power-saving switches. Their names and paths changed between HiOS versions and no source gives them for HiOS 16.
6. An app can open the Auto-start screen but cannot read whether it is switched on. Over adb the freezer's state can be read through logs and `dumpsys`; the commands that change it are reported to be blocked on retail firmware.

## 1. The mechanisms

### 1.1 Hiber, the vendor freezer

| Claim | Label | Source, device, date |
|---|---|---|
| Transsion firmware contains a hibernation system named Hiber inside `system_server`, working independently of Doze and App Standby. Disabling battery optimisation or choosing Unrestricted does not affect it. | reported | Decompilation of `services.jar` from an unnamed Tecno device. Model and HiOS version not given. 2026-02-26. [DKMA #3800](https://github.com/urbandroid-team/dont-kill-my-app/issues/3800) |
| When an app is frozen, Hiber removes all of its scheduled alarms from the system's alarm store and holds them in memory until the app is unfrozen, for example when the user opens it. New alarms set by an already-frozen app are intercepted too. Jobs and services are held the same way. | reported | Same source. |
| `Usf_Hiber` froze an app's uid about 6 s after it left the foreground, foreground service or not, and deferred its alarms; an in-app timer never fired while backgrounded. | reported | Tecno Pova 7, HiOS, Android 16. 2026-10-05. [turbo-optimizer PR 1](https://github.com/barrydash21-beep/turbo-optimizer/pull/1) |
| Log line `Usf_Hiber/stateManager: freeze uid` appeared about 5 s after the activity paused. The process cgroup read `frozen 1` while Android's own activity manager reported `isFrozen=false` and an active foreground service. Battery exemption and a recents lock did not prevent it. | reported | Infinix X6853, XOS 16.3.0 (Android 16), `com.transsion.usf` 25.11.24.001. 2026-10-18. [pirate-cinema notes](https://github.com/cyberboy1999/pirate-cinema/blob/98214f1f47c2c8238efb1dbe0b3539bb53950cec/docs/PROJECT.md) |
| A frozen app's broadcast receiver was skipped (`Hiber/broadcast restricted queue` in broadcast history). After allowing the app in Phone Master's App Launch list and setting battery to "No restrictions", the broadcast arrived 2.4 s after the event with the app closed. | reported | Infinix X6886, Android 16. 2026-11-12. [codka PR 12](https://github.com/Eng-Mohamed-abdirahman/codka/pull/12) |
| XOS 16.3 has a broadcast filter that blocks delivery to a hibernating receiver under some conditions; Google's push action is explicitly exempt. | reported | Decompiled XOS 16.3, not tested on a device by the author. 2026-10-25. [MicroG-RE #17](https://github.com/MorpheApp/MicroG-RE/issues/17) |
| "Aggressive battery setting causes app to be completely halted, like frozen in time, so, timers, services, foreground services, all of them stop working until you return manually to the app." Developer workaround: "No known solution on the developer end." | reported | Tecno Spark, version not given. Text dated 2024-05-16. [dontkillmyapp.com/tecno](https://dontkillmyapp.com/tecno), [source file](https://github.com/urbandroid-team/dont-kill-my-app/blob/master/_vendors-content/tecno/explanation.md), origin [DKMA #975](https://github.com/urbandroid-team/dont-kill-my-app/issues/975) |

What this means for firing: if the reports hold on the Camon 40, a scheduled alarm set by a frozen, non-exempt app does not fire at its time. It fires late, when something unfreezes the app. Two user reports fit that pattern: alarms ringing "10 minutes later or even 2 hours later" on a Tecno Camon 19 Pro (Android 13, 2025-05-12, [Fossify Clock #176](https://github.com/FossifyOrg/Clock/issues/176)), and an alarm plugin that worked on a Samsung but not on a Tecno Spark 7P (Android 11, 2023-11, [alarm #95](https://github.com/gdelataillade/alarm/issues/95)). Neither report identifies the cause. `unverified` as an explanation.

Which user switch exempts an app from Hiber is **not established**. The codka report says Auto-start plus unrestricted battery was enough on Android 16. The pirate-cinema report says battery exemption plus a recents lock was not enough, and did not test Auto-start. `unverified`

### 1.2 Auto-start management (also labelled App Launch)

| Claim | Label | Source, device, date |
|---|---|---|
| Phone Master has an Auto-start Management list with a per-app toggle. Carlcare tells users to switch apps off there for speed, and to "keep essential apps" on so notifications are not missed. | documented | Carlcare, 2024-02-17, no HiOS version. [Carlcare Kenya](https://www.carlcare.com/ke/tips-detail/speed-up-slow-tecno-phone/) |
| After a process is killed, the vendor power manager (`tranpm`, log line `proxyBlkService`) blocked the system from restarting the app's service. The app's developer reads this as the Auto-start check. | reported | Infinix Note 50x, Android 16, logcat attached. 2026-09-30. [dechainer #55](https://github.com/warleysr/dechainer/issues/55) |
| A well-known alarm app tells Tecno and Infinix owners that Auto Start "lets Alarmy start automatically after your phone is rebooted or when it gets closed in the background. Without it, alarms may never ring." | reported | Alarmy support article, undated, read 2026-11-17. [Alarmy help](https://alarmy-android.zendesk.com/hc/en-us/articles/46701731923609--No-Ring-How-to-Fix-Alarm-Issues-on-Other-Brands-Infinix-Tecno-Sony-Sharp-etc) |
| New third-party apps are off the list by default. | unverified | Implied by every guide; no source states the default for a sideloaded app on HiOS 16. |

### 1.3 Recents: swipe, clear-all and the lock

| Claim | Label | Source, device, date |
|---|---|---|
| Swiping the app's card away killed the activity, the ringing foreground service, its ongoing notification and the alarm sound. `stopWithTask=false`, `onTaskRemoved`, a separate task affinity and a private service process did not prevent it. | reported | Tecno Pova Neo 3, Android 14. 2026-07-30. [Razbudilnik PR 11](https://github.com/pyclan88/Razbudilnik/pull/11), [notes at that revision](https://github.com/pyclan88/Razbudilnik/blob/f7fb4e7666418c25ebf70d3876e1b72579c361ec/CODEX_HANDOFF.md) |
| After that swipe "the package was not force-stopped. `dumpsys package` reported `stopped=false`", and a scheduled alarm set earlier still fired and restarted the app. | reported | Same source. The package name contained `alarm` (see 1.4). |
| The kill is recorded as exit reason 13 (other kills by system), subreason "One-key clean", description `manual_oneKeyClean`. That is not the user-requested reason a force-stop records. | reported | Infinix Note 50x, Android 16. 2026-09-30. [dechainer #55](https://github.com/warleysr/dechainer/issues/55) |
| Locking the card (padlock in recents) stopped the kill. | reported | Same Infinix Note 50x owner, 2026-10-01. Also Tecno phones in 2021: [Briar #2010](https://code.briarproject.org/briar/briar/-/issues/2010), [Briar #2159](https://code.briarproject.org/briar/briar/-/issues/2159) |
| A recents lock does not stop Hiber freezing the app. | reported | Infinix X6853, XOS 16.3.0. 2026-10-18. pirate-cinema notes, linked above. |

For contrast, a real force-stop on Android 15 and later cancels every pending intent the app holds, which includes its scheduled alarms, until the user opens the app again. `documented`, [Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states). No source reports a recents swipe on a Transsion phone putting an app in that state. Whether Phone Master's own cleaner or a power-saving mode force-stops apps is `unverified`.

### 1.4 The package-name allowlist and `setAlarmClock`

| Claim | Label | Source, device, date |
|---|---|---|
| Hiber does not look at the alarm type. `RTC_WAKEUP`, exact alarms and `setAlarmClock` alarms are all withheld alike. | reported | Decompilation, [DKMA #3800](https://github.com/urbandroid-team/dont-kill-my-app/issues/3800), 2026-02-26. Not confirmed by a device test. |
| Alarms are exempt for packages on an allowlist matched by substring. Listed entries: `alarm`, `clock`, `calendar`, and a few named apps (TickTick and some prayer-time apps). A second list exempts whole apps from hibernation (WhatsApp, Telegram, Google Play services and others). The lists live in `/system/product/hiber/config/hiber.json`, config version 1.2.3, and can be replaced from Transsion's cloud. | reported | Same source. |
| "Alarm and background behavior on the Tecno Android 14 test device became reliable only after adopting that package identity" (application ID changed to end in `.alarm`). | reported | Tecno Pova Neo 3, Android 14. 2026-07-17. [Razbudilnik PR 4](https://github.com/pyclan88/Razbudilnik/pull/4), [notes](https://github.com/pyclan88/Razbudilnik/blob/d16fb936e0a32ed70c9e02378dc442ef4e4201c0/CODEX_HANDOFF.md) |
| The allowlist exists and works on HiOS 16. | unverified | No Android 16 source tests it. |

So the answer to "are `setAlarmClock` alarms treated differently" is: by stock Android yes (ticket 03), by the reported Transsion layer no. The package name is the reported lever, and it is an undocumented one that Transsion can change remotely.

### 1.5 Boot

| Claim | Label | Source, device, date |
|---|---|---|
| `BOOT_COMPLETED` reached the app 11 min 30 s and 11 min 51 s after two boots, because the firmware's broadcast queue waits on a system receiver. Allowing Auto-start "does not shorten that wait, but on such phones it may be needed for the app to start at all." | reported | TECNO phone, HiOS, Android 15, model not given. 2026-10-31. [OpenIPC decoder PR 258](https://github.com/OpenIPC/decoder/pull/258), [usage notes](https://github.com/OpenIPC/decoder/blob/c6719a0bc30748f035e28481ba7f87ab32d57a82/docs/usage.md) |
| A boot receiver rescheduled the alarm after reboot and unlock, and it fired. Delay not measured. | reported | Tecno Pova Neo 3, Android 14, package name containing `alarm`. 2026-07-18. [Razbudilnik PR 5](https://github.com/pyclan88/Razbudilnik/pull/5) |
| `BOOT_COMPLETED` was not delivered on Tecno C9, Tecno W3 and Infinix Note 3 until the app was enabled in the vendor's start-up manager. | reported | Android 6 or 7 era, 2017-01. [Stack Overflow 41657586](https://stackoverflow.com/questions/41657586/boot-completed-broadcast-receiver-not-working-in-xos-android) |
| Whether the Camon 40 on HiOS 16 delivers the boot broadcast to an app that is off the Auto-start list, and how late. | unverified | The three sources disagree and none is HiOS 16. |

Scheduled alarms do not survive a reboot on any Android phone, so every minute between boot and the boot broadcast is a minute in which nothing is scheduled. An 11-minute gap would be a real hole for a task due soon after a restart.

### 1.6 Power-saving switches

All `reported`, all from [dontkillmyapp.com/tecno](https://dontkillmyapp.com/tecno) ([user steps](https://github.com/urbandroid-team/dont-kill-my-app/blob/master/_vendors-content/tecno/user.md), last edited 2024-07-19). The Power Marathon items trace to one Infinix Hot 10 Play on Android 11 ([DKMA #474](https://github.com/urbandroid-team/dont-kill-my-app/issues/474), 2022-06-27). The page names no HiOS version, and owners say it is out of date for HiOS 15 ([#3937](https://github.com/urbandroid-team/dont-kill-my-app/issues/3937), 2026-03; [#1679](https://github.com/urbandroid-team/dont-kill-my-app/issues/1679), 2025-06).

- **Power Saving Management For Apps** (Settings, Battery Lab, Battery Saving Settings). Described as on by default and as the switch that causes the halt in 1.1. A draft Infinix page says it is enabled "with every system setup (confirmed on Android 14)" and that the stock Unrestricted setting does not override it ([DKMA PR 4413](https://github.com/urbandroid-team/dont-kill-my-app/pull/4413), 2026-10-11). One Infinix Hot 60 Pro owner reports the item disappeared after updating to XOS 15.1.2 ([DKMA #4394](https://github.com/urbandroid-team/dont-kill-my-app/issues/4394)).
- **Power Marathon**: Power Boost, Screen off push block, Screen off sleep, Screen off scheduled push.
- **Sleep Mode Optimization**: turns on airplane mode at night. That does not stop a local scheduled alarm by itself; it matters only as a sign the phone is in its most aggressive state.
- **Smart Scenes, Short Video Optimization**.
- **App Booster** in Phone Master: a protected list of at most four apps.

### 1.7 Freezer and cleaners

- **Freezer.** A launcher feature where the user adds apps by hand "to totally freeze the background process of apps". `documented`, [Tecno FAQ, HiOS tab](https://www.tecno-mobile.com/faq/), undated. "The frozen app won't run, send notifications, or even show on your home screen until you unfreeze it." `documented`, [Carlcare](https://www.carlcare.com/ng/tips-detail/apps-disappearing-on-android-and-iphone-home-screen/), 2022-08-15. It only affects apps the owner puts in it.
- **Phone Master cleaning.** The exit record in 1.3 names "One-key clean", so the recents swipe and the cleaner appear to share one kill path. `reported`. Whether Phone Master's scheduled or lock-screen cleaning kills apps without any user action on HiOS 16 is `unverified`.

## 2. Where the settings live

No source gives paths for HiOS 16. The table lists every reported location; on the phone, the Settings search box is the reliable way in.

| Setting | Reported location | Version the report covers |
|---|---|---|
| Background activity for the app | Settings, App management, App list, the app, Battery: turn on "Allow background activity"; on the same screen turn off "Stop app when idle" | HiOS 15 and XOS 15, translated from Russian firmware, collected from 4PDA owners. [DKMA #4394](https://github.com/urbandroid-team/dont-kill-my-app/issues/4394), 2026-10-06 |
| Same, Infinix wording | App management, App list, the app, Battery: background usage on, mode "Unrestricted"; then Battery & Power Saving: choose "No Restrictions" for the app | XOS, version not given. [AdGuard KB](https://adguard.com/kb/adguard-for-android/solving-problems/background-work/#infinix) |
| Unused-app handling | Settings, App Management, App List, the app, App Permissions, Unused Apps: turn off "Remove permissions and free up space" | HiOS, version not given, 2025-04-16. [TrueConf](https://trueconf.com/blog/knowledge-base/trueconf-notifications-not-working-on-android-ios-how-to-fix-it) |
| Auto-start | Phone Master, Toolbox, Auto-start management | Older HiOS. [dontkillmyapp.com/tecno](https://dontkillmyapp.com/tecno), [Carlcare](https://www.carlcare.com/ke/tips-detail/speed-up-slow-tecno-phone/) |
| Auto-start | Phone Master, Accelerate, Auto-start management | HiOS 15. DKMA #4394 |
| Auto-start | Settings, App management, Auto-start management; shown as "App Launch" on some firmware | XOS 15 and 16. AdGuard KB; [codka PR 12](https://github.com/Eng-Mohamed-abdirahman/codka/pull/12) |
| Protected apps | Phone Master, top-left menu, Settings, Protected apps | HiOS 15. DKMA #4394 only |
| Recents lock | Recents, the small triangle or down arrow by the app name, Lock | HiOS 15 (DKMA #4394) and older (dontkillmyapp.com/tecno) |
| Power Saving Management For Apps | Settings, Battery Lab, Battery Saving Settings | Older HiOS. May be absent on current firmware |
| Same, newer wording | Settings, Battery & Power Saving, Battery saver, Other Power Saving Settings: untick "Power Saving Management For Apps" | Infinix, Android 14. [DKMA PR 4413](https://github.com/urbandroid-team/dont-kill-my-app/pull/4413) |
| Sleep mode optimisation, automatic power saving | Settings, "Battery saving and power consumption" | HiOS 15. DKMA #4394 |
| Power Marathon items | Phone Master or Power Marathon app, Advanced Settings | Android 11 era. May be absent on current firmware |
| Freezer | Home screen long-press, Desktop Setting, Other settings, Freezer | Carlcare 2022 |

Version mapping: HiOS 15 is Android 15 and first shipped on the Camon 40 series in February 2025; HiOS 16 is Android 16 ([Wikipedia, HiOS](https://en.wikipedia.org/wiki/HiOS)). The Camon 40 was in the first HiOS 16 wave, Q2 2026 ([androidinsider.ru](https://androidinsider.ru/os/hios-16-dlya-smartfonov-tecno-chto-novogo-kogda-vyjdet-i-kto-poluchit.html), 2026-04-07). Item names are reported to change even between point updates of one model, and one Infinix owner's scheduled notifications started working only after a system update ([flutter_local_notifications #2821](https://github.com/MaikuB/flutter_local_notifications/issues/2821), opened 2026-09-03).

## 3. What an app or adb can see

### From the app

| Capability | Label | Source |
|---|---|---|
| Open the Auto-start screen with the explicit component `com.transsion.phonemaster` / `com.cyin.himgr.autostart.AutoStartActivity`. | reported | First found by `dumpsys activity` on a Tecno in 2020 ([AutoStarter #47](https://github.com/judemanutd/AutoStarter/issues/47)). "App Launch button opens the right screen" on an Infinix X6886, Android 16, 2026-11-12 ([codka](https://github.com/Eng-Mohamed-abdirahman/codka/blob/main/app/src/main/java/com/codka/app/sms/BackgroundSetup.kt)). Used by [pano-scrobbler](https://github.com/kawaiiDango/pano-scrobbler/blob/main/composeApp/src/androidMain/kotlin/com/arn/scrobble/utils/AndroidStuff.kt) and [auto_start_flutter](https://github.com/seena98/auto_start_flutter/blob/master/android/src/main/kotlin/co/techFlow/auto_start_flutter/AutoStartIntents.kt). |
| Fallback intent action `com.cyin.himgr.applicationmanager.view.activities.AUTO_START_ACTIVITY`. | reported | The string appears in decompiled Phone Master code ([copy](https://github.com/thinkertomar/Focus-Elite/blob/main/content/decompiled_src/sources/com/transsion/utils/h1.java)) and in codka. |
| The same component failed with "component not found" on one phone, which used `com.mediatek.autobootcontroller` instead. | reported | Unnamed Tecno or Infinix, 2020-12, AutoStarter #47. A fallback to the app's own details page is needed. |
| Whether that component is exported on the Camon 40 with HiOS 16. | unverified | |
| Read whether Auto-start is on for the app. | reported as impossible | "Android can't tell us whether App Launch is on" (codka). No public API or readable setting was found in any source. |
| Tell after the fact that the process was killed, and by what: the stock exit-reasons API returns the vendor's subreason text. | reported | The "One-key clean" record in [dechainer #55](https://github.com/warleysr/dechainer/issues/55). |
| Tell after the fact that the app was force-stopped: `ApplicationStartInfo.wasForceStopped()`. | documented | [Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states) |

The stock battery-exemption check only answers for Doze. It says nothing about Hiber, Auto-start or the Freezer.

### Over adb, read-only

Reported to work on at least one Transsion phone; none confirmed on the Camon 40.

- `adb shell dumpsys package <pkg>` and look for `stopped=`: shows whether a swipe force-stopped the package (Razbudilnik, Tecno, Android 14).
- `adb logcat` filtered on `Usf_Hiber`, `tranpm` and `tranpm_dfx_process_died`: shows the freeze, blocked service restarts and the kill reason (dechainer and pirate-cinema, Android 16).
- `/sys/fs/cgroup/apps/uid_<uid>/pid_<pid>/cgroup.events`: `frozen 1` while the app is frozen (pirate-cinema, XOS 16.3.0).
- Broadcast history from `dumpsys activity broadcasts`: shows receivers skipped with `Hiber/broadcast restricted queue` (codka, Android 16).
- `adb shell dumpsys activity service com.transsion.usf/.UsfMainService -h` and `-help -hiber`: lists Hiber's own commands; read-only queries returned `is_runtime_white_app` and `get_app_mode` for a uid (pirate-cinema, XOS 16.3.0).
- `adb shell dumpsys activity hiber dump`, `is_white_app <pkg>`, `get_app_mode <uid>`: the older form of the same queries ([DKMA #3800](https://github.com/urbandroid-team/dont-kill-my-app/issues/3800), firmware version not given). On XOS 16.3.0 the similar `dumpsys hiber --help` failed, so which form the Camon 40 answers to is `unverified`.
- `adb shell dumpsys alarm`: stock. If Hiber withholds alarms as reported, the app's entries should vanish from this list while it is frozen. Nobody is known to have checked. `unverified`

### Over adb, changing state

DKMA #3800 gives `dumpsys activity hiber set_app_mode <uid> 1` as a way to exempt an app from Hiber permanently. On XOS 16.3.0 the same command and the global disable were found to be guarded by a debuggable-build check and did nothing on retail firmware; a policy switch printed success while the app stayed frozen (pirate-cinema notes, 2026-10-18). Treat these as `unverified` and probably unavailable. They are listed for completeness, not as a recommendation, and running them is the owner's decision.

## 4. What the sources are and how far to trust them

| Source | What it is | Recency and coverage |
|---|---|---|
| Tecno FAQ, Carlcare | Official, user-facing | Undated, 2022, 2024. Describe Freezer and the Auto-start list only. |
| dontkillmyapp.com Tecno page | Community-maintained, by the Sleep as Android developers | Created 2024-05, last content edit 2024-07-19. No HiOS version. Based on an Android 11 Infinix and an unnamed Tecno Spark. Rated 3 of 5 for severity. No Infinix page exists; one is in draft. |
| DKMA #3800 | One person's decompilation write-up | 2026-02-26. No device model, no HiOS version, no replies. Its vocabulary (Hiber, `set_app_mode`, proxying) is matched by independent device logs from 2026, and its allowlist claim by one alarm developer's experience. Its exact lists and its `setAlarmClock` claim are uncorroborated. |
| DKMA #4394 and the linked settings checker | Second-hand summary of 4PDA owner posts | 2026-10. HiOS 15 and XOS 15. Labels translated from Russian. Written by the author of a commercial tool. |
| Developer reports (dechainer, pirate-cinema, codka, turbo-optimizer, Razbudilnik, OpenIPC) | Individual developers' issue threads, pull requests and working notes from their own phones | 2026-07 to 2026-11. Android 14, 15 and 16. Small projects, single devices, written for their own purposes; several read as machine-assisted notes. They include log lines, which is why they are used. |
| App vendors' help pages (Alarmy, AdGuard, TrueConf) | Support advice | Undated or 2025. Generic; show what established apps tell Transsion owners to change. |
| Briar issues, Stack Overflow, AutoStarter | Older developer reports | 2017 to 2021. Kept for the recents lock, boot and intent history. |

Searches that found nothing useful: Tecno's developer or support sites for any background-management documentation; the issue trackers of Tasks.org, Home Assistant, ntfy, uhabits and Signal for Tecno or Infinix alarm reports; any report naming the Camon 40.

Overall strength: **low to moderate**. The existence of a vendor freezer that acts within seconds and is not controlled by the stock battery setting is supported by four independent Android 16 device reports and is the firmest finding. The behaviour of scheduled alarms under it, the allowlist, the effect of each user switch, and boot timing each rest on one or two reports, none from the target phone.

## 5. Checklist: settings the owner changes once

Do all of these for Remind Me, then repeat the check after every HiOS update. Names are the reported ones; use Settings search if a path is missing.

1. **App battery page**: allow background activity, set battery use to Unrestricted or "No restrictions", and turn off "Stop app when idle" (or the Unused apps toggle).
2. **Auto-start**: switch Remind Me on in Phone Master's Auto-start management or App Launch list.
3. **Protected apps**: add Remind Me in Phone Master's settings, if that list exists.
4. **Recents lock**: lock the Remind Me card.
5. **Power saving**: turn off Power Saving Management For Apps if present; turn off sleep mode optimisation and automatic power saving; turn off Power Boost and the three "Screen off" items if the firmware still has them. Do not use the strongest power-saving mode.
6. **Freezer**: never add Remind Me to it.
7. **Cleaners**: do not run Phone Master's one-tap clean on it; add it to any cleaner exception list offered.

Not a setting, but a build decision for the prototype to settle: whether the application ID should contain `alarm`, `clock` or `calendar`.

## 6. Behaviours the reliability prototype must test on the Camon 40

Run each with a notification-style and an alarm-style task, scheduled with the API ticket 03 selects.

1. **Freeze timing.** Send the app to the background and watch `Usf_Hiber` in logcat. Does it freeze, and how many seconds after leaving the screen?
2. **Firing while frozen.** Scheduled alarms at 2 minutes, 30 minutes and overnight, screen off, unplugged. Do they fire on time, late, or only when the app is opened? Check `dumpsys alarm` while frozen to see whether the entries are still there.
3. **Package name.** Two builds identical except for the application ID, one neutral and one containing `alarm`. Same schedule as test 2. This decides whether the allowlist is real on HiOS 16.
4. **`setAlarmClock` against `setExactAndAllowWhileIdle`**, under the neutral package name.
5. **Each owner setting alone.** Starting from a fresh install with defaults, turn on one checklist item at a time and repeat test 2, to find the smallest set that works and to learn what the defaults do to a sideloaded app.
6. **Swipe from recents.** With a task pending: swipe the card, then read `stopped=` from `dumpsys package`, check `dumpsys alarm`, and wait for the time. Repeat with clear-all, with Phone Master's clean, and with the card locked.
7. **Swipe while ringing.** Does swiping the card silence an alarm that is ringing? If so, does a recovery scheduled alarm bring it back?
8. **Ringing duration.** Does the ringing service keep playing for several minutes with the screen locked, or is it frozen a few seconds in?
9. **Full-screen alarm from a frozen or killed app.** Does the full-screen screen appear over the lock screen?
10. **Reboot.** Time from boot to `LOCKED_BOOT_COMPLETED` and to `BOOT_COMPLETED`, with Auto-start off and on, before and after first unlock. Schedule a task for a few minutes after the reboot and see whether it is missed.
11. **Power-saving modes.** Repeat test 2 with battery saver on and with the strongest power-saving mode on.
12. **Repeat rule.** When a repeating task fires, is the next occurrence scheduled before the app is frozen again?
13. **Force-stop, as a control.** Confirm scheduled alarms are cancelled and come back when the app is next opened.
14. **Auto-start deep link.** Does `com.transsion.phonemaster/com.cyin.himgr.autostart.AutoStartActivity` open from the app, and does the action fallback?
15. **Diagnostics.** Which read-only Hiber queries answer on this firmware, and does the exit-reasons API report the vendor's kill reason, so the app can tell the owner when HiOS interfered.
