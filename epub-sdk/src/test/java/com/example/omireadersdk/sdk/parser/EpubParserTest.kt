package com.example.omireadersdk.sdk.parser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EpubParserTest {

    private lateinit var parser: EpubParser

    @Before
    fun setUp() {
        parser = EpubParser()
    }

    @Test
    fun `EpubParser can be instantiated`() {
        assertNotNull(parser)
    }

    @Test
    fun `parseTimeCode HH MM SS with millis`() {
        val smilParser = SmilParser()
        assertEquals(3_723_500L, smilParser.parseTimeCode("1:02:03.500"))
    }

    @Test
    fun `parseTimeCode MM SS with millis`() {
        val smilParser = SmilParser()
        assertEquals(123_500L, smilParser.parseTimeCode("2:03.500"))
    }

    @Test
    fun `parseTimeCode seconds only`() {
        val smilParser = SmilParser()
        assertEquals(3_500L, smilParser.parseTimeCode("3.500"))
    }

    @Test
    fun `parseTimeCode zero`() {
        val smilParser = SmilParser()
        assertEquals(0L, smilParser.parseTimeCode("0"))
    }

    @Test
    fun `parseTimeCode invalid returns zero`() {
        val smilParser = SmilParser()
        assertEquals(0L, smilParser.parseTimeCode("invalid"))
    }

    @Test
    fun `parseTimeCode handles missing millis`() {
        val smilParser = SmilParser()
        assertEquals(63_000L, smilParser.parseTimeCode("1:03"))
    }
}