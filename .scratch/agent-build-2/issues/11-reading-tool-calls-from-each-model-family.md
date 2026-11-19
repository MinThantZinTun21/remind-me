# Reading tool calls from each model family

Type: grilling
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

How does the app read the tool calls of a model it did not ship with?

[Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md) lists LFM, Qwen, Gemma, Granite, SmolLM3, Ministral and others, each writing calls in its own format: LFM writes `<tool_call><function=…>` blocks, Qwen writes JSON inside `<tool_call>`, Gemma writes code blocks. [llama.cpp as a second engine](02-llama-cpp-as-a-second-engine.md) found that llama.cpp reads the calls back itself (`common_chat_parse`) for GGUF files; LiteRT-LM offers `ModelInfo.supportsFunctionCalling()` but the app parses LFM3's calls itself today.

- For `.litertlm` files: does the app write one parser per family, rely on LiteRT-LM's own parsing where it exists, or only offer families it can already read?
- What the model screen shows for a model whose format the app cannot read, given the owner chose to trust the label.
- How the tool list and instruction are given to each family: the model's own chat template with tools, or the app's own prompt as now.
- What counts as a malformed call, and whether it gets the same second attempt as an unusable call in [Tool contract v2](05-tool-contract-v2.md).

## Answer

Settled with the owner on 2026-11-18; the owner accepted every recommendation. Facts are from [Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md) (section 2 of its research file) and [Moving to LiteRT-LM 0.19](12-litert-lm-0-19.md).

**What is known.** Every `.litertlm` file names its model type. LiteRT-LM reads the calls itself for the `qwen3`, `lfm2` (on 0.18.0+), `gemma4`, `function_gemma` and `qwen2p5` types, but only for tools given through `ConversationConfig.tools`; Remind Me gives them through `extraContext` today. `generic_model` files (LFM3, Qwen3.5, Granite 4.2) get no parsing on 0.18.1; on 0.19+ only a file that sets `tool_call_config` does. LFM3 and Granite 4.2 write `<tool_call><function=…><parameter=…>` blocks, the shape Remind Me already reads.

1. **Tools go through `ConversationConfig.tools`**, so each model sees them in its own trained format, and the runtime returns finished calls for the five types above. Remind Me's own reader stays for `generic_model` files.
2. **The app reads only what it can.** It reads the `<function=…>` shape plus the five runtime types. After a download it checks the file's model type; a model it cannot read is marked on the Models screen "Remind Me can't read this model's replies yet", cannot be chosen, and offers Delete. This reads the file's own label, so it is not the testing the owner ruled out. No new reader is written per family.
3. **One instruction for every model.** No per-family prompts. A model that needs its own wording in the bake-off stays off the built-in list.
4. **A garbled call is an unusable call**: it gets the single second attempt from [Tool contract v2](05-tool-contract-v2.md), never another.
5. **GGUF files, if llama.cpp is added,** use llama.cpp's own reading (`common_chat_parse`) and the model's template.
6. **Thinking is off for every model**, to keep within 30 seconds.
