# Android 16 scheduling and firing APIs for Remind Me

Research for ticket `.scratch/remind-me/issues/03-android-16-alarm-apis.md`.
Scope: stock Android platform rules for an app that targets API 36 and is
sideloaded. Manufacturer behaviour (Tecno HiOS) is a separate ticket.

Researched 2026-11-17 against developer.android.com and the AOSP
`android16-release` branch. Every claim carries a source link; the source list
is at the end. Anything I could not confirm from a primary source is marked
**unverified**.

Vocabulary: "notification" and "alarm" are the two delivery styles from
`GLOSSARY.md`. Android's own scheduler is also called an alarm
(`AlarmManager`), so this document writes "AlarmManager alarm" or "scheduled
wake-up" for the platform object and keeps plain "alarm" for the delivery
style.

## Short answer

| | Notification delivery style | Alarm delivery style |
|---|---|---|
| Scheduling call | `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, t, pendingIntent)` | `AlarmManager.setAlarmClock(AlarmClockInfo(t, showIntent), pendingIntent)` |
| Target of the PendingIntent | Manifest `BroadcastReceiver` | Manifest `BroadcastReceiver` that starts a foreground service |
| What fires | `NotificationManager.notify()` on a channel that has a sound | Foreground service (type `systemExempted`) posts an ongoing `CATEGORY_ALARM` notification with a full-screen intent and loops sound on the alarm audio stream until dismissed |
| Doze | Fires in Doze, rate limited | Fires at the exact time; the system leaves Doze for it |

