package me.johardt.heracles.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestModalHostTest {
    @Test
    void rawInspectorIsAnExclusiveModalLayerAndSurvivesScreenRebuildCopies() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.EDITOR);
        host.open(QuestModalHost.Modal.RAW_INSPECTOR);

        QuestModalHost copy = host.copy();

        assertTrue(copy.shouldBlockUnderlyingInput());
        assertEquals(QuestModalHost.Modal.RAW_INSPECTOR, copy.active());
    }

    @Test
    void closingAChildOverlayReturnsToItsParentAndRequestsFocus() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.TASK_EDITOR);
        assertTrue(host.consumeFocusRestoreRequest());

        host.open(QuestModalHost.Modal.PICKER);
        assertEquals(QuestModalHost.Modal.TASK_EDITOR, host.parent());
        assertTrue(host.rendersAsOverlay());

        host.close();
        assertEquals(QuestModalHost.Modal.TASK_EDITOR, host.active());
        assertTrue(host.consumeFocusRestoreRequest());
    }

    @Test
    void dirtyDismissalIsHandledAtTheOverlaySeam() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.EDITOR);
        host.consumeFocusRestoreRequest();
        var discarded = new boolean[1];

        assertTrue(host.requestDismissal(true, () -> discarded[0] = true));
        assertEquals(QuestModalHost.Modal.DISCARD_CONFIRMATION, host.active());
        assertTrue(host.hasPendingDismissal());

        host.cancelDismissal();
        assertEquals(QuestModalHost.Modal.EDITOR, host.active());
        assertFalse(discarded[0]);

        assertTrue(host.requestDismissal(true, () -> discarded[0] = true));
        assertTrue(host.confirmDismissal());
        assertTrue(discarded[0]);
        assertEquals(QuestModalHost.Modal.EDITOR, host.active());
    }

    @Test
    void inlineChooserBlocksInputWithoutReplacingTheParentWidgetTree() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.TASK_CHOOSER);

        assertTrue(host.shouldBlockUnderlyingInput());
        assertFalse(host.rendersAsOverlay());
        assertFalse(host.ownsWidgetTree());
    }
}
