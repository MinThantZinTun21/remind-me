# Mobile models that make tool calls, and where to download them

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

Which openly downloadable models can run on the Camon 40 (Helio G99, 8 GB RAM, CPU only) and make tool calls? Where can an app download them without a browser? Cover both LiteRT-LM (`.litertlm`) and llama.cpp (GGUF) files.

For each model family worth listing (at least LFM3, Gemma 3n and 4, Qwen3 and 3.5, Llama 3.2, Phi-4-mini, SmolLM3, Hammer, and FunctionGemma):

- The file sizes and quantizations published for phones, and where they are published: the Hugging Face repository and the exact file.
- The context length the mobile file was built with. This matters because the current `.litertlm` file is fixed at 2,048 tokens.
- The exact format in which it expects tools and writes calls.
- Its licence, and whether downloading needs an account or accepting terms (gated repositories).

Then the catalogue question. How can the app find "mobile models that can make tool calls" by itself? Look at Hugging Face's public API: search by library, tags, pipeline, file type and file size, and whether a model's chat template mentions tools. Is there a stable list the app could read instead, such as litert-community or Google AI Edge's model list? What does an unauthenticated download allow (rate limits, resumable downloads with Range requests)?

End with the shortest rule the app could use to label a model as tool-calling and mobile-sized, and the models that rule would list today.

## Answer

Researched 2026-11-18. The full findings, with a source link for each fact and a "Could not confirm" section, are on branch `research/mobile-tool-calling-models` (commit 1cf984c, local only, not pushed), file `research/mobile-tool-calling-models.md`. No model was downloaded and no account was used.

**No ready-made catalogue.** The Hugging Face Hub has no tool-calling tag, no file-size filter and no documented search endpoint; its search is described only in the `huggingface_hub` client's source. Google publishes no machine-readable list of tool-calling phone models. The nearest thing is the `litertlm_manifest.json` files in litert-community repositories, a community habit rather than a Google format. So the app needs its own rule.

**The rule.** A file is *mobile and tool-calling* when all of these hold:

1. It is a `.litertlm` file, or a text-only `.gguf` file.
2. It is at most 3.0 GB, the size read with `?blobs=true`.
3. The repository's `gated` field is false.
4. Its chat template handles tools. For GGUF, the Hub's `gguf.chat_template` mentions `tools`. For `.litertlm`, the card or manifest says tool calls work; after download, `ModelInfo.supportsFunctionCalling()` is true, or the app parses that family's format itself.
5. Its minimum runtime is no newer than the app's LiteRT-LM version.
6. It has at least 2,048 tokens of context.

**LiteRT-LM files that pass on 0.18.1 today:**

| Model | Size | Context |
|---|---|---|
| LFM3-1.7B mixed_int4 ekv2048 (the current one; the app parses its calls) | 1.44 GB | 2,048 |
| LFM2.5-1.2B int4 | 0.74 GB | 4,096 |
| Qwen3-0.6B | 0.34 GB | 4,096 |
| Qwen3-1.7B | 0.98 GB | 4,096 |
| Qwen3-4B | 2.27 GB | 4,096 |
| Gemma 4 E2B (minimum runtime unpublished) | 2.59 GB | up to 32K |
| Qwen3-4B-2507 (minimum runtime unpublished) | 2.66 GB | 2,048 |

**More that pass once the app moves to LiteRT-LM 0.19.x**, all with 4,096 context: LFM3 ekv4096, LFM2.5-350M and 2.6B, Qwen3.5-4B, five Granite 4.0/4.1/4.2 files, SmolLM3-3B and Ministral 3 3B.

**GGUF files that pass if llama.cpp is added** (the app sets the context, up to the trained maximum): LFM3 Q4_K_M (1.10 GB), LFM2.5, Qwen3, Qwen3.5, Llama 3.2 1B and 3B, Phi-4-mini, SmolLM3, Hammer 2.1 (non-commercial licence), FunctionGemma, Granite and Ministral 3.

**Left out:**
- Qwen3.5 0.8B and 2B `.litertlm`: their bundled templates have no tools.
- Gemma 3n: not trained for tools.
- Phi-4-mini `.litertlm` and Gemma 4 E4B: over 3 GB.
- Gemma 4 E2B GGUF: every file is over 3 GB.

**Gated (401 without an account and accepted terms):** google/gemma-3n-E2B and E4B-it-litert-lm (approved by hand), google/functiongemma-270m-it (by hand), litert-community functiongemma-270m-ft-mobile-actions, Llama-3.2-1B and 3B, Gemma3-1B-IT and the other Gemma 2 and 3 repositories (approved automatically). Ungated copies still carry their licences.

**Downloading without an account.** Range requests work, so downloads can resume, but the signed download link expires after about an hour and must be fetched again. Anonymous limits are 500 API calls and 3,000 file requests per 5 minutes.
