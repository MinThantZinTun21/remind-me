# Build the agent test set

Type: task
Status: open
Blocked by: 05, 07
Map: [Remind Me v1](../map.md)

## Question

What is the set of sentences, with the correct tool calls for each, that candidate models are judged against?

Manual work the agent can do alone (AFK), with the owner checking the result: expand the owner's real sentences to about 50 in the same voice, fix a reference date and time for the whole set, and write the expected tool calls for each sentence in the format the tool contract defines. Include cases that should produce a question back and cases that should be declined. State how a response is scored as correct.

Resolved when the test set file exists in the repo and the owner has looked it over.