Permissions to declare: `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`,
`USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `WAKE_LOCK`, `VIBRATE`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Details and grant paths are in
section 2; the full recommendation is in section 8.

What stock Android does not guarantee (section 7): nothing fires after a
Force stop until the user opens the app again, nothing fires while the phone
is powered off, and a full-screen takeover only happens when the phone is
locked or the screen is off.

## 1. Which scheduling call, and what Doze and App Standby allow

### The three exact calls

The scheduling guide ranks the exact calls by how hard they push through
battery saving [schedule]:

- `setExact()`: fires at a nearly precise time "as long as other
  battery-saving measures aren't in effect". Doze defers it to the next
  maintenance window [doze]. Not usable for this app.
- `setExactAndAllowWhileIdle()`: fires at a nearly precise time "even if
  battery-saving measures are in effect" [schedule].
- `setAlarmClock()`: fires at a precise time; "the system never adjusts their
  delivery time. The system identifies these alarms as the most critical ones
  and leaves low-power modes if necessary" [schedule]. The Doze guide adds:
  "Alarms set with setAlarmClock() continue to fire normally. The system exits
  Doze shortly before those alarms fire" [doze].

All AlarmManager alarms "operate outside of your application" and can fire
"even when your app is not running, and even if the device itself is asleep"
[schedule]. They are "retained while the device is asleep" but "will be
cleared if it is turned off and rebooted" [alarmmgr]. The system holds a CPU
wake lock for the duration of the receiving `BroadcastReceiver.onReceive()`
[alarmmgr].

`setRepeating()` and `setInexactRepeating()` are inexact since API 19 and must
not be used for a repeat rule; the reference says an app that needs precise
repeats "must use one-time exact alarms, rescheduling each time" [alarmmgr].

Listener-based alarms (`OnAlarmListener` overloads) "may be canceled by the
Android system whenever the calling process no longer has any components
running", so only the `PendingIntent` overloads survive the process being
killed [alarmmgr]. AOSP confirms this with the compat change
`EXACT_LISTENER_ALARMS_DROPPED_ON_CACHED` [AlarmManager.java]. The app must
use `PendingIntent` overloads.

### What `setAlarmClock` guarantees

- Implies `RTC_WAKEUP`, is allowed in Doze, and its trigger time is shown to
  the user as the next alarm [alarmmgr].
- In AOSP it is scheduled with `FLAG_WAKE_FROM_IDLE` and a zero-length window,
  is exempt from App Standby bucket quotas (`isExemptFromAppStandby`), from
  Battery Saver deferral (`isExemptFromBatterySaver`), and from the user's
  "Restricted" battery setting ("Don't defer alarm clocks" in
  `isBackgroundRestricted`) [AlarmManagerService.java].
- "Alarms scheduled via this API will be allowed to start a foreground service
  even if the app is in the background" [alarmmgr].

### What `setExactAndAllowWhileIdle` guarantees

- Fires in Doze. On firing, the app is put on a temporary power exemption list
  for about 10 seconds [alarmmgr]; AOSP: `ALLOW_WHILE_IDLE_ALLOWLIST_DURATION`
  = 10 s [AlarmManagerService.java].
- It is rate limited while the device is idle. The documentation is
  inconsistent about the rate: "no more than once per nine minutes, per app"
  [doze]; "about every minute" normally and "significantly longer, such as 15
  minutes" in low-power idle [alarmmgr]; "7 per hour" [powerdet]. The
  Android 16 source is the tie-breaker: 72 per rolling hour for an app that
  targets Android 12+ and holds an exact-alarm permission
  (`DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72`, `ALLOW_WHILE_IDLE_WINDOW = 1 hour`),
  and 7 per hour for the compatibility case
  [AlarmManagerService.java]. An OEM may change these constants (HiOS ticket).
- It is exempt from App Standby bucket quotas, because the exemption test
  includes the allow-while-idle flags [AlarmManagerService.java]. In Battery
  Saver it is still governed by the allow-while-idle quota
  [AlarmManagerService.java].
- It is not exempt from the user's "Restricted" battery setting. The
  Android 13 notes say that in that state "Alarms aren't triggered" and the app
  "Can't launch foreground services" [a13all]. In AOSP the deferral goes
  through `AppStateTrackerImpl.areAlarmsRestricted`, which returns false for
  power-exempt apps [AppStateTrackerImpl.java]. Which branch of that method is
  active on the target phone is **unverified**.
- Also allowed to start a foreground service from the background [alarmmgr].

### Effect of the battery-optimisation exemption

If the user puts the app on the battery-optimisation exemption list, AOSP
marks every AlarmManager alarm from that app
`FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED`: "allow these alarms to go off as normal
even while idle, with no timing restrictions" [AlarmManagerService.java]. The
app is then also exempt from App Standby bucket restrictions [standby] and may
start foreground services from the background [fgsbg]. This removes the
allow-while-idle quota as a concern for the notification delivery style.

### Other limits

- At most 500 concurrent AlarmManager alarms per app
  (`DEFAULT_MAX_ALARMS_PER_UID = 500`; exceeding it throws
  `IllegalStateException`) [AlarmManagerService.java]. Not documented on
  developer.android.com.
- A trigger time less than 5 seconds in the future is pushed out to 5 seconds
  (`DEFAULT_MIN_FUTURITY`) [AlarmManagerService.java].
- Exact AlarmManager alarms "aren't affected by foreground service launch
  restrictions" [schedule].

### What changed in Android 14, 15 and 16

- Android 14: `SCHEDULE_EXACT_ALARM` is denied by default for newly installed
  apps that target 33+ [a14all]; foreground service types became mandatory
  for target 34+ [fgstypes]; full-screen intents were restricted [a14t].
  `AlarmManager.cancelAll()` was added in API 34 [alarmmgr].
- Android 15: Force stop now cancels all of the app's pending intents and the
  stopped state lasts until the user launches the app [a15all]; apps
  targeting 35+ cannot start certain foreground service types from
  `BOOT_COMPLETED` and must be the top app or run a foreground service to
  request audio focus [a15t].
- Android 16: neither behaviour-changes page mentions AlarmManager, exact
  alarms or full-screen intents [a16all] [a16t]. The related changes are
  JobScheduler quota tightening, which is one more reason not to use
  WorkManager or JobScheduler for firing [a16all].
- Looking ahead, Android 17 hardens background audio but exempts apps holding
  an exact-alarm permission that play `USAGE_ALARM` audio from a foreground
  service [a17all]. The recommended design already has that shape.

## 2. Permissions and how each is granted to a sideloaded app

### Exact alarms: `USE_EXACT_ALARM`, not `SCHEDULE_EXACT_ALARM`

| | `USE_EXACT_ALARM` | `SCHEDULE_EXACT_ALARM` |
|---|---|---|
| Protection level | `normal` [CoreManifest] | `signature\|privileged\|appop` [CoreManifest] |
| Grant | Automatic at install [schedule] | By the user in Settings, "Alarms & reminders"; denied by default on a fresh install targeting 33+ [schedule] [a14all] |
| Revocable | No [schedule] | Yes; on revocation "your app stops, and all future exact alarms are canceled" [schedule] |
| Restriction | Google Play policy limits it to alarm, timer and calendar apps [schedule] [manperm] | None |

Use `USE_EXACT_ALARM`. The only cost is a Google Play policy, which does not
apply to an app that is never published there; in exchange nothing can be
revoked from under the app and no settings trip is needed. The reference says
to request only one of the two [manperm]. A holder of `USE_EXACT_ALARM` also
stays in the working-set standby bucket or better [manperm] and is exempt
from the restricted bucket [standby].

Without either permission, an exact call throws `SecurityException` unless the
app is on the battery-optimisation exemption list
[AlarmManagerService.java]. `AlarmManager.canScheduleExactAlarms()` reports
the state [alarmmgr].

If `SCHEDULE_EXACT_ALARM` were used instead, the app would have to send the
user to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` [settingsref] and
listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, which is
sent only when the permission is granted [alarmmgr].

