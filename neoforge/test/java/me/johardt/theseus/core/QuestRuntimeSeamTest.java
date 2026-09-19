package me.johardt.theseus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestRuntimeSeamTest {

    @TempDir
    Path directory;

    @Test
    void constructionDoesNotTouchExternalAdapters() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new FailingProgressStore(),
            new FakeWorld(catalog),
            new RecordingSync()
        );

        assertNotNull(runtime);
    }

    @Test
    void reloadUsesTheInjectedWorld() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new InMemoryProgressStore(),
            world,
            new RecordingSync()
        );

        assertEquals(catalog.quests().size(), runtime.reload());
        assertEquals(1, world.catalogLoads);
    }

    @Test
    void claimRulesAndRewardsRunThroughTestAdapters() {
        QuestCatalog catalog = QuestCatalog.load(directory);
        FakeWorld world = new FakeWorld(catalog);
        QuestRuntime runtime = new QuestRuntime(
            catalog,
            TaskEngine.defaultBuilder().build(),
            new InMemoryProgressStore(),
            world,
            new RecordingSync()
        );

        assertTrue(runtime.triggerDummy(null, "demo_welcome"));
        assertTrue(runtime.claim(null, "welcome"));
        assertEquals(1, world.experienceGranted);
        assertFalse(world.experienceWasPoints);
    }

    private static final class InMemoryProgressStore implements ProgressStore {
        private JsonObject value = new JsonObject();

        @Override
        public JsonObject load() {
            return value.deepCopy();
        }

        @Override
        public void save(JsonObject progress) {
            value = progress.deepCopy();
        }
    }

    private static final class FailingProgressStore implements ProgressStore {
        @Override
        public JsonObject load() throws IOException {
            throw new IOException("constructor performed I/O");
        }

        @Override
        public void save(JsonObject progress) throws IOException {
            throw new IOException("constructor performed I/O");
        }
    }

    private static final class RecordingSync implements QuestSync {
        @Override
        public void snapshot(ServerPlayer player, String json, boolean open) {}

        @Override
        public void notification(
            ServerPlayer player,
            String kind,
            String title,
            String detail
        ) {}
    }

    private static final class FakeWorld implements QuestWorld {
        private final QuestCatalog catalog;
        private int catalogLoads;
        private int experienceGranted;
        private boolean experienceWasPoints;

        private FakeWorld(QuestCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public QuestCatalog loadCatalog() {
            catalogLoads++;
            return catalog;
        }

        @Override
        public UUID playerId(ServerPlayer player) {
            return new UUID(0, 1);
        }

        @Override
        public List<ServerPlayer> onlinePlayers() {
            return List.of();
        }

        @Override
        public boolean canEdit(ServerPlayer player) {
            return true;
        }

        @Override
        public boolean isIntegratedServer() {
            return true;
        }

        @Override
        public boolean containsRegistryTarget(
            RegistryValidation.Target target,
            String value
        ) {
            return true;
        }

        @Override
        public boolean advancementGranted(
            ServerPlayer player,
            String advancement
        ) {
            return false;
        }

        @Override
        public boolean hasLootTable(String id) {
            return true;
        }

        @Override
        public void message(ServerPlayer player, String message) {}

        @Override
        public void grantExperience(
            ServerPlayer player,
            int amount,
            boolean points
        ) {
            experienceGranted += amount;
            experienceWasPoints = points;
        }

        @Override
        public void giveItem(ServerPlayer player, ItemStack stack) {}

        @Override
        public void runCommand(ServerPlayer player, String command) {}

        @Override
        public void generateLoot(
            ServerPlayer player,
            String lootTable,
            Consumer<ItemStack> receiver
        ) {}

        @Override
        public Set<TaskEngine.Signal.RegistryEntry> structuresAt(
            ServerPlayer player
        ) {
            return Set.of();
        }
    }
}
