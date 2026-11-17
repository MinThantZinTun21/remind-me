# Model bake-off on the Camon 40

Type: prototype
Status: open
Blocked by: 01, 02, 10
Map: [Remind Me v1](../map.md)

## Question

Which model and runtime, running on the owner's Camon 40, turns the test set into correct tool calls often enough and fast enough?

A throwaway harness that runs each shortlisted model on the phone against the test set and records, per model: share of sentences with fully correct tool calls, the kinds of mistake made (wrong date, wrong repeat rule, malformed call, missed second task, failed two-step change), seconds to load, seconds per message, and memory used.

The owner chooses the winner from the results. The answer names the model, the runtime and the quantization, says whether two-step changes passed or v1 is create-only, and says whether the model or code should do date arithmetic.