### `POST_NOTIFICATIONS`

A runtime ("dangerous") permission since Android 13, off by default on a fresh
install; the app must show the system prompt [notifperm] [CoreManifest]. It
is the same prompt for a sideloaded app. The user can revoke it later in
Settings. A foreground service can still start without it, but its
notification is then only visible in the Task Manager [notifperm], which
would leave the alarm delivery style with sound and no visible way to dismiss
other than the full-screen activity. Treat a missing grant as a blocking
set-up problem and check `NotificationManager.areNotificationsEnabled()` on
every app start.

### `USE_FULL_SCREEN_INTENT`

- Declared as `normal` in the reference [manperm]; in the Android 16 platform
  manifest it is `normal|appop` [CoreManifest], so it is granted at install
  but the user can switch it off.
- Android 14 restriction: for apps targeting 34+, full-screen intents are
  meant for calling and alarm apps only, and "The Google Play Store revokes
  default USE_FULL_SCREEN_INTENT permissions for any apps that don't fit this
  profile" [a14t]. The revocation is done by the Play Store, not by the
  platform. A sideloaded app is therefore expected to keep the install-time
  grant. This is an inference from the wording and from AOSP, where the app-op
  is created with the default mode and the check is a plain permission check
  [AppOpInfo.java] [NotificationManagerService.java]; confirm on the phone
  with the call below. **Unverified on the device.**
- Check with `NotificationManager.canUseFullScreenIntent()`; if false, send
  the user to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` with a
  `package:` data URI [a14t] [notifmgr].
- If the permission is missing, the system strips the full-screen intent and
  shows a heads-up notification instead [notifbuilder]
  [NotificationManagerService.java].

### `RECEIVE_BOOT_COMPLETED`

Normal permission, granted at install [manperm]. Needed to receive
`ACTION_BOOT_COMPLETED` [schedule]. The broadcast is only delivered once the
user has launched the app at least once [schedule].

### Foreground service permissions

`FOREGROUND_SERVICE` plus the type-specific permission
`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`; both are normal, install-time
permissions [CoreManifest] [fgstypes]. Section 5 explains the type choice.

### Battery-optimisation exemption

Not a manifest permission in itself; it is a per-app list the user controls
[doze]. To ask with a one-tap system dialog, declare
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (normal) and fire
`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with a `package:` URI;
check with `PowerManager.isIgnoringBatteryOptimizations()` [doze]
[settingsref]. The restriction on that intent is a Google Play policy [doze],
so it does not bind a sideloaded app. Strictly optional on stock Android when
`setAlarmClock` is used, but recommended: it lifts the allow-while-idle quota
for the notification delivery style and keeps the app out of bucket
restrictions (section 1).

### `WAKE_LOCK`, `VIBRATE`

Normal permissions [CoreManifest]. `WAKE_LOCK` lets the receiver and the
ringing service hold a partial wake lock, as DeskClock does [AlarmService.kt]
[AlarmAlertWakeLock.kt]. `VIBRATE` is needed to vibrate during an alarm.

### Sideloading and "restricted settings"

Android blocks some settings for sideloaded apps until the user explicitly
allows them. In Android 16 the protected list covers SMS permissions, device
admin, accessibility, notification-listener access, `SYSTEM_ALERT_WINDOW`,
usage stats and the dialer and SMS roles [ECM]. It does not include exact
alarms, full-screen intents, notifications or battery optimisation, so none of
the grants above is blocked by sideloading.

### Not needed

`SCHEDULE_EXACT_ALARM` (replaced by `USE_EXACT_ALARM`), `SYSTEM_ALERT_WINDOW`
(a restricted setting for sideloaded apps [ECM], and the full-screen intent
makes it unnecessary), `TURN_SCREEN_ON` (signature/privileged/appop
[CoreManifest]; the activity attribute is enough), `DISABLE_KEYGUARD`.
`ACCESS_NOTIFICATION_POLICY` is only needed if the app itself should mark a
channel as bypassing Do Not Disturb (section 6).

## 3. Reboot, app update, Force stop, clock and time-zone changes

