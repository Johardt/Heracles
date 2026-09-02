package me.johardt.heracles.client;

/**
 * Central modal policy for the quest editor.  The screen still renders each
 * form, but all transient layers share one ordering and dismissal interface.
 */
public final class QuestModalHost {
    private Modal active = Modal.NONE;

    public Modal active() {
        return active;
    }

    public boolean isOpen() {
        return active != Modal.NONE;
    }

    public boolean is(Modal modal) {
        return active == modal;
    }

    public void open(Modal modal) {
        if (modal == null || modal == Modal.NONE) throw new IllegalArgumentException("A real modal is required");
        active = modal;
    }

    public void close() {
        active = Modal.NONE;
    }

    public QuestModalHost copy() {
        QuestModalHost copy = new QuestModalHost();
        copy.active = active;
        return copy;
    }

    public boolean shouldBlockUnderlyingInput() {
        return isOpen();
    }

    public enum Modal {
        NONE,
        DIAGNOSTICS,
        FILE_IMPORT,
        PICKER,
        CONFIRMATION,
        EDITOR
    }
}
