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
The on-phone model that reads the user's typed text, decides what to do and to which task, and passes on the user's own words for when, how often and how early; the app turns those words into the task's properties.
_Avoid_: Assistant, chatbot, parser, AI

**Model**:
A downloadable file the agent runs on, chosen by the user from those that make tool calls; one is in use at a time.
_Avoid_: LLM, AI, brain, engine (the engine is what runs a model)

**Conversation**:
The messages the user and the agent exchange while the agent screen is open; the agent remembers it until the user leaves the screen.
_Avoid_: Chat, session, history (history is done tasks)

**Tool**:
A function the app offers the agent for acting on tasks; the agent's only way to change anything.
_Avoid_: Function, command, API

**Context**:
Everything the model reads for one message: the instruction, the tools, the tasks shown, the conversation so far and the new message; it has a fixed size, so the oldest parts are left out first.
_Avoid_: Prompt (the instruction is one part of the context), memory, window

**Orchestrator**:
The part of the app that runs one message from start to finish: it builds the context, asks the model once (twice when the first reply is unusable), carries out the tool calls and adds the outcome to the conversation. It is app code, not a second model.
_Avoid_: Multi-agent, planner, router

**Action card**:
The app's account of what the agent just did to a task, from which the user can undo or edit it.
_Avoid_: Draft, preview, confirmation
