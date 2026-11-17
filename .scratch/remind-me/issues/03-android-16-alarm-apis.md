# Android 16 rules for exact alarms and full-screen alarms

Type: research
Status: open
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
