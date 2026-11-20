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
import java.util.concurrent.TimeUnit

/**
 * The model on the phone, chosen on the Models screen (LFM3-1.7B unless the owner picks another), run by
 * LiteRT-LM on the CPU, fully offline.
 *
 * The model is loaded when the user opens the agent screen and dropped when they leave it; it never runs
 * in the background or when a task fires. It reads the preface as soon as it is loaded, which is most of
 * the wait on this phone. Every call blocks: the screen makes them on its own thread. Each message gets a newly loaded model, because this model
 * carries state from one conversation into the next on the same loaded copy (LiteRT-LM issue #3165).
 * The shape follows Google's offline_planner sample for the same model and runtime.
 */
class AgentEngine(private val modelFile: File, private val cacheDir: File) : ModelPort {

    override val contextTokens: Int = ModelOffers.contextIn(modelFile.name) ?: DEFAULT_CONTEXT

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /** The preface the waiting conversation has already read. */
    private var prepared: Preface? = null

    /**
     * Drops the model that answered the last message, if any, loads a fresh one and has it read [preface],
     * so that the wait is over before the user has finished typing.
     */
    @Synchronized
    override fun prepare(preface: Preface) {
        runCatching { load(preface) }.onFailure { Log.e(TAG, "model failed to load", it) }.getOrThrow()
    }

    private fun load(preface: Preface) {
        closeEngine()
        val started = SystemClock.elapsedRealtime()
        val loading = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), cacheDir = cacheDir.path))
        try {
            loading.initialize()
            val loaded = SystemClock.elapsedRealtime()
            conversation = loading.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(preface.instruction),
                    samplerConfig = GREEDY,
                    automaticToolCalling = false,
                    extraContext = mapOf("tools" to JsonParser.parseString(preface.tools).asJsonArray),
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
        prepared = preface
    }

    /**
     * A talk with the model that has read [preface]. If the waiting model read a different preface (a task
     * changed since, or the day did), a fresh one reads this one first. Closing the talk drops the model,
     * whatever happened: it carries state into the next conversation (LiteRT-LM issue #3165).
     */
    @Synchronized
    override fun open(preface: Preface): Talk {
        if (conversation == null || prepared != preface) prepare(preface)
        val talking = conversation ?: error("the model is not loaded")
        return object : Talk {
            override fun say(text: String, onText: (String) -> Unit): ModelReply = ask(talking, text, onText)
            override fun close() = this@AgentEngine.close()
        }
    }

    private fun ask(talking: Conversation, text: String, onText: (String) -> Unit): ModelReply {
        val reply = StringBuilder()
        val started = SystemClock.elapsedRealtime()
        var firstAt = 0L
        var written = 0
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        talking.sendMessageAsync(
            Message.user(text),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime()
                    written++
                    for (content in message.contents.contents) if (content is Content.Text) reply.append(content.text)
                    onText(reply.toString())
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
        failure?.let { throw IllegalStateException(it.message, it) }
        val finished = SystemClock.elapsedRealtime()
        val tokens = runCatching { talking.getTokenCount() }.getOrDefault(-1)
        val first = if (firstAt == 0L) finished - started else firstAt - started
        Log.i(TAG, "reply in ${finished - started} ms, first token after $first ms, $tokens tokens in context")
        Log.i(TAG, "reply: $reply")
        return ModelReply(reply.toString(), tokens, written, first, finished - started)
    }

    /** Drops the model. */
    @Synchronized
    fun close() = closeEngine()

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

        /** The context of a model file whose name does not say. */
        private const val DEFAULT_CONTEXT = 2048

        /** The same text on the same phone gives the same calls. */
        private val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
        private val NO_THINKING = ThinkingConfig(enableThinking = false)

        /** The model file chosen on the Models screen, or null when there is none to use. */
        fun modelFile(context: Context): File? = Models(context).inUse()

        /** Where the model file goes: the app's own folder on shared storage, which a USB copy can reach. */
        fun modelFolder(context: Context): File? = context.getExternalFilesDir(null)
    }
}
