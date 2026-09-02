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
        return QuestDiagnostics.validateDisplay(draft, changedFields, validItem).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst().orElse("");
    }
}
