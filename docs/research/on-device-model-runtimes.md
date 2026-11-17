# On-device model runtimes for the Camon 40

Research for ticket `01-on-device-model-runtimes`. Checked on 2026-11-17 against each project's own repo, release notes and docs. Repo facts are from the default branch at these commits: LiteRT-LM `8ccdff3de9`, llama.cpp `9581154a22`, ExecuTorch `4eba23c441`, ONNX Runtime GenAI `314f3f68a8`, MLC LLM `87a90ce002`, MNN `5b471144f4`.

Anything marked **unverified** could not be confirmed from a primary source. Anything marked **inference** is my reasoning from cited figures, not a measurement.

## Answer in short

**Shortlist for the bake-off: LiteRT-LM first, llama.cpp second.** Run both on CPU. Treat the GPU as an optional experiment, not the plan.

- **LiteRT-LM** is the only candidate that gives a Kotlin app everything from one Gradle line: a stable Kotlin API, tools declared as annotated Kotlin functions, a tool-calling loop, and JSON-schema constrained output. It is Google's replacement for the MediaPipe LLM Inference API, and a Google collaborator's advice for the Helio G99 specifically is to use its CPU backend.
- **llama.cpp** is the fallback and the control. It loads GGUF, so it can run nearly any small model (including a fine-tune, if the bake-off ends there), its grammar system can force every tool call to be well formed, and its Arm CPU path is the best documented. The cost is that there is no official Kotlin package: you write a small JNI layer or take a third-party wrapper.

Not shortlisted: ExecuTorch (no constrained output, no tool calling in the Android API), ONNX Runtime GenAI (constrained output not reachable from the Java binding, AAR is not on Maven), MLC LLM (GPU-only, Android build currently broken on main), MNN (no Kotlin package, no grammar), MediaPipe LLM Inference (retired in favour of LiteRT-LM).

**No runtime publishes a speed figure for the Helio G99.** Every number below is from a different chip. The bake-off has to measure it.

## The device, as it matters here

