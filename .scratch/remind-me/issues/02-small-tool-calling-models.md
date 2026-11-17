# Small models that can call tools

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me v1](../map.md)

## Question

Which openly available models of about 3 GB or less on disk can reliably call tools, and are plausible on a Helio G99 phone with 8 GB RAM?

Cover at least: FunctionGemma, Gemma 3 (270M, 1B) and Gemma 3n, Qwen3 small sizes, Llama 3.2 1B and 3B, Phi-4-mini, SmolLM3, and any small model trained specifically for function calling (the Hammer family and similar). For each:

- Parameter count and quantized file size.
- Whether tool calling is a trained capability, and the exact format the model expects tools in and emits calls in.
- Whether it can emit more than one tool call in a turn, and whether it handles a tool result followed by a second call.
- Context window, and how it is told the current date and time.
- Any published evidence about date and time reasoning ("next Friday", "every weekday", "in 3 hours").
- Licence, and in which file formats it is published (so it can be matched to a runtime).

End with a shortlist of at most four models for the bake-off, ordered by how likely each is to pass, and say whether the evidence suggests the model should compute absolute dates itself or hand relative expressions to code.

## Answer

Researched 2026-11-17. Recorded by the charting session from the research agent's committed findings, with the owner's go-ahead (the agent could not write outside its worktree).

- The list in this ticket's question is dated. The relevant small models now are Gemma 4 E2B, Qwen3.5 (0.8B, 2B, 4B), Liquid's LFM2.5 and LFM3-1.7B, and IBM Granite 4.x. Gemma 3 and 3n, Llama 3.2, Phi-4-mini, SmolLM3 and the Hammer family are overtaken or over budget.
- Every shortlisted model has trained tool calling, can emit several tool calls in one turn, and has a template that takes a tool result followed by a second call. FunctionGemma 270M is not trained for the second call without fine-tuning; it is the fine-tune fallback only.
- Memory is not the limit on the Camon 40; speed is. No Helio G99 measurement exists for any candidate. The agent's extrapolation from a Raspberry Pi 5 (same Cortex-A76 cores) is roughly 5 to 20 s per tool call for 2B-class models and 15 to 40 s for 4B models, more if the model thinks first. That is slower than the "few seconds" accepted at charting. Measure decode speed on the phone before running the test set.
- All tool-calling scores are vendor-reported, on full-precision weights and on differing benchmark versions. The one published before-and-after (Llama 3.2) shows plain 4-bit quantization costing 11 to 14 points, so prefer quantization-aware files and test an 8-bit file where it fits.
- No model knows the date; the app must write the current date, time and day of week into the prompt.
- Published evidence on date reasoning (PRIMETIME, arXiv 2504.16155): turning a natural-language datetime into ISO 8601, the best small model measured was right about 72% of the time (Qwen3 4B), a 1.7B model 58%, a 0.6B model 7%. A second paper (arXiv 2507.06450) finds models do more than 10 points better when they write an expression that code resolves. Nobody has published results on "next Friday", "every weekday" or "in 3 hours" for the shortlisted models.
- Shortlist, most likely to pass first: (1) LFM3-1.7B (Liquid AI, 1.05 GB GGUF or 1.44 GB `.litertlm`), (2) Gemma 4 E2B (Google, 2.59 GB `.litertlm`), (3) Qwen3.5-2B (Alibaba, 1.28 GB GGUF), (4) Qwen3.5-4B (Alibaba, 2.58 GB GGUF), the last being the accuracy ceiling and probably too slow. First reserve: LFM2.5-2.6B, which always thinks before answering.
- Date arithmetic verdict: code should do it. The agent passes relative due times, lead times as durations and repeat rules as named cycles, and the app resolves them against the clock. This is evidence for the tool contract ticket, where the owner decides.
- Not settled here or in the runtimes ticket: whether released Android builds of the two shortlisted runtimes can load the `lfm3` and `qwen35` model files. The bake-off must check this first.

Findings: branch `research/small-tool-calling-models` (commit `bcc06db`), file `docs/research/small-tool-calling-models.md`. Read it with `git show research/small-tool-calling-models:docs/research/small-tool-calling-models.md`.
