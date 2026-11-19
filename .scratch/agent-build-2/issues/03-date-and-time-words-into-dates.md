# Turning date and time words into dates on the phone

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

The model is poor at date arithmetic ("next tuesday" became Monday, and "5 30 tomorrow" became 09:30). The owner decided the app's code will work out dates and times from the model's words. How should that code work?

- The libraries available for Kotlin and Java on Android that turn English expressions into dates and times. Examples are Natty, the JVM version of Duckling, the date parsing in Hawking and in ICU, and any newer ones. For each, give its maintenance status, licence and size, and what it gets wrong.
- What the alternative looks like: the model fills small typed fields (a weekday name, "tomorrow", a day offset, hour, minute, am or pm) and plain code does the arithmetic. Which split between model and code is most reliable for a 1 to 2 B model? Look for published evidence, such as function-calling benchmarks with date fields and the Hammer and xLAM papers.
- The tricky meanings to settle, with what common apps do for each:
  - "next tuesday" said on a Monday.
  - "this friday" said on a Friday.
  - "5 30" with no am or pm.
  - "tonight", "end of the month", "in 2 weeks", "every other day", and "the 31st" in a 30-day month.

End with a recommended approach and the list of expressions it must handle.

## Answer

Researched 2026-11-18. The full findings are on branch `research/date-words-to-dates` (commit c5ed1f5, local only, not pushed), file `research/date-words-to-dates.md`. No library was run: each library's behaviour comes from its source, its tests or its README, all linked there. No official docs were found for TickTick, Things, Google Calendar or Apple Reminders, so the comparison with other apps rests on Todoist, Alexa, chrono and Duckling.

**Split between model and code.** The model copies the user's own words for *when* into one string field, `when` (for example "next tuesday at 2 pm", "5 30 tomorrow" or "on the 31st"). The app's code reads those words and does all the arithmetic with `java.time`. The model still decides the task, the repeat rule, the lead time and the style.

Code refuses a `when` that contains words the user did not type, which catches the model working out a date itself. `due` leaves the schema, and "Today" and "Coming days" can leave the prompt.

The evidence that small models should not do the arithmetic:
- At adding days to a date, Qwen3 1.7B was right 25% of the time, and 1B models never (PRIMETIME, arXiv 2504.16155).
- Turning relative dates into exact ones defeats even large models (ToolSandbox, arXiv 2408.04682).
- HammerBench (arXiv 2412.16516) keeps the time in the user's own wording.

**Library.** None: write a small Kotlin parser, with one test per expression. The candidates and why each was rejected:
- Natty and its fork take "next" from the calendar week, and give a day with no time the time now.
- Hawking is GPL and over 11 MB.
- ICU cannot parse expressions like these.
- ML Kit's entity extraction is a closed beta and has no repeat rules.
- dateparser-kotlin turns "next tuesday" said on a Monday into tomorrow, and rejects "tomorrow at 7".
- KWhen is too new to depend on.

**Fields the model fills.**
- `title`.
- `when`.
- `repeat`, `repeat_every` and `repeat_days`.
- `repeat_until`, also in the user's own words, such as "for 3 weeks".
- `warn_before`.
- `style`.

In `update_task`, words that give only a time keep the task's day, and words that give only a day keep its time.

**Meanings for the tricky expressions:**
- **"next tuesday":** Tuesday of next week, with weeks running Monday to Sunday, so 8 days ahead when said on a Monday. Todoist, chrono and Duckling agree.
- **"this friday" said on a Friday:** today if the time is still ahead, otherwise a week on.
- **"5 30" with no am or pm:**
  - "am", "pm" or a part-of-day word decides it.
  - With no day said, it is whichever reading comes round next.
  - With a day said, 7 to 11 means morning, 12 means noon, and 1 to 6 means afternoon, unless the style is alarm.
- **"tonight":** 21:00 today. "tonight at 8" is 20:00.
- **"end of the month":** the last day of this month.
- **"in 2 weeks":** today plus 14 days. Minutes and hours stay exact.
- **"every other day":** daily, every 2 days, starting the next time the clock shows the given time.
- **"the 31st" in a 30-day month:** that month's last day. Monthly repeats keep `RepeatRule`'s existing fallback to the last day.
- **No time given:** 09:00, never the time now.
- **No `when` at all:** one hour from now.
