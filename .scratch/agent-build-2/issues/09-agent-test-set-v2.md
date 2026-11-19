# Agent test set v2

Type: task (AFK)
Status: open
Blocked by: 04
Map: [Remind Me agent build 2](../map.md)

## Question

Against what set of messages, with what expected results, is a model judged for this build?

Work for the agent alone, with the owner checking the result:

- Expand the owner's messages to about 60, in the same voice.
- Fix a reference date and time for the whole set.
- Write the expected tool calls for each message in the v2 contract, including multi-turn follow-ups.
- State how a reply is scored. Score each property separately: title, due, repeat and days, warning, and style.

Then make `AgentOnPhoneTest` able to read the set and print the score.

Resolved when the set and scoring are in the repo, and the owner has looked them over.
