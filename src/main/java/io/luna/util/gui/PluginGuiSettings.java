package io.luna.util.gui;

import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import javafx.scene.Scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A model representing plugin GUI settings that can be saved, imported, and exported.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginGuiSettings {

    /**
     * The {@code settings.toml} directory.
     */
    private static final Path SETTINGS_DIR = Paths.get("./data/gui/settings.toml");

    /**
     * The plugin GUI.
     */
    private final PluginGui gui;

    /**
     * If the GUI is in dark mode.
     */
    private boolean darkMode;

    /**
     * If the GUI should save settings on exit.
     */
    private boolean saveOnExit;

    /**
     * A list of selected plugins.
     */
    private final Set<String> selected = Sets.newConcurrentHashSet();

    /**
     * Creates a new {@link PluginGuiSettings}.
     *
     * @param gui The plugin GUI.
     */
    PluginGuiSettings(PluginGui gui) {
        this.gui = gui;
    }

    /**
     * Returns a TOML String representation of the settings.
     */
    String toToml() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("dark_mode", darkMode);
        settings.put("save_on_exit", saveOnExit);
        settings.put("selected", selected.toArray());
        return new TomlWriter().write(new Settings(settings));
    }

    /**
     * Saves the current settings.
     */
    boolean save(Path path) {
        try {
            Files.write(path, toToml().getBytes());
        } catch (IOException e) {
            gui.openErrorAlert(e);
            return false;
        }
        return true;
    }

    /**
     * Saves the current settings to {@code SETTINGS_DIR}.
     */
    boolean save() {
        return save(SETTINGS_DIR);
    }

    /**
     * Loads a settings file.
     */
    void load(Path path) throws IllegalStateException {
        Toml tomlReader = new Toml().read(path.toFile());
        JsonObject tomlSettings = tomlReader.getTable("settings").to(JsonObject.class);
        set(tomlSettings);
    }

    /**
     * Loads the settings file from {@code SETTINGS_DIR}.
     */
    void load() {
        load(SETTINGS_DIR);
    }

    /**
     * Updates the GUI with these settings.
     */
    void update() {
        PluginGuiController controller = gui.getController();
        Scene scene = gui.getScene();

        // Apply dark mode setting.
        if (darkMode) {
            controller.getDarkTheme().setSelected(true);
            scene.getStylesheets().add("dark_theme.css");
        } else {
            controller.getDarkTheme().setSelected(false);
            scene.getStylesheets().remove("dark_theme.css");
        }

        // Apply save on exit setting.
        controller.getSaveOnExit().setSelected(saveOnExit);

        // Apply selected plugins.
        controller.getChangeListener().setFiringEvents(false);
        if (selected.size() > 0) {
            gui.getPluginItems().forEach((k, v) -> {
                if (selected.contains(k)) {
                    v.setSelected(true);
                } else {
                    v.setSelected(false);
                }
            });
        } else {
            gui.getPluginItems().forEach((k, v) -> v.setSelected(false));
        }
        controller.getChangeListener().setFiringEvents(true);

        // Rebuild plugin interface using new settings.
        controller.buildPluginViewer();
    }

    /**
     * Sets the backing fields to {@code settings}.
     */
    void set(JsonObject settings) {
        darkMode = settings.get("dark_mode").getAsBoolean();
        saveOnExit = settings.get("save_on_exit").getAsBoolean();
        settings.getAsJsonArray("selected").
                forEach(e -> selected.add(e.getAsString()));
    }

    /**
     * Sets if the GUI is in dark mode.
     */
    void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    /**
     * @return If the GUI is in dark mode.
     */
    boolean isDarkMode() {
        return darkMode;
    }

    /**
     * Sets if the GUI should save settings on exit.
     */
    void setSaveOnExit(boolean saveOnExit) {
        this.saveOnExit = saveOnExit;
    }

    /**
     * @return If the GUI should save settings on exit.
     */
    boolean isSaveOnExit() {
        return saveOnExit;
    }

    /**
     * @return A list of selected plugins.
     */
    Set<String> getSelected() {
        return selected;
    }

    /**
     * A dummy class for TOML serialization.
     */
    private final class Settings {
        private final Map<String, Object> settings;

        private Settings(Map<String, Object> settings) {
            this.settings = settings;
        }
    }
}