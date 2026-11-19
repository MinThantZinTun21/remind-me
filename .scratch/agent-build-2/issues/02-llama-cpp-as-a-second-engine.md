# llama.cpp as a second engine on Android

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

What would it take to run GGUF models with llama.cpp inside Remind Me, beside LiteRT-LM?

- The ways to use llama.cpp from a Kotlin Android app today: the official Android example, maintained bindings and libraries, or building it with the NDK ourselves. Which are maintained, and their licences.
- How much it adds to the APK (arm64-v8a only), and how long it takes to build.
- Published speed for 1 to 4 B models at Q4 on a Helio G99 or similar Cortex-A76 phone: tokens per second for reading the prompt and for writing. Also memory use, and whether the read prompt can be kept between messages (prompt cache or session save).
- Tool calls: whether llama.cpp applies the model's own chat template with tools on Android, and whether it parses tool calls back out. Also whether grammar-forced output (GBNF or JSON schema) works on Android, and what it costs in speed.
- Anything known about running LiteRT-LM and llama.cpp in one app: both native, both large.

End with a recommendation on whether llama.cpp is worth adding for this app, and the route that would be used.

## Answer

Researched 2026-11-18. The full findings, with a source link for each fact and estimates labelled as such, are on branch `research/llama-cpp-on-android` (commit e768ad1, local only, not pushed), file `research/llama-cpp-on-android.md`. Nothing was cloned, built or downloaded; library sizes come from reading each AAR's zip directory.

**Recommendation: worth trying, as a spike that must beat LiteRT-LM on the Camon 40.** Build llama.cpp from source as our own small Android library module.

**Route.**
- Pin a release tag and copy the Gradle and CMake setup of the official `examples/llama.android/lib` (MIT, maintained by Arm).
- Build one static `.so` (`BUILD_SHARED_LIBS=OFF`, `GGML_BACKEND_DL=OFF`) for one CPU variant, `armv8.2-a+dotprod+fp16`, with KleidiAI off at first.
- Write our own JNI layer: `common_chat_templates_apply` with `use_jinja=true`, the tools as OpenAI-style JSON and `tool_choice=REQUIRED`; pass the returned grammar to the sampler; read the calls back with `common_chat_parse`; snapshot the fixed instruction with `llama_state_seq_*`; use 2 threads.
- Not chosen: the official module's Kotlin API (no Jinja templates, tools, grammar or saved state), Llamatik (its own tool format, 86 MB AAR) and RunAnywhere (not an open-source licence).

**Size and build.** About 12 MB uncompressed (4.5 MB compressed) for one CPU variant, 17.5 MB for all seven. LiteRT-LM 0.18.1 already adds 22.2 MB. A plain NDK build takes about 5 minutes.

**Speed on the G99.** No published figure. Estimated from a Raspberry Pi 5 (same Cortex-A76 core): a 1.7B Q4_0 model reads about 45 to 60 tokens/s and writes about 9 to 13 tokens/s, against about 35 to 45 tokens/s reading with LiteRT-LM on this phone today. The real gain is keeping the processed instruction between messages, so each message pays only for the new words and the reply.

**Tool calls.** Chat templating, call parsing and grammar-forced output (GBNF or JSON schema) are plain C++ in llama.cpp's `common/` and run on Android; llama.rn ships this exact path. LFM3-1.7B has a GGUF release, and its template should select the `<tool_call><function=` parser; not yet seen on a device. The speed cost of grammar is unmeasured but expected to be small while output stays valid.

**Two engines in one app.** Done before (llamadart). Keep the `.so` self-contained and never load or run both models at once.

**Gate for the bake-off.** Keep llama.cpp only if a complete tool call arrives clearly faster than with LiteRT-LM, with no malformed calls and no crash; otherwise delete the module.
