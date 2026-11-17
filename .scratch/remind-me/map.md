# Map: Remind Me v1

Label: wayfinder:map

## Destination

A build-ready spec for Remind Me v1, saved in this repo: a personal Android app where an on-phone agent turns typed English text into tasks that fire as notifications or alarms. Every decision is locked and the two risky parts (the agent's accuracy on the phone, and firing reliably on Tecno HiOS) are proven by prototypes on the owner's Camon 40. Building the app is the handoff after this map, not part of it.

## Notes

**Plan, don't build.** No ticket on this map writes product code. Prototypes are throwaway.

**Vocabulary.** Use the terms in [GLOSSARY.md](../../GLOSSARY.md): task, due time, lead time, repeat rule, delivery style, notification, alarm, agent, tool, action card.

**Skills to consult.** `grilling` and `domain-modeling` for every grilling ticket; `prototype` for prototype tickets; `research` for research tickets; `android-development`, `android-cli` and `android-adb` for anything touching the phone.

**Target device (the only device).** Tecno Camon 40 (model CM5), MediaTek Helio G99 (MT6789), 8 GB RAM, arm64-v8a, Android 16 (API 36), HiOS, about 86 GB free. No Gemini Nano. Reachable over adb.

**Fixed while charting** (settled with the owner before any ticket existed; do not reopen without them):

- Personal use only, sideloaded. No Play Store.
- The model runs on the phone, fully offline. A one-time 1 to 3 GB model download or USB copy during setup is fine. A few seconds of wait per message is accepted.
- English, typed text only.
- The model is an agent. The app only supplies tools. The agent decides due time, repeat rule, lead time and delivery style; there is no settings default for lead time.
- The agent acts immediately. The app then shows an action card of what it did, with Undo and Edit.
- Tools: create, list, update and delete tasks. If the bake-off shows the phone model cannot manage two-step requests (find, then change), v1 falls back to create-only.
- One text may create several tasks.
- When information is missing the agent guesses and says what it chose; it asks back only when there is nothing to guess from.
- The agent handles reminders only and declines anything else.
- The model runs only when the user sends text. Firing is done by Android's own alarm system with no model involved.
- Delivery style is notification by default; alarm only when the text asks for it.
- Native Kotlin with Jetpack Compose.
- A manual form exists for creating and editing tasks by hand; it is what Edit opens.
- Firing after swipe-away, reboot and sleep is a hard requirement.
- A model passes at roughly 90% correct tool calls on about 50 sentences written the way the owner really types.

## Decisions so far

<!-- one line per closed ticket: [title](issues/NN-slug.md): gist -->

- [On-device model runtimes for the Camon 40](issues/01-on-device-model-runtimes.md): bake-off shortlist is LiteRT-LM and llama.cpp, both on CPU; no published speed figure exists for the Helio G99.
- [Small models that can call tools](issues/02-small-tool-calling-models.md): bake-off shortlist is LFM3-1.7B, Gemma 4 E2B, Qwen3.5-2B, Qwen3.5-4B; speed is the limit (estimated 5 to 20 s per tool call); evidence says code, not the model, should do date arithmetic.
- [Android 16 rules for exact alarms and full-screen alarms](issues/03-android-16-alarm-apis.md): `setExactAndAllowWhileIdle` for notifications, `setAlarmClock` plus a `systemExempted` foreground service for alarms, `USE_EXACT_ALARM` at install; survives sleep, swipe-away and reboot (after rescheduling), but not Force stop.
- [What Tecno HiOS does to background apps and alarms](issues/04-hios-background-restrictions.md): low-to-moderate evidence, none from a Camon 40; a HiOS freezer is reported to hold back scheduled alarms within seconds of leaving the app, a swipe kills the process without a force stop, and the boot broadcast may arrive minutes late; six one-time settings and eight behaviours for the prototype to test.

## Not yet specified

- **If no model passes the bar, or the ones that pass are too slow.** Research estimates 5 to 20 s per tool call on this phone against the "few seconds" accepted at charting. Whether to fine-tune a small model on the test set, accept a bigger and slower model, drop to create-only, or have code do the date arithmetic the model gets wrong. Hangs on the model bake-off.
- **The agent's instructions.** System prompt wording, examples, and how much of the task list and conversation the model is shown each turn. Hangs on the tool contract and on which model wins.
- **First-run setup.** How the model file gets onto the phone and the order of the permission walkthrough (notifications, exact alarms, full-screen alarm, battery, autostart). Hangs on the winning model and runtime and on what the reliability prototype shows HiOS needs.
- **App architecture.** Storage, the scheduling component, how the agent loop is structured in code, and when the model is loaded into and dropped from memory. Hangs on the tool contract and the runtime choice.
- **If HiOS cannot be made to fire reliably.** What to relax or work around if the reliability prototype shows the freezer holds back alarms whatever the owner sets: which parts of the hard requirement give way, and whether the application ID should contain `alarm` to land on HiOS's reported allowlist. Hangs on the alarm reliability prototype.
- **Clock and time zone changes.** What happens to scheduled tasks when the owner travels or the clock is changed by hand. Probably falls out of firing behaviour; may not need its own ticket.
- **Assembling the spec.** Once the tickets are closed, collapsing the decisions into the single spec document that is the destination.

## Out of scope

- Accounts, cloud sync and backup.
- Calendar integration, location-based reminders, home-screen widgets, sharing.
- Subtasks, categories and priorities.
- Voice input.
- Languages other than English (including Burmese).
- Play Store release and any second device; iOS.
- General chatbot behaviour.
- Running the model at fire time or in the background.
- A cloud model or a model served from the Mac.
- Building the app itself.
