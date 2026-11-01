package io.github.tomsadeh.puki.ui.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerRewardTest {

    @Test
    fun `no line crossed means no sticker`() {
        assertNull(nextSticker(rewarded = 5, stars = 5))
        assertNull(nextSticker(rewarded = 4, stars = 5))
    }

    @Test
    fun `a line crossed gives its sticker`() {
        assertEquals("cat", nextSticker(rewarded = 3, stars = 5)?.id)
    }

    @Test
    fun `two lines crossed give the first, then the other`() {
        val first = nextSticker(rewarded = 3, stars = 7)
        assertEquals("cat", first?.id)
        assertEquals("rainbow", nextSticker(rewarded = first!!.starsRequired, stars = 7)?.id)
    }

    @Test
    fun `stickers go up in stars and have their own ids`() {
        val lines = allStickers.map { it.starsRequired }
        assertEquals(lines.sorted(), lines)
        assertTrue(lines.toSet().size == lines.size)
        assertEquals(allStickers.size, allStickers.map { it.id }.toSet().size)
    }
}
