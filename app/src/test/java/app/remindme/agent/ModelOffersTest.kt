package app.remindme.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOffersTest {

    @Test
    fun `the rule keeps phone-sized text models and drops the rest`() {
        assertTrue(ModelOffers.passes("Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977_000_000))
        assertFalse(ModelOffers.passes("qwen3_4b_channelwise_int8_float32kv.litertlm", 5_672_000_000))
        assertFalse(ModelOffers.passes("Qwen3-0.6B.mediatek.mt6993.litertlm", 1_203_000_000))
        assertFalse(ModelOffers.passes("gemma-4-E2B-it-gpu.litertlm", 2_341_000_000))
        assertFalse(ModelOffers.passes("gemma-4-E2B-it-web.litertlm", 2_008_000_000))
        assertFalse(ModelOffers.passes("model.gguf", 1_000_000_000))
    }

    @Test
    fun `context is read from the file name when it is written there`() {
        assertEquals(2048, ModelOffers.contextIn("LFM3-1.7B_mixed_int4_ekv2048.litertlm"))
        assertNull(ModelOffers.contextIn("Qwen3-0.6B.litertlm"))
    }

    @Test
    fun `gated repositories and repositories without a usable file are not candidates`() {
        val listing = """
            [
              {"id": "litert-community/Qwen3-0.6B", "gated": false, "cardData": {"license": "apache-2.0"},
               "siblings": [{"rfilename": "README.md"}, {"rfilename": "Qwen3-0.6B.litertlm"}]},
              {"id": "google/gemma-3n-E2B-it-litert-lm", "gated": "manual",
               "siblings": [{"rfilename": "gemma-3n-E2B-it-int4.litertlm"}]},
              {"id": "litert-community/whisper-tiny", "gated": false, "siblings": [{"rfilename": "whisper.litertlm"}]},
              {"id": "someone/notes", "gated": false, "siblings": [{"rfilename": "README.md"}]}
            ]
        """.trimIndent()

        assertEquals(listOf(ModelOffers.Candidate("litert-community/Qwen3-0.6B", "apache-2.0")), ModelOffers.candidates(listing))
    }

    @Test
    fun `a repository's sizes decide which of its files are offered`() {
        val blobs = """
            {"id": "litert-community/Qwen3-0.6B", "siblings": [
              {"rfilename": "README.md", "size": 1000},
              {"rfilename": "Qwen3-0.6B.litertlm", "size": 614000000},
              {"rfilename": "Qwen3-0.6B.mediatek.mt6993.litertlm", "size": 1203000000}
            ]}
        """.trimIndent()

        val offers = ModelOffers.offersIn(ModelOffers.Candidate("litert-community/Qwen3-0.6B", "apache-2.0"), blobs)

        assertEquals(1, offers.size)
        val offer = offers.single()
        assertEquals("Qwen3-0.6B.litertlm", offer.file)
        assertEquals(614_000_000L, offer.bytes)
        assertEquals("apache-2.0", offer.licence)
        assertFalse(offer.tried)
        assertEquals("https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm", offer.url)
    }

    @Test
    fun `only the model in use since build 1 is marked as tried`() {
        assertEquals(listOf(ModelOffers.TRIED_FILE), ModelOffers.BUILT_IN.filter { it.tried }.map { it.file })
        assertTrue(ModelOffers.BUILT_IN.all { ModelOffers.passes(it.file, it.bytes) })
    }
}