| Event | What happens to scheduled wake-ups | How the app recovers |
|---|---|---|
| Reboot | All cleared [alarmmgr] [schedule] | Reschedule on `ACTION_BOOT_COMPLETED` and `ACTION_LOCKED_BOOT_COMPLETED` |
| App update (reinstall of a new APK) | Kept: AOSP comments "This package is being updated; don't kill its alarms" [AlarmManagerService.java]. Posted notifications are cancelled [AlarmInitReceiver.kt] | Reschedule on `ACTION_MY_PACKAGE_REPLACED` anyway |
| Force stop | All removed, and all the app's pending intents cancelled [a15all] [AlarmManagerService.java] | Nothing until the user opens the app; then `ACTION_BOOT_COMPLETED` is delivered [a15all] |
| Clock set by user or network | The system re-evaluates RTC alarms against the new clock and broadcasts `ACTION_TIME_CHANGED` [AlarmManagerService.java] | Reschedule; also catch up on anything now in the past |
| Time-zone change | RTC triggers are UTC instants and do not move; `ACTION_TIMEZONE_CHANGED` is broadcast [AlarmManagerService.java] [intentref] | Recompute every due time that is defined in local wall-clock time, then reschedule |
| Swipe from recents | Nothing is removed (see below) | None needed |
| Power off | Nothing fires while off | On boot, detect due times that passed and fire them late |

Details:

- **Boot.** `ACTION_LOCKED_BOOT_COMPLETED` is sent before the user unlocks the
  phone for the first time after a reboot; to receive it the receiver must be
  `directBootAware`, and until the first unlock only device-protected storage
  is readable [directboot] [intentref]. The direct-boot guide names alarm
  clock apps as a use case [directboot]. `ACTION_BOOT_COMPLETED` arrives
  after the first unlock. If tasks are stored in normal (credential-protected)
  storage and nothing is direct-boot aware, a task that comes due between a
  reboot and the first unlock fires late, at unlock. Supporting firing before
  first unlock means keeping the scheduling data in device-protected storage
  and marking the receiver, service and alarm activity `directBootAware`, as
  DeskClock does [DeskClockManifest].
- **Broadcast delivery.** `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`,
  `TIME_SET`, `TIMEZONE_CHANGED` and `LOCALE_CHANGED` are on the list of
  implicit broadcasts that manifest receivers may still receive [bcastex].
  `MY_PACKAGE_REPLACED` is sent explicitly to the updated app [intentref].
- **Force stop.** Android 15 changed the stopped state: an app stays stopped
  until the user directly launches or interacts with it, and "the system also
  cancels all pending intents when the app enters the stopped state". When
  the app leaves the stopped state it receives `ACTION_BOOT_COMPLETED`, so the
  boot receiver doubles as the recovery path.
  `ApplicationStartInfo.wasForceStopped()` tells the app it happened [a15all].
  In AOSP the alarm service removes all of the package's AlarmManager alarms
  on `ACTION_PACKAGE_RESTARTED` [AlarmManagerService.java].
- **Swipe from recents.** On AOSP, removing a task from recents kills the
  task's processes unless they have another visible task or a foreground
  service [ActivityTaskSupervisor.java]. It is a process kill, not a Force
  stop: the package does not enter the stopped state, which the Android 15
  notes tie to the Force stop action [a15all], and AlarmManager alarms are
  held by the system, not by the app process [schedule]. So on stock Android a
  swipe loses nothing, and a ringing alarm survives the swipe because its
  process has a foreground service. Some OEM builds turn the swipe into a
  Force stop; that belongs to the HiOS ticket.
