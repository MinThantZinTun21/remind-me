# What Tecno HiOS does to background apps and alarms

Type: research
Status: open
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
