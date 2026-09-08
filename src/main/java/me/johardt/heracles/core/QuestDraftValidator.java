package me.johardt.heracles.core;

import com.google.gson.JsonObject;

import java.util.List;
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

    static List<QuestDiagnostics.Diagnostic> validate(QuestDraft draft, Predicate<String> validItem) {
        if (draft == null) return List.of();
        return draft.diagnostics(validItem);
    }
}
