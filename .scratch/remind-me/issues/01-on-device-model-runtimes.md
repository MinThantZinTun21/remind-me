# On-device model runtimes for the Camon 40

Type: research
Status: resolved
Blocked by: none
Map: [Remind Me v1](../map.md)

## Question

Which runtimes can run a small language model with tool calling inside a native Kotlin app on a Tecno Camon 40 (Helio G99 / MT6789, Mali-G57 GPU, 8 GB RAM, arm64-v8a, Android 16)?

For each serious candidate (at least LiteRT-LM / MediaPipe LLM Inference, llama.cpp, MLC LLM, ExecuTorch, ONNX Runtime GenAI) establish:

- Whether it has a maintained Kotlin or Java API, and how it is added to an Android project.
- CPU and GPU support on this chip; whether GPU is realistic on Mali-G57 or CPU is the practical path.
- Which model file formats it loads.
- Whether it supports tool or function calling directly, and whether it supports constrained output (grammar or JSON schema) so a tool call is always well formed.
- Published speed figures on comparable mid-range chips.
- Licence and current maintenance state.

End with a shortlist of at most two runtimes worth trying in the bake-off, and why.

## Answer

Researched 2026-11-17 against each project's repo, docs and release notes. Recorded by the charting session from the research agent's committed findings, with the owner's go-ahead (the agent could not write outside its worktree).

- CPU is the practical path on this phone. A Google collaborator's advice for a Helio G99 device is the LiteRT-LM CPU backend; where Mali GPUs of this class were measured against their CPU (llama.cpp Vulkan on Mali-G57 and Mali-G68, MNN OpenCL on a G99), the CPU won. The G99 has no NPU, and only 2 of its 8 cores are fast (2x Cortex-A76 + 6x Cortex-A55).
- No runtime publishes a speed figure for the Helio G99. Nearest anchors: LiteRT-LM on a Raspberry Pi 5 (4x A76) runs Gemma-4-E2B at 133 tokens/s prefill and 8 tokens/s decode, 7.8 s to first token; llama.cpp on a Galaxy A34 runs Llama-3.2-1B Q4_0 at 74 prefill and 14 decode. Expect this phone at or below those, so the bake-off must measure time to a complete tool call with the system prompt already cached.
- MediaPipe LLM Inference is retired on Android: its docs redirect to LiteRT-LM.
- Ruled out: ExecuTorch (Android LLM API is experimental, has no tool calling, and its grammar fields are "currently ignored"); ONNX Runtime GenAI (no Maven artifact, constrained output not reachable from the Java binding); MLC LLM (GPU-only, no tagged release since 2023, Android build broken on main); MNN (no Kotlin package, no grammar or JSON-schema constraint).
- Shortlist 1, LiteRT-LM (v0.19.1, Apache-2.0): one Gradle line from Google Maven, a Kotlin API marked Stable, tool calling built in, and JSON-schema constrained output from Kotlin. Weak points: the tool-call constraint is an experimental flag (fixes landed after 0.19.1 was cut), non-Gemma models have open tool-calling bugs, and models must exist as `.litertlm`.
- Shortlist 2, llama.cpp (v1.0.0, MIT): loads GGUF, so nearly any small model or a later fine-tune; GBNF and JSON-schema grammars can make every tool call well formed. Cost: no published Android package, and the in-repo Android module exposes no tools or grammar, so the app owns a small JNI layer (or depends on the one-maintainer Llamatik wrapper). In-process tool calling on Android has no first-party example.
- Why both: they cover each other's weak points, and running one model family through both separates "is the model good enough" from "is the runtime's tool-calling plumbing good enough".
- Unverified: whether LiteRT-LM's GPU backend works at all on Mali-G57 MC2. One line to try in the bake-off.

Findings: branch `research/on-device-model-runtimes` (commit `f9bc416`), file `docs/research/on-device-model-runtimes.md`. Read it with `git show research/on-device-model-runtimes:docs/research/on-device-model-runtimes.md`.
