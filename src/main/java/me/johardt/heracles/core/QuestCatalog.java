package me.johardt.heracles.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.heracles.Heracles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class QuestCatalog {
    private static final List<String> DEMO_QUESTS = List.of(
        "welcome.json", "gather_logs.json", "craft_table.json", "combat.json", "nether_trip.json", "compatibility.json",
        "reward_showcase.json"
    );
    private final Map<String, QuestDefinition> quests;
    private final Map<String, Set<String>> dependents;
    private final Set<String> groups;
    private final List<String> groupOrder;
    private final Map<String, ChapterSettings> chapterSettings;
    private final List<QuestDefinition.ValidationIssue> issues;
    private final Map<String, List<Path>> conflictingPaths;

    private QuestCatalog(Map<String, QuestDefinition> quests, List<String> configuredOrder, Map<String, ChapterSettings> chapterSettings, Map<String, List<Path>> conflictingPaths) {
        this.quests = Map.copyOf(quests);
        this.dependents = buildDependents(quests);
        this.groups = quests.values().stream()
            .flatMap(quest -> quest.display().groups().keySet().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<String> order = new LinkedHashSet<>(configuredOrder);
        order.addAll(this.groups);
        if (order.isEmpty()) order.add("Main");
        this.groupOrder = List.copyOf(order);
        this.chapterSettings = Map.copyOf(chapterSettings);
        this.conflictingPaths = Map.copyOf(conflictingPaths);
        List<QuestDefinition.ValidationIssue> allIssues = new java.util.ArrayList<>(validate(quests));
        conflictingPaths.forEach((id, paths) -> allIssues.add(new QuestDefinition.ValidationIssue(
            QuestDefinition.Severity.ERROR, id, "Duplicate quest ID '" + id + "' in " + paths.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(" and "))
        )));
        this.issues = List.copyOf(allIssues);
    }

    public static QuestCatalog load(Path configDirectory) {
        Path heracles = configDirectory.resolve(Heracles.MOD_ID);
        Path questsDirectory = heracles.resolve("quests");
        try {
            Files.createDirectories(questsDirectory);
            installDemoIfEmpty(heracles, questsDirectory);
            Map<String, QuestDefinition> quests = new LinkedHashMap<>();
            Map<String, List<Path>> questPaths = new LinkedHashMap<>();
            try (Stream<Path> files = Files.walk(questsDirectory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> loadQuest(path, quests, questPaths));
            }
            Map<String, List<Path>> conflicts = new LinkedHashMap<>();
            questPaths.forEach((id, paths) -> { if (paths.size() > 1) conflicts.put(id, List.copyOf(paths)); });
            QuestCatalog catalog = new QuestCatalog(
                quests,
                loadGroupOrder(heracles.resolve("groups.txt")),
                loadChapterSettings(heracles.resolve("group_settings.json")), conflicts
            );
            Heracles.LOGGER.info("Loaded {} core quests from {} ({} validation issues)", quests.size(), questsDirectory, catalog.issues.size());
            catalog.issues.forEach(issue -> {
                if (issue.severity() == QuestDefinition.Severity.ERROR) {
                    Heracles.LOGGER.error("Quest validation: {}: {}", issue.path(), issue.message());
                } else {
                    Heracles.LOGGER.warn("Quest validation: {}: {}", issue.path(), issue.message());
                }
            });
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Heracles quests from " + questsDirectory, exception);
        }
    }

    private static void installDemoIfEmpty(Path heracles, Path questsDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(questsDirectory)) {
            if (files.anyMatch(path -> path.getFileName().toString().endsWith(".json"))) return;
        }
        Path target = questsDirectory.resolve("getting_started");
        Files.createDirectories(target);
        copyResource("/config/heracles/groups.txt", heracles.resolve("groups.txt"));
        copyResource("/config/heracles/group_settings.json", heracles.resolve("group_settings.json"));
        for (String quest : DEMO_QUESTS) {
            copyResource("/config/heracles/quests/getting_started/" + quest, target.resolve(quest));
        }
        Heracles.LOGGER.info("Installed demo quests into {}", questsDirectory);
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream stream = QuestCatalog.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing bundled resource " + resource);
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void loadQuest(Path path, Map<String, QuestDefinition> quests, Map<String, List<Path>> questPaths) {
        String filename = path.getFileName().toString();
        String id = filename.substring(0, filename.length() - ".json".length());
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            List<String> duplicateKeys = JsonDuplicateKeyDetector.findDuplicates(source);
            if (!duplicateKeys.isEmpty()) throw new IllegalArgumentException("Duplicate JSON key(s): " + String.join(", ", duplicateKeys));
            JsonObject json = JsonParser.parseString(source).getAsJsonObject();
            questPaths.computeIfAbsent(id, ignored -> new java.util.ArrayList<>()).add(path);
            // Files.walk is sorted: retaining the first file makes loading deterministic.
            quests.putIfAbsent(id, QuestDefinition.parse(id, json));
        } catch (Exception exception) {
            Heracles.LOGGER.error("Quest validation: {}:$: {}", path, exception.getMessage());
        }
    }

    public Map<String, QuestDefinition> quests() {
        return quests;
    }

    public Set<String> groups() {
        return groups;
    }

    public List<String> groupOrder() { return groupOrder; }

    public Map<String, ChapterSettings> chapterSettings() { return chapterSettings; }

    static List<String> loadGroupOrder(Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
            .map(String::trim).filter(name -> !name.isEmpty()).distinct().toList();
    }

    static Map<String, ChapterSettings> loadChapterSettings(Path path) throws IOException {
        if (!Files.exists(path)) return Map.of();
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, ChapterSettings> settings = new LinkedHashMap<>();
        root.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject value = entry.getValue().getAsJsonObject();
            settings.put(entry.getKey(), new ChapterSettings(
                value.has("icon") ? value.get("icon").getAsString() : "minecraft:map",
                value.has("background") ? value.get("background").getAsString() : "",
                !value.has("iconEnabled") || value.get("iconEnabled").getAsBoolean(),
                value.has("backgroundOpacity") ? Math.clamp(value.get("backgroundOpacity").getAsInt(), 0, 100) : 100
            ));
        });
        return settings;
    }

    public record ChapterSettings(String icon, String background, boolean iconEnabled, int backgroundOpacity) {
        public ChapterSettings {
            icon = icon == null || icon.isBlank() ? "minecraft:map" : icon;
            background = background == null ? "" : background;
            backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
        }

        /** Compatibility constructor for existing callers and old metadata. */
        public ChapterSettings(String icon, String background) {
            this(icon, background, true, 100);
        }
    }

    public Set<String> dependents(String questId) {
        return dependents.getOrDefault(questId, Set.of());
    }

    public List<QuestDefinition.ValidationIssue> issues() {
        return issues;
    }

    /** IDs with more than one source file. Mutations against these IDs are unsafe. */
    public Map<String, List<Path>> conflictingPaths() { return conflictingPaths; }

    public boolean hasConflict(String questId) { return conflictingPaths.containsKey(questId); }

    static void writeDependencies(
        Path configDirectory,
        String questId,
        Set<String> dependencies
    ) throws IOException {
        Path questsDirectory = configDirectory.resolve(Heracles.MOD_ID).resolve("quests");
        List<Path> matches;
        try (Stream<Path> files = Files.walk(questsDirectory)) {
            matches = files
                .filter(path -> path.getFileName().toString().equals(questId + ".json"))
                .toList();
        }
        if (matches.size() != 1) throw new IOException(
            matches.isEmpty()
                ? "Quest file not found for " + questId
                : "Multiple quest files found for " + questId
        );
        Path target = matches.getFirst();
        JsonObject root = JsonParser.parseString(
            Files.readString(target, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        com.google.gson.JsonArray values = new com.google.gson.JsonArray();
        dependencies.stream().sorted().forEach(values::add);
        root.add("dependencies", values);

        Path temporary = Files.createTempFile(target.getParent(), questId + "-", ".json.tmp");
        try {
            Files.writeString(temporary, new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean wouldCreateCycle(
        Map<String, QuestDefinition> quests,
        String prerequisiteId,
        String dependentId
    ) {
        return dependsOn(quests, prerequisiteId, dependentId, new HashSet<>());
    }

    /** Returns the complete cycle introduced by adding dependent → prerequisite, if any. */
    public static List<String> dependencyCyclePath(Map<String, QuestDefinition> quests, String prerequisiteId, String dependentId) {
        List<String> path = new java.util.ArrayList<>();
        if (!findDependencyPath(quests, prerequisiteId, dependentId, new HashSet<>(), path)) return List.of();
        path.add(0, dependentId);
        return List.copyOf(path);
    }

    private static boolean findDependencyPath(Map<String, QuestDefinition> quests, String current, String target, Set<String> visited, List<String> path) {
        if (!visited.add(current)) return false;
        path.add(current);
        if (current.equals(target)) return true;
        QuestDefinition quest = quests.get(current);
        if (quest != null) for (String dependency : quest.dependencies()) {
            if (findDependencyPath(quests, dependency, target, visited, path)) return true;
        }
        path.removeLast();
        return false;
    }

    private static boolean dependsOn(
        Map<String, QuestDefinition> quests,
        String questId,
        String targetId,
        Set<String> visited
    ) {
        if (!visited.add(questId)) return false;
        QuestDefinition quest = quests.get(questId);
        if (quest == null) return false;
        if (quest.dependencies().contains(targetId)) return true;
        return quest.dependencies().stream().anyMatch(dependency ->
            dependsOn(quests, dependency, targetId, visited)
        );
    }

    private static Map<String, Set<String>> buildDependents(Map<String, QuestDefinition> quests) {
        Map<String, Set<String>> result = new HashMap<>();
        quests.forEach((id, quest) -> quest.dependencies().forEach(dependency ->
            result.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(id)));
        result.replaceAll((ignored, values) -> Set.copyOf(values));
        return Map.copyOf(result);
    }

    private static List<QuestDefinition.ValidationIssue> validate(Map<String, QuestDefinition> quests) {
        List<QuestDefinition.ValidationIssue> issues = new java.util.ArrayList<>();
        quests.forEach((id, quest) -> {
            quest.issues().forEach(issue -> issues.add(new QuestDefinition.ValidationIssue(
                issue.severity(), id + "." + issue.path(), issue.message())));
            quest.dependencies().stream().filter(dependency -> !quests.containsKey(dependency)).forEach(dependency ->
                issues.add(new QuestDefinition.ValidationIssue(QuestDefinition.Severity.ERROR, id + ".dependencies", "Missing quest " + dependency)));
            detectCycle(id, id, quests, new HashSet<>(), issues);
        });
        return List.copyOf(issues);
    }

    /** Validates only dependency references and cycles for an in-memory catalog. */
    public static List<QuestDefinition.ValidationIssue> validateDependencies(Map<String, QuestDefinition> quests) {
        List<QuestDefinition.ValidationIssue> issues = new java.util.ArrayList<>();
        quests.forEach((id, quest) -> {
            quest.dependencies().stream()
                .filter(dependency -> !quests.containsKey(dependency))
                .forEach(dependency -> issues.add(new QuestDefinition.ValidationIssue(
                    QuestDefinition.Severity.ERROR,
                    id + ".dependencies",
                    "Missing quest " + dependency
                )));
            List<String> cycle = dependencyCyclePath(quests, id);
            if (!cycle.isEmpty()) {
                issues.add(new QuestDefinition.ValidationIssue(
                    QuestDefinition.Severity.ERROR,
                    id + ".dependencies",
                    "Dependency cycle: " + String.join(" → ", cycle)
                ));
            }
        });
        return issues.stream().distinct().toList();
    }

    private static List<String> dependencyCyclePath(Map<String, QuestDefinition> quests, String origin) {
        return findCyclePath(quests, origin, origin, new LinkedHashSet<>());
    }

    private static List<String> findCyclePath(Map<String, QuestDefinition> quests, String origin, String current, Set<String> path) {
        if (!path.add(current)) return current.equals(origin) ? List.of(origin) : List.of();
        QuestDefinition quest = quests.get(current);
        if (quest != null) {
            for (String dependency : quest.dependencies()) {
                if (dependency.equals(origin)) {
                    List<String> cycle = new java.util.ArrayList<>(path);
                    cycle.add(origin);
                    return List.copyOf(cycle);
                }
                List<String> nested = findCyclePath(quests, origin, dependency, new LinkedHashSet<>(path));
                if (!nested.isEmpty()) return nested;
            }
        }
        return List.of();
    }

    private static void detectCycle(String origin, String current, Map<String, QuestDefinition> quests, Set<String> path, List<QuestDefinition.ValidationIssue> issues) {
        if (!path.add(current)) {
            if (current.equals(origin)) {
                QuestDefinition.ValidationIssue issue = new QuestDefinition.ValidationIssue(QuestDefinition.Severity.ERROR, origin + ".dependencies", "Dependency cycle detected");
                if (!issues.contains(issue)) issues.add(issue);
            }
            return;
        }
        QuestDefinition quest = quests.get(current);
        if (quest != null) quest.dependencies().forEach(dependency -> detectCycle(origin, dependency, quests, new HashSet<>(path), issues));
    }
}
