package io.luna.util.gui;

import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoize;

/**
 * A model that contains functions to manage file loading and browsing.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginGuiFileManager {

    /**
     * The plugin GUI.
     */
    private final PluginGui gui;

    /**
     * The cached contents of the "info.txt" file.
     */
    private final Supplier<String> infoFile = memoize(() -> loadFile("info.txt"));

    /**
     * The cached contents of the "license.txt" file.
     */
    private final Supplier<String> licenseFile = memoize(() -> loadFile("license.txt"));

    /**
     * Creates a new {@link PluginGuiFileManager}.
     *
     * @param gui The plugin GUI.
     */
    PluginGuiFileManager(PluginGui gui) {
        this.gui = gui;
    }

    /**
     * Displays a "save file" interface and applies the consumer to the saved file.
     *
     * @param action The function to apply to the saved file.
     */
    void saveFile(Consumer<File> action) {
        File file = getFileChooser("Save File").showSaveDialog(gui.getWindow());
        
        if (file != null) {
            action.accept(file);
        }
    }

    /**
     * Displays an "open file" interface and applies the consumer to the opened file.
     *
     * @param action The function to apply to the opened file.
     */
    void openFile(Consumer<File> action) {
        File file = getFileChooser("Open File").showOpenDialog(gui.getWindow());
        
        if (file != null) {
            action.accept(file);
        }
    }

    /**
     * Returns a file chooser instance with a title.
     *
     * @param title The window title.
     * @return The file chooser instance.
     */
    private FileChooser getFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialDirectory(new File("./data/"));
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Luna Settings File", "*.toml"));
        return fileChooser;
    }

    /**
     * Loads the contents of a file and returns it as a String.
     *
     * @param name The name of the file to load.
     * @return The contents of the file.
     */
    private String loadFile(String name) {
        Path filePath = Paths.get("./data/gui/").resolve(name);
        
        try {
            return new String(Files.readAllBytes(filePath));
        } catch (IOException e) {
            gui.openErrorAlert(e);
        }
        
        return null;
    }

    /**
     * @return The contents of the "info.txt" file.
     */
    String getInfoFile() {
        return infoFile.get();
    }

    /**
     * @return The contents of the "license.txt" file.
     */
    String getLicenseFile() {
        return licenseFile.get();
    }
}