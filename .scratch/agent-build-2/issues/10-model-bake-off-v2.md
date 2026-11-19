# Model bake-off v2 on the Camon 40

Type: prototype
Status: open
Blocked by: 09
Map: [Remind Me agent build 2](../map.md)

## Question

Which model and engine should be the default for build 2? Is llama.cpp worth adding?

Run the shortlisted models from [Mobile models that make tool calls](01-mobile-models-that-make-tool-calls.md) on the phone against [Agent test set v2](09-agent-test-set-v2.md). Use LiteRT-LM, and llama.cpp if [llama.cpp as a second engine](02-llama-cpp-as-a-second-engine.md) says it is workable. For each model, record:

- The score per property.
- Seconds from Send to the action card, with a kept conversation of 1, 3 and 6 turns.
- Load time and memory use.
- Context used.

The owner picks the default model, and decides whether the second engine goes into the build. The 30-second limit applies.
