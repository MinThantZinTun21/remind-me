package app.remindme.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/** A model file the owner can download: where it is on Hugging Face and what the Models screen says about it. See GLOSSARY.md. */
data class ModelOffer(
    val repo: String,
    val file: String,
    val name: String,
    val bytes: Long,
    /** Tokens of context; null when neither the list nor the file name says. */
    val context: Int?,
    val licence: String?,
    /** True only for a model Remind Me has been run with on the owner's phone and whose tool calls it reads. */
    val tried: Boolean,
) {
    /** Hugging Face sends this to a freshly signed link on every request, so a resumed download never meets an expired one. */
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$file"
}

/**
 * Where the Models screen's list comes from: the models built into the app first, then what Hugging Face
 * lists today that passes the rule from "Mobile models that make tool calls". Only public, ungated
 * repositories are read, without an account.
 */
object ModelOffers {
    /** The rule drops anything bigger: it would not fit beside the phone's other apps in memory. */
    const val MAX_BYTES = 3_000_000_000L

    /** The file in use since agent build 1, the one model the app is known to read. */
    const val TRIED_FILE = "LFM3-1.7B_mixed_int4_ekv2048.litertlm"

    /** Models known to run on LiteRT-LM 0.19.1 that say they make tool calls; sizes read from the Hub on 2026-11-19. */
    val BUILT_IN = listOf(
        ModelOffer("litert-community/LFM3-1.7B-LiteRT", TRIED_FILE, "LFM3 1.7B", 1_442_000_000, 2048, "LFM Open License", tried = true),
        ModelOffer("litert-community/Qwen3-0.6B", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", "Qwen3 0.6B", 343_000_000, 4096, "Apache 2.0", tried = false),
        ModelOffer("litert-community/LFM2.5-1.2B-Instruct", "LFM2.5-1.2B-Instruct_int4.litertlm", "LFM2.5 1.2B", 736_000_000, 4096, "LFM Open License", tried = false),
        ModelOffer("litert-community/Qwen3-1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", "Qwen3 1.7B", 977_000_000, 4096, "Apache 2.0", tried = false),
        ModelOffer("litert-community/Qwen3-4B", "qwen3_4b_mixed_int4.litertlm", "Qwen3 4B", 2_273_000_000, 4096, "Apache 2.0", tried = false),
        ModelOffer("litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", "Gemma 4 E2B", 2_588_000_000, 32768, "Apache 2.0", tried = false),
    )

    /** Words in a repository or file name that mean it is not a text model for this phone's CPU. */
    private val NOT_FOR_THIS_PHONE = listOf(
        "embedding", "encoder", "colbert", "whisper", "parakeet", "moonshine", "kokoro", "-vl", "vl-", "vision",
        "_gpu", "-gpu", "-web", "npu", "tensor_g", "qualcomm", "mediatek", "intel_", "qcs", "abliterated", "uncensored",
    )

    /** True when a file passes the parts of the rule the app can check before downloading. */
    fun passes(file: String, bytes: Long): Boolean =
        file.endsWith(".litertlm") && bytes in 1..MAX_BYTES && NOT_FOR_THIS_PHONE.none { it in file.lowercase() }

    /** Tokens of context written into a LiteRT-LM file name as `ekv2048`, or null. */
    fun contextIn(file: String): Int? = Regex("ekv(\\d{3,6})").find(file)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The ungated repositories in a Hub listing (`/api/models?filter=litert-lm&expand[]=gated&expand[]=siblings…`)
     * that have a `.litertlm` file worth asking the size of, with their licences.
     */
    fun candidates(listing: String): List<Candidate> {
        val models = JsonParser.parseString(listing) as? JsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val repo = model.get("id")?.asString ?: return@mapNotNull null
            // The listing writes false for an open repository and "auto" or "manual" for a gated one.
            val gated = model.get("gated")?.let { !(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && !it.asBoolean) } ?: true
            if (gated || NOT_FOR_THIS_PHONE.any { it in repo.lowercase() }) return@mapNotNull null
            val files = (model.get("siblings") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("rfilename")?.asString }
                .filter { it.endsWith(".litertlm") && NOT_FOR_THIS_PHONE.none { word -> word in it.lowercase() } }
            if (files.isEmpty()) return@mapNotNull null
            Candidate(repo, licenceOf(model))
        }
    }

    data class Candidate(val repo: String, val licence: String?)

    /** The offers in one repository's `?blobs=true` answer that pass the rule. */
    fun offersIn(candidate: Candidate, blobs: String): List<ModelOffer> {
        val model = JsonParser.parseString(blobs) as? JsonObject ?: return emptyList()
        return (model.get("siblings") as? JsonArray).orEmpty().mapNotNull { element ->
            val sibling = element as? JsonObject ?: return@mapNotNull null
            val file = sibling.get("rfilename")?.asString ?: return@mapNotNull null
            val bytes = sibling.get("size")?.asLong ?: return@mapNotNull null
            if (!passes(file, bytes)) return@mapNotNull null
            ModelOffer(candidate.repo, file, file.removeSuffix(".litertlm"), bytes, contextIn(file), candidate.licence ?: licenceOf(model), tried = false)
        }
    }

    private fun licenceOf(model: JsonObject): String? =
        (model.get("cardData") as? JsonObject)?.get("license")?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()

    /**
     * Asks Hugging Face for today's LiteRT-LM models and keeps the files that pass the rule and are not
     * built in. Blocks: call it off the main thread. Throws when the Hub cannot be reached.
     */
    fun searchHub(): List<ModelOffer> {
        val listing = get("$HUB/api/models?filter=litert-lm&sort=downloads&limit=$SEARCHED&expand[]=gated&expand[]=siblings&expand[]=cardData")
        val builtIn = BUILT_IN.map { it.repo to it.file }.toSet()
        return candidates(listing).flatMap { candidate ->
            runCatching { offersIn(candidate, get("$HUB/api/models/${candidate.repo}?blobs=true")) }.getOrDefault(emptyList())
        }.filter { (it.repo to it.file) !in builtIn }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) error("Hugging Face answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private const val HUB = "https://huggingface.co"

    /** The most downloaded LiteRT-LM repositories looked at; each costs one more request. */
    private const val SEARCHED = 40
}
