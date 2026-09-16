package me.johardt.heracles.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraclesClientOptionsTest {
    @TempDir
    Path gameDirectory;

    @Test
    void missingFileUsesDefaults() throws Exception {
        HeraclesClientOptions.load(gameDirectory);

        assertEquals(
            HeraclesClientOptions.Preferences.DEFAULT,
            HeraclesClientOptions.preferences()
        );
        assertTrue(Files.exists(optionsFile()));
        JsonObject defaults = JsonParser.parseString(
            Files.readString(optionsFile(), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        assertEquals(1, defaults.get("schemaVersion").getAsInt());
        assertEquals("FLOATING", defaults.get("minimapMode").getAsString());
        assertEquals("TOP_RIGHT", defaults.get("trackerAnchor").getAsString());
    }

    @Test
    void newFormatRoundTrips() throws Exception {
        HeraclesClientOptions.load(gameDirectory);
        HeraclesClientOptions.setMaxEditorHistory(321);
        HeraclesClientOptions.setMinimapMode(HeraclesClientOptions.MinimapMode.DOCKED);
        HeraclesClientOptions.setMinimapPosition(0.25, 0.75);
        HeraclesClientOptions.setShowGrid(true);
        HeraclesClientOptions.setSnapToGrid(true);
        HeraclesClientOptions.setTrackerAnchor(HeraclesClientOptions.TrackerAnchor.BOTTOM_RIGHT);
        HeraclesClientOptions.setTutorialAutoShow(false);
        HeraclesClientOptions.setTutorialSeen(true);

        HeraclesClientOptions.load(gameDirectory);

        assertEquals(1, HeraclesClientOptions.preferences().schemaVersion());
        assertEquals(321, HeraclesClientOptions.maxEditorHistory());
        assertEquals(HeraclesClientOptions.MinimapMode.DOCKED, HeraclesClientOptions.minimapMode());
        assertEquals(0.25, HeraclesClientOptions.minimapX());
        assertEquals(0.75, HeraclesClientOptions.minimapY());
        assertTrue(HeraclesClientOptions.showGrid());
        assertTrue(HeraclesClientOptions.snapToGrid());
        assertEquals(HeraclesClientOptions.TrackerAnchor.BOTTOM_RIGHT, HeraclesClientOptions.trackerAnchor());
        assertFalse(HeraclesClientOptions.tutorialAutoShow());
        assertTrue(HeraclesClientOptions.tutorialSeen());
    }

    @Test
    void newFieldsWinAndMalformedFieldsFallBackIndependently() throws Exception {
        write("""
            {
              "maxEditorHistory": "not a number",
              "minimapMode": "hidden",
              "minimapX": 2.0,
              "minimapY": "bad",
              "showGrid": "not a boolean",
              "snapToGrid": true,
              "trackerAnchor": "not-an-anchor",
              "tutorialAutoShow": true,
              "tutorialSeen": "bad"
            }
            """);

        HeraclesClientOptions.load(gameDirectory);

        assertEquals(100, HeraclesClientOptions.maxEditorHistory());
        assertEquals(HeraclesClientOptions.MinimapMode.HIDDEN, HeraclesClientOptions.minimapMode());
        assertEquals(1.0, HeraclesClientOptions.minimapX());
        assertEquals(1.0, HeraclesClientOptions.minimapY());
        assertFalse(HeraclesClientOptions.showGrid());
        assertTrue(HeraclesClientOptions.snapToGrid());
        assertEquals(HeraclesClientOptions.TrackerAnchor.TOP_RIGHT, HeraclesClientOptions.trackerAnchor());
        assertTrue(HeraclesClientOptions.tutorialAutoShow());
        assertFalse(HeraclesClientOptions.tutorialSeen());
    }

    @Test
    void numericValuesAreBounded() throws Exception {
        write("""
            {
              "maxEditorHistory": 99999,
              "minimapX": -10,
              "minimapY": 10
            }
            """);

        HeraclesClientOptions.load(gameDirectory);

        assertEquals(1000, HeraclesClientOptions.maxEditorHistory());
        assertEquals(0.0, HeraclesClientOptions.minimapX());
        assertEquals(1.0, HeraclesClientOptions.minimapY());
        assertEquals(HeraclesClientOptions.TrackerAnchor.TOP_RIGHT, HeraclesClientOptions.trackerAnchor());
    }

    @Test
    void jsoncCommentsAreAccepted() throws Exception {
        write("""
            {
              // Resourceful Config uses comments for entry hints.
              "schemaVersion": 1,
              /* Keep this file editable without Resourceful Config. */
              "maxEditorHistory": 250
            }
            """);

        HeraclesClientOptions.load(gameDirectory);

        assertEquals(250, HeraclesClientOptions.maxEditorHistory());
    }

    @Test
    void unchangedSetterDoesNotRewrite() throws Exception {
        HeraclesClientOptions.load(gameDirectory);
        HeraclesClientOptions.setMaxEditorHistory(200);
        String before = Files.readString(optionsFile(), StandardCharsets.UTF_8);

        HeraclesClientOptions.setMaxEditorHistory(200);

        assertEquals(before, Files.readString(optionsFile(), StandardCharsets.UTF_8));
    }

    @Test
    void saveProducesValidJson() throws Exception {
        HeraclesClientOptions.load(gameDirectory);
        HeraclesClientOptions.setSnapToGrid(true);

        JsonObject root = JsonParser.parseString(
            Files.readString(optionsFile(), StandardCharsets.UTF_8)
        ).getAsJsonObject();

        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals(true, root.get("snapToGrid").getAsBoolean());
        assertNotEquals(0, root.size());
    }

    private Path optionsFile() {
        return gameDirectory.resolve("config").resolve("heracles_options.jsonc");
    }

    private void write(String json) throws Exception {
        Files.createDirectories(optionsFile().getParent());
        Files.writeString(optionsFile(), json, StandardCharsets.UTF_8);
    }
}
