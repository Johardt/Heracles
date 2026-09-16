package me.johardt.heracles.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestNodeMetricsTest {
    @Test
    void defaultNodeUsesIconSizePlusEightAndKeepsCenter() {
        QuestNodeMetrics metrics = QuestNodeMetrics.forQuest(10, -20, 16, "default");

        assertEquals(24, metrics.containerSize());
        assertEquals(24, metrics.bounds().width());
        assertEquals(24, metrics.bounds().height());
        assertEquals(10, metrics.centerX());
        assertEquals(-20, metrics.centerY());
        assertEquals(2, metrics.iconX());
        assertEquals(-28, metrics.iconY());
    }

    @Test
    void largeIconExpandsLogicalNodeAndSmallIconRemainsClickable() {
        QuestNodeMetrics small = QuestNodeMetrics.forQuest(0, 0, 8, "default");
        QuestNodeMetrics large = QuestNodeMetrics.forQuest(0, 0, 64, "default");

        assertEquals(16, small.containerSize());
        assertEquals(72, large.containerSize());
        assertTrue(large.bounds().contains(35, 0));
        assertTrue(small.bounds().contains(7, 0));
    }

    @Test
    void specialBackgroundUnionScalesItsFrameAndOffsets() {
        QuestNodeMetrics defaultDiamonds = QuestNodeMetrics.forQuest(0, 0, 16, "heracles:textures/gui/quest_backgrounds/diamonds.png");
        QuestNodeMetrics largeDiamonds = QuestNodeMetrics.forQuest(0, 0, 64, "heracles:textures/gui/quest_backgrounds/diamonds.png");

        assertEquals(32, defaultDiamonds.backgroundWidth());
        assertEquals(-4, defaultDiamonds.backgroundOffsetX());
        assertEquals(128, largeDiamonds.backgroundWidth());
        assertEquals(-16, largeDiamonds.backgroundOffsetX());
        assertTrue(defaultDiamonds.bounds().width() > defaultDiamonds.containerSize());
        assertTrue(largeDiamonds.bounds().width() > largeDiamonds.containerSize());
    }

    @Test
    void minimapKeepsSmallMarksCompactAndAddsTwoLargerTiers() {
        assertEquals(5, QuestNodeMetrics.minimapMarkSize(8));
        assertEquals(5, QuestNodeMetrics.minimapMarkSize(16));
        assertEquals(7, QuestNodeMetrics.minimapMarkSize(17));
        assertEquals(7, QuestNodeMetrics.minimapMarkSize(48));
        assertEquals(9, QuestNodeMetrics.minimapMarkSize(49));
        assertEquals(9, QuestNodeMetrics.minimapMarkSize(64));
    }
}
