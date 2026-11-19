# Map: Remind Me agent build 2

Label: wayfinder:map

## Destination

A build-ready spec for **agent build 2**, saved in this repo. With it, the agent understands any set of weekdays, and the action card has day buttons to correct them. The agent keeps the conversation while the agent screen is open. The app's own code turns the model's date and time words into dates. Tool calls are measurably more reliable. A settings screen downloads, switches between and deletes any mobile model that can make tool calls. Every decision is locked, and the risky parts (accuracy, speed and context size on the Camon 40) are proven by runs on the phone. Building it is the handoff after this map, not part of it.

## Notes

**Plan, don't build.** No ticket on this map writes product code. Prototypes are throwaway and stay off `feat/agent`.

**Vocabulary.** Use the terms in [GLOSSARY.md](../../GLOSSARY.md), including the new ones: conversation, model.

**Skills to consult.** `grilling` and `domain-modeling` for every grilling ticket, `prototype` for prototype tickets, and `research` for research tickets. Use `android-development`, `android-cli` and `android-adb` for anything touching the phone.

**Starting point.** Agent build 1 is on `feat/agent` (d1c8d35 on top of main's eb7e593, local only), plus uncommitted prompt and screen fixes from 2026-11-18. It uses LiteRT-LM 0.18.1 on the CPU with `LFM3-1.7B_mixed_int4_ekv2048.litertlm` (1.44 GB). The agent replies with tool calls only. Every message gets a newly loaded model, because of LiteRT-LM issue #3165.

**Facts every ticket needs.**

- The current model file holds **2,048 tokens of context** (`ekv2048`). One message with an empty task list already uses about **1,300 to 1,500 tokens**. Remembering earlier messages will not fit without a context plan or a bigger-context model.
- On the Camon 40: model load is 4 to 13 s, and reading the instruction takes 33 to 40 s in the background before each message. A reply takes 8 to 18 s, with the first token after about 2 to 3 s.
- [Phone run of 2026-11-18](assets/2026-11-18-phone-run.md): of 14 sentences, the main failures were wrong dates and times worked out by the model. Examples: "next tuesday" became Monday, and "5 30 tomorrow" became 09:30. There were also invented repeats ("Friday 3pm" became weekly; "call mom tomorrow" became daily), wrong weekday lists, and updates that changed nothing.

**Fixed while charting** (settled with the owner on 2026-11-18; do not reopen without them):

- The destination is a spec, then a build, as in build 1.
- English only, typed text.
- The agent keeps the **conversation for this visit only**: while the agent screen is open, so follow-ups like "change it to 7" work. It is forgotten when the user leaves the screen.
- Custom days: the agent sets any set of weekdays from the text, and the action card shows the seven days as buttons for a one-tap correction.
- The app's code works out dates and times from the model's words. (The model was also to decide the repeat rule, lead time and delivery style; the owner moved these to the app in [Tool contract v2](issues/05-tool-contract-v2.md).)
- A second engine (llama.cpp beside LiteRT-LM) may be looked into.
- One message may take up to **30 seconds** from Send to the action card.
- The model screen offers **any mobile model that can make tool calls**. The app trusts the model's own label; it does not test a model before letting the owner choose it.
- The app may use the internet **only to download models**. The model still runs offline, and tasks never leave the phone.

**Relation to the v1 map.** [Remind Me v1](../remind-me/map.md) still has open tickets that this map partly overtakes. They are left as they are: tool contract (07), conversation behaviour (09), building the agent test set (10), the model bake-off (12), and collecting real reminder sentences (05). [Collect the owner's sentences](issues/04-collect-the-owners-sentences.md) also answers v1's ticket 05.

## Decisions so far

<!-- one line per closed ticket: [title](issues/NN-slug.md): gist -->

- [Turning date and time words into dates on the phone](issues/03-date-and-time-words-into-dates.md): the model copies the user's words into one `when` field and a small Kotlin parser of our own does the arithmetic (no library fits); "next tuesday" means next week's Tuesday, no time means 09:00.
- [Mobile models that make tool calls](issues/01-mobile-models-that-make-tool-calls.md): no catalogue exists, so the app applies its own six-part rule (`.litertlm` or text `.gguf`, at most 3 GB, not gated, template handles tools, runtime new enough, at least 2,048 context); 7 LiteRT-LM files pass on 0.18.1 today and about 12 more on 0.19.x.
- [llama.cpp as a second engine](issues/02-llama-cpp-as-a-second-engine.md): worth a spike, built from source as our own module with our own JNI (chat template with tools, grammar, call parsing, saved instruction); about 12 MB; kept only if it beats LiteRT-LM on the phone.
- [Moving to LiteRT-LM 0.19](issues/12-litert-lm-0-19.md): move to 0.19.1 for its newer model files (a version change only); it does not fix the fresh-load-per-message issue #3165, whose fix is in the unreleased 0.20, and it does not parse LFM3's tool calls.
- [Tool contract v2](issues/05-tool-contract-v2.md): the model copies the user's own words into `when`, `repeat` (end included) and `warn`, and the app reads them and refuses words not in the message; alarm only when the words ask; no warning unless stated; tasks named by id; one second attempt; `NONE` stays.
- [Conversation and the context budget](issues/06-conversation-and-context-budget.md): fits in 2,048 tokens once tool contract v2 slims the prompt; keeps the user's words and one line per card, oldest dropped first; tasks touched in the conversation always shown; Undo and Edit rewrite the card's line; the model reloads per message but reads everything in the background while the user types.
- [Reading tool calls from each model family](issues/11-reading-tool-calls-from-each-model-family.md): tools go through `ConversationConfig.tools` so LiteRT-LM reads Qwen3, LFM2.5, Gemma 4, FunctionGemma and Granite 4.0 calls itself; Remind Me reads the `<function=…>` shape of generic files; a model of any other kind is marked unreadable and cannot be chosen; one instruction for all; thinking off.
- [The model screen](issues/08-model-screen.md): a "Models" menu item; a built-in list of known-good models above live Hugging Face results marked "not tried"; downloads over Wi-Fi only that run in the background and resume; one model in use, and changing it ends the conversation; models needing an account are not offered. Built ahead of the spec on 2026-11-19 at the owner's request; only LFM3 is known to work until tools go through `ConversationConfig.tools`.
- [Day buttons on the action card](issues/07-day-buttons-on-the-action-card.md): the owner chose "Repeating only": a seven-day row on weekly and daily cards (the last day can't be turned off there); on one-time cards the row moves the task to that day, plus a "Repeat every <day>" button; Undo takes back one day tap at a time; the same buttons replace the Edit form's weekday picker.

## Not yet specified

- **Making tool calls reliable beyond date words.** llama.cpp can force output to a grammar built from the tools; LiteRT-LM 0.18.1 already has `ResponseFormat.regex` and `ResponseFormat.json`, unused so far. A second attempt with the reason is decided (tool contract v2). Whether build 2 also forces output to a grammar, or adds a few worked examples to the prompt, hangs on the bake-off.
- **Assembling the spec.** Once every ticket is closed, collapse the decisions into the single spec document that is the destination.

## Out of scope

- Languages other than English, including Burmese.
- Training or fine-tuning a model of our own.
- Running the model on the phone's GPU or NPU.
- Remembering conversations between visits.
- Testing a downloaded model on the phone before it may be chosen (the owner chose to trust the label).
- A cloud model or a model served from the Mac.
- Internet use for anything except downloading models.
- Building agent build 2 itself.
