package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestMutationCoordinatorTest {
    @Test
    void onlyMatchingAcknowledgementSettlesPendingMutation() {
        QuestMutationCoordinator coordinator = new QuestMutationCoordinator();
        JsonObject request = new JsonObject();
        request.addProperty("id", "quest");

        var pending = coordinator.begin("save", request);
        assertTrue(coordinator.isPending());
        assertNull(coordinator.complete(pending.requestId() + 1, true, "wrong request"));
        assertTrue(coordinator.isPending());

        var completion = coordinator.complete(pending.requestId(), true, "saved");
        assertEquals("save", completion.pending().operation());
        assertEquals("saved", completion.message());
        assertTrue(completion.success());
        assertTrue(!coordinator.isPending());
    }

    @Test
    void resetAcknowledgementSurvivesSyncDrivenCoordinatorReconstruction() {
        QuestMutationCoordinator coordinator = new QuestMutationCoordinator();
        var request = new JsonObject();
        request.addProperty("scope", "task");
        request.addProperty("quest", "quest");
        request.addProperty("entry", "outer/leaf");
        var pending = coordinator.begin("reset_progress", request);

        QuestMutationCoordinator reconstructed = coordinator.copy();
        var completion = reconstructed.complete(pending.requestId(), true, "Reset task progress");

        assertEquals("reset_progress", completion.pending().operation());
        assertTrue(completion.success());
        assertTrue(!reconstructed.isPending());
    }
}
