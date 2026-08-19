package me.johardt.heracles.core;

import com.google.gson.JsonObject;

import java.util.function.Predicate;

final class QuestDraftValidator {
    private QuestDraftValidator() {}

    static String validateDisplay(
        JsonObject draft,
        JsonObject changedFields,
        Predicate<String> validItem
    ) {
        String title = draft.has("title") ? draft.get("title").getAsString().trim() : "";
        if (title.isEmpty()) return "Quest title must not be empty";
        if (changedFields == null || changedFields.has("icon")) {
            String icon = draft.has("icon") ? draft.get("icon").getAsString() : "minecraft:map";
            if (!validItem.test(icon)) return "Invalid quest icon";
        }
        if (changedFields == null || changedFields.has("background")) {
            String background = draft.has("background") ? draft.get("background").getAsString() : "";
            if (!background.matches("heracles:textures/gui/quest_backgrounds/[a-z0-9_-]+\\.png")) {
                return "Invalid quest background";
            }
        }
        return "";
    }
}
