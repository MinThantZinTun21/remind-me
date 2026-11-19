package app.remindme.agent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The model on the phone, chosen on the Models screen (LFM3-1.7B unless the owner picks another), run by
 * LiteRT-LM on the CPU, fully offline.
 *
 * The model is loaded when the user opens the agent screen and dropped when they leave it; it never runs
 * in the background or when a task fires. It reads the system instruction as soon as it is loaded, which
 * is most of the wait on this phone. Each message gets a newly loaded model, because this model
 * carries state from one conversation into the next on the same loaded copy (LiteRT-LM issue #3165).
 * The shape follows Google's offline_planner sample for the same model and runtime.
 */
class AgentEngine(private val modelFile: File, private val cacheDir: File) {

    /** Called on the engine's own thread; the screen moves them to the main thread. */
    interface Listener {
        /** The reply so far, while it is being written. */
        fun onText(text: String)
        fun onDone(reply: Reply)
        fun onError(message: String)
    }

    /** A finished reply and how long the phone took over it. */
    data class Reply(val text: String, val firstTokenMs: Long, val totalMs: Long, val tokens: Int)

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "agent-model").apply { isDaemon = true } }
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /** The system instruction the waiting conversation has already read. */
    private var prepared: String? = null

    /**
     * Drops the model that answered the last message, if any, loads a fresh one and has it read the
     * [system] instruction and the tools, so that the wait is over before the user has finished typing.
     * Reports how long that took.
     */
    fun load(system: String, onResult: (Result<Long>) -> Unit) {
        executor.execute {
            val result = runCatching { prepare(system) }
            result.onFailure { Log.e(TAG, "model failed to load", it) }
            onResult(result)
        }
    }

    private fun prepare(system: String): Long {
        closeEngine()
        val started = SystemClock.elapsedRealtime()
        val loading = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), cacheDir = cacheDir.path))
        try {
            loading.initialize()
            val loaded = SystemClock.elapsedRealtime()
            conversation = loading.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(system),
                    samplerConfig = GREEDY,
                    automaticToolCalling = false,
                    extraContext = mapOf("tools" to JsonParser.parseString(Agent.TOOLS_JSON).asJsonArray),
                    prefillPrefaceOnInit = true,
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = NO_THINKING,
                ),
            )
            Log.i(TAG, "model loaded in ${loaded - started} ms, instruction read in ${SystemClock.elapsedRealtime() - loaded} ms")
        } catch (failure: Throwable) {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { loading.close() }
            throw failure
        }
        engine = loading
        prepared = system
        return SystemClock.elapsedRealtime() - started
    }

    /**
     * Sends the user's [text] to the model that has read [system]. If the waiting model read a different
     * instruction (a task changed since, or the day did), a fresh one reads this one first. The model is
     * dropped afterwards whatever happened: call [load] before the next message.
     */
    fun ask(system: String, text: String, listener: Listener) {
        executor.execute {
            try {
                if (conversation == null || prepared != system) prepare(system)
                val talking = conversation ?: error("the model is not loaded")
                val reply = StringBuilder()
                val started = SystemClock.elapsedRealtime()
                var firstAt = 0L
                var failure: Throwable? = null
                val done = CountDownLatch(1)
                talking.sendMessageAsync(
                    Message.user(text),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime()
                            for (content in message.contents.contents) if (content is Content.Text) reply.append(content.text)
                            listener.onText(reply.toString())
                        }

                        override fun onDone() = done.countDown()

                        override fun onError(throwable: Throwable) {
                            failure = throwable
                            done.countDown()
                        }
                    },
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = NO_THINKING,
                )
                if (!done.await(REPLY_TIMEOUT_S, TimeUnit.SECONDS)) {
                    talking.cancelProcess()
                    done.await(STOP_TIMEOUT_S, TimeUnit.SECONDS)
                    failure = failure ?: IllegalStateException("no reply within $REPLY_TIMEOUT_S seconds")
                }
                val finished = SystemClock.elapsedRealtime()
                val failed = failure
                if (failed != null) {
                    listener.onError("The model stopped: ${failed.message}")
                } else {
                    val tokens = runCatching { talking.getTokenCount() }.getOrDefault(-1)
                    val first = if (firstAt == 0L) finished - started else firstAt - started
                    Log.i(TAG, "reply in ${finished - started} ms, first token after $first ms, $tokens tokens in context")
                    Log.i(TAG, "reply: $reply")
                    listener.onDone(Reply(reply.toString(), first, finished - started, tokens))
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "message failed", failure)
                listener.onError("${failure.javaClass.simpleName}: ${failure.message}")
            } finally {
                closeEngine()
            }
        }
    }

    /** Drops the model once any message in flight has finished, and stops the engine's thread. */
    fun close() {
        executor.execute { closeEngine() }
        executor.shutdown()
    }

    private fun closeEngine() {
        runCatching { conversation?.close() }
        conversation = null
        prepared = null
        engine?.let { loaded -> runCatching { loaded.close() }.onFailure { Log.w(TAG, "closing the model: ${it.message}") } }
        engine = null
    }

    companion object {
        private const val TAG = "RemindMeAgent"

        /** Room for about three tool calls and a sentence. */
        private const val MAX_OUTPUT_TOKENS = 320
        private const val REPLY_TIMEOUT_S = 240L
        private const val STOP_TIMEOUT_S = 10L

        /** The same text on the same phone gives the same calls. */
        private val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
        private val NO_THINKING = ThinkingConfig(enableThinking = false)

        /** The model file chosen on the Models screen, or null when there is none to use. */
        fun modelFile(context: Context): File? = Models(context).inUse()

        /** Where the model file goes: the app's own folder on shared storage, which a USB copy can reach. */
        fun modelFolder(context: Context): File? = context.getExternalFilesDir(null)
    }
}
