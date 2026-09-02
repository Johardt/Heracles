package me.johardt.heracles.client;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Small platform adapter for the native multi-file JSON picker. */
public final class NativeFilePicker {
    private NativeFilePicker() {}

    public static void open(Consumer<List<Path>> selected, Consumer<String> failed) {
        Thread picker = new Thread(() -> {
            try {
                if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Native file dialogs are unavailable in headless mode");
                FileDialog dialog = new FileDialog((Frame) null, "Import Heracles quests", FileDialog.LOAD);
                dialog.setMultipleMode(true);
                dialog.setFilenameFilter((directory, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) { dialog.dispose(); }
                });
                dialog.setVisible(true);
                java.io.File[] files = dialog.getFiles();
                selected.accept(files == null ? List.of() : java.util.Arrays.stream(files).map(java.io.File::toPath).toList());
            } catch (RuntimeException exception) {
                failed.accept(exception.getMessage() == null ? "Could not open the native file picker" : exception.getMessage());
            }
        }, "heracles-file-picker");
        picker.setDaemon(true);
        picker.start();
    }
}
