package me.johardt.heracles.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTypeRegistryTest {
    @Test
    void defaultsExposeBuiltInTypesAndExplicitUnknowns() {
        EditorTypeRegistry registry = EditorTypeRegistry.defaults();

        assertTrue(registry.find(EditorTypeRegistry.Kind.TASK, "heracles:composite").orElseThrow().allowsNested());
        assertTrue(registry.resolve(EditorTypeRegistry.Kind.REWARD, "heracles:item").editable());
        assertFalse(registry.resolve(EditorTypeRegistry.Kind.TASK, "other:custom").editable());
        assertEquals("No editor is registered for this type", registry.resolve(EditorTypeRegistry.Kind.TASK, "other:custom").availabilityReason());
    }

    @Test
    void duplicateDescriptorsAreRejected() {
        EditorTypeRegistry.Builder builder = EditorTypeRegistry.builder();
        var descriptor = EditorTypeRegistry.Descriptor.builtIn(EditorTypeRegistry.Kind.TASK, "example:test", "Test");
        builder.register(descriptor);
        assertThrows(IllegalArgumentException.class, () -> builder.register(descriptor));
    }
}
