# Every fire is scheduled with the alarm-clock call

Lead-time warnings, notifications and alarms are all scheduled with `AlarmManager.setAlarmClock`, not only alarms. Android's documentation would have notifications use `setExactAndAllowWhileIdle`, but the owner's Tecno Camon 40 (HiOS 16.3) freezes an app five seconds after it leaves the screen, and on 2026-11-17 the throwaway FireTest prototype showed the freezer waking the app for an alarm-clock entry (`reason=alarmClock`, 67 ms late) while the exact-alarm entry was absent from `dumpsys alarm` for as long as the app was frozen. Firing after swipe-away, reboot and sleep is a hard requirement, so the app uses the one call seen to pass the freezer.

## Considered options

- `setExactAndAllowWhileIdle` for notifications, as ticket 03 recommended: not shown to arrive on time from a frozen app on this phone.
- Relying on HiOS settings (Auto-start, battery exemption) to keep the app unfrozen: untested, and depends on the owner's settings surviving system updates.

## Consequences

- The status bar shows an alarm-clock icon whenever any task is pending, and the lock screen's "next alarm" shows the next task. The owner accepted this.
- If a later test shows the exact-alarm call is reliable from a frozen app, this can be reversed for notifications without touching stored data.
