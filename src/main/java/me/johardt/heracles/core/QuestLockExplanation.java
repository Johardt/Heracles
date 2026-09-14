package me.johardt.heracles.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds actionable player-facing reasons for a locked quest. */
public final class QuestLockExplanation {
    private QuestLockExplanation() {}

    public static Explanation explain(
        QuestDefinition quest,
        Map<String, State> states,
        String currentChapter
    ) {
        List<Blocker> blockers = new ArrayList<>();
        for (String dependencyId : quest.dependencies()) {
            State dependency = states.get(dependencyId);
            if (dependency != null && dependency.complete()) continue;
            String title = dependency == null ? dependencyId : dependency.title();
            String chapter = dependency == null || dependency.chapters().isEmpty()
                ? "Unknown chapter"
                : dependency.chapters().stream().sorted().findFirst().orElse("Unknown chapter");
            boolean selectable = dependency != null && dependency.chapters().contains(currentChapter);
            blockers.add(new Blocker(dependencyId, chapter + " › " + title, selectable));
        }
        if (!blockers.isEmpty()) {
            return new Explanation(Kind.DEPENDENCY, "Complete the prerequisite quests", blockers);
        }
        return new Explanation(
            Kind.POLICY,
            "Locked by progression policy (visibility: " + friendly(quest.settings().hiddenUntil()) + ")",
            List.of()
        );
    }

    private static String friendly(QuestDefinition.Visibility visibility) {
        return visibility.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    public enum Kind { DEPENDENCY, POLICY }
    public record Explanation(Kind kind, String summary, List<Blocker> blockers) {
        public Explanation { blockers = List.copyOf(blockers); }
    }
    public record Blocker(String questId, String label, boolean selectable) {}
    public record State(String title, boolean complete, Set<String> chapters) {
        public State { chapters = Set.copyOf(chapters); }
    }
}
