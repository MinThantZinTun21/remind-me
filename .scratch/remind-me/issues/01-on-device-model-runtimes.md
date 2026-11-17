# On-device model runtimes for the Camon 40

Type: research
Status: open
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
