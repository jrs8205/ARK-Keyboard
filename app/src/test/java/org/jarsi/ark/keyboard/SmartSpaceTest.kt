package org.jarsi.ark.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSpaceTest {

    @Test
    fun `lauseen paattajien tunnistus`() {
        assertTrue(SmartSpace.isSentenceEnder('.'))
        assertTrue(SmartSpace.isSentenceEnder('…'))
        assertFalse(SmartSpace.isSentenceEnder(','))
        assertTrue(SmartSpace.isPunctuation(','))
        assertFalse(SmartSpace.isPunctuation('a'))
    }

    @Test
    fun `kaksoisvalilyonnin pisteelle kelpaavat merkit`() {
        assertTrue(SmartSpace.canEndSentence('a'))
        assertTrue(SmartSpace.canEndSentence('ä'))
        assertTrue(SmartSpace.canEndSentence('9'))
        assertTrue(SmartSpace.canEndSentence(')'))
        assertTrue(SmartSpace.canEndSentence('"'))
        assertFalse(SmartSpace.canEndSentence('.'))
        assertFalse(SmartSpace.canEndSentence(','))
        assertFalse(SmartSpace.canEndSentence(' '))
        assertFalse(SmartSpace.canEndSentence('…'))
    }
}
