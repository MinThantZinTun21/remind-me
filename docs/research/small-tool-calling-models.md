# Small models that can call tools

Research for ticket `.scratch/remind-me/issues/02-small-tool-calling-models.md`.
Checked on 2026-11-17. No app code was written.

## Question

Which openly available models of about 3 GB or less on disk can reliably call tools, and are plausible on a Helio G99 phone with 8 GB RAM? Which four should go to the bake-off, and should the agent compute absolute dates itself or hand relative expressions to code?

## Short answer

Shortlist for the bake-off, ordered by how likely each is to reach the pass bar at a wait the owner would tolerate:

1. **LFM3-1.7B** (Liquid AI, 1.05 to 1.44 GB). Highest published tool-calling scores of anything under 3 GB, built for on-device agents, and Google's LiteRT samples already include a Kotlin app that turns one typed sentence into calendar actions with it.
2. **Gemma 4 E2B** (Google, 2.59 GB as `.litertlm`). Apache-2.0, dedicated tool tokens, and the first-party Android runtime (LiteRT-LM) handles its tool format; fastest measured 2B-class model on the nearest comparable CPU. Its tool-calling scores, as reported by a competitor, are the weakest of the four.
3. **Qwen3.5-2B** (Alibaba, 1.28 GB). Apache-2.0, answers without thinking by default, and its template supports several calls and a second call after a tool result; mid-table scores.
4. **Qwen3.5-4B** (Alibaba, 2.58 to 2.75 GB). The accuracy ceiling under 3 GB, corroborated by two vendors, but on this phone it will probably take far longer than "a few seconds" per message. It is in the list to learn whether size buys accuracy.

First reserve: **LFM2.5-2.6B** (1.67 GB), which has the second-best published scores but always thinks before answering. Fine-tune fallback if nothing passes: **FunctionGemma 270M**.

Date arithmetic: **code should do it.** The evidence says small models are unreliable at turning relative expressions into absolute dates and that the same models do much better when they emit a structured relative expression for code to resolve. The prompt should still state the current date, time and day of week.

Biggest gap: nobody has published a speed measurement for any of these models on a Helio G99, and every tool-calling score comes from a vendor running full-precision weights, not the 4-bit files the phone will run.

## Method and limits

- Sources are model cards, vendor documentation, chat templates read from the model repositories, and papers. Each claim carries a link. Where a primary page could not be read, the claim is marked **unverified**.
- File sizes come from the Hugging Face Hub API (`https://huggingface.co/api/models/<repo>?blobs=true`) on 2026-11-17, in decimal units (1 GB = 10^9 bytes). The link given is the repository the file lives in.
- "Q4_K_M" and "Q4_0" are 4-bit GGUF quantizations for llama.cpp. `.litertlm` is the LiteRT-LM format. Which runtime loads which file on Android is ticket 01's question; this note only records what is published.
- Benchmark scores are **not comparable across vendors**. BFCL (the Berkeley Function-Calling Leaderboard) exists in versions 2, 3 and 4, each vendor runs it with its own harness and settings, and some cards quote a home-made "BFCL" metric. Scores are comparable only within one row source.
- Gated repositories (Gemma 3, FunctionGemma, Gemma 3n, Llama 3.2) were read through their vendors' public documentation instead of the Hugging Face card.

## The field in November 2026

The ticket's list was written from older knowledge. Checking each vendor's Hugging Face organisation by creation date shows these changes:

