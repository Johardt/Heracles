package me.johardt.heracles.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestGraphLayoutTest {
    private static final QuestGraphLayout.CanvasBounds CANVAS =
        new QuestGraphLayout.CanvasBounds(10, 20, 300, 200);

    @Test
    void worldAndScreenCoordinatesRoundTripAtSeveralViewports() {
        List<QuestGraphLayout.ViewportState> viewports = List.of(
            new QuestGraphLayout.ViewportState(0, 0, 0.15),
            new QuestGraphLayout.ViewportState(23.5, -17.25, 0.75),
            new QuestGraphLayout.ViewportState(-80, 45, 2.0)
        );

        for (QuestGraphLayout.ViewportState viewport : viewports) {
            QuestGraphLayout.Point screen = QuestGraphLayout.worldToScreen(
                CANVAS,
                viewport,
                42.5,
                -13.25
            );
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                CANVAS,
                viewport,
                screen.x(),
                screen.y()
            );

            assertEquals(42.5, world.x(), 0.000001);
            assertEquals(-13.25, world.y(), 0.000001);
        }
    }

    @Test
    void nodeHitTestingUsesTheViewportTransform() {
        Map<String, QuestGraphLayout.NodeBounds> nodes = new LinkedHashMap<>();
        nodes.put("alpha", QuestGraphLayout.NodeBounds.centered(30, -10, 24, 24));

        QuestGraphLayout.ViewportState viewport = new QuestGraphLayout.ViewportState(0, 0, 2);
        QuestGraphLayout.Point center = QuestGraphLayout.worldToScreen(CANVAS, viewport, 30, -10);

        assertEquals(
            "alpha",
            QuestGraphLayout.hitTest(nodes, CANVAS, viewport, center.x(), center.y())
        );
        assertEquals(
            null,
            QuestGraphLayout.hitTest(nodes, CANVAS, viewport, 12, 22)
        );
    }

    @Test
    void fitHandlesEmptySingleWideTallAndPaddedContent() {
        QuestGraphLayout.ViewportState empty = QuestGraphLayout.fitViewport(
            CANVAS,
            QuestGraphLayout.WorldBounds.empty()
        );
        assertEquals(0, empty.centerWorldX());
        assertEquals(0, empty.centerWorldY());
        assertEquals(1, empty.zoom());

        QuestGraphLayout.WorldBounds single = QuestGraphLayout.boundsOf(
            List.of(QuestGraphLayout.NodeBounds.centered(100, 50, 24, 24)),
            8
        );
        QuestGraphLayout.ViewportState singleFit = QuestGraphLayout.fitViewport(CANVAS, single);
        assertEquals(100, singleFit.centerWorldX());
        assertEquals(50, singleFit.centerWorldY());
        assertEquals(1, singleFit.zoom());

        QuestGraphLayout.WorldBounds wide = new QuestGraphLayout.WorldBounds(-1000, -10, 1000, 10);
        QuestGraphLayout.ViewportState wideFit = QuestGraphLayout.fitViewport(CANVAS, wide);
        assertEquals(0, wideFit.centerWorldX());
        assertEquals(0, wideFit.centerWorldY());
        assertEquals(0.15, wideFit.zoom());

        QuestGraphLayout.WorldBounds tall = new QuestGraphLayout.WorldBounds(-10, -1000, 10, 1000);
        QuestGraphLayout.ViewportState tallFit = QuestGraphLayout.fitViewport(CANVAS, tall);
        assertEquals(0.15, tallFit.zoom());

        assertTrue(single.minX() < 88);
        assertTrue(single.maxX() > 112);
        assertFalse(single.isEmpty());
    }

    @Test
    void visibleWorldIsFiniteForLargeSyntheticGraphs() {
        List<QuestGraphLayout.NodeBounds> nodes = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            nodes.add(QuestGraphLayout.NodeBounds.centered(
                (index % 25) * 96,
                (index / 25) * 72,
                24,
                24
            ));
        }

        QuestGraphLayout.WorldBounds bounds = QuestGraphLayout.boundsOf(nodes, 48);
        QuestGraphLayout.ViewportState viewport = QuestGraphLayout.fitViewport(CANVAS, bounds);
        QuestGraphLayout.WorldBounds visible = QuestGraphLayout.visibleWorld(CANVAS, viewport);

        assertTrue(Double.isFinite(bounds.minX()));
        assertTrue(Double.isFinite(bounds.maxY()));
        assertTrue(Double.isFinite(viewport.zoom()));
        assertTrue(Double.isFinite(visible.width()));
        assertTrue(Double.isFinite(visible.height()));
    }
}
