# Conversation and the context budget

Type: grilling
Status: resolved
Blocked by: none
Map: [Remind Me agent build 2](../map.md)

## Question

What goes into the model's context for each message once the conversation is kept for the visit, and what happens when it does not fit?

- The parts and their sizes: instruction, tools, coming days, task list, earlier messages, and what the agent did. Today these add up to about 1,300 to 1,500 tokens, against 2,048 in the current model file. Give a budget per part for each candidate context size: 2k, 4k and 8k.
- What is kept from earlier turns: the user's words, the agent's tool calls, or a short line per action card ("created id 11 Call mom Thu 09:00"). Also what is dropped first when the context is full.
- The task list: which tasks are shown when there are many (today the limit is 15), and whether the agent can ask for more.
- How Undo and Edit made on a card during the conversation reach the model before the next message.
- Whether the model is kept loaded between messages in one visit, or reloaded each time as now (LiteRT-LM issue #3165). What each choice costs in seconds.

## Answer

Settled with the owner on 2026-11-18; the owner accepted every recommendation.

**Budget.** Measured on the Camon 40 the same day: 1,363 tokens in context for one message with today's prompt and a short task list. The split below is estimated at about 4 characters a token from `Agent.kt`, not counted by the model's tokenizer.

| Part | Today | After [Tool contract v2](05-tool-contract-v2.md) (estimate) |
|---|---|---|
| Tool definitions | ~570 | ~420 (fewer fields) |
| Rules | ~480 | ~250 (no date or repeat arithmetic to explain) |
| Header, today and coming days | ~170 | ~60 (the app works out dates) |
| Each task | ~28 | ~28 |
| The message and the reply | ~150 | ~150 |

That leaves about 1,100 tokens for tasks and the conversation even at 2,048: 15 tasks take about 420, and one earlier exchange about 35. A bigger context buys more of both; the conversation itself does not need one.

1. **What is kept from each earlier exchange:** the user's words and one short line per action card ("created id 11 Call mom, Thu 19 Nov 09:00, once"). The model's raw tool calls are not kept.
2. **How much:** as many exchanges as fit, oldest dropped first. If the message still does not fit, the task list shrinks next; the rules and tools never do.
3. **Which tasks the model sees:** those touched in this conversation always, then the rest soonest due first, up to what fits (about 15 at 2K, 40 at 4K). The model cannot ask for more. A task it cannot see gets the card "Couldn't find that task", and the user acts on the home screen.
4. **Undo and Edit on a card mid-conversation:** the app rewrites that card's line before the next message ("… (undone by you)", "(you changed it to Fri 09:00)") and rebuilds the task list, so the model never works from something already changed.
5. **Loading and reading.** Until LiteRT-LM 0.20 (issue #3165), the model is loaded fresh for each message. The instruction, task list and conversation so far are read in the background as soon as the last card is shown, while the user types. After Send the wait is the new words (about 1 s per 45 tokens) and the reply. If Send comes before the read is done, the button shows "Getting ready…" and the message goes when it finishes. The 30 seconds count from Send, so a first message right after opening the screen may exceed them; the bake-off measures this.
6. **What ends a visit:** only leaving the agent screen (back, or another screen in the app). Locking the phone or switching apps and coming back is the same visit. If Android ends the app in the background, the conversation is lost and nothing else.
