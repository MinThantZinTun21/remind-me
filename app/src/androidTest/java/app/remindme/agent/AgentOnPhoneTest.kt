package app.remindme.agent

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.remindme.RemindMeApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real model on the phone over a list of sentences, without opening a screen, and logs what the
 * agent did with each (tag RemindMeAgent). It is how the agent is checked on the phone it is for.
 *
 * The sentences are the lines of `sentences.txt` in the app's own folder on shared storage, beside the
 * model file. A line starting with `+` keeps what the agent did, so that the next line can change it;
 * any other line is undone straight away. Everything kept is undone at the end.
 */
@RunWith(AndroidJUnit4::class)
class AgentOnPhoneTest {
    @Test
    fun theAgentActsOnEachSentence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as RemindMeApp
        val model = AgentEngine.modelFile(app)
        val sentences = File(AgentEngine.modelFolder(app), "sentences.txt").takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }
        assumeTrue("no model file or no sentences.txt on this phone", model != null && sentences != null)

        val engine = AgentEngine(model!!, app.cacheDir)
        val kept = mutableListOf<ActionCard>()
        try {
            for (line in sentences!!) {
                val keep = line.startsWith("+")
                val sentence = line.removePrefix("+").trim()
                var system = ""
                var message = ""
                instrumentation.runOnMainSync { system = app.agent.systemInstruction(); message = app.agent.userMessage(sentence) }

                val done = CountDownLatch(1)
                var reply: AgentEngine.Reply? = null
                var error: String? = null
                engine.ask(
                    system,
                    message,
                    object : AgentEngine.Listener {
                        override fun onText(text: String) = Unit
                        override fun onDone(reply_: AgentEngine.Reply) { reply = reply_; done.countDown() }
                        override fun onError(message: String) { error = message; done.countDown() }
                    },
                )
                done.await(6, TimeUnit.MINUTES)

                Log.i(TAG, "SAY  $message".replace("\n", " | "))
                val got = reply
                if (got == null) {
                    Log.i(TAG, "FAIL ${error ?: "no reply"}")
                    continue
                }
                Log.i(TAG, "RAW  " + got.text.replace("\n", " "))
                instrumentation.runOnMainSync {
                    val parsed = ReplyParser.parse(got.text)
                    parsed.problem?.let { Log.i(TAG, "PROB $it") }
                    val cards = app.agent.act(parsed.calls)
                    if (cards.isEmpty()) Log.i(TAG, "CARD (none)")
                    for (card in cards) {
                        val task = card.task
                        Log.i(
                            TAG,
                            "CARD ${card.action} " + (card.refusal?.let { "REFUSED: $it" } ?: "ok") +
                                (task?.let { " | id ${it.id} \"${it.title}\" due ${it.due} ${it.repeat} ${it.style} warn ${it.leadTimes}" } ?: ""),
                        )
                    }
                    if (keep) kept += cards else cards.reversed().forEach { app.agent.undo(it) }
                }
                Log.i(TAG, "TIME ${got.totalMs} ms, first token after ${got.firstTokenMs} ms, ${got.tokens} tokens")
            }
        } finally {
            instrumentation.runOnMainSync { kept.reversed().forEach { app.agent.undo(it) } }
            engine.close()
            Log.i(TAG, "END")
        }
    }

    private companion object {
        const val TAG = "RemindMeAgent"
    }
}