- **Reference implementation.** DeskClock's `AlarmInitReceiver` handles
  `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `LOCALE_CHANGED`,
  `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED` in one receiver,
  using `goAsync()` plus a partial wake lock, and calls one routine that fixes
  every alarm instance [AlarmInitReceiver.kt] [AlarmStateManager.kt].
- **Foreground services at boot.** Apps targeting 35+ cannot start
  `mediaPlayback`, `dataSync`, `camera`, `phoneCall`, `mediaProjection` or
  `microphone` foreground services from a `BOOT_COMPLETED` receiver [a15t].
  `systemExempted` is not on that list. Rescheduling at boot does not need a
  foreground service in any case.

## 4. Showing a full-screen alarm over the lock screen

The supported route is a notification carrying a full-screen intent
[timesens] [notifbuilder]:

1. Post a notification on a channel with `IMPORTANCE_HIGH`; a full-screen
   intent "must also be posted to a channel with importance level set to
   IMPORTANCE_HIGH or higher" [notifbuilder].
2. Call `setFullScreenIntent(activityPendingIntent, true)`; requires
   `USE_FULL_SCREEN_INTENT` [notifbuilder].
3. The alarm activity calls `setShowWhenLocked(true)` and
   `setTurnScreenOn(true)` (or sets the equivalent manifest attributes) so it
   draws over the keyguard and wakes the screen [activityref]. DeskClock adds
   `FLAG_KEEP_SCREEN_ON`, `excludeFromRecents` and an empty `taskAffinity`
   [AlarmActivity.kt] [DeskClockManifest].

When the activity actually launches is decided by SystemUI. In Android 16 the
full-screen intent launches when the device is not interactive (screen off),
is dreaming, or the keyguard is showing; when the phone is unlocked and in
use the decision is "expected to heads-up instead"
[FullScreenIntentDecisionProvider.kt]. The reference states the same: "the
system UI will display a heads up notification, instead of launching this
intent, while the user is using the device", and with the permission that
heads-up stays until the user dismisses or snoozes it [notifbuilder]. Do Not
Disturb can also suppress the full-screen intent
[FullScreenIntentDecisionProvider.kt].

Consequences for the design:

- While the phone is in use, the alarm delivery style appears as a persistent
  heads-up banner with its actions, not a full-screen takeover. The sound
  still rings because it comes from the service, not from the activity.
- Do not start the activity directly from the receiver or service. Background
  activity starts are blocked [bgstarts], and Android 12 forbids notification
  trampolines [a12t]. The full-screen intent is the exception path the
  platform provides.
- Apps targeting 35+ must opt in before another app may use their
  background-activity-launch privileges through a `PendingIntent` [bgstarts].
  Whether that opt-in is ever needed for a full-screen-intent `PendingIntent`
  is **unverified**; SystemUI launches it under its own privileges, and the
  prototype should confirm the activity appears from a locked, sleeping phone.
- All `PendingIntent`s must declare mutability; use `FLAG_IMMUTABLE` [a12t].

## 5. Keeping the sound playing until dismissed, and the service type

A notification's own sound plays once (it loops only with `FLAG_INSISTENT`,
and then stops as soon as the shade is opened) [notifref]. For a ring that
continues until the user acts, the time-sensitive guide says to tie the
notification to a foreground service [timesens]. DeskClock does exactly this:
the scheduled `PendingIntent` targets `AlarmService`, which takes a partial
wake lock, calls `startForeground()` with the alarm notification, and starts
`AlarmKlaxon` [AlarmService.kt] [AlarmNotifications.kt].

Audio, following DeskClock [AsyncRingtonePlayer.kt] [AlarmKlaxon.kt]:

- `AudioAttributes` with `USAGE_ALARM` and `CONTENT_TYPE_SONIFICATION`, so the
  sound plays on the alarm volume stream, independent of the ring and
  notification volumes [audioattr].
- Looping playback (`isLooping = true`) until the service is told to stop.
- Transient audio focus on the alarm stream. On target 35+ an app "must be the
  top app or running a foreground service in order to request audio focus"
  [a15t] [audiofocus], so request focus after `startForeground()`.
- Vibration with alarm usage attributes.

The notification the service posts: ongoing, `CATEGORY_ALARM`, public
visibility, the full-screen intent, and Dismiss and Snooze actions
[AlarmNotifications.kt]. Since Android 13 and 14 the user can swipe away
foreground-service and ongoing notifications except while the phone is locked
[a13all] [a14all]; set a delete intent and treat the swipe as a dismiss so
the ring never continues with nothing on screen.

### Starting the service from the background

Starting a foreground service from the background is restricted since
Android 12, with an exemption when "Your app invokes an exact alarm to
complete an action that the user requests" [fgsbg]; both exact calls carry
that allowance [alarmmgr]. The battery-optimisation exemption is a second,
independent exemption [fgsbg].

### Foreground service type on API 36

A type is mandatory for apps targeting 34+ [fgstypes]. Options:

- **`systemExempted` (recommended).** The documentation reserves it for,
  among others, "Apps holding SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM
  permission" that use a foreground service to continue alarms in the
  background; it requires `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` [fgstypes]
  [serviceinfo]. AOSP enforces exactly that pair: the type permission plus
  any one of `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` or VPN activation
  [ForegroundServiceTypePolicy.java]. It has no time limit and is not on the
  list of types blocked at `BOOT_COMPLETED` [a15t].
- **`mediaPlayback` (fallback).** Requires only
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and no runtime prerequisite [fgstypes],
  but cannot be started from `BOOT_COMPLETED` on target 35+ [a15t].
- `shortService` times out after about three minutes and `specialUse` exists
  for cases no other type covers [fgstypes]; neither fits better.

DeskClock is not a guide here: on `android16-release` it still targets SDK 30
and declares no service type [DeskClockManifest]. Which type the Google Clock
app uses is **unverified**.

## 6. Notification channels

Channel properties are fixed when the channel is first created. After that the
app can only change the name and description; importance, sound and the rest
belong to the user [channels] [notifchan]. Changing them later means creating
a channel with a new id. Create two channels, one per delivery style.

| Setting | Notification delivery style | Alarm delivery style |
|---|---|---|
| Importance | `IMPORTANCE_HIGH` (sound and heads-up) or `IMPORTANCE_DEFAULT` (sound only) [channels] | `IMPORTANCE_HIGH`, required for the full-screen intent [notifbuilder] |
| Channel sound | Default notification sound, or `setSound(uri, attrs)` before creation [notifchan] | None (`setSound(null, null)`); the service plays the ring |
| Audio usage | `USAGE_NOTIFICATION_EVENT` [audioattr] | `USAGE_ALARM` on the service's player [audioattr] |
| Notification category | `CATEGORY_REMINDER` [notifref] | `CATEGORY_ALARM` [notifref] |
| Vibration | Channel default | Driven by the service |

Notes:

- **Who plays the notification sound.** The system plays a channel's sound
  through its own ringtone player, using the channel's audio attributes
  [NotificationAttentionHelper.java]. The app process does not need to stay
  alive after `notify()`, which is why the notification delivery style needs
  no service.
- **Volume.** The system skips the sound when the volume of the stream that
  matches the channel's audio attributes is zero
  [NotificationAttentionHelper.java]. A notification-style task is therefore
  silent when the phone is on silent or vibrate. The alarm delivery style uses
  the alarm stream, which has its own volume; if that is at zero the ring is
  silent too.
- **Do Not Disturb.** The system treats a notification as an alarm when it has
  `CATEGORY_ALARM` or its audio attributes use `USAGE_ALARM`
  [ZenModeFiltering.java]. Alarms are allowed by the default DND policy
  (`DEFAULT_ALLOW_ALARMS = true`) while reminders are not
  (`DEFAULT_ALLOW_REMINDERS = false`) [ZenModeConfig.java]. In "alarms only"
  mode only alarms get through, and in total silence nothing does
  [ZenModeFiltering.java] [notifmgr]. If the user disallows alarms in their
  DND policy, the alarm stream itself is muted [ZenPolicy.java]. So with
  default settings the alarm delivery style rings through DND and the
  notification delivery style is silenced by it.
- **Bypass DND on a channel.** `NotificationChannel.setBypassDnd(true)` only
  takes effect for an app that has been granted Do Not Disturb access, or when
  the user sets it in the channel's settings [notifchan]. The app can deep-link
  to the channel with `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`
  [channels]. Asking for DND access
  (`ACCESS_NOTIFICATION_POLICY` plus
  `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) is optional and not
  recommended for v1; apps targeting 35+ can no longer change the global DND
  state anyway [a15t].
