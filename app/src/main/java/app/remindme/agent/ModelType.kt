package app.remindme.agent

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The model type a `.litertlm` file names in its own metadata (`LlmMetadata.llm_model_type`), read from the
 * file without loading it. The runtime reads the tool calls of five types itself; Remind Me's own reader
 * reads `generic_model` files that write `<tool_call><function=…>` blocks. Any other type is one Remind Me
 * cannot read the replies of.
 *
 * The layout is LiteRT-LM's file format version 1: a 32-byte header, then a FlatBuffer listing the sections,
 * one of which is the LlmMetadata protobuf.
 */
object ModelType {

    /** The types whose calls the runtime reads, when the tools come through `ConversationConfig.tools`. */
    val RUNTIME_READS = setOf("qwen3", "lfm2", "gemma4", "function_gemma", "qwen2p5")

    const val GENERIC = "generic_model"

    /**
     * Whether Remind Me can read the replies of a model of [type]. A file whose type could not be read at all
     * is let through: the runtime and Remind Me's reader still try, and a wrong refusal would lock the user out.
     */
    fun readable(type: String?): Boolean = type == null || type == GENERIC || type in RUNTIME_READS

    /** The type [file] names, like "lfm2" or "generic_model"; null when the file does not say or cannot be read. */
    fun of(file: File): String? {
        // The model list is built on the main thread: each file's header is read once, not on every look.
        val key = "${file.path}:${file.length()}:${file.lastModified()}"
        synchronized(seen) { if (key in seen) return seen[key] }
        val type = runCatching { RandomAccessFile(file, "r").use(::read) }.getOrNull()
        synchronized(seen) { seen[key] = type }
        return type
    }

    /** The type each file was found to name, by path, size and time written. */
    private val seen = HashMap<String, String?>()

    private fun read(file: RandomAccessFile): String? {
        val start = bytes(file, 0, HEADER)
        if (String(start.array(), 0, 8, Charsets.US_ASCII) != "LITERTLM" || start.getInt(8) != 1) return null
        val headerEnd = start.getLong(24)
        if (headerEnd <= HEADER || headerEnd - HEADER > MAX_READ) return null
        val header = bytes(file, HEADER.toLong(), (headerEnd - HEADER).toInt())
        val (begin, end) = metadataSection(header) ?: return null
        if (end <= begin || end - begin > MAX_READ || end > file.length()) return null
        return typeIn(bytes(file, begin, (end - begin).toInt()))
    }

    private fun bytes(file: RandomAccessFile, at: Long, size: Int): ByteBuffer {
        val array = ByteArray(size)
        file.seek(at)
        file.readFully(array)
        return ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN)
    }

    /** Where the LlmMetadata section lies: `LiteRTLMMetaData.section_metadata.objects[i]` with data type 5. */
    private fun metadataSection(buffer: ByteBuffer): Pair<Long, Long>? {
        val root = buffer.getInt(0)
        val sections = field(buffer, root, 1)?.let { ref(buffer, it) } ?: return null
        val objects = field(buffer, sections, 0)?.let { ref(buffer, it) } ?: return null
        for (i in 0 until buffer.getInt(objects)) {
            val section = ref(buffer, objects + 4 + 4 * i)
            val type = field(buffer, section, 3)?.let { buffer.get(it).toInt() and 0xff } ?: 0
            if (type != LLM_METADATA) continue
            val begin = field(buffer, section, 1)?.let(buffer::getLong) ?: 0L
            val end = field(buffer, section, 2)?.let(buffer::getLong) ?: 0L
            return begin to end
        }
        return null
    }

    /** Where field [index] of the FlatBuffer table at [table] is, or null when the table leaves it out. */
    private fun field(buffer: ByteBuffer, table: Int, index: Int): Int? {
        val vtable = table - buffer.getInt(table)
        val slot = 4 + 2 * index
        if (slot >= (buffer.getShort(vtable).toInt() and 0xffff)) return null
        val offset = buffer.getShort(vtable + slot).toInt() and 0xffff
        return if (offset == 0) null else table + offset
    }

    private fun ref(buffer: ByteBuffer, at: Int): Int = at + buffer.getInt(at)

    /** The name of the member set in `llm_model_type` (field 6), a message whose one field is the type. */
    private fun typeIn(proto: ByteBuffer): String? {
        val modelType = lengthDelimited(proto, 0, proto.limit(), LLM_MODEL_TYPE) ?: return null
        var at = modelType.first
        while (at < modelType.second) {
            val (tag, next) = varint(proto, at)
            val number = (tag ushr 3).toInt()
            if ((tag and 7L).toInt() == 2) return TYPES[number]
            at = skip(proto, next, (tag and 7L).toInt()) ?: return null
        }
        return null
    }

    /** The bounds of the first length-delimited field numbered [number] between [from] and [to]. */
    private fun lengthDelimited(proto: ByteBuffer, from: Int, to: Int, number: Int): Pair<Int, Int>? {
        var at = from
        while (at < to) {
            val (tag, next) = varint(proto, at)
            val wire = (tag and 7L).toInt()
            if (wire == 2) {
                val (length, body) = varint(proto, next)
                if ((tag ushr 3).toInt() == number) return body to body + length.toInt()
                at = body + length.toInt()
            } else {
                at = skip(proto, next, wire) ?: return null
            }
        }
        return null
    }

    private fun skip(proto: ByteBuffer, at: Int, wire: Int): Int? = when (wire) {
        0 -> varint(proto, at).second
        1 -> at + 8
        2 -> varint(proto, at).let { (length, body) -> body + length.toInt() }
        5 -> at + 4
        else -> null
    }

    private fun varint(proto: ByteBuffer, from: Int): Pair<Long, Int> {
        var value = 0L
        var shift = 0
        var at = from
        while (true) {
            val byte = proto.get(at++).toInt() and 0xff
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return value to at
            shift += 7
        }
    }

    private const val HEADER = 32
    private const val MAX_READ = 16 * 1024 * 1024
    private const val LLM_METADATA = 5
    private const val LLM_MODEL_TYPE = 6

    /** `LlmModelType`'s members by field number (runtime/proto/llm_model_type.proto, LiteRT-LM 0.19.1). */
    private val TYPES = mapOf(
        1 to GENERIC, 2 to "gemma3n", 3 to "function_gemma", 4 to "gemma3", 5 to "qwen3", 7 to "qwen2p5",
        8 to "gemma4", 9 to "fast_vlm", 11 to "lfm2", 12 to "minicpm5", 13 to "minicpmv4",
    )
}
