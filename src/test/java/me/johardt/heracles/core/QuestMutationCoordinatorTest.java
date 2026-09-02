package me.johardt.heracles.core;

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
}