- **Notification cooldown.** Android 16's notification service contains a
  cooldown that lowers the alert volume of notifications that arrive in quick
  succession; notifications with `CATEGORY_ALARM` are exempt
  [NotificationAttentionHelper.java]. It is controlled by feature flags and a
  user setting, so whether it is active on the target phone is **unverified**.
  It could soften a notification-style task that fires right after other
  notifications; it cannot affect the alarm delivery style, whose sound does
  not come from the notification.

## 7. Limits, the repeat pattern, and what cannot be guaranteed

### Standard pattern for tasks that repeat

- Never use `setRepeating`. Schedule one exact AlarmManager alarm for the next
  occurrence only, and when it fires compute and schedule the following one
  [alarmmgr]. DeskClock does this for every alarm [AlarmStateManager.kt].
- The database of tasks is the source of truth; the AlarmManager entries are a
  disposable projection of it. One idempotent "schedule everything from the
  database" routine runs on: app start, `BOOT_COMPLETED`,
  `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED`, and after every task edit (section 3).
- That routine also catches up: any fire time that is in the past and not
  recorded as fired is fired immediately, which covers power-off and Force
  stop.
- A task holds at most two scheduled wake-ups at a time (the lead-time fire
  and the due-time fire), each for its next occurrence. With the 500 limit
  that allows about 250 active tasks; if more are ever needed, schedule only
  the earliest N and re-run the routine on each fire.
- Each wake-up needs a distinct `PendingIntent`. Identity is
  `Intent.filterEquals` (action, data, type, component, categories; extras do
  not count) plus the request code, and scheduling with an equal
  `PendingIntent` replaces the earlier one [alarmmgr] [schedule]. Use a data
  URI such as `remindme://fire/<taskId>/<lead|due>`; that also makes
  cancellation exact.
- When a repeat rule is defined in local wall-clock time, store the rule and
  recompute the UTC instant on every reschedule, so daylight-saving and
  time-zone changes resolve correctly.

### What stock Android cannot guarantee

Measured against the hard requirement (fire while asleep, after a reboot,
after a swipe from recents):

- **Asleep, Doze, swiped from recents, app updated:** met on stock Android by
  the mechanisms above.
- **After a reboot:** met once the boot receiver has run. Not met for the
  interval between reboot and first unlock unless the firing path is
  direct-boot aware (section 3). Not met at all if the user has never opened
  the app since install [schedule].
- **Force stop:** not met. Every scheduled wake-up and pending intent is
  removed and nothing runs until the user opens the app [a15all]. No API
  works around this. The app can only detect it afterwards and report missed
  tasks.