- **Google.** Gemma 4 replaced Gemma 3 and 3n for on-device use; the E2B and E4B repositories were created 2026-03-02 ([google/gemma-4-E2B-it](https://huggingface.co/google/gemma-4-E2B-it), [launch post](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/)). There is no FunctionGemma successor: [google/functiongemma-270m-it](https://huggingface.co/google/functiongemma-270m-it) was created 2025-10-08 and last modified 2026-01-14.
- **Alibaba.** Qwen3.5 small models (0.8B, 2B, 4B, 9B) arrived 2026-02-27/28 ([Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B)). Later Qwen generations in the official organisation are large models only; community repositories with names such as "Qwen3.8-4B" are not official and were not examined (unverified).
- **Liquid AI.** LFM2.5 (350M, 1.2B, 2.6B) and LFM3-1.7B, the latter released 2026-10-23 ([LiquidAI/LFM3-1.7B](https://huggingface.co/LiquidAI/LFM3-1.7B)).
- **IBM.** Granite 4.0 nano (October 2025), 4.1-3b (April 2026), 4.2-3b (August 2026) ([ibm-granite/granite-4.2-3b](https://huggingface.co/ibm-granite/granite-4.2-3b)).
- **Mistral.** Ministral 3 3B ([mistralai/Ministral-3-3B-Instruct-2512](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512)).
- **No newer small model** from Meta (Llama 3.2 is still the latest small Llama), Microsoft (Phi-4-mini-instruct is still the latest small Phi), Hugging Face (SmolLM3), MadeAgents (Hammer 2.1, December 2024) or Salesforce (xLAM-2, March 2025).
- **New tiny tool-calling specialists.** Needle 3 from Cactus Compute ([Cactus-Compute/needle3](https://huggingface.co/Cactus-Compute/needle3), 2026-09-16) and Falcon-H1-Tiny-Tool-Calling-90M from TII ([tiiuae/Falcon-H1-Tiny-Tool-Calling-90M](https://huggingface.co/tiiuae/Falcon-H1-Tiny-Tool-Calling-90M), January 2026).

## Comparison table

"Tool calling trained" means the vendor states it as a capability and the chat template has a tool format. "Parallel" is more than one tool call in one turn. "Two-step" is a tool result followed by a second call. Scores are quoted with their source because they do not compare across sources.

| Model | Params | 4-bit file | Tool calling trained | Call format out | Parallel | Two-step | Context | Licence | Published formats | Tool-calling evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FunctionGemma 270M | 268M | 253 MB GGUF (Q4_K_M); 288 MB `.litertlm` | Yes, only purpose | `<start_function_call>call:name{k:<escape>v<escape>}<end_function_call>` | Yes | **No**, needs fine-tuning | 32K | Gemma Terms | safetensors, `.litertlm`, community GGUF | BFCL Simple 61.6, Parallel 39.0; Mobile Actions 58% base, 85% fine-tuned (Google) |
| Gemma 3 270M | 268M | 304 MB `.litertlm` (q8) | No | none of its own | n/a | n/a | 32K | Gemma Terms | safetensors, `.litertlm`, GGUF | none |
| Gemma 3 1B | 1.00B | 1.00 GB GGUF (QAT Q4_0); 584 MB `.litertlm` | No | none of its own | n/a | n/a | 32K | Gemma Terms | safetensors, GGUF, `.litertlm`, `.task` | BFCLv3 16.64 (Liquid's table) |
| Gemma 3n E2B | 2B effective | 3.66 GB `.litertlm` | No | none of its own | n/a | n/a | 32K | Gemma Terms | safetensors, `.litertlm` | none; over budget |
| **Gemma 4 E2B** | 2.3B effective | 2.59 GB `.litertlm`; 3.04 GB GGUF (Q4_0) | Yes | `<\|tool_call>call:name{k:<\|"\|>v<\|"\|>}<tool_call\|>` | Yes | Template supports; not demonstrated in docs | 128K | Apache-2.0 | safetensors, GGUF (QAT), `.litertlm` | BFCLv4 36.98, ToolSandbox 52.40 (Liquid's table) |
| Qwen3 0.6B | 0.6B | 639 MB GGUF (Q8_0); 343 to 614 MB `.litertlm` | Yes | `<tool_call>{"name":…,"arguments":…}</tool_call>` | Yes | Yes (template) | 32K | Apache-2.0 | safetensors, GGUF, `.litertlm` | none found |
| Qwen3 1.7B | 1.7B | 1.11 GB GGUF | Yes | same | Yes | Yes (template) | 32K | Apache-2.0 | safetensors, GGUF, `.litertlm` | BFCLv3 46.30 (Liquid's table) |
| Qwen3 4B | 4.0B | 2.50 GB GGUF | Yes | same | Yes | Yes (template) | 32K (262K for Instruct-2507) | Apache-2.0 | safetensors, GGUF, `.litertlm` | BFCL-V4 39.9, TAU2 43.2 for 4B-2507 (Qwen's table) |
| Qwen3.5 0.8B | 0.75B | 533 MB GGUF | Yes | `<tool_call><function=name><parameter=k>v</parameter></function></tool_call>` | Yes | Yes (template) | 262K | Apache-2.0 | safetensors, community GGUF, `.litertlm` | BFCL-V4 25.3, TAU2 11.6 (Qwen) |
| **Qwen3.5 2B** | 1.9B | 1.28 GB GGUF; 2.12 GB `.litertlm` (int8) | Yes | same | Yes | Yes (template) | 262K | Apache-2.0 | safetensors, community GGUF, `.litertlm` | BFCL-V4 43.6, TAU2 48.8 (Qwen) |
| **Qwen3.5 4B** | 4.2B | 2.74 GB GGUF; 2.75 GB `.litertlm` | Yes | same | Yes | Yes (template) | 262K | Apache-2.0 | safetensors, community GGUF, `.litertlm` | BFCL-V4 50.3, TAU2 79.9 (Qwen); BFCLv4 50.56 to 55.12, ToolSandbox 75.4 (Liquid) |
| Llama 3.2 1B | 1.23B | 808 MB GGUF | Yes | `[name(k='v'), name2(...)]` | Yes | Yes (`ipython` role) | 128K | Llama 3.2 Community | safetensors, community GGUF, ExecuTorch `.pte` | BFCL V2 25.7; 14.3 after 4-bit PTQ (Meta) |
| Llama 3.2 3B | 3.21B | 2.02 GB GGUF | Yes | same | Yes | Yes (`ipython` role) | 128K | Llama 3.2 Community | same | BFCL V2 67.0; 53.4 after 4-bit PTQ (Meta) |
| Phi-4-mini | 3.84B | 2.49 GB GGUF | Yes | `<\|tool_call\|>` tokens; exact shape unverified | unverified | unverified | 128K | MIT | safetensors, ONNX, community GGUF, `.litertlm` (3.9 GB) | no score captured |
| SmolLM3 3B | 3.08B | 1.92 GB GGUF | Yes | `<tool_call>{json}</tool_call>` or `<code>name(k="v")</code>` | Yes (per prompt text) | Template supports | 64K | Apache-2.0 | safetensors, GGUF, ONNX | "BFCL" 92.3 on the card's own metric |
| Hammer 2.1 0.5b / 1.5b / 3b | 0.49B / 1.54B / 3.09B | 398 MB / 986 MB / 1.93 GB GGUF | Yes, only purpose | Hermes-style `<tool_call>{json}</tool_call>` or fenced JSON | Yes | Yes (stated) | 32K | CC-BY-NC-4.0 (0.5b, 1.5b); qwen-research (3b) | safetensors, community GGUF, ExecuTorch | scores published only as an image |
| xLAM-2 1b / 3b fc-r | 1.54B / 3.09B | 986 MB / 1.93 GB GGUF | Yes, only purpose | JSON list; needs vendor parser | Yes | Yes (stated) | 32K | CC-BY-NC-4.0 | safetensors, official GGUF | scores published only as an image |
| LFM2.5 350M | 0.35B | 229 MB GGUF | Yes | `<\|tool_call_start\|>[name(k="v")]<\|tool_call_end\|>` | Yes (list) | Yes (docs) | 32K | LFM Open v1.0 | safetensors, GGUF, ONNX, MLX | none captured |
| LFM2.5 1.2B | 1.17B | 731 MB GGUF | Yes | same | Yes (list) | Yes (docs) | 32K | LFM Open v1.0 | same | BFCLv3 49.12 (Liquid) |
| LFM2.5 2.6B | 2.70B | 1.67 GB GGUF | Yes | same; always thinks first | Yes (list) | Yes (docs) | 131K | LFM Open v1.0 | same | BFCLv4 56.88, ToolSandbox 77.83 (Liquid) |
| **LFM3 1.7B** | 1.73B | 1.05 GB GGUF (QAD-Q4_0); 1.44 GB `.litertlm` | Yes, stated design goal | `<tool_call><function=name><parameter=k>v</parameter></function></tool_call>` | Yes | Yes (docs and LiteRT card) | 256K | LFM Open v1.0 | safetensors, GGUF, ONNX, MLX, `.litertlm` | BFCL V4 71.39, ToolSandbox 80.36 (Liquid) |
| Granite 4.0 350m / 1b | 0.35B / 1.6B | 237 MB / 1.02 GB GGUF | Yes | `<tool_call>{json}</tool_call>` | Yes | Template supports | 32K / 131K | Apache-2.0 | safetensors, official GGUF | BFCL v3 39.32 / 54.82 (IBM) |
| Granite 4.2 3b | 3.66B | 2.24 GB GGUF | Yes | Qwen3.5-style XML | Yes | Template supports | 128K | Apache-2.0 | safetensors, official GGUF | BFCL v4 52.41 (IBM) |
| Ministral 3 3B | 3.43B | 2.15 GB GGUF | Yes | `[TOOL_CALLS]name[ARGS]{json}` | Yes | Yes | 256K | Apache-2.0 | safetensors, official GGUF | none captured |
| Needle 3 | 29M to 121M | 35 MB `.cact` | Yes, only purpose | engine-specific | Yes (stated) | unverified | unverified | Apache-2.0 | `.cact` (Cactus engine only) | no numbers published |
| Falcon-H1-Tiny-Tool-Calling | 90M to 108M | 68 MB GGUF | Yes, only purpose | unverified | unverified | unverified | 262K in GGUF metadata | Falcon-LLM | safetensors, GGUF | none on the card |

## Model by model

### FunctionGemma 270M

- **Size.** 268,098,176 parameters ([model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)). Files: 536 MB safetensors and a 288 MB `.litertlm` in [the official repo](https://huggingface.co/google/functiongemma-270m-it); community GGUF at 253 MB (Q4_K_M) and 292 MB (Q8_0) in [unsloth/functiongemma-270m-it-GGUF](https://huggingface.co/unsloth/functiongemma-270m-it-GGUF).
- **Tool calling.** It is the model's only purpose. Google says it "is intended to be fine-tuned for your specific function-calling task, including multi-turn use cases" and is "not intended for use as a direct dialogue model" ([model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)).
- **Format** ([formatting guide](https://ai.google.dev/gemma/docs/functiongemma/formatting-and-best-practices)). The developer turn must contain the sentence "You are a model that can do function calling with the following functions", followed by declarations:
  `<start_function_declaration>declaration:get_current_weather{description:<escape>…<escape>,parameters:{…}}<end_function_declaration>`.
  The model answers `<start_function_call>call:get_current_weather{location:<escape>Tokyo, Japan<escape>}<end_function_call>`. The app returns `<start_function_response>response:get_current_weather{temperature:15,weather:<escape>sunny<escape>}<end_function_response>`, and `<start_function_response>` should be an extra stop sequence. Through the chat template the result is a `tool`-role message whose content is `{"name": …, "response": …}` ([full sequence guide](https://ai.google.dev/gemma/docs/functiongemma/full-function-calling-sequence-with-functiongemma)).
- **Parallel and two-step.** Trained for single-turn and parallel calls. Not trained for chaining: for multi-step and multi-turn use the guide says the model "has not been trained to perform these tasks out of the box" and "is not trained to reason through this dependency chain automatically" ([formatting guide](https://ai.google.dev/gemma/docs/functiongemma/formatting-and-best-practices)). That rules it out, untuned, for "find the task, then change it".
- **Context and date.** 32K tokens of input; knowledge cutoff August 2024 ([model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)). Google's own fine-tuning dataset puts the clock in the developer turn, verbatim: "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2025-06-04T15:29:23 / Day of week is Wednesday / You are a model that can do function calling with the following functions" ([google/mobile-actions dataset](https://huggingface.co/datasets/google/mobile-actions), [guide](https://ai.google.dev/gemma/docs/mobile-actions)).
- **Evidence.** BFCL, zero-shot: Simple 61.6, Multiple 63.5, Parallel 39.0, Parallel Multiple 29.5, Relevance 61.1, Irrelevance 73.7. Mobile Actions: 58% before fine-tuning, 85% after ([model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)).
- **Date evidence.** Mobile Actions has a `create_calendar_event(title, datetime)` tool that takes an absolute `YYYY-MM-DDTHH:MM:SS`. I read the first 23 calendar samples in the dataset file: almost every user sentence spells out the calendar date next to any relative phrase (for example "this Friday, June 6th, 2025, at 2 PM"); one was purely relative ("this upcoming Thursday at 2 PM"). So the 85% figure shows the model can copy and reformat a stated date; it does not show relative-date arithmetic ([dataset](https://huggingface.co/datasets/google/mobile-actions)).
- **Speed.** On a Galaxy S25 Ultra CPU: about 126 tokens per second decoding, 0.3 s to first token, about 551 MB peak memory ([model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)). It will be fast on the G99.
- **Licence.** Gemma Terms of Use, a custom licence with use restrictions, not an open-source licence ([terms](https://ai.google.dev/gemma/terms)).

### Gemma 3 (270M, 1B) and Gemma 3n

- **Size.** 268M and 999,885,952 parameters; 32K context for both ([Gemma 3 model card](https://ai.google.dev/gemma/docs/core/model_card_3)). Files: 1.00 GB QAT GGUF for 1B ([google/gemma-3-1b-it-qat-q4_0-gguf](https://huggingface.co/google/gemma-3-1b-it-qat-q4_0-gguf)); 584 MB int4 `.litertlm` and 555 MB `.task` ([litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)); 304 MB q8 `.litertlm` for 270M ([litert-community/gemma-3-270m-it](https://huggingface.co/litert-community/gemma-3-270m-it)).
- **Tool calling.** Not listed as a capability and no tool-calling benchmark on the card ([model card](https://ai.google.dev/gemma/docs/core/model_card_3)). Gemma 3 has no tool tokens; tool use is by prompt convention only. Google's page describing that convention now covers Gemma 4 and the Gemma 3 version returned 404, so the exact convention is **unverified**.
- **Evidence.** Liquid's comparison table gives Gemma 3 1B a BFCLv3 score of 16.64, the lowest of the five 1B-class models it lists ([LFM2.5-1.2B-Instruct card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct)).
- **Gemma 3n.** E2B and E4B, 32K context, tool calling not listed ([Gemma 3n model card](https://ai.google.dev/gemma/docs/gemma-3n/model_card)). The int4 `.litertlm` is 3.66 GB for E2B ([google/gemma-3n-E2B-it-litert-lm](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)), over the 3 GB budget.
- **Licence.** Gemma Terms of Use ([terms](https://ai.google.dev/gemma/terms)).
- **Verdict.** Not candidates. Gemma 3 1B has no trained tool format and the weakest score; Gemma 3n is over budget; both are superseded by Gemma 4 E2B.

### Gemma 4 E2B (and E4B)

- **Size.** E2B is 2.3B "effective" parameters (5.1B counting its embedding tables); E4B is 4.5B effective ([model card](https://huggingface.co/google/gemma-4-E2B-it)). Files for E2B: 2.59 GB `.litertlm` ([litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)); 3.35 GB official QAT GGUF plus a 987 MB vision projector that a text-only app can skip ([google/gemma-4-E2B-it-qat-q4_0-gguf](https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf)); 3.04 GB community Q4_0 GGUF ([unsloth/gemma-4-E2B-it-GGUF](https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF)). E4B is 3.66 GB as `.litertlm` and 5.15 GB as GGUF, over budget.
- **Tool calling.** "Native support for structured tool use", a native system role, and thinking switched on by a `<|think|>` token at the start of the system prompt ([model card](https://huggingface.co/google/gemma-4-E2B-it)). Google says thinking "significantly enhances its function-calling accuracy" ([function calling guide](https://ai.google.dev/gemma/docs/capabilities/function-calling)).
- **Format** ([chat template](https://huggingface.co/google/gemma-4-E2B-it/blob/main/chat_template.jinja), [guide](https://ai.google.dev/gemma/docs/capabilities/function-calling)). Tools go in the system turn as `<|tool>declaration:name{description:<|"|>…<|"|>,parameters:{…}}<tool|>`. The model emits `<|tool_call>call:get_current_temperature{location:<|"|>London<|"|>}<tool_call|>`. The result goes back as `<|tool_response>response:name{temperature:15,weather:<|"|>sunny<|"|>}<tool_response|>`.
- **Parallel and two-step.** The template loops over several tool calls in one turn and accepts a list of tool responses. The guide shows only independent parallel requests; it shows no second call that depends on a first result, so two-step behaviour is supported by the template but not demonstrated by Google.
- **Context and date.** 128K tokens; training cutoff January 2025 ([model card](https://huggingface.co/google/gemma-4-E2B-it)). Neither the card nor the guide says how to supply the date; it has to go in the system turn.
- **Evidence.** No tool-calling score on Google's card. Liquid's tables report Gemma 4 E2B at BFCLv4 36.98 and ToolSandbox 52.40, and E4B at 46.39 and 65.00 ([LFM2.5-2.6B card](https://huggingface.co/LiquidAI/LFM2.5-2.6B)). These are a competitor's measurements.
- **Speed** ([LiteRT-LM Gemma 4 page](https://developers.google.com/edge/litert-lm/models/gemma-4), [LiteRT card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)). E2B on a Galaxy S26 Ultra CPU: 557 tokens per second prefill, 47 decode, 1.8 s to first token, 1,733 MB. On a Raspberry Pi 5 CPU: 133 prefill, 7.6 decode, 7.8 s to first token, 1,546 MB. E4B on the Pi 5: 51 prefill, 3 decode.
- **Licence.** Apache-2.0 ([licence page](https://ai.google.dev/gemma/docs/gemma_4_license)).

### Qwen3 (0.6B, 1.7B, 4B)

- **Size.** 0.6B, 1.7B and 4.0B parameters; 32,768-token context; Apache-2.0 ([Qwen/Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), [Qwen/Qwen3-1.7B](https://huggingface.co/Qwen/Qwen3-1.7B), [Qwen/Qwen3-4B](https://huggingface.co/Qwen/Qwen3-4B)). Qwen3-4B-Instruct-2507 has a 262,144-token context and no thinking mode ([card](https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507)). Files: 639 MB Q8_0 for 0.6B ([Qwen/Qwen3-0.6B-GGUF](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF)); 1.11 GB Q4_K_M for 1.7B ([unsloth/Qwen3-1.7B-GGUF](https://huggingface.co/unsloth/Qwen3-1.7B-GGUF)); 2.50 GB Q4_K_M for 4B ([Qwen/Qwen3-4B-GGUF](https://huggingface.co/Qwen/Qwen3-4B-GGUF)) and for 4B-Instruct-2507 ([unsloth/Qwen3-4B-Instruct-2507-GGUF](https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF)). `.litertlm` files exist for all three ([litert-community/Qwen3-0.6B](https://huggingface.co/litert-community/Qwen3-0.6B), [Qwen3-1.7B](https://huggingface.co/litert-community/Qwen3-1.7B), [Qwen3-4B](https://huggingface.co/litert-community/Qwen3-4B)).
- **Format** ([chat template in tokenizer_config.json](https://huggingface.co/Qwen/Qwen3-1.7B/blob/main/tokenizer_config.json)). The system turn carries "# Tools … You are provided with function signatures within <tools></tools> XML tags:" and one JSON schema per tool. The model replies `<tool_call>{"name": <function-name>, "arguments": <args-json-object>}</tool_call>`. Results return as `<tool_response>…</tool_response>` inside a user turn.
- **Parallel and two-step.** The template text says "You may call one or more functions", renders several calls per turn, and has explicit handling for a multi-step exchange of calls and results.
- **Thinking.** On by default; turn off with `enable_thinking=False` or `/no_think`. The card warns "DO NOT use greedy decoding" and recommends temperature 0.7, top_p 0.8, top_k 20 for non-thinking use ([Qwen3-1.7B card](https://huggingface.co/Qwen/Qwen3-1.7B)).
- **Evidence.** Liquid's table: Qwen3-1.7B BFCLv3 46.30 ([LFM2.5-1.2B-Instruct card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct)). Qwen's own table for the previous generation: Qwen3-4B-2507 BFCL-V4 39.9, TAU2-Bench 43.2 ([Qwen3.5-4B card](https://huggingface.co/Qwen/Qwen3.5-4B)).
- **Date evidence.** In the PRIMETIME study's task of turning a natural-language datetime into ISO 8601, Qwen3 4B scored 71.6% with a direct prompt, 1.7B 58.3%, and 0.6B 6.8% ([arXiv 2504.16155](https://arxiv.org/html/2504.16155v2)). These were the best small open models in that table and still far below the pass bar.
- **Verdict.** Superseded by Qwen3.5 on Qwen's own numbers. Qwen3-4B-Instruct-2507 remains a reasonable substitute if Qwen3.5 files will not load in the chosen runtime.

### Qwen3.5 (0.8B, 2B, 4B)

- **Size.** 0.75B, 1.88B and 4.21B text parameters; 262,144-token context; Apache-2.0; text and vision ([Qwen/Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B), [Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B), [Qwen/Qwen3.5-4B](https://huggingface.co/Qwen/Qwen3.5-4B)). No GGUF in the official Qwen organisation was found on 2026-11-17. Community GGUF: 533 MB ([unsloth/Qwen3.5-0.8B-GGUF](https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF)), 1.28 GB Q4_K_M and 2.01 GB Q8_0 ([unsloth/Qwen3.5-2B-GGUF](https://huggingface.co/unsloth/Qwen3.5-2B-GGUF)), 2.74 GB Q4_K_M and 2.58 GB Q4_0 ([unsloth/Qwen3.5-4B-GGUF](https://huggingface.co/unsloth/Qwen3.5-4B-GGUF)). The GGUF metadata names the architecture `qwen35`, so the runtime must be recent enough to know it. `.litertlm`: 965 MB for 0.8B, 2.12 GB for 2B, 2.75 GB for 4B ([litert-community/Qwen3.5-2B](https://huggingface.co/litert-community/Qwen3.5-2B), [Qwen3.5-4B](https://huggingface.co/litert-community/Qwen3.5-4B)).
- **Tool calling.** "Qwen3.5 excels in tool calling capabilities" ([Qwen3.5-2B card](https://huggingface.co/Qwen/Qwen3.5-2B)).
- **Format** ([chat template](https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/chat_template.jinja)). The system turn carries "# Tools / You have access to the following functions:" and the tool schemas as JSON inside `<tools>…</tools>`. The call format changed from Qwen3's JSON to XML:

  ```
  <tool_call>
  <function=example_function_name>
  <parameter=example_parameter_1>
  value_1
  </parameter>
  </function>
  </tool_call>
  ```

  Results return as `<tool_response>` inside a user turn.
- **Parallel and two-step.** The template renders several `<tool_call>` blocks per turn and handles alternating calls and results.
- **Thinking.** 0.8B and 2B default to non-thinking; 4B defaults to thinking. The cards warn the two smaller models are more prone to thinking loops when thinking is on. Recommended non-thinking sampling for text: temperature 1.0, top_p 1.0, top_k 20, presence_penalty 2.0 ([Qwen3.5-2B card](https://huggingface.co/Qwen/Qwen3.5-2B)).
- **Evidence.** Qwen's table: BFCL-V4 25.3 / 43.6 / 50.3 and TAU2-Bench 11.6 / 48.8 / 79.9 for 0.8B / 2B / 4B ([Qwen3.5-4B card](https://huggingface.co/Qwen/Qwen3.5-4B)). Liquid, a competitor, measured Qwen3.5-4B at BFCLv4 50.56 to 55.12 and ToolSandbox 75.40 to 75.55 ([LFM2.5-2.6B card](https://huggingface.co/LiquidAI/LFM2.5-2.6B), [LFM3-1.7B card](https://huggingface.co/LiquidAI/LFM3-1.7B)), which agrees with Qwen's figure. The drop from 4B to 2B to 0.8B is steep, especially on the multi-turn TAU2 benchmark.
- **Speed.** A community LiteRT conversion reports the 2B int8 file at 22.9 tokens per second decoding on a Galaxy S26 CPU and 4.2 on a Raspberry Pi 5 CPU with 56 tokens per second prefill ([litert-community/Qwen3.5-2B](https://huggingface.co/litert-community/Qwen3.5-2B)).
- **Runtime cautions from the same card.** That `.litertlm` bundle ships a simplified chat template, not Qwen's own, and the card does not mention tools, so tool definitions would have to be written into the prompt by the app. An earlier int8 file looped on arithmetic on the CPU backend until it was re-quantized on 2026-10-11. The card also says a new conversation on a reused engine inherits state from the previous one in LiteRT-LM.

### Llama 3.2 (1B, 3B)

- **Size.** 1.23B and 3.21B parameters; 128K context; knowledge cutoff December 2023 ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)). Files: 808 MB Q4_K_M for 1B ([bartowski/Llama-3.2-1B-Instruct-GGUF](https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF)); 2.02 GB for 3B ([bartowski/Llama-3.2-3B-Instruct-GGUF](https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF)). Meta's own ExecuTorch SpinQuant files are 1,083 MB and 2,435 MB with an 8K context ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)).
- **Format** ([prompt format](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/text_prompt_format.md), [Meta docs](https://dev.meta.ai/llama/docs/model-cards-and-prompt-formats/llama3_2)). Tool definitions are JSON in the system or user prompt. The model answers with a Python-style list, for example `[get_weather(city='San Francisco', metric='celsius'), get_weather(city='Seattle', metric='celsius')]<|eot_id|>`. Results come back under the `ipython` role.
- **Parallel and two-step.** "The output supports multiple tool calls natively" ([prompt format](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/text_prompt_format.md)). The same page notes the 1B and 3B models do not support the built-in tools of larger Llamas.
- **Date.** Whether the 3.2 prompt format has a standard "Today Date" line was not confirmed (unverified).
- **Evidence.** BFCL V2 at full precision: 1B 25.7, 3B 67.0. After plain 4-bit post-training quantization: 14.3 and 53.4. With Meta's SpinQuant: 15.9 and 60.1. With QAT plus LoRA: 23.7 and 63.5 ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)). This is the clearest published measurement that 4-bit quantization costs tool-calling accuracy: 11 to 14 points here.
- **Date evidence.** PRIMETIME ISO 8601 parsing, direct prompt: 3B 3.8%, 1B 3.2% ([arXiv 2504.16155](https://arxiv.org/html/2504.16155v2)).
- **Speed.** On a OnePlus 12 with ExecuTorch: 1B SpinQuant 50.2 tokens per second, 3B SpinQuant 19.7 ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)).
- **Licence.** Llama 3.2 Community License, a custom licence ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)).
- **Verdict.** Not candidates. Models released in the two years since beat them at the same size, and the date-parsing result is near zero.

### Phi-4-mini

- **Size.** 3.84B parameters; 128K context; cutoff June 2024; MIT licence ([microsoft/Phi-4-mini-instruct](https://huggingface.co/microsoft/Phi-4-mini-instruct)). Files: 2.49 GB Q4_K_M ([unsloth/Phi-4-mini-instruct-GGUF](https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF)); the `.litertlm` is about 3.9 GB, over budget ([litert-community/Phi-4-mini-instruct](https://huggingface.co/litert-community/Phi-4-mini-instruct)).
- **Format** ([card](https://huggingface.co/microsoft/Phi-4-mini-instruct)). Tools are a JSON list in the system turn wrapped in `<|tool|>…<|/tool|>`:
  `<|system|>You are a helpful assistant with some tools.<|tool|>[{"name": "get_weather_updates", …}]<|/tool|><|end|><|user|>…<|end|><|assistant|>`.
  The tokenizer has `<|tool_call|>`, `<|/tool_call|>` and `<|tool_response|>` tokens ([added_tokens.json](https://huggingface.co/microsoft/Phi-4-mini-instruct/blob/main/added_tokens.json)).
- **Parallel and two-step.** The official chat template renders tools only into the system turn; the exact output shape, parallel calls and tool-result handling are **unverified** from the card.
- **Evidence.** No tool-calling score was captured from the card (unverified). PRIMETIME ISO 8601 parsing: 1.1% with a direct prompt, 44.4% at best ([arXiv 2504.16155](https://arxiv.org/html/2504.16155v2)).
- **Verdict.** Not a candidate: as big as Qwen3.5-4B, older, with a less complete tool format and no published score.

### SmolLM3 3B

- **Size.** 3.08B parameters; trained on 64K context, usable to 128K; Apache-2.0 ([HuggingFaceTB/SmolLM3-3B](https://huggingface.co/HuggingFaceTB/SmolLM3-3B)). File: 1.92 GB Q4_K_M ([ggml-org/SmolLM3-3B-GGUF](https://huggingface.co/ggml-org/SmolLM3-3B-GGUF)); an ONNX build also exists.
- **Format** ([card](https://huggingface.co/HuggingFaceTB/SmolLM3-3B), [chat template](https://huggingface.co/HuggingFaceTB/SmolLM3-3B/blob/main/chat_template.jinja)). Two modes. `xml_tools` gives `<tool_call>{"name": "get_weather", "arguments": {"city": "Copenhagen"}}</tool_call>`. `python_tools` gives `<code>get_weather(city="Copenhagen")</code>`. The system text says "You may call one or more functions"; tool results are rendered as a user turn.
- **Date.** The template writes a metadata block into every system prompt: "Knowledge Cutoff Date: June 2025 / Today Date: <day month year> / Reasoning Mode: /think or /no_think". It gives the date only, with no time and no day of week ([chat template](https://huggingface.co/HuggingFaceTB/SmolLM3-3B/blob/main/chat_template.jinja)).
- **Evidence.** The card reports "BFCL" 92.3 without thinking, alongside 89.5 for Qwen3-1.7B and 95.0 for Qwen3-4B. Those numbers are roughly double what other sources give the same Qwen models on BFCL v3 and v4, so the card's metric is its own and cannot be compared with the rest of this note ([card](https://huggingface.co/HuggingFaceTB/SmolLM3-3B)).
- **Verdict.** A plausible reserve at 1.92 GB, but it is from July 2025, and on its own table it trails Qwen3-4B, which Qwen3.5-4B has since passed.

### Hammer 2.1 and other tool-calling specialists

- **Hammer 2.1** ([MadeAgents/Hammer2.1-1.5b](https://huggingface.co/MadeAgents/Hammer2.1-1.5b), [paper](https://arxiv.org/abs/2410.04587)). Fine-tuned from Qwen2.5-Coder for tool calling, in 0.5b, 1.5b and 3b sizes. The card says version 2.1 added multi-step and multi-turn calling and can decline when no tool fits. Calls come out Hermes-style through the chat template, or as fenced JSON with the card's own prompt. Community GGUF only: 398 MB, 986 MB and 1.93 GB at Q4_K_M, 32,768-token context ([mradermacher/Hammer2.1-0.5b-GGUF](https://huggingface.co/mradermacher/Hammer2.1-0.5b-GGUF), [1.5b](https://huggingface.co/mradermacher/Hammer2.1-1.5b-GGUF), [3b](https://huggingface.co/mradermacher/Hammer2.1-3b-GGUF)). Licence: CC-BY-NC-4.0 for 0.5b and 1.5b, the Qwen research licence for 3b; both forbid commercial use, which does not matter for a personal app. Its benchmark results are published only as an image and were not captured. Last updated December 2024. The card says it was integrated into Google AI Edge; Google's page for that returned 404, so its current status is unverified.
- **xLAM-2 fc-r** ([Salesforce/xLAM-2-1b-fc-r](https://huggingface.co/Salesforce/xLAM-2-1b-fc-r)). Qwen2.5-based, trained for multi-turn tool calling, CC-BY-NC-4.0, official GGUF at 986 MB (1b) and 1.93 GB (3b). It needs a vendor-supplied parser; scores are published only as an image. March 2025.
- **Arch-Function 1.5B / 3B** ([katanemo/Arch-Function-3B](https://huggingface.co/katanemo/Arch-Function-3B)). September 2024, research-only licence. Not examined further.
- **Needle 3** ([card](https://huggingface.co/Cactus-Compute/needle3), [vendor page](https://cactuscompute.com/needle)). 29M to 121M parameters in a 35 MB file, a custom architecture that runs only on the Cactus engine. The vendor says two requests give two calls in order and that it trades general chat ability for tool calling. No accuracy numbers, no statement on tool results, no date handling. It could not say what it chose or decline off-topic text, which the map requires of the agent.
- **Falcon-H1-Tiny-Tool-Calling-90M** ([card](https://huggingface.co/tiiuae/Falcon-H1-Tiny-Tool-Calling-90M), [GGUF](https://huggingface.co/tiiuae/Falcon-H1-Tiny-Tool-Calling-90M-GGUF)). 68 MB at Q4_K_M, custom Falcon licence. The card gives no accuracy data.
- **Verdict.** The specialists from 2024 and early 2025 are built on Qwen2.5 and have been overtaken by general models whose training now includes tool calling (Qwen3.5, LFM3, Granite 4.2). None is on the shortlist. FunctionGemma is the one worth keeping, as the fine-tune fallback, because Google publishes the full fine-tuning recipe for exactly this kind of task.

### LFM2.5 (350M, 1.2B, 2.6B)

- **Size.** 350M, 1.17B and 2.70B parameters; context 32,768 for the two smaller and 131,072 for 2.6B ([LiquidAI/LFM2.5-350M](https://huggingface.co/LiquidAI/LFM2.5-350M), [LFM2.5-1.2B-Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct), [LFM2.5-2.6B](https://huggingface.co/LiquidAI/LFM2.5-2.6B)). Official GGUF: 229 MB, 731 MB, and for 2.6B 1.67 GB Q4_K_M, 1.59 GB QAD-Q4_0, 2.87 GB Q8_0 ([LiquidAI/LFM2.5-2.6B-GGUF](https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF)). The GGUF architecture is `lfm2`.
- **Format** ([Liquid tool-use docs](https://docs.liquid.ai/lfm/key-concepts/tool-use), [chat template](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct/blob/main/chat_template.jinja)). Tools are JSON in the system prompt after "List of tools:". The model answers with a Python-style list between special tokens: `<|tool_call_start|>[get_candidate_status(candidate_id="12345")]<|tool_call_end|>`. The result goes back in a `tool` turn. Adding "Output function calls as JSON" to the system prompt switches the output to JSON.
- **Parallel and two-step.** The output is a list, so several calls fit in one turn; the docs show the call, result, answer loop.
- **Thinking.** The 2.6B card says it "is a pure reasoning model that always thinks before it answers"; its chat template opens every assistant turn with a `<think>` tag ([2.6B card](https://huggingface.co/LiquidAI/LFM2.5-2.6B)). The card documents no way to turn that off, so every message pays for the thinking tokens. On a slow phone that is the main reason it is a reserve and not on the shortlist.
- **Evidence.** Liquid's tables: LFM2.5-1.2B BFCLv3 49.12, against Granite 4.0-1B 52.43, Qwen3-1.7B 46.30, Llama 3.2 1B 21.44, Gemma 3 1B 16.64 ([1.2B card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct)). LFM2.5-2.6B BFCLv4 56.88 and ToolSandbox 77.83, against Qwen3.5-4B 50.56 / 75.55 and Gemma 4 E2B 36.98 / 52.40 ([2.6B card](https://huggingface.co/LiquidAI/LFM2.5-2.6B)). Liquid recommends fine-tuning LFM2.5 for a specific use.
- **Licence.** LFM Open License v1.0: Apache-like terms, with commercial use limited to organisations under 10 million US dollars of annual revenue ([licence](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct/blob/main/LICENSE)). Not an open-source licence, and no obstacle to a personal, sideloaded app.

### LFM3 1.7B

- **Size.** 1.73B parameters; 256K context; released 2026-10-23 ([card](https://huggingface.co/LiquidAI/LFM3-1.7B)). Official GGUF: 1.05 GB QAD-Q4_0, 1.10 GB Q4_K_M, 1.85 GB Q8_0 ([LiquidAI/LFM3-1.7B-GGUF](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF)). `.litertlm`: 1.44 GB mixed int4, 1.80 GB int8 ([litert-community/LFM3-1.7B-LiteRT](https://huggingface.co/litert-community/LFM3-1.7B-LiteRT)). ONNX and MLX builds also exist.
- **Tool calling.** The card says it is "designed for local agents, tool calling, structured outputs, and domain-specific fine-tuning" and that the last stage of training used "a larger share of agentic traces and tool calls". It adds: "It is not recommended for agentic coding or complex multi-step reasoning due to its small size" ([card](https://huggingface.co/LiquidAI/LFM3-1.7B)).
- **Format** ([chat template](https://huggingface.co/LiquidAI/LFM3-1.7B/blob/main/chat_template.jinja)). Tools in `<tools>` in the system turn; calls in the same XML shape as Qwen3.5 (`<tool_call><function=get_weather><parameter=city>…</parameter></function></tool_call>`); results as `tool` messages.
- **Parallel and two-step.** The card shows several `<tool_call>` blocks in one turn and multi-round loops. The LiteRT card shows a call, a `tool` message with the result, and a follow-up answer through LiteRT-LM's Python API ([LiteRT card](https://huggingface.co/litert-community/LFM3-1.7B-LiteRT)).
- **Thinking.** On by default. `enable_thinking=False` turns it off, "but this behavior can be inconsistent, especially for reasoning-heavy tasks". Recommended sampling: temperature 0.1, top_k 50, repetition penalty 1.1 ([card](https://huggingface.co/LiquidAI/LFM3-1.7B)).
- **Evidence.** Liquid's table, all models at full precision: BFCL V4 71.39 and ToolSandbox 80.36, against LFM2.5-2.6B 55.89 / 78.04, Qwen3.5-4B 55.12 / 75.40, Qwen3.5-9B 62.83 / 76.41, Gemma 4 E4B 48.25 / 69.27 ([card](https://huggingface.co/LiquidAI/LFM3-1.7B)). No independent replication was found; the model is under four weeks old.
- **Closest prior art to this app.** The LiteRT card points to [offline_planner](https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert_lm/offline_planner) in Google's litert-samples repository: an Android app, using LiteRT-LM's Kotlin API, that "turns one typed sentence into actions on the app's own calendar and outbox" with the LFM3 mixed-int4 file, parsing the model's XML tool calls itself. I confirmed the page exists; I did not read its code.
- **Speed** ([LiteRT card](https://huggingface.co/litert-community/LFM3-1.7B-LiteRT)). Mixed int4 on CPU with 4 threads: Galaxy S26, 316 tokens per second prefill and 35.5 decode, 2.2 GB peak memory; Pixel 8a (Tensor G3), 112 prefill and 14.7 decode, 2.0 GB peak, 9.2 s to first token after a 1,024-token prompt. The card also measured that the mixed-int4 file picks the same next token as the full-precision model at only 78 to 80% of test positions, against 89 to 96% for the int8 file, so quantization changes this model's behaviour noticeably.
- **Runtime caution.** The GGUF architecture is `lfm3`, and Liquid's own speed test used "a llama.cpp build with LFM3 support" while the other models used a stock release ([card](https://huggingface.co/LiquidAI/LFM3-1.7B)). Whether a released llama.cpp Android build loads it is for ticket 01 to confirm.
- **Licence.** LFM Open License v1.0, as for LFM2.5.

### IBM Granite and Ministral 3

- **Granite 4.0 nano** ([granite-4.0-350m](https://huggingface.co/ibm-granite/granite-4.0-350m), [granite-4.0-1b](https://huggingface.co/ibm-granite/granite-4.0-1b)). Apache-2.0. 237 MB and 1.02 GB at Q4_K_M ([350m GGUF](https://huggingface.co/ibm-granite/granite-4.0-350m-GGUF), [1b GGUF](https://huggingface.co/ibm-granite/granite-4.0-1b-GGUF)); the GGUF metadata gives a context of 32,768 tokens for 350m and 131,072 for 1b. Hermes-style format: `<tools>` JSON in, `<tool_call>{"name":…,"arguments":…}</tool_call>` out ([chat template](https://huggingface.co/ibm-granite/granite-4.0-1b/blob/main/chat_template.jinja)). IBM's BFCL v3: 39.32 for 350M, 54.82 for 1B.
- **Granite 4.2-3b** ([card](https://huggingface.co/ibm-granite/granite-4.2-3b)). Apache-2.0, 3.66B parameters, 128K context, 2.24 GB at Q4_K_M ([GGUF](https://huggingface.co/ibm-granite/granite-4.2-3b-GGUF)). The tool format moved to the Qwen3.5-style XML. IBM's BFCL v4: 52.41. It is the Apache-licensed alternative to Qwen3.5-4B at a slightly smaller size.
- **Ministral 3 3B** ([card](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512)). Apache-2.0, 3.43B parameters, 256K context, official GGUF at 2.15 GB. Format: `[AVAILABLE_TOOLS][…][/AVAILABLE_TOOLS]` in, `[TOOL_CALLS]name[ARGS]{json}` out, `[TOOL_RESULTS]…[/TOOL_RESULTS]` back ([chat template](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512/blob/main/chat_template.jinja)). Mistral recommends temperature below 0.1 and as few tools as possible. Its default system prompt states "The current date is {today}" and tells the model to resolve dates, giving "yesterday" as an already-computed value. No tool-calling score was captured.

## Dates and times

### How each model is told the current date and time

No model in this note knows the date. In every case the app has to write it into the prompt. What vendors do:

- Google's Mobile Actions prompt for FunctionGemma gives the full timestamp **and the day of week** in the developer turn ([dataset](https://huggingface.co/datasets/google/mobile-actions)).
- SmolLM3's template inserts "Today Date" (date only) automatically ([template](https://huggingface.co/HuggingFaceTB/SmolLM3-3B/blob/main/chat_template.jinja)).
- Mistral's default system prompt gives today's date and a pre-computed "yesterday" ([card](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512)).
- Qwen, Liquid, IBM, Meta and Gemma 4 templates insert nothing; a line in the system prompt is the only route.

Two of the three vendors that do insert a date also pre-compute something for the model (the weekday; "yesterday"). That is a hint about where they think the model is weak.

### Published evidence on date reasoning

- **PRIMETIME** ([arXiv 2504.16155](https://arxiv.org/abs/2504.16155), [full text](https://arxiv.org/html/2504.16155v2)) tests datetime parsing and arithmetic "primitives". It finds them "individually unreliable", with accuracy "from near-zero to perfect across models and prompting conditions". For turning a natural-language datetime into ISO 8601 with a direct prompt, small open models scored: Qwen3 4B 71.6%, Qwen3 1.7B 58.3%, Gemma 3 4B 8.1%, Qwen3 0.6B 6.8%, Llama 3.2 3B 3.8%, Llama 3.2 1B 3.2%, SmolLM2 1.7B 1.7%, Phi-4-mini 1.1%, Gemma 3 1B 0.0%. On a composed task (add 250 days and compare), untuned Qwen2.5 models from 0.5B to 7B were at chance, 41 to 52%. After fine-tuning, Qwen2.5 1.5B and 3B reached 99.3%. The authors note that datetime arithmetic is trivial in a programming language and that results swing with prompt wording.
- **A Semantic Parsing Framework for End-to-End Time Normalization** ([arXiv 2507.06450](https://arxiv.org/html/2507.06450v1)) compares two ways of normalising time expressions. Having the model write a symbolic expression that code then executes "significantly outperforms" having the model state the resulting interval directly, by more than 10 points. A fine-tuned Qwen2.5-0.5B writing the symbolic form scored 0.59, above a frontier cloud model stating intervals directly (0.38).
- **Test of Time** ([arXiv 2406.09170](https://arxiv.org/abs/2406.09170)) is a benchmark of temporal reasoning that separates arithmetic from semantics. I read only the abstract and search summaries; its detailed error breakdown is unverified here.
- **Mobile Actions** ([dataset](https://huggingface.co/datasets/google/mobile-actions)), the only vendor dataset close to this app, mostly sidesteps the problem: its user sentences usually state the calendar date outright.

### What was not found

No published measurement of any shortlisted model on the phrases this app lives on: "next Friday", "every weekday", "in 3 hours", "an hour before". PRIMETIME's small-model table predates Qwen3.5, Gemma 4 and LFM3. The bake-off's own test set will be the first evidence.

### Verdict: code computes the dates

The agent should not output absolute dates worked out from relative phrases. It should output what the user said in a structured form, and Kotlin code should resolve it against the clock. Reasons:

1. The best small model measured got about 72% on plain date-to-ISO translation, and most were under 10%. A 90% bar on whole tool calls leaves no room for a date step that fails a quarter of the time.
2. The one study that compared the two designs found the "model writes an expression, code executes it" design better by more than 10 points, and found a 0.5B model good enough at writing the expression.
3. Vendors that ship date-aware prompts pre-compute weekday and "yesterday" in code.
4. Fine-tuning can fix date arithmetic (99.3% in PRIMETIME), but that is a fallback the map lists as undecided, and code is free.
5. Repeat rules and lead times are not date arithmetic at all. "Every weekday" is a set of days and "an hour before" is a duration. A model that is made to compute them into timestamps can only lose information.

What this means for the tool contract (ticket 07) and the bake-off (ticket 12), as input and not as a decision:

- Let tool arguments carry a relative due time, for example a day reference (`today`, `tomorrow`, a weekday name with `this` or `next`, or an offset of N minutes, hours or days) plus a clock time, with an absolute date accepted only when the user typed one.
- Let lead time be a duration and the repeat rule be a named cycle with a list of days.
- Still put the current date, time and day of week in the prompt, as Google does. The agent needs them to read "tonight" or "this afternoon" sensibly and to say what it chose.
- If the bake-off has time, run the winning model once with absolute-date arguments as well. It is a cheap way to confirm this verdict on the owner's own sentences.

## Plausibility on the Helio G99

No published speed measurement of any language model on a Helio G99 was found. The G99 has two Cortex-A76 cores and six Cortex-A55 cores. The nearest published proxy is the Raspberry Pi 5, which has four Cortex-A76 cores at a slightly higher clock. The G99 has half as many fast cores, so it should be at or below the Pi 5 figures. That inference is mine and is unverified.

| Model and file | Raspberry Pi 5 CPU, decode | Raspberry Pi 5 CPU, prefill | Source |
|---|---|---|---|
| Gemma 4 E2B `.litertlm` | 7.6 tok/s | 133 tok/s | [LiteRT card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Gemma 4 E4B `.litertlm` | 3 tok/s | 51 tok/s | [LiteRT-LM page](https://developers.google.com/edge/litert-lm/models/gemma-4) |
| Qwen3.5-2B int8 `.litertlm` | 4.2 tok/s | 56 tok/s | [LiteRT card](https://huggingface.co/litert-community/Qwen3.5-2B) |
| LFM3-1.7B | not published; 14.7 tok/s on a Pixel 8a CPU | 112 tok/s on a Pixel 8a CPU | [LiteRT card](https://huggingface.co/litert-community/LFM3-1.7B-LiteRT) |

What follows from those numbers, as estimates to be replaced by measurement:

- **Memory is not the constraint.** Peak memory for the 2B-class files is 1.5 to 2.2 GB in the measurements above, and a 4B 4-bit file is under 3 GB on disk. All fit in 8 GB.
- **Speed is the constraint.** One tool call is roughly 40 to 80 output tokens. At 4 to 8 tokens per second that is 5 to 20 seconds for a 2B-class model, before any thinking tokens. A 4B model at 2 to 3 tokens per second is 15 to 40 seconds per call, and a two-step request doubles it. The map accepts "a few seconds"; 4B models are outside that, and 2B-class models are at its edge.
- **Thinking multiplies the wait.** LFM3 and Qwen3.5-4B think by default, Gemma 4's tool calling is said to benefit from it, and LFM2.5-2.6B always thinks. Each shortlisted model must be tested with thinking off first, since thinking tokens are generated at the same slow rate.
- **The prompt must be cached.** The tool definitions and instructions will be several hundred tokens. At 50 to 130 tokens per second of prefill that is 3 to 15 seconds if paid on every message. The runtime needs to keep the system prompt's state between messages; whether it can is ticket 01's question.
- **Quantization costs accuracy.** Meta measured an 11 to 14 point BFCL drop from plain 4-bit quantization on Llama 3.2, recovered mostly by quantization-aware training ([model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md)). The LFM3 LiteRT card measured that its mixed-int4 file diverges from the full-precision model far more than its int8 file. Prefer files made with quantization-aware methods (Google's QAT Gemma 4 files, Liquid's QAD files) and, where the size allows, test an 8-bit file of the 1.7B and 2B models alongside the 4-bit one: LFM3 Q8_0 is 1.85 GB and Qwen3.5-2B Q8_0 is 2.01 GB, both within budget.

## Shortlist for the bake-off

Ordered by how likely each is to reach roughly 90% correct tool calls at a wait the owner would tolerate. On accuracy alone, ignoring speed, Qwen3.5-4B would move to first or second.

| # | Model | File to test | Why | Main risk |
|---|---|---|---|---|
| 1 | LFM3-1.7B | QAD-Q4_0 GGUF 1.05 GB, or mixed-int4 `.litertlm` 1.44 GB; Q8_0 1.85 GB as the accuracy check | Best published tool-calling scores under 3 GB; smallest and fastest of the strong models; multi-call and two-step shown; an existing Kotlin sample does a near-identical job | Released 2026-10-23, scores are vendor-only; needs a runtime that knows the `lfm3` architecture; turning thinking off "can be inconsistent" |
| 2 | Gemma 4 E2B | `.litertlm` 2.59 GB | Apache-2.0; dedicated tool tokens; first-party Android runtime with tool handling; fastest measured 2B-class decode on Cortex-A76; thinking is optional | Weakest reported tool scores of the four (as measured by a competitor); two-step not demonstrated; GGUF is just over 3 GB |
| 3 | Qwen3.5-2B | Q4_K_M GGUF 1.28 GB; Q8_0 2.01 GB as the accuracy check | Apache-2.0; no thinking by default; template handles several calls and call-result-call; same family as #4, so the pair shows what size buys | Mid-table scores (BFCL-V4 43.6, TAU2 48.8, both with thinking on); slowest measured 2B-class decode on Cortex-A76; the `.litertlm` bundle lacks the tool template; no official GGUF |
| 4 | Qwen3.5-4B | Q4_0 GGUF 2.58 GB | Highest multi-turn agent score under 3 GB, confirmed by a second vendor; Apache-2.0 | Expected 15 to 40 s per tool call on this phone; thinks by default; no official GGUF |

Reserves, in order: LFM2.5-2.6B (1.59 GB QAD-Q4_0; second-best published scores under 3 GB, but it always thinks, so expect a long wait); Granite 4.2-3b (Apache-2.0, 2.24 GB); Granite 4.0-1b (Apache-2.0, 1.02 GB, Hermes-style JSON).

If nothing passes: fine-tune FunctionGemma 270M on the test-set style, following Google's Mobile Actions recipe, with the two-step case included in the training data, or fine-tune LFM3 with Liquid's published recipe for a phone app with many tools. Both vendors document this path; neither result is predictable from published numbers.

Before running the full test set, measure each candidate's decode speed on the Camon 40. Drop any model that cannot finish one tool call in a time the owner accepts; do not spend test-set effort on it.

## Open uncertainties

1. **Speed on the actual phone.** No Helio G99 measurement exists for any candidate. Everything in the plausibility section is extrapolated from a Raspberry Pi 5 and a Pixel 8a.
2. **Accuracy of the files the phone will run.** All tool-calling scores are for full-precision weights on servers. The only published before-and-after (Llama 3.2) shows 4-bit quantization costing 11 to 14 BFCL points.
3. **Vendor-only scores.** LFM3's lead rests on Liquid's own table. BFCL versions and harnesses differ across vendors, so cross-vendor ranking is weak. No independent comparison covering LFM3, Qwen3.5 and Gemma 4 together was found.
4. **The phrases this app depends on.** No published evidence on "next Friday", "every weekday", "in 3 hours" for any shortlisted model.
5. **Two-step behaviour at this size.** Templates support a tool result followed by a second call for every shortlisted model, but only Qwen's TAU2 numbers measure multi-turn tool use, and they fall from 79.9 at 4B to 48.8 at 2B. LFM3's own card advises against complex multi-step reasoning. The create-only fallback in the map may be needed.
6. **Runtime support.** Whether Android builds of llama.cpp or LiteRT-LM load `lfm3` and `qwen35` files, apply the tool templates, and cache the system prompt is ticket 01's question and was not settled here.
7. **Unverified details.** Gemma 3's prompt-only tool convention; Phi-4-mini's exact call output and score; Llama 3.2's date line; Hammer's and xLAM-2's scores (published as images); Hammer's current Google AI Edge status; the offline_planner sample's code.
