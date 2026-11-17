# Conversation behaviour

Type: grilling
Status: open
Blocked by: 07
Map: [Remind Me v1](../map.md)

## Question

How does the agent behave in conversation?

- What the agent says after acting, and what the action card shows beside it.
- The guessing policy: which time of day it picks when none is given, what it does with a time that has already passed today, how it chooses a lead time for different kinds of task, and when it chooses an alarm.
- Exactly when it asks a question back instead of guessing, and how the owner's answer is tied to the earlier message.
- How much earlier conversation the model is shown, and whether the conversation is kept between app launches.
- What the owner sees while the model is working, given a wait of several seconds.
- What happens when the model produces an invalid tool call, no tool call, or crashes: retry, explain, or hand over to the manual form.
- How it declines off-topic text.
- What Undo restores for each tool (created, changed, deleted).
