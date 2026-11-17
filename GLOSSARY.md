# Remind Me

A personal Android app where an on-phone agent turns typed text into scheduled notifications and alarms.

## Language

**Task**:
A thing the user needs to do, with a due time.
_Avoid_: Reminder (for the thing itself), event, todo

**Due time**:
The moment a task is supposed to happen, always a date and a time on the phone's own clock, so 9:00 stays 9:00 when the time zone changes; a task always announces itself at its due time.
_Avoid_: Task time, deadline

**Lead time**:
How long before the due time the user gets an early warning about a task; a task has none, one or two.
_Avoid_: Reminder time, offset, "before time"

**Repeat rule**:
Whether a task happens once or recurs, on what cycle (daily, on chosen weekdays, every N days, every N weeks, monthly on a day of the month, or yearly), and until what date if it ends.
_Avoid_: Recurrence, schedule

**Delivery style**:
How a task announces itself at its due time: as a notification or as an alarm. Lead-time warnings are always notifications.
_Avoid_: Type, mode

**Occurrence**:
One single happening of a task; a one-time task has one, a repeating task has many.
_Avoid_: Instance, event

**Done**:
The user's statement that they did an occurrence, separate from dismissing its notification or alarm; on a repeating task it covers that occurrence only.
_Avoid_: Completed, finished, dismissed

**Overdue**:
A task whose due time has passed without the occurrence being marked done; on a repeating task the next occurrence replaces an overdue one.
_Avoid_: Missed, late

**Stop**:
The user's silencing of an alarm, or swiping away of a notification, without saying the occurrence is done; the task is then overdue.
_Avoid_: Dismiss, cancel, close

**Fire**:
One announcement the phone makes for an occurrence: a lead-time warning, the notification or alarm at the due time, or a snooze coming back.
_Avoid_: Trigger, alert, ping

**Snooze**:
The user's request to be told about an occurrence again ten minutes later; it does not change the due time.
_Avoid_: Postpone, delay, remind me later

**History**:
Where done one-time tasks are kept for 30 days before they are removed.
_Avoid_: Archive, log, completed list

**Notification**:
A normal heads-up with sound that tells the user about a task; the default delivery style.
_Avoid_: Noti, reminder, alert

**Alarm**:
A full-screen ring that continues until the user answers it or ten minutes pass, and that sounds even when the phone is on silent; the delivery style used when the user's text asks for it.
_Avoid_: Loud notification, alert

**Agent**:
The on-phone model that reads the user's typed text and decides every property of a task: due time, repeat rule, lead time and delivery style.
_Avoid_: Assistant, chatbot, parser, AI

**Tool**:
A function the app offers the agent for acting on tasks; the agent's only way to change anything.
_Avoid_: Function, command, API

**Action card**:
The app's account of what the agent just did to a task, from which the user can undo or edit it.
_Avoid_: Draft, preview, confirmation