- **Phone powered off or battery dead:** nothing fires. Stock Android has no
  public power-on alarm API for third-party apps (**unverified**; I found no
  such API in the AlarmManager reference [alarmmgr]). Missed fires are caught
  up at boot.
- **Full-screen takeover while the phone is in use:** not provided; a
  persistent heads-up appears instead (section 4).
- **User-controlled switches that defeat firing:** notifications disabled for
  the app or a channel; full-screen intents switched off; battery set to
  "Restricted" (stops the notification delivery style; `setAlarmClock` is
  exempt in AOSP); alarm volume at zero; total-silence DND; alarms disallowed
  in DND. The app can detect most of these and warn, but cannot override them.
- **Many notification-style fires close together in Doze:** bounded by the
  allow-while-idle quota (72 per hour in AOSP) unless the app is exempt from
  battery optimisation (section 1).

## 8. Recommendation

### Alarm delivery style

1. Schedule with
   `AlarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), firePendingIntent)`,
   where `showIntent` opens the task in the app and `firePendingIntent` is an
   immutable broadcast `PendingIntent` unique to the task and fire kind.
2. The manifest `BroadcastReceiver` calls `startForegroundService()` on the
   ringing service (permitted because the trigger is an exact AlarmManager
   alarm).
3. The service, declared with `android:foregroundServiceType="systemExempted"`,
   calls `startForeground()` with a notification on the `IMPORTANCE_HIGH`
   alarm channel: ongoing, `CATEGORY_ALARM`, public visibility,
   `setFullScreenIntent(alarmActivityPendingIntent, true)`, Dismiss and
   Snooze actions, and a delete intent that dismisses.
4. The service holds a partial wake lock, requests transient audio focus and
   loops the sound with `USAGE_ALARM` until Dismiss or Snooze.
5. The alarm activity uses `setShowWhenLocked(true)` and
   `setTurnScreenOn(true)`.
6. Fallback if `systemExempted` misbehaves in the prototype: `mediaPlayback`
   with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

Side effect to accept: `setAlarmClock` shows the alarm icon and the next
alarm time in the system UI [alarmmgr].

### Notification delivery style

1. Schedule with
   `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, firePendingIntent)`.
2. The manifest `BroadcastReceiver` posts the notification with
   `NotificationManager.notify()` on the notification channel
   (`IMPORTANCE_HIGH` for sound plus heads-up), `CATEGORY_REMINDER`, with a
   content intent that opens the task. No service.
3. In the same `onReceive`, schedule the task's next fire.
4. Escalation, if the HiOS ticket or testing shows late or missed fires: ask
   for the battery-optimisation exemption first; if that is not enough,
   schedule this style with `setAlarmClock` as well, at the cost of the
   status-bar alarm icon appearing for every task.

### Shared

- One reschedule-everything routine wired to `BOOT_COMPLETED`,
  `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED` and app start.
- A set-up check screen that verifies, and links to the fix for, each of:
  `areNotificationsEnabled()`, `canUseFullScreenIntent()`,
  `canScheduleExactAlarms()`, `isIgnoringBatteryOptimizations()`.
- Test with `adb shell dumpsys deviceidle force-idle` and
  `adb shell am set-inactive <package> true` [doze], and inspect
  `adb shell dumpsys alarm`.

### Full permission list

Declare in the manifest:

