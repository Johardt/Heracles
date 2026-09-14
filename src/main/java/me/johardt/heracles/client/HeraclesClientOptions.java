package me.johardt.heracles.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.heracles.Heracles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small persisted client preference set; Beta 8 display options remain out of scope. */
public final class HeraclesClientOptions {
    private static final String FILE_NAME = "heracles_options.json";
    private static int maxEditorHistory = 100;
    private static Path file;

    private HeraclesClientOptions() {}

    public static synchronized void load(Path gameDirectory) {
        file = gameDirectory.resolve(FILE_NAME);
        maxEditorHistory = 100;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            maxEditorHistory = bounded(root.has("maxEditorHistory") ? root.get("maxEditorHistory").getAsInt() : 100);
        } catch (Exception exception) {
            Heracles.LOGGER.warn("Could not read {}: {}", file, exception.getMessage());
        }
    }

    public static synchronized int maxEditorHistory() {
        return maxEditorHistory;
    }

    public static synchronized void setMaxEditorHistory(int value) {
        maxEditorHistory = bounded(value);
        save();
    }

    private static int bounded(int value) {
        return Math.max(10, Math.min(1000, value));
    }

    private static void save() {
        if (file == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("maxEditorHistory", maxEditorHistory);
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Heracles.LOGGER.warn("Could not save {}: {}", file, exception.getMessage());
        }
    }
}
