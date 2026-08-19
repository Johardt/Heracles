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

    private QuestCatalog(Map<String, QuestDefinition> quests, List<String> configuredOrder, Map<String, ChapterSettings> chapterSettings) {
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
        this.issues = validate(quests);
    }

    public static QuestCatalog load(Path configDirectory) {
        Path heracles = configDirectory.resolve(Heracles.MOD_ID);
        Path questsDirectory = heracles.resolve("quests");
        try {
            Files.createDirectories(questsDirectory);
            installDemoIfEmpty(heracles, questsDirectory);
            Map<String, QuestDefinition> quests = new LinkedHashMap<>();
            try (Stream<Path> files = Files.walk(questsDirectory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> loadQuest(path, quests));
            }
            QuestCatalog catalog = new QuestCatalog(
                quests,
                loadGroupOrder(heracles.resolve("groups.txt")),
                loadChapterSettings(heracles.resolve("group_settings.json"))
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

    private static void loadQuest(Path path, Map<String, QuestDefinition> quests) {
        String filename = path.getFileName().toString();
        String id = filename.substring(0, filename.length() - ".json".length());
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            quests.put(id, QuestDefinition.parse(id, json));
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
                value.has("background") ? value.get("background").getAsString() : ""
            ));
        });
        return settings;
    }

    public record ChapterSettings(String icon, String background) {}

    public Set<String> dependents(String questId) {
        return dependents.getOrDefault(questId, Set.of());
    }

    public List<QuestDefinition.ValidationIssue> issues() {
        return issues;
    }

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
