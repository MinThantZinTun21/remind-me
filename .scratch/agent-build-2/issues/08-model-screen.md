# The model screen

Type: grilling
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

How does the owner find, download, choose and remove models?

- Where the list comes from: searched live on Hugging Face by the rule from [Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md), a list file shipped with the app, or both. How it is filtered by size against the phone's memory.
- What each entry shows: name, size, engine, context length, licence, and "tool calls" as the model claims.
- Downloading: Wi-Fi only or not, progress, pause and resume, carrying on with the app closed, checking free space, and what happens to a half-finished file.
- Choosing: one model in use at a time. What happens to a conversation in progress when the model changes.
- Removing: what happens when the model in use is deleted. Also whether a model copied over USB is kept beside downloaded ones.
- Models that need an account or accepted terms to download.
- Where the screen lives: in the menu next to Set-up and the Fire log, or on the agent screen.

## Answer

Settled with the owner on 2026-11-18. The owner said "wifi", then accepted every recommended answer below. Facts come from [Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md).

1. **Wi-Fi only, strictly.** On mobile data a download waits and says "Waiting for Wi-Fi". There is no button to use mobile data instead.
2. **Where the list comes from: both.** A list built into the app comes first, made of models known to work with Remind Me. Below it are live results from Hugging Face that pass the six-part rule, each marked "not tried with Remind Me". Which live models the app can actually read is decided in [Reading tool calls from each model family](11-reading-tool-calls-from-each-model-family.md).
3. **Nothing hidden for space.** Every model passing the rule is shown, and the rule already drops files over 3 GB. When a model does not fit the free storage, its Download button is greyed out with "Needs 2.6 GB, 1.1 GB free".
4. **Each model shows** its name, size, context length (2K, 4K…), licence, and a "Tool calls" label. The engine is not shown unless llama.cpp joins the app.
5. **Downloading.**
   - Progress shows on the screen and in a notification.
   - The download carries on with the app closed.
   - It pauses when Wi-Fi drops and resumes where it stopped.
   - A half-finished file is kept until the download finishes or the owner taps Cancel.
   - Download links expire after about an hour, so the app fetches a new one when resuming.
6. **Choosing.** One model is in use at a time. Changing it ends the conversation in progress, and the new model starts fresh. Tasks are never touched.
7. **Removing.** The model in use can be deleted after a confirm. The agent screen then says "No model, choose one" and links to this screen. A model copied over USB is listed beside downloaded ones and can be chosen or deleted the same way.
8. **Models needing an account or accepted terms are not offered.** Signing in would mean storing an account on the phone and using the internet for more than downloads. Ungated alternatives exist: Qwen3, LFM2.5 and Gemma 4 E2B.
9. **Where it lives.** A "Models" item in the menu next to Set-up and the Fire log. The agent screen links straight to it when there is no model.

## Built ahead of the spec

Built at the owner's request (2026-11-19), on `feat/agent`, uncommitted. The files are:
- `agent/ModelOffers.kt`: the built-in list, the six-part rule and the Hugging Face search. Tested in `ModelOffersTest`.
- `agent/Models.kt`: the models on the phone, the model in use, and downloads through Android's DownloadManager.
- `ui/ModelsScreen.kt`.
- The Models menu item in `ui/HomeScreen.kt`.
- The "No model, choose one" state in `ui/AgentScreen.kt`.
- The INTERNET permission and the download-finished receiver in the manifest.

Where the build differs from the answer above:
- **Only LFM3 is known to work.** Tools do not go through `ConversationConfig.tools` yet ([Reading tool calls from each model family](11-reading-tool-calls-from-each-model-family.md)). Until they do, the agent can read only the `<function=…>` shape, so the other models can be downloaded and chosen but will likely fail to make tool calls.
- **Resuming is left to DownloadManager.** Each request to a Hugging Face `resolve` link gets a newly signed link, so resuming after an hour should work. Not yet tried on the phone.
- **Changing the model does not yet end a conversation.** Build 1 keeps no conversation, so there is none to end.
