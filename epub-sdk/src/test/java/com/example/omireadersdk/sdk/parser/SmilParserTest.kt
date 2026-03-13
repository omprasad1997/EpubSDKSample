package com.example.omireadersdk.sdk.parser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmilParserTest {

    private lateinit var parser: SmilParser

    @Before
    fun setUp() {
        parser = SmilParser()
    }

    @Test
    fun `parse valid SMIL produces correct number of clips`() {
        val doc = parser.parse(smilWithTwoClips.byteInputStream(), "ch1")
        assertEquals(2, doc.clips.size)
    }

    @Test
    fun `parse sets correct spineItemId`() {
        val doc = parser.parse(smilWithTwoClips.byteInputStream(), "my-spine-id")
        assertEquals("my-spine-id", doc.spineItemId)
    }

    @Test
    fun `parse first clip has correct id and fragment`() {
        val doc = parser.parse(smilWithTwoClips.byteInputStream(), "ch1")
        val clip = doc.clips[0]
        assertEquals("par1", clip.id)
        assertEquals("s001", clip.textFragmentId)
    }

    @Test
    fun `parse first clip has correct timing`() {
        val doc = parser.parse(smilWithTwoClips.byteInputStream(), "ch1")
        val clip = doc.clips[0]
        assertEquals(0L,    clip.clipBeginMs)
        assertEquals(3500L, clip.clipEndMs)
        assertEquals(3500L, clip.durationMs)
    }

    @Test
    fun `parse second clip has correct timing`() {
        val doc = parser.parse(smilWithTwoClips.byteInputStream(), "ch1")
        val clip = doc.clips[1]
        assertEquals(3500L, clip.clipBeginMs)
        assertEquals(7200L, clip.clipEndMs)
    }

    @Test
    fun `parse empty body returns empty clip list`() {
        val smil = """
            <?xml version="1.0" encoding="UTF-8"?>
            <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
                <body><seq id="seq1"/></body>
            </smil>
        """.trimIndent()
        val doc = parser.parse(smil.byteInputStream(), "ch1")
        assertTrue(doc.clips.isEmpty())
    }

    @Test
    fun `par without text src is skipped`() {
        val smil = """
            <?xml version="1.0" encoding="UTF-8"?>
            <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
                <body>
                    <seq>
                        <par id="p1">
                            <audio src="ch1.mp3" clipBegin="0" clipEnd="1.0"/>
                        </par>
                    </seq>
                </body>
            </smil>
        """.trimIndent()
        val doc = parser.parse(smil.byteInputStream(), "ch1")
        assertTrue(doc.clips.isEmpty())
    }

    @Test fun `parseTimeCode HH_MM_SS`()   { assertEquals(3_723_500L, parser.parseTimeCode("1:02:03.500")) }
    @Test fun `parseTimeCode MM_SS`()       { assertEquals(123_500L,   parser.parseTimeCode("2:03.500")) }
    @Test fun `parseTimeCode SS`()          { assertEquals(3_500L,     parser.parseTimeCode("3.500")) }
    @Test fun `parseTimeCode zero string`() { assertEquals(0L,         parser.parseTimeCode("0")) }
    @Test fun `parseTimeCode invalid`()     { assertEquals(0L,         parser.parseTimeCode("bad")) }

    private val smilWithTwoClips = """
        <?xml version="1.0" encoding="UTF-8"?>
        <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
            <body>
                <seq id="seq1">
                    <par id="par1">
                        <text src="chapter1.xhtml#s001"/>
                        <audio src="../audio/ch1.mp3" clipBegin="0:00:00.000" clipEnd="0:00:03.500"/>
                    </par>
                    <par id="par2">
                        <text src="chapter1.xhtml#s002"/>
                        <audio src="../audio/ch1.mp3" clipBegin="0:00:03.500" clipEnd="0:00:07.200"/>
                    </par>
                </seq>
            </body>
        </smil>
    """.trimIndent()
}