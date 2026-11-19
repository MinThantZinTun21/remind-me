# Tool contract v2

Type: grilling
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

What exactly do the tools take and return, now that code works out the dates?

- How the model gives the due time: as the words themselves, as typed fields, or a mix. The answer comes from [Turning date and time words into dates on the phone](03-date-and-time-words-into-dates.md).
- How the model gives weekdays for a repeat: one list of day names for any set, including "all days", "weekends" and "every day except friday". How `daily`, `weekdays` and `weekly` fold into that.
- What stops the model inventing a repeat rule: a required `once`, a separate tool for repeating tasks, or code checks against the words typed.
- How a follow-up points at a task: by id from the list, by "the one just made", or both.
- What a tool returns when the app cannot use a call. Whether the model gets a second attempt with the reason, and how many attempts fit inside 30 seconds.
- Whether `NONE` stays as the reply when there is nothing to remind of.

## Answer

Round 1, 2026-11-18. The owner accepted every recommendation. Q1 to Q3 reopen the charting decision that the model decides the repeat rule, lead time and delivery style; the owner chose to move them to the app.

1. **Repeat in the user's words.** The model copies how it comes round, as typed, into one `repeat` field ("every sunday and wednesday", "weekends", "every day except friday", "every other day"). The app reads it from a fixed vocabulary and refuses words that are not in the message.
2. **Alarm only when the words ask.** An alarm needs "alarm", "wake" or "wake me", or "don't let me miss" in the message; otherwise a notification, switchable on the card.
3. **No early warning unless the user says one.** A stated warning ("30 minutes before", "the day before") is copied as words and read by the app.
4. **Follow-ups name a task by id only.** Each earlier action reaches the model as a short line with its id.
5. **One second attempt**, only when nothing in the reply was usable, with the reason, in the same conversation. Calls that worked keep their cards.
6. **`NONE` stays** for messages that are not reminders; the app shows "Nothing to remind you of".

Round 2, 2026-11-18. The owner accepted every recommendation.

7. **The end of a repeat goes in the same words**: one `repeat` field holds "every monday for 3 weeks" or "daily until friday". `repeat_until` and `repeat_every` leave the schema.
8. **Repeat words the app cannot read** ("every full moon", "every payday") count as an unusable call and get the one second attempt. If that fails, nothing is created, and the card says "Remind Me can't repeat 'every full moon' yet — set it by hand". The app never quietly turns it into a one-time task.
9. **The glossary's Agent entry changes**: the agent decides what to do and to which task, and passes on the user's own words; the app turns those words into the task's properties.

**The contract that follows.** Each word field is copied from the message, and the app refuses one whose words the message does not contain.

| Tool | Fields |
|---|---|
| `create_task` | `title`, `when` (words), `repeat` (words, may include the end), `warn` (words), `style` |
| `update_task` | `id`, plus any of the create fields; a `when` with only a time keeps the day, and one with only a day keeps the time |
| `delete_task` | `id` |
| `complete_task` | `id` |

`style` is `alarm` or `notification`; the app keeps `alarm` only when the message says "alarm", "wake" or "wake me", or "don't let me miss", and keeps `notification` always. Whether `when` and `repeat` are words for every model family, and how calls are read back, is up to [Reading tool calls from each model family](11-reading-tool-calls-from-each-model-family.md).

Round 3, 2026-11-19, with the agent build 2 spec. The owner accepted every recommendation.

10. **Two more tools.** `snooze_task(id, for)` and `show_tasks(when)`. `undo_last` and `find_tasks` are left out.
11. **`snooze_task` acts only on an occurrence that has already fired and is not done.** For one not yet due, the card says "Gym isn't due yet. To move it, say when" and nothing changes.
12. **`for` is in the user's words**, read like `when` ("for an hour", "until 5", "30 minutes"). Without it, 10 minutes. Never past 24 hours or the next occurrence of a repeating task: the card refuses and suggests moving the task.
13. **`show_tasks(when)`** reads "today", "tomorrow", a weekday, "this week", "next week", "next N days", "overdue" or "all"; other words are an unusable call and get the one second attempt. The card lists up to 10 tasks (title, due, repeat), each opening the task, then "and N more, open the list"; it has no Undo. The model is told "showed 4 tasks for tomorrow: ids 3, 7, 9, 12", and those tasks count as touched in this conversation.
