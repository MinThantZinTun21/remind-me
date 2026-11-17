# Small models that can call tools

Type: research
Status: open
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