| Permission | Why | How it is granted |
|---|---|---|
| `android.permission.USE_EXACT_ALARM` | Exact scheduling; prerequisite for the `systemExempted` service type | Install time, not revocable |
| `android.permission.POST_NOTIFICATIONS` | Any notification, including the alarm's | Runtime prompt; user can revoke |
| `android.permission.USE_FULL_SCREEN_INTENT` | Full-screen alarm over the lock screen | Install time for a sideloaded app (verify with `canUseFullScreenIntent()`); user can switch off |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule after reboot and after leaving the stopped state | Install time |
| `android.permission.FOREGROUND_SERVICE` | Ringing service | Install time |
| `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Service type `systemExempted` | Install time |
| `android.permission.WAKE_LOCK` | Keep the CPU awake while ringing and rescheduling | Install time |
| `android.permission.VIBRATE` | Vibrate during an alarm | Install time |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets the app show the one-tap exemption dialog | Install time; the exemption itself is the user's choice |

Optional: `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` (only if the
fallback service type is used) and
`android.permission.ACCESS_NOTIFICATION_POLICY` (only if the app should set
bypass-DND itself). Do not declare `SCHEDULE_EXACT_ALARM`,
`SYSTEM_ALERT_WINDOW`, `TURN_SCREEN_ON` or `DISABLE_KEYGUARD`.

## Unverified items to settle in a prototype on the phone

- `canUseFullScreenIntent()` returns true right after sideloading.
- The full-screen activity launches from a locked, sleeping phone with no
  extra background-activity-launch opt-in.
- `startForeground()` with type `systemExempted` succeeds when the app holds
  `USE_EXACT_ALARM` only.
- Behaviour of `setExactAndAllowWhileIdle` when the user sets the app's
  battery usage to "Restricted".
- Whether notification cooldown is active and audibly affects
  notification-style tasks.
- Which foreground service type Google Clock uses (reference only).

## Sources

developer.android.com:

- [schedule] https://developer.android.com/develop/background-work/services/alarms/schedule
- [doze] https://developer.android.com/training/monitoring-device-state/doze-standby
- [alarmmgr] https://developer.android.com/reference/android/app/AlarmManager
- [powerdet] https://developer.android.com/topic/performance/power/power-details
- [standby] https://developer.android.com/topic/performance/appstandby
- [manperm] https://developer.android.com/reference/android/Manifest.permission
- [settingsref] https://developer.android.com/reference/android/provider/Settings
- [notifperm] https://developer.android.com/develop/ui/views/notifications/notification-permission
- [timesens] https://developer.android.com/develop/ui/views/notifications/time-sensitive
- [channels] https://developer.android.com/develop/ui/views/notifications/channels
- [notifchan] https://developer.android.com/reference/android/app/NotificationChannel
- [notifmgr] https://developer.android.com/reference/android/app/NotificationManager
- [notifref] https://developer.android.com/reference/android/app/Notification
- [notifbuilder] https://developer.android.com/reference/android/app/Notification.Builder
- [activityref] https://developer.android.com/reference/android/app/Activity
- [intentref] https://developer.android.com/reference/android/content/Intent
- [bcastex] https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- [directboot] https://developer.android.com/privacy-and-security/direct-boot
- [bgstarts] https://developer.android.com/guide/components/activities/background-starts
- [fgstypes] https://developer.android.com/develop/background-work/services/fgs/service-types
- [fgsbg] https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- [serviceinfo] https://developer.android.com/reference/android/content/pm/ServiceInfo
- [audioattr] https://developer.android.com/reference/android/media/AudioAttributes
- [audiofocus] https://developer.android.com/media/optimize/audio-focus
- [a12t] https://developer.android.com/about/versions/12/behavior-changes-12
- [a13all] https://developer.android.com/about/versions/13/behavior-changes-all
- [a14all] https://developer.android.com/about/versions/14/behavior-changes-all
- [a14t] https://developer.android.com/about/versions/14/behavior-changes-14
- [a15all] https://developer.android.com/about/versions/15/behavior-changes-all
- [a15t] https://developer.android.com/about/versions/15/behavior-changes-15
- [a16all] https://developer.android.com/about/versions/16/behavior-changes-all
- [a16t] https://developer.android.com/about/versions/16/behavior-changes-16
- [a17all] https://developer.android.com/about/versions/17/behavior-changes-all

AOSP, branch `android16-release`:

- [AlarmManagerService.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java
- [AlarmManager.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/apex/jobscheduler/framework/java/android/app/AlarmManager.java
- [AppStateTrackerImpl.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/apex/jobscheduler/service/java/com/android/server/AppStateTrackerImpl.java
- [CoreManifest] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/res/AndroidManifest.xml
- [AppOpInfo.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/AppOpInfo.java
- [ForegroundServiceTypePolicy.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ForegroundServiceTypePolicy.java
- [NotificationManagerService.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java
- [NotificationAttentionHelper.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationAttentionHelper.java
- [ZenModeFiltering.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/ZenModeFiltering.java
- [ZenModeConfig.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/ZenModeConfig.java
- [ZenPolicy.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/ZenPolicy.java
- [FullScreenIntentDecisionProvider.kt] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/interruption/FullScreenIntentDecisionProvider.kt
- [ActivityTaskSupervisor.java] https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/ActivityTaskSupervisor.java
- [ECM] https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/service/java/com/android/ecm/EnhancedConfirmationService.java

AOSP DeskClock, branch `android16-release` (reference implementation; note it
targets SDK 30):

- [DeskClockManifest] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/AndroidManifest.xml
- [AlarmStateManager.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/alarms/AlarmStateManager.kt
- [AlarmService.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/alarms/AlarmService.kt
- [AlarmNotifications.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/alarms/AlarmNotifications.kt
- [AlarmKlaxon.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/alarms/AlarmKlaxon.kt
- [AlarmActivity.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/alarms/AlarmActivity.kt
- [AlarmInitReceiver.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/AlarmInitReceiver.kt
- [AsyncRingtonePlayer.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/AsyncRingtonePlayer.kt
- [AlarmAlertWakeLock.kt] https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/android16-release/src/com/android/deskclock/AlarmAlertWakeLock.kt
