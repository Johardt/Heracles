package me.johardt.heracles.core;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Type-first editor metadata.  The editor can discover a type without knowing
 * how its form is implemented, while unknown types remain explicitly
 * read-only instead of being represented by a fake fallback.
 */
public final class EditorTypeRegistry {
    private final Map<Key, Descriptor> descriptors;

    private EditorTypeRegistry(Map<Key, Descriptor> descriptors) {
        this.descriptors = Map.copyOf(descriptors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EditorTypeRegistry defaults() {
        Builder builder = builder();
        registerTasks(builder);
        registerRewards(builder);
        return builder.build();
    }

    public Optional<Descriptor> find(Kind kind, String id) {
        return Optional.ofNullable(descriptors.get(new Key(kind, id)));
    }

    public Descriptor resolve(Kind kind, String id) {
        return find(kind, id).orElseGet(() -> Descriptor.unknown(kind, id));
    }

    public List<Descriptor> descriptors(Kind kind) {
        return descriptors.values().stream().filter(value -> value.kind() == kind).toList();
    }

    public record Descriptor(
        Kind kind,
        String id,
        String label,
        String availabilityReason,
        boolean editable,
        boolean executable,
        boolean allowsNested
    ) {
        public Descriptor {
            if (kind == null || id == null || id.isBlank()) throw new IllegalArgumentException("Type kind and ID are required");
            label = label == null || label.isBlank() ? id : label;
            availabilityReason = availabilityReason == null ? "" : availabilityReason;
        }

        public static Descriptor builtIn(Kind kind, String id, String label) {
            return new Descriptor(kind, id, label, "", true, true, kind == Kind.TASK && id.equals("heracles:composite"));
        }

        public static Descriptor unknown(Kind kind, String id) {
            return new Descriptor(kind, id == null || id.isBlank() ? "unknown" : id, "Unknown type", "No editor is registered for this type", false, false, false);
        }

        public JsonObject defaultSource() {
            JsonObject source = new JsonObject();
            source.addProperty("type", id);
            return source;
        }
    }

    public enum Kind { TASK, REWARD, ICON }

    public static final class Builder {
        private final Map<Key, Descriptor> descriptors = new LinkedHashMap<>();

        public Builder register(Descriptor descriptor) {
            Key key = new Key(descriptor.kind(), descriptor.id());
            if (descriptors.putIfAbsent(key, descriptor) != null) {
                throw new IllegalArgumentException("Editor type already registered: " + descriptor.kind() + " " + descriptor.id());
            }
            return this;
        }

        public EditorTypeRegistry build() {
            return new EditorTypeRegistry(descriptors);
        }
    }

    private record Key(Kind kind, String id) {}

    private static void registerTasks(Builder builder) {
        register(builder, Kind.TASK, "heracles:dummy", "Dummy");
        register(builder, Kind.TASK, "heracles:item", "Acquire Item");
        register(builder, Kind.TASK, "heracles:xp", "Experience");
        register(builder, Kind.TASK, "heracles:kill_entity", "Kill Entity");
        register(builder, Kind.TASK, "heracles:advancement", "Advancement");
        register(builder, Kind.TASK, "heracles:biome", "Biome");
        register(builder, Kind.TASK, "heracles:block_interaction", "Block Interaction");
        register(builder, Kind.TASK, "heracles:changed_dimension", "Changed Dimension");
        register(builder, Kind.TASK, "heracles:check", "Check");
        register(builder, Kind.TASK, "heracles:composite", "Composite");
        register(builder, Kind.TASK, "heracles:entity_interaction", "Entity Interaction");
        register(builder, Kind.TASK, "heracles:item_interaction", "Item Interaction");
        register(builder, Kind.TASK, "heracles:item_use", "Item Use");
        register(builder, Kind.TASK, "heracles:location", "Location");
        register(builder, Kind.TASK, "heracles:recipe", "Recipe");
        register(builder, Kind.TASK, "heracles:stat", "Stat");
        register(builder, Kind.TASK, "heracles:structure", "Structure");
    }

    private static void registerRewards(Builder builder) {
        register(builder, Kind.REWARD, "heracles:xp", "Experience");
        register(builder, Kind.REWARD, "heracles:item", "Item");
        register(builder, Kind.REWARD, "heracles:loottable", "Loot Table");
        register(builder, Kind.REWARD, "heracles:command", "Command");
        register(builder, Kind.REWARD, "heracles:selectable", "Selectable Reward");
    }

    private static void register(Builder builder, Kind kind, String id, String label) {
        builder.register(Descriptor.builtIn(kind, id, label));
    }
}
