# Tool contract

Type: grilling
Status: open
Blocked by: 02, 06
Map: [Remind Me v1](../map.md)

## Question

What are the exact tools the app offers the agent?

- The name, parameters and return value of each tool (create, list, update, delete).
- How times cross the boundary: does the agent hand over absolute dates and times, or relative expressions ("tomorrow", "in 3 hours", "next Friday") that code resolves? Who does the date arithmetic?
- How repeat rules and lead times are expressed as parameters.
- What the app tells the agent each turn without being asked: current date, time, weekday, time zone, and whether the existing task list is included or must be fetched with the list tool.
- How the agent points at an existing task when updating or deleting, and what happens when its description matches several tasks or none.
- What a tool returns on bad input, and whether the agent gets a second attempt.
