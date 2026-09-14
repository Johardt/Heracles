package me.johardt.heracles.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.johardt.heracles.core.QuestIconDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Client extension seam for icon rendering and structured editor defaults. */
public final class QuestIconRegistry {
    private static final Map<String, Descriptor> TYPES = new LinkedHashMap<>();

    static {
        register(new Descriptor(
            QuestIconDefinition.ITEM_TYPE,
            "Item",
            () -> QuestIconDefinition.item("minecraft:map").source(),
            (graphics, source, x, y, size) -> graphics.item(item(source, Items.MAP), x, y)
        ));
    }

    private QuestIconRegistry() {}

    public static synchronized void register(Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.type().isBlank()) throw new IllegalArgumentException("Icon type is required");
        if (TYPES.putIfAbsent(descriptor.type(), descriptor) != null) {
            throw new IllegalArgumentException("Icon type already registered: " + descriptor.type());
        }
    }

    public static synchronized Descriptor descriptor(String type) {
        return TYPES.get(type);
    }

    public static synchronized Map<String, Descriptor> descriptors() {
        return Map.copyOf(TYPES);
    }

    /** Returns false for an unknown type so the caller can render an explicit fallback. */
    public static boolean render(
        GuiGraphicsExtractor graphics,
        QuestIconDefinition icon,
        ItemStack fallback,
        int x,
        int y,
        int size
    ) {
        Descriptor descriptor = descriptor(icon.type());
        if (descriptor == null) {
            graphics.item(fallback.isEmpty() ? new ItemStack(Items.BARRIER) : fallback, x, y);
            return false;
        }
        descriptor.renderer().render(graphics, icon.source(), x, y, size);
        return true;
    }

    private static ItemStack item(JsonElement source, Item fallback) {
        JsonElement value = source;
        if (value != null && value.isJsonObject()) value = value.getAsJsonObject().get("item");
        if (value == null || !value.isJsonPrimitive()) return new ItemStack(fallback);
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(value.getAsString()));
            return new ItemStack(item == null || item == Items.AIR ? fallback : item);
        } catch (RuntimeException exception) {
            return new ItemStack(fallback);
        }
    }

    public record Descriptor(
        String type,
        String label,
        Supplier<JsonElement> defaultSource,
        Renderer renderer
    ) {
        public Descriptor {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(defaultSource, "defaultSource");
            Objects.requireNonNull(renderer, "renderer");
        }

        public JsonElement createDefault() {
            JsonElement value = defaultSource.get();
            return value == null ? new JsonObject() : value.deepCopy();
        }
    }

    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, JsonElement source, int x, int y, int size);
    }
}
