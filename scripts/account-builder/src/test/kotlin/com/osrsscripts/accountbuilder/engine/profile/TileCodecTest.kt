package com.osrsscripts.accountbuilder.engine.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileCodecTest {

    @Test
    fun formatsCoordinatesAsCsv() {
        assertEquals("3200,3201,1", TileCodec.format(3200, 3201, 1))
    }

    @Test
    fun parsesWellFormedString() {
        assertEquals(Triple(3200, 3201, 1), TileCodec.parse("3200,3201,1"))
    }

    @Test
    fun roundTrips() {
        val s = TileCodec.format(2950, 3310, 0)
        assertEquals(Triple(2950, 3310, 0), TileCodec.parse(s))
    }

    @Test
    fun returnsNullForNullOrBlank() {
        assertNull(TileCodec.parse(null))
        assertNull(TileCodec.parse(""))
        assertNull(TileCodec.parse("   "))
    }

    @Test
    fun returnsNullForMalformedStrings() {
        assertNull(TileCodec.parse("3200,3201")) // too few
        assertNull(TileCodec.parse("3200,3201,0,0")) // too many
        assertNull(TileCodec.parse("a,b,c")) // non-numeric
        assertNull(TileCodec.parse("3200, x, 0")) // partial non-numeric
    }
}