- Helio G99: 2x Cortex-A76 at up to 2.2 GHz plus 6x Cortex-A55 at up to 2.0 GHz, Mali-G57 MC2 GPU, LPDDR4X. MediaTek's product page lists no NPU or APU. ([MediaTek](https://www.mediatek.com/products/smartphones/mediatek-helio-g99))
- The CPU has NEON, dotprod and fp16 but no SVE: a llama.cpp contributor building for this chip family reports compile flags of `-march=armv8.2-a+dotprod+fp16` on "Cortex-A76, no SVE". ([llama.cpp #22861](https://github.com/ggml-org/llama.cpp/pull/22861))
- So only two of the eight cores are fast. Runtimes that scale across "big" cores have two to work with.
- No vendor NPU path applies. ExecuTorch's MediaTek backend targets Dimensity 9300 and 9400 only ([docs](https://github.com/pytorch/executorch/blob/main/docs/source/backends-mediatek.md)), and ExecuTorch 1.6.0 lists the MediaTek backend as "temporarily unsupported" ([release notes](https://github.com/pytorch/executorch/releases/tag/v1.6.0)).

## Comparison

| | LiteRT-LM | llama.cpp | ExecuTorch | ONNX Runtime GenAI | MLC LLM | MNN |
|---|---|---|---|---|---|---|
| Kotlin/Java API | Kotlin, marked Stable | Kotlin sample module in-repo; no published package | Java/Kotlin `LlmModule`, marked experimental | Java binding | Kotlin `MLCEngine` | None documented for LLM |
| How added | `com.google.ai.edge.litertlm:litertlm-android` on Google Maven | Build the in-repo module with NDK, or a third-party Maven wrapper | `org.pytorch:executorch-android` on Maven Central | AAR downloaded from GitHub release, or source build | Source build via `mlc_llm package` | Source build of `.so` plus own JNI |
| CPU on G99 | Yes (XNNPACK) | Yes (NEON, optional KleidiAI) | Yes (XNNPACK with KleidiAI) | Yes | Not a supported path | Yes |
| GPU on Mali-G57 | OpenCL; unverified on this GPU | OpenCL is Adreno-only; Vulkan is slower than CPU on Mali | Vulkan; unverified on this GPU | No Mali GPU path found | OpenCL, the only path | OpenCL; slower than CPU on G99 in one report |
| Model format | `.litertlm` | GGUF | `.pte` | ONNX folder with `genai_config.json` | MLC-compiled weights plus model library | `.mnn` |
| Tool calling built in | Yes, with automatic loop | Yes in C++ (`common/chat`), not in the Kotlin sample | No | Via grammar, C/C++/Python | Yes (OpenAI-style) | Template-level only |
| Constrained output | JSON schema and regex in Kotlin; tool-call constraint behind an experimental flag | GBNF, JSON schema, tool-call grammar | Fields reserved, "currently ignored" | Lark, JSON schema, regex via LLGuidance; not in Java | JSON schema field; strict tool calls still an open PR | None documented |
| Licence | Apache-2.0 | MIT | BSD | MIT | Apache-2.0 | Apache-2.0 |
| Latest release | v0.19.1, 2026-11-05 | v1.0.0, 2026-11-13 | v1.6.0, 2026-11-12 | v0.20.0, 2026-11-14 | No tagged release since 2023 | 3.6.2, 2026-10-29 |

Sources for each cell are in the sections below.

## LiteRT-LM (and MediaPipe LLM Inference)

**MediaPipe LLM Inference is retired on Android.** Its guide URLs (`.../mediapipe/solutions/genai/llm_inference`, `.../llm_inference/android`) now 301-redirect to the LiteRT-LM site, and the last `tasks-genai` version on Google Maven is 0.10.35 ([redirect target](https://developers.google.com/edge/litert-lm), [Google Maven index](https://dl.google.com/android/maven2/com/google/mediapipe/group-index.xml)). The separate AI Edge Function Calling SDK guide (`.../mediapipe/solutions/genai/function_calling/android`) returns 404. Treat LiteRT-LM as the only Google option.

**API and integration.**
- The README lists Kotlin as "Stable", for "Android apps & JVM". ([README](https://github.com/google-ai-edge/LiteRT-LM/blob/main/README.md))
- One dependency: `implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")`. ([Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)) Google Maven has 0.19.1; the AAR is about 21 MB. ([index](https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/group-index.xml))
- Versions 0.17.0 onward carry Kotlin metadata 2.4.0, so the app module needs Kotlin 2.3 or newer. ([Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md))
- The API is `Engine` → `Conversation` → `sendMessage` / `sendMessageAsync` (callback or Kotlin `Flow`). `engine.initialize()` can take "up to 10 seconds" and must run off the main thread. (same guide)

**CPU and GPU.**
- Backends are `Backend.CPU()`, `Backend.GPU()` and `Backend.NPU(...)`. GPU on Android needs `libOpenCL.so` declared in the manifest, so the GPU path is OpenCL. (same guide)
- A user on a Helio G99 phone (Galaxy A16) reported Gemma-4-E2B as too heavy for an agent loop; the Google collaborator's reply was to use `Backend.CPU()` and lower the maximum sequence length. ([LiteRT-LM #3298](https://github.com/google-ai-edge/LiteRT-LM/issues/3298))
- Google will not maintain a GPU denylist; apps are expected to build their own, and "most of the time, our liteRT-LM default to CPU". ([LiteRT-LM #2460](https://github.com/google-ai-edge/LiteRT-LM/issues/2460), a mid-range MediaTek phone whose GPU produced garbage while CPU was correct)
- Even on a flagship, Google's own table shows GPU helping prefill far more than decode: Gemma3-1B on a Galaxy S24 Ultra decodes at 33 tk/s on CPU and 24 tk/s on GPU. ([overview](https://developers.google.com/edge/litert-lm/overview))
- **Whether the GPU backend works at all on Mali-G57 MC2 is unverified.** I found no report for this GPU in the google-ai-edge repos.

**Model format.** `.litertlm` only. Ready-made files are published under [huggingface.co/litert-community](https://huggingface.co/litert-community) and Google's own model repos; other models need converting and packaging with `litert-lm-builder`. ([builder doc](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/litert_lm_builder.md)) The officially listed chat models include Gemma4-E2B (2583 MB), Gemma3-1B (1005 MB), Qwen2.5-1.5B (1598 MB), Qwen3-0.6B (586 MB) and FunctionGemma (289 MB). ([overview](https://developers.google.com/edge/litert-lm/overview))

**Tool calling.**
- Built in. Tools are a class implementing `ToolSet` with `@Tool` and `@ToolParam` annotations, or an `OpenApiTool` with a hand-written JSON spec. Parameter types are limited to `String`, `Int`, `Boolean`, `Float`, `Double` and lists of those. ([Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md))
- By default the library runs the tool and feeds the result back to the model itself. Setting `automaticToolCalling = false` returns `message.toolCalls` to the app instead, which is what an action card with Undo needs. (same guide)
- It "only works with models with tool support". (same guide)

**Constrained output.**
- JSON schema and regex are available from Kotlin: set `enableResponseFormat = true` on `ConversationConfig`, then pass `ResponseFormat.json(schema)` or `ResponseFormat.regex(pattern)` to `sendMessage`. It is backed by LLGuidance. ([Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt), [ResponseFormat.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/ResponseFormat.kt)) `ResponseFormat.kt` is present at the v0.19.1 tag.
- Constraining the tool call itself is behind `ExperimentalFlags.enableConversationConstrainedDecoding`, documented as "primarily used for function calling" and "may change or be removed without notice". It is present at v0.19.1. ([ExperimentalFlags.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt))
- A `ToolChoice` type (auto, none, required, forced tool, allowed tools) exists on main but **not** at the v0.19.1 tag, so it is not in the current Maven release. ([ToolChoice.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/ToolChoice.kt))
- Lark grammars are supported in C++ but the Kotlin `ResponseFormat` exposes only JSON schema and regex. ([C++ doc](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/constrained-decoding.md))
- Field evidence that the JSON-schema path works in the shipped Android AAR: on 0.18.0 with speculative decoding off, 59 of 60 calls returned schema-valid JSON on a Galaxy S25 FE. The same report shows it failing completely when combined with speculative decoding; that was fixed on main on 2026-10-19. ([LiteRT-LM #3944](https://github.com/google-ai-edge/LiteRT-LM/issues/3944))

**Known rough edges relevant to the bake-off.**
- Open: with Qwen2.5-1.5B on CPU and automatic tool calling, the engine stopped responding after the first one or two tool calls (reported on 0.10.2). ([LiteRT-LM #2256](https://github.com/google-ai-edge/LiteRT-LM/issues/2256))
- Tool-call parsing is per model family and still being added; a Qwen3.5 parser landed in October 2026. ([LiteRT-LM #3927](https://github.com/google-ai-edge/LiteRT-LM/issues/3927))
- The repo has 787 open issues against a pre-1.0 version number. ([repo](https://github.com/google-ai-edge/LiteRT-LM))

**Licence and maintenance.** Apache-2.0. Four releases in the six weeks to 2026-11-05; commits daily. Described as "production-ready" and used in Chrome, Chromebook Plus and Pixel Watch. ([README](https://github.com/google-ai-edge/LiteRT-LM/blob/main/README.md), [releases](https://github.com/google-ai-edge/LiteRT-LM/releases))

## llama.cpp

**API and integration.**
- No published Android package. The v1.0.0 release has no Android asset. ([release](https://github.com/ggml-org/llama.cpp/releases/tag/v1.0.0))
- The repo ships an Android library module at `examples/llama.android/lib` (package `com.arm.aichat`, contributed by Arm). You import it into Android Studio and it builds llama.cpp with CMake and the NDK. It sets `minSdk = 33`, `arm64-v8a` and `x86_64`, and enables KleidiAI and all CPU variants. ([android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md), [build.gradle.kts](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/build.gradle.kts), [CMakeLists.txt](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/src/main/cpp/CMakeLists.txt))
- That module's Kotlin interface is chat-only: `loadModel`, `setSystemPrompt`, `sendUserPrompt(): Flow<String>`, `bench`. It exposes no tools, no grammar and no sampler settings. ([InferenceEngine.kt](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/src/main/java/com/arm/aichat/InferenceEngine.kt))
- It does link `llama-common`, which contains the tool-calling and grammar code, so extending its one JNI file (`ai_chat.cpp`) is the realistic route. This is a few hundred lines of C++ the project would own.
- Third-party option: Llamatik, a Kotlin Multiplatform wrapper on Maven Central (`com.llamatik:core`, 2.4.0 published 2026-11-16, MIT code). It offers `generateJson(prompt, jsonSchema)` and an agent/tool layer. It is essentially a one-person project (229 of its commits from one author) and pins llama.cpp v0.9.0. ([README](https://github.com/ferranpons/Llamatik/blob/main/README.md), [Maven](https://repo1.maven.org/maven2/com/llamatik/core/maven-metadata.xml)) Its tool-calling quality is unverified.
- `kherud/java-llama.cpp` has had no push since 2025-06-20. ([repo](https://github.com/kherud/java-llama.cpp))

**CPU and GPU.**
- CPU is the supported Android path. The Android binding "supports hardware acceleration up to SME2 for Arm" and "automatically detects the host's hardware to load compatible kernels", running on "older devices that may lack modern CPU features or have limited RAM". ([android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md))
- The OpenCL backend is "designed to enable llama.cpp on Qualcomm Adreno GPU"; its verified list is Adreno only. ([OPENCL.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md))
- Vulkan does run on Mali but loses to the CPU. On a phone with a Mali-G57, a 7B Q4_0 model generated at 0.86 t/s on Vulkan against 2.29 t/s on CPU. On a Galaxy A34 (Mali-G68 MC4), Llama-3.2-1B Q4_0 generated at 6.75 t/s on Vulkan against 14.23 t/s on CPU, and prompt processing was 4.74 t/s against 73.90 t/s. ([discussion #10879, Mali-G57](https://github.com/ggml-org/llama.cpp/discussions/10879#discussioncomment-11810293), [Mali-G68](https://github.com/ggml-org/llama.cpp/discussions/10879#discussioncomment-12226306))
- Mali Vulkan crashes are still being filed in October 2026. ([#30920](https://github.com/ggml-org/llama.cpp/issues/30920))
- **Verdict: CPU only on this phone.**

**Model format.** GGUF. This is the format most small models are published in first, and the one fine-tuning toolchains export to.

**Tool calling and constrained output.**
- `common/chat.h` is described in its own header as "Chat support (incl. tool call grammar constraining & output parsing)". Given the model's Jinja chat template and a list of tools, `common_chat_templates_apply` returns the prompt plus a grammar and a parser; `common_chat_parse` turns the output into tool calls. ([chat.h](https://github.com/ggml-org/llama.cpp/blob/master/common/chat.h))
- "Function calling is supported for all models": native handlers for Llama 3.x, Hermes, Qwen 2.5, Mistral Nemo and others, and a generic fallback for any other template. ([function-calling.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/function-calling.md))
- Any output can be constrained with a GBNF grammar, and JSON schemas are converted to grammars. Some schema features are unsupported and "skipped silently". ([grammars/README.md](https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md))
- The docs describe tool calling through `llama-server`. Using it in-process from an Android app means calling the same C++ functions through JNI; that works in principle because the code is in `llama-common`, but **I found no first-party Android example of it (unverified end to end)**.
- The docs warn that heavy KV-cache quantisation "can substantially degrade the model's tool calling performance". ([function-calling.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/function-calling.md))

**Licence and maintenance.** MIT. v1.0.0 on 2026-11-13, now on semantic versioning with a rule that breaking changes to `llama.h` bump the major version. Several builds a day. ([release](https://github.com/ggml-org/llama.cpp/releases/tag/v1.0.0), [release.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/release.md)) Note that `common/` (where the tool-calling code lives) is not covered by that API promise.

## ExecuTorch

- **API.** `org.pytorch:executorch-android` on Maven Central (1.5.1 is the latest there; 1.6.0 is tagged but not yet published). The AAR ships `arm64-v8a` and `x86_64` with the XNNPACK backend. ([Android guide](https://github.com/pytorch/executorch/blob/main/docs/source/using-executorch-android.md), [Maven](https://repo1.maven.org/maven2/org/pytorch/executorch-android/maven-metadata.xml))
- The LLM API is `LlmModule`: load a `.pte` and a tokenizer, call `generate(prompt, config, callback)`. The docs say "This API is experimental and subject to change." ([run-on-android.md](https://github.com/pytorch/executorch/blob/main/docs/source/llm/run-on-android.md))
- **CPU and GPU.** CPU via XNNPACK with KleidiAI on by default for Arm. A Vulkan backend exists with its own artifact (`executorch-android-vulkan`), needs Vulkan 1.1, and 1.6.0 says "Tuned kernels for Mali and Adreno". LLM performance on Mali-G57 MC2 is unverified. ([llama README](https://github.com/pytorch/executorch/blob/main/examples/models/llama/README.md), [Vulkan overview](https://github.com/pytorch/executorch/blob/main/docs/source/backends/vulkan/vulkan-overview.md), [release notes](https://github.com/pytorch/executorch/releases/tag/v1.6.0))
- **Format.** `.pte`, produced by exporting a PyTorch model per backend. There is no GGUF-style drop-in catalogue; the export step is yours.
- **Tool calling and constrained output.** Neither. `LlmModule` takes a raw prompt string and has no tool or template parameters. `GenerationConfig` has `grammar` and `grammar_type` fields documented as "Reserved for constrained decoding; currently ignored", and "No in-tree runner currently implements `grammar` or `grammar_type`." ([runner README](https://github.com/pytorch/executorch/blob/main/extension/llm/runner/README.md))
- **Speed.** OnePlus 12, Llama 3.2 1B SpinQuant: 50.2 tok/s decode, 260.5 tok/s prefill, 0.3 s to first token, 1921 MiB RSS. ([llama README](https://github.com/pytorch/executorch/blob/main/examples/models/llama/README.md)) Flagship only.
- **Licence and maintenance.** BSD. Very active; v1.6.0 on 2026-11-12. ([LICENSE](https://github.com/pytorch/executorch/blob/main/LICENSE))
- **Why not.** The app would have to write its own prompt templating, tool-call parsing and constrained decoding, and export every candidate model itself. That is the whole job the shortlisted runtimes already do.

## ONNX Runtime GenAI

- **API.** A Java binding exists (`ai.onnxruntime.genai`), but the README's support matrix says Java "Requires build from source". The v0.20.0 release does attach `onnxruntime-genai-android-0.20.0.aar`, which you would add as a local file. Nothing is on Maven Central under `com.microsoft.onnxruntime`. ([README](https://github.com/microsoft/onnxruntime-genai/blob/main/README.md), [release assets](https://github.com/microsoft/onnxruntime-genai/releases/tag/v0.20.0), [Maven directory](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/))
- **CPU and GPU.** The README lists CPU, CUDA, DirectML, TensorRT-RTX, OpenVINO, QNN and WebGPU. None of these is a Mali GPU path on Android except possibly WebGPU, which is unverified. CPU is the practical path.
- **Format.** An ONNX model folder with a GenAI config, produced by the project's model builder.
- **Tool calling and constrained output.** Strong in the core: LLGuidance with Lark grammar, JSON schema and regex, described as "useful when using function/tool calling". But it needs a build with `USE_GUIDANCE=ON`, and I found no guidance method in the Java binding: `GeneratorParams.java` has only search and speculative options, and a code search of `src/java` for "guidance" returns no API file. Whether the released AAR is built with guidance is unverified. ([ConstrainedDecoding.md](https://github.com/microsoft/onnxruntime-genai/blob/main/docs/ConstrainedDecoding.md), [GeneratorParams.java](https://github.com/microsoft/onnxruntime-genai/blob/main/src/java/src/main/java/ai/onnxruntime/genai/GeneratorParams.java))
- **Speed.** No Android figures found in the repo or docs (unverified).
- **Licence and maintenance.** MIT. Active; v0.20.0 on 2026-11-14. Focus is Windows and desktop (it "powers Foundry Local, Windows ML").
- **Why not.** The one feature that would justify it is not reachable from Kotlin without extending the binding.

## MLC LLM

- **API.** Kotlin `MLCEngine` with an OpenAI-style request, including `tools` and a `response_format` with a `schema` field. ([OpenAIProtocol.kt](https://github.com/mlc-ai/mlc-llm/blob/main/android/mlc4j/src/main/java/ai/mlc/mlcllm/OpenAIProtocol.kt))
- No Maven artifact. You run `mlc_llm package`, which needs Rust, the NDK, a JDK and the TVM source tree, and produces an `mlc4j` Gradle subproject. ([Android docs](https://llm.mlc.ai/docs/deploy/android.html))
- **CPU and GPU.** GPU only in practice: "MLC LLM needs an actual mobile GPU to meaningfully run at an accelerated speed", and the Android build sets `USE_OPENCL=ON`. The README lists "OpenCL on Mali GPU" as supported. ([Android docs](https://llm.mlc.ai/docs/deploy/android.html), [prepare_libs.py](https://github.com/mlc-ai/mlc-llm/blob/main/android/mlc4j/prepare_libs.py), [README](https://github.com/mlc-ai/mlc-llm/blob/main/README.md)) Its demo APK is built for a Snapdragon 8 Gen 2.
- **Format.** MLC-converted weights plus a model library compiled per target.
- **Tool calling and constrained output.** Tool calling exists but is weak: Qwen3 models "could not recognize the tool" (open since 2025-09), and the PR to make tool calls obey a strict format has been open since 2025-03. ([#3344](https://github.com/mlc-ai/mlc-llm/issues/3344), [#3190](https://github.com/mlc-ai/mlc-llm/pull/3190))
- **Maintenance.** Commits continue (latest 2026-11-16), but there is no tagged GitHub release since 2023, and two open issues report the Android build broken on main: a version skew with TVM (2026-09-23) and a compile crash for `android:generic` targeting a Mali-G57 (2026-10-30). ([#3552](https://github.com/mlc-ai/mlc-llm/issues/3552), [#3587](https://github.com/mlc-ai/mlc-llm/issues/3587))
- **Licence.** Apache-2.0.
- **Why not.** It bets everything on the weakest part of this phone, and its Android toolchain does not currently build cleanly.

## Other candidates looked at

- **MNN (Alibaba).** Apache-2.0, active (3.6.2 on 2026-10-29). Its LLM docs describe building `libMNN.so` and `libllm.so` from source; no Maven package or Kotlin API is documented, and no grammar or JSON-schema constraint is documented, only `logit_bias` and banned tokens. ([MNN LLM docs](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html)) One user measured OpenCL slower than CPU on a Helio G99 (2.5 s against 1.5 s for the same model). ([MNN #3942](https://github.com/alibaba/MNN/issues/3942)) Not shortlisted.
- **Cactus.** The repo is now a Python package ("Run text, speech and vision models locally with one Python package"). Not a Kotlin candidate today. ([README](https://github.com/Cactus-Compute/cactus/blob/main/README.md))
- **Gemini Nano / AICore.** Excluded by the brief: not available on this device.

## Speed: what is published, and what it implies

No project publishes a Helio G99 figure, and my searches of each repo's issues found none with tokens-per-second on that chip. The nearest published points:

| Source | Device (CPU) | Runtime, model | Prefill | Decode |
|---|---|---|---|---|
| [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) | Raspberry Pi 5 (4x Cortex-A76) | LiteRT-LM CPU, Gemma-4-E2B (2.58 GB) | 133 tk/s | 8 tk/s, 7.8 s to first token |
| [llama.cpp discussion](https://github.com/ggml-org/llama.cpp/discussions/10879#discussioncomment-12226306) | Galaxy A34 (mid-range MediaTek, Mali-G68 MC4) | llama.cpp CPU, 8 threads, Llama-3.2-1B Q4_0 | 73.9 t/s | 14.2 t/s |
| [llama.cpp discussion](https://github.com/ggml-org/llama.cpp/discussions/10879#discussioncomment-11810293) | Phone with Mali-G57, "4x A76 + 4x A55" | llama.cpp CPU, Llama-2-7B Q4_0 | 2.4 t/s | 2.3 t/s |
| [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) | Galaxy S24 Ultra | LiteRT-LM CPU, Gemma3-1B | 177 tk/s | 33 tk/s |
| [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) | Galaxy S25 Ultra | LiteRT-LM CPU, Qwen2.5-1.5B | 298 tk/s | 34 tk/s |
| [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) | Galaxy S25 Ultra | LiteRT-LM CPU, FunctionGemma 270M | 2238 tk/s | 154 tk/s |
| [ExecuTorch llama README](https://github.com/pytorch/executorch/blob/main/examples/models/llama/README.md) | OnePlus 12 | ExecuTorch XNNPACK, Llama-3.2-1B SpinQuant | 260.5 tok/s | 50.2 tok/s |

What this implies for the Camon 40 (**inference, not measurement**):

- The Raspberry Pi 5 row is the closest official anchor: same core type, but four fast cores at a higher clock where the G99 has two. Expect the G99 to be slower than that row, so a 2.6 GB model is likely in the low single digits of tokens per second with a first-token wait near or above 10 seconds on a cold prompt.
- A 1B-class model at 4-bit is likely around 10 tokens per second decode on this phone, going by the Galaxy A34 row.
- For this app, prefill matters more than decode. A tool call is perhaps 50 to 100 output tokens, but every message carries the system prompt and tool descriptions in front of it. At 70 to 130 tokens per second, a 700-token prompt costs 5 to 10 seconds unless the fixed prefix is processed once and kept. LiteRT-LM has `prefillPrefaceOnInit` on `ConversationConfig` for this ([Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt)); llama.cpp keeps the KV cache between calls on one context. The bake-off should measure time to a complete tool call with the prefix already cached, not raw decode speed.

## GPU or CPU on this phone

**CPU is the practical path.** The evidence, in order of weight:

1. Google's own advice for a Helio G99 device is `Backend.CPU()`. ([LiteRT-LM #3298](https://github.com/google-ai-edge/LiteRT-LM/issues/3298))
2. Where Mali GPUs of this class have been measured against their CPU, the CPU won: llama.cpp Vulkan on Mali-G57 and Mali-G68, and MNN OpenCL on a G99. (sources above)
3. llama.cpp's faster GPU backend (OpenCL) does not target Mali. ([OPENCL.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md))
4. Mid-range GPU drivers are a known source of wrong output and crashes, and Google leaves the denylist to the app. ([LiteRT-LM #2460](https://github.com/google-ai-edge/LiteRT-LM/issues/2460))

The one GPU experiment worth five minutes in the bake-off is `Backend.GPU()` in LiteRT-LM, because it costs one line to try and LiteRT-LM's GPU prefill is where its published speedups are largest. Its behaviour on Mali-G57 MC2 is unverified.

## Why these two for the bake-off

**LiteRT-LM** is the default choice because it removes the most work and risk from the app:

- A stable, first-party Kotlin API from Google Maven. No NDK, no C++.
- Tools declared in Kotlin, with a manual mode that hands tool calls back to the app.
- JSON-schema constrained output proven in the shipped Android AAR, and a tool-call constraint flag.
- Backed by the vendor that retired the previous option in its favour, with a release every two to three weeks.
- Its weak points are the ones a bake-off exposes quickly: tool-call constraint is experimental, non-Gemma models have open tool-calling bugs, and the model must exist as `.litertlm`.

**llama.cpp** earns the second slot because it covers exactly those weak points:

- GGUF means the bake-off is not limited to models someone has packaged for LiteRT-LM, and a fine-tuned model (one of the map's fallbacks) can be loaded the day it is trained.
- Grammar-constrained decoding is mature and can be made total: a GBNF grammar or a tool-call grammar leaves the model no way to emit a malformed call.
- It is the most widely used CPU runtime on Arm, MIT licensed, and just reached 1.0.
- Its cost is integration: a JNI layer the project owns, or a dependency on a small third-party wrapper.

Running both also separates two questions the bake-off would otherwise confuse: whether a model is good enough, and whether a runtime's tool-calling plumbing is good enough. The same model family (for example Qwen 2.5 or Gemma) can be run through both.

## What stays open

- **Actual speed on the Helio G99**, for any runtime and model. Nothing published; must be measured.
- **Whether LiteRT-LM's GPU backend runs correctly on Mali-G57 MC2.** No report found.
- **Whether LiteRT-LM's experimental tool-call constraint works with the non-Gemma models** on the 0.19.1 AAR. Recent fix PRs in this area (nested schemas, whitespace loops, argument order) were closed on 2026-11-10 and 2026-11-11, after 0.19.1 was cut on 2026-11-05, so they are not in the current Maven release. ([#4534](https://github.com/google-ai-edge/LiteRT-LM/pull/4534), [#4467](https://github.com/google-ai-edge/LiteRT-LM/pull/4467))
- **llama.cpp tool calling in-process on Android.** The code is linked into the Android library but the documented path is the server; no first-party in-app example found.
- **Whether the released ONNX Runtime GenAI AAR includes guidance support.** Not checked beyond the source.
- **16 KB page-size compatibility** of each prebuilt native library on Android 16. Not checked for any runtime.
- **Model licences** (Gemma terms, Qwen, Llama) are out of scope here and belong to the model ticket.
