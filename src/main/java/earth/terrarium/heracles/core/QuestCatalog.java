package earth.terrarium.heracles.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import earth.terrarium.heracles.Heracles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class QuestCatalog {
    private static final List<String> DEMO_QUESTS = List.of("welcome.json", "gather_logs.json");
    private final Map<String, QuestDefinition> quests;

    private QuestCatalog(Map<String, QuestDefinition> quests) {
        this.quests = Map.copyOf(quests);
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
            Heracles.LOGGER.info("Loaded {} core quests from {}", quests.size(), questsDirectory);
            return new QuestCatalog(quests);
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
            Heracles.LOGGER.error("Failed to load core quest {}", path, exception);
        }
    }

    public Map<String, QuestDefinition> quests() {
        return quests;
    }
}
