# Day buttons on the action card

Type: prototype
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

How should the seven day buttons on the action card look and behave?

Make a rough Compose prototype the owner can try on the phone, off `feat/agent`. Things to settle with the owner:

- Whether the buttons show on every card or only on repeating ones.
- What tapping a day does to a one-time task: it could turn into a weekly task, or move to that day.
- What happens when every day is on (daily) and when every day is off.
- Where the first occurrence lands after a change.
- How Undo on the card treats a correction made by tapping.
- Whether the same buttons replace the weekday picker in the Edit form.

## Answer

The owner tried three versions on the Camon 40 on 2026-11-18 and chose **B, "Repeating only"**.

- **Where the buttons show.** Only on cards for tasks that repeat on weekdays or every day: a row of seven joined day buttons labelled "Repeats on". Monthly and yearly cards have no day row.
- **Repeating cards.** A tap turns that day on or off. The last day cannot be turned off from the card; the card says to use Edit to make the task one-time.
- **All seven on** makes the task daily, and the card says "Every day". **All off** cannot happen on the card; making a task one-time is done in Edit.
- **One-time cards.** The row is labelled "Move to" and has one day lit, the day the task falls on. A tap on another day **moves** the task to the next such day at the same time: today, if the time has not passed yet. A separate "Repeat every <day>" button turns it into a weekly task on its own day.
- **First occurrence after a change.** The next time the new rule rings after now, from the task's own time of day.
- **Undo.** Each day tap is its own step. While there are day changes, the card's button reads "Undo day change" and takes back the last one only. With none left, it reads "Undo" and removes the task as in build 1.
- **Edit form.** The same seven buttons replace the Edit form's weekday picker.
- **Week start.** The prototype could start the week on Sunday or Monday; the owner did not choose. Follow the phone's locale.

Not chosen: A (round buttons on every card; a tap on a one-time task made it weekly; Undo removed the whole task) and C (a days label opening a panel of days and shortcuts).

Built ahead of the spec at the owner's request (2026-11-18), on `feat/agent`, uncommitted: `core/DayButtons.kt` (the rules, tested in `DayButtonsTest`), `ui/DayButtonRow.kt` (the shared row), the action card in `ui/AgentScreen.kt`, and the Edit form's `ui/RepeatField.kt`.

Prototype: `prototype/day-buttons` at 1fdbc41 (local, unpushed, never to be merged), file `app/src/main/java/app/remindme/ui/DayButtonsPrototype.kt`, installed as the separate app "Day buttons (prototype)", `app.remindme.daybuttons.prototype`.
