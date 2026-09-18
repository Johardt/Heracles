package me.johardt.heracles.client;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestSurfaceLayoutTest {
    private static final QuestGraphLayout.CanvasBounds CANVAS =
        new QuestGraphLayout.CanvasBounds(10, 20, 300, 200);

    @Test
    void layoutCanBePickedWithoutRenderingAFrame() {
        QuestSurfaceLayout.Layout layout = QuestSurfaceLayout.layout(
            List.of(new QuestSurfaceLayout.QuestNode("alpha", 30, -10, 16, "default")),
            CANVAS,
            new QuestGraphLayout.ViewportState(0, 0, 2)
        );
        QuestGraphLayout.Point center = QuestGraphLayout.worldToScreen(
            CANVAS,
            new QuestGraphLayout.ViewportState(0, 0, 2),
            30,
            -10
        );

        assertEquals("alpha", layout.pick(center.x(), center.y()).orElseThrow().questId());
        assertTrue(layout.pick(12, 22).isEmpty());
    }

    @Test
    void lastNodeWinsWhenVisualBoundsOverlap() {
        QuestSurfaceLayout.Layout layout = QuestSurfaceLayout.layout(
            List.of(
                new QuestSurfaceLayout.QuestNode("behind", 0, 0, 16, "default"),
                new QuestSurfaceLayout.QuestNode("front", 0, 0, 16, "default")
            ),
            CANVAS,
            QuestGraphLayout.ViewportState.DEFAULT
        );

        assertEquals(
            "front",
            layout.pick(CANVAS.centerX(), CANVAS.centerY()).orElseThrow().questId()
        );
    }

    @Test
    void defaultAndScaledNodesKeepTheirAuthoredCenter() {
        QuestSurfaceLayout.Layout layout = QuestSurfaceLayout.layout(
            List.of(
                new QuestSurfaceLayout.QuestNode("small", 10, -20, 8, "default"),
                new QuestSurfaceLayout.QuestNode("large", 50, 40, 64, "default")
            ),
            CANVAS,
            QuestGraphLayout.ViewportState.DEFAULT
        );
        QuestSurfaceLayout.Node small = layout.find("small").orElseThrow();
        QuestSurfaceLayout.Node large = layout.find("large").orElseThrow();

        assertEquals(10, small.centerX());
        assertEquals(-20, small.centerY());
        assertEquals(16, small.bounds().width());
        assertEquals(72, large.bounds().width());
        assertTrue(large.contains(85, 40));
        assertEquals(5, small.minimapMarkSize());
        assertEquals(9, large.minimapMarkSize());
    }

    @Test
    void specialBackgroundIsPartOfThePickSurface() {
        QuestSurfaceLayout.Layout layout = QuestSurfaceLayout.layout(
            List.of(new QuestSurfaceLayout.QuestNode(
                "diamond",
                0,
                0,
                64,
                "heracles:textures/gui/quest_backgrounds/diamonds.png"
            )),
            CANVAS,
            QuestGraphLayout.ViewportState.DEFAULT
        );
        QuestSurfaceLayout.Node diamond = layout.find("diamond").orElseThrow();

        assertEquals(128, diamond.backgroundBounds().width());
        assertEquals(-64, diamond.backgroundBounds().x());
        assertEquals(128, diamond.bounds().width());
        assertEquals(64, diamond.iconBounds().width());
    }
}
