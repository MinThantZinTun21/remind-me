# Android 16 rules for exact alarms and full-screen alarms

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me v1](../map.md)

## Question

On Android 16 (API 36), what must a sideloaded app do so that a notification or a full-screen alarm fires at an exact time, including while the phone is asleep, after a reboot, and after the app was swiped away?

Establish from the official Android documentation:

- Which scheduling call is right for each delivery style (`setAlarmClock`, `setExactAndAllowWhileIdle`, others) and what each guarantees under Doze and App Standby.
- The permissions involved and how each is granted for a sideloaded app: `SCHEDULE_EXACT_ALARM` versus `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` (and its restrictions since Android 14), `RECEIVE_BOOT_COMPLETED`, battery-optimisation exemption.
- What happens to scheduled alarms on reboot, on app update, on force stop, and when the clock or time zone changes, and which broadcasts let the app reschedule.
- How a full-screen alarm is shown over the lock screen, how sound keeps playing until dismissed, and what foreground-service type (if any) that requires on API 36.
- Notification channel settings that matter (importance, sound, bypassing Do Not Disturb, alarm audio stream).
- Any limit on the number of scheduled alarms, and the standard pattern for repeating tasks (schedule only the next occurrence, or all).

End with the recommended mechanism for each delivery style and the full list of permissions the app must hold.

## Answer

Recorded by the charting session from the research agent's committed findings, with the owner's go-ahead (the agent could not write outside its worktree).

- Android 16 itself changes nothing for exact alarms or full-screen intents; the rules that bind are from Android 12 to 15 (exact-alarm permissions, mandatory foreground service types, full-screen intent limits, Force stop cancelling pending intents).
- Use `USE_EXACT_ALARM`, not `SCHEDULE_EXACT_ALARM`: it is granted at install and cannot be revoked, and its only restriction is a Google Play policy that does not apply to a sideloaded app. No grant needed here is blocked by Android's "restricted settings" for sideloaded apps.
- Scheduled wake-ups survive sleep, Doze, a swipe from recents and an app update. A reboot clears them all; reschedule from the task database on `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and app start, and fire late anything that came due meanwhile.
- Stock Android cannot guarantee firing after Force stop (all wake-ups and pending intents are removed until the user opens the app), while powered off, before the app's first launch, or before the first unlock after a reboot unless the firing path is direct-boot aware.
- A full-screen alarm takes over only when the phone is locked or the screen is off; while the phone is in use the system shows a persistent heads-up banner instead. The ring is unaffected because a foreground service plays it.
- Channel settings are fixed at creation: one `IMPORTANCE_HIGH` channel per delivery style. With default Do Not Disturb settings the alarm style rings through (alarm category, alarm audio stream) and the notification style is silenced.
- Limits: 500 scheduled wake-ups per app (AOSP source, not in the docs). Notification-style fires in Doze are capped at 72 per hour in AOSP unless the app is exempt from battery optimisation. Repeating tasks schedule only the next occurrence and reschedule when it fires.
- Recommended mechanism, notification delivery style: `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` to a manifest `BroadcastReceiver` that calls `notify()` on a channel with sound; no service. Fallback if fires are late: battery-optimisation exemption, then `setAlarmClock`.
- Recommended mechanism, alarm delivery style: `AlarmManager.setAlarmClock(...)` to a manifest `BroadcastReceiver` that starts a foreground service of type `systemExempted`; the service posts an ongoing `CATEGORY_ALARM` notification with `setFullScreenIntent` and loops sound with `USAGE_ALARM` until dismissed; the alarm activity uses `setShowWhenLocked` and `setTurnScreenOn`. Fallback service type: `mediaPlayback`.
- Full permission list: `USE_EXACT_ALARM`, `POST_NOTIFICATIONS` (runtime prompt), `USE_FULL_SCREEN_INTENT` (install time, verify with `canUseFullScreenIntent()`), `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `WAKE_LOCK`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the exemption itself is granted by the user).

Unverified by documentation; the reliability prototype must settle these on the phone:

- `canUseFullScreenIntent()` returns true right after sideloading.
- The full-screen activity launches from a locked, sleeping phone with no extra background-activity-launch opt-in.
- `startForeground()` with type `systemExempted` succeeds with only `USE_EXACT_ALARM`.
- How `setExactAndAllowWhileIdle` behaves when the user sets battery to "Restricted".
- Whether notification cooldown is active and softens notification-style sound.

Findings: branch `research/android-16-alarm-apis` (commit `2dece2b`), file `docs/research/android-16-alarm-apis.md`. Read it with `git show research/android-16-alarm-apis:docs/research/android-16-alarm-apis.md`.
