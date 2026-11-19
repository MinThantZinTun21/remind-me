# Moving to LiteRT-LM 0.19

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

Should build 2 move from LiteRT-LM 0.18.1 to 0.19.x?

[Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md) found that 0.19.x unlocks the 4,096-token LFM3 file and about a dozen more tool-calling models.

- What changed in the Kotlin API between 0.18.1 and the latest 0.19.x, and what Remind Me's `AgentEngine` would have to change.
- Whether 0.19.x fixes or changes issue #3165 (the model has to be loaded fresh for each message), and whether a conversation can now be kept open with its processed instruction.
- Whether 0.19.x parses tool calls itself, for which model families, and how `supportsFunctionCalling()` behaves.
- APK size and minimum Android version changes; anything reported about MediaTek or Helio G99 phones.
- Whether `.litertlm` files built for 0.18.1 still load on 0.19.x.

## Answer

Researched 2026-11-18. The full findings, with a source link for each fact and a "Could not confirm" section, are on branch `research/litert-lm-0-19` (commit 89ef90d, local only, not pushed), file `research/litert-lm-0-19.md`. Nothing was downloaded and no dependency was changed.

**Recommendation: move to 0.19.1, for the newer model files only. Keep loading a fresh model for each message: 0.19 does not fix issue #3165.**

- **No code change.** Every Kotlin API `AgentEngine` uses is unchanged in 0.19.1; only the version in `app/build.gradle.kts` changes. New in 0.19: `Conversation.clone()` and `Session.clone()`, `SpeculativeDecodingConfig` (off by default) and `ExperimentalFlags.enableYnnpack`.
- **Issue #3165 remains.** Its fix (commit e1f86b05, 2026-11-03) is only in `0.20.0-nightly.20261116`, which is not on Google Maven. In 0.19.1 `Reset()` only rewinds the step counter, so LFM3's short-convolution state carries over. LFM3's model card says the same.
- **`clone()` does not help LFM3 yet.** Clones share the parent's state buffers; a report on #3165 shows the 2nd and 3rd clones of an LFM2.5 conversation going wrong even with the fix. Real prefix caching for this kind of model (commit d09104f2) is also nightly-only. Checkpoints exist only in the C API.
- **Tool calls stay raw text for LFM3.** 0.19 parses calls only when the model file says how (set up for Qwen 3.5 and MiniCPM 5), and only for tools passed through `ConversationConfig.tools`; the app passes them through `extraContext`. `supportsFunctionCalling()` only reports a flag in the file.
- **Constrained decoding already exists in 0.18.1:** `ResponseFormat.regex` and `ResponseFormat.json`.
- **Size and Android version.** The AAR grows by 1.4 % (20,905,360 to 21,200,360 bytes), the arm64 native library by 537,176 bytes. `minSdkVersion` stays 24.
- **Helio G99.** No MediaTek regression or fix reported in 0.19; the one G99 issue (#3298, closed) advises the CPU backend.
- **Old files load.** The file format's major version is 1 in both releases, and that is all the reader checks.
- **What 0.19 unlocks:** files whose manifests require 0.19.0 — LFM2.5-350M and 2.6B, Qwen3.5-4B, SmolLM3-3B, and the re-uploaded LFM3 int8 and 4,096-token files. For the 4,096-token LFM3 file the 0.19 requirement is about a GPU crash on 0.18.x (#3986); whether it runs on 0.18.1's CPU is unchecked.
- **Known 0.19.1 bugs that do not touch the app's pattern:** one context overflow breaks every later message in that conversation (#4612); `clone()` leaks memory with speculative decoding on (#4641).

**Not confirmed:** CPU speed on the G99, whether `clone()` gives right answers for LFM3 itself, and when 0.20.0 ships.
