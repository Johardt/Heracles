package me.johardt.heracles.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestPositionInputTest {
    @Test
    void validIntegerUpdatesValueAndPreservesSubmittedText() {
        QuestPositionInput.Result result = QuestPositionInput.parse("  -27 ", 4);

        assertTrue(result.valid());
        assertEquals(-27, result.value());
        assertEquals("  -27 ", result.text());
    }

    @Test
    void blankAndInvalidInputKeepPreviousCoordinate() {
        QuestPositionInput.Result blank = QuestPositionInput.parse("  ", 12);
        QuestPositionInput.Result invalid = QuestPositionInput.parse("12.5", 12);

        assertFalse(blank.valid());
        assertEquals(12, blank.value());
        assertFalse(invalid.valid());
        assertEquals(12, invalid.value());
    }
}
