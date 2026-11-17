package app.remindme.agent

/** One tool call as the agent wrote it: the tool's name and its arguments, in the order given. */
data class ToolCall(val name: String, val args: Map<String, String>)

/**
 * What a reply holds: the tool calls, the text outside them (what the agent says to the user), and a
 * problem when part of the reply could not be read as a call.
 */
data class ParsedReply(val calls: List<ToolCall>, val answer: String, val problem: String?)

/**
 * Reads the agent's reply. The model on the phone (LFM3) writes each call as text:
 * ```
 * <tool_call>
 * <function=create_task>
 * <parameter=title>
 * Call the bank
 * </parameter>
 * </function>
 * </tool_call>
 * ```
 * The runtime hands that text back untouched for this model, so the app reads it here.
 */
object ReplyParser {
    private val thought = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private val call = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
    private val function = Regex("<function=([^>\\n]+)>(.*?)</function>", RegexOption.DOT_MATCHES_ALL)
    private val parameter = Regex("<parameter=([^>\\n]+)>\\n?(.*?)\\n?</parameter>", RegexOption.DOT_MATCHES_ALL)

    /** Reads [text]. While the reply is still arriving ([complete] false) a call left open is not a problem yet. */
    fun parse(text: String, complete: Boolean = true): ParsedReply {
        // An empty thought, or a stray end of one, can come before the answer even with thinking off.
        val clean = thought.replace(text, "").replace("</think>", "").replace("<think>", "")
        val calls = mutableListOf<ToolCall>()
        var problem: String? = null
        for (match in call.findAll(clean)) {
            val found = function.find(match.groupValues[1])
            if (found == null) {
                problem = "The agent wrote a tool call without naming a tool."
                continue
            }
            val args = LinkedHashMap<String, String>()
            for (p in parameter.findAll(found.groupValues[2])) args[p.groupValues[1].trim()] = p.groupValues[2].trim()
            calls += ToolCall(found.groupValues[1].trim(), args)
        }
        var rest = call.replace(clean, "")
        if ("<tool_call>" in rest) {
            if (complete) problem = "The agent's reply stopped in the middle of a tool call."
            rest = rest.substringBefore("<tool_call>")
        }
        if (problem == null && complete && ("<function=" in rest || "</tool_call>" in rest)) {
            problem = "The agent wrote a tool call the app could not read."
        }
        return ParsedReply(calls, rest.trim(), problem)
    }
}
