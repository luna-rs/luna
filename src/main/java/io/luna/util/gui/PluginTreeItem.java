package io.luna.util.gui;

import io.luna.game.plugin.Plugin;
import io.luna.game.plugin.PluginMetadata;
import javafx.scene.control.CheckBoxTreeItem;

import java.util.function.BiConsumer;

import static io.luna.util.StringUtils.COMMA_JOINER;

/**
 * A model representing a {@link CheckBoxTreeItem} plugin within a plugin tree. This class provides additional
 * functions that allow access to plugin metadata and the GUI description text area.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeItem extends CheckBoxTreeItem<String> {

    /**
     * The plugin instance.
     */
    private final Plugin plugin;

    /**
     * The plugin controller.
     */
    private final PluginGuiController controller;

    /**
     * Creates a new {@link PluginTreeItem}.
     *
     * @param plugin The plugin instance.
     * @param controller The plugin controller.
     */
    PluginTreeItem(Plugin plugin, PluginGuiController controller) {
        super(plugin.getMetadata().getName());
        this.plugin = plugin;
        this.controller = controller;
    }

    /**
     * Adds all scripts as leaf child nodes.
     */
    void addScriptChildren() {
        for (String script : plugin.getFiles().keySet()) {
            getChildren().add(new CheckBoxTreeItem<>(script));
        }
    }

    /**
     * Displays a description of the contained metadata onto the GUI.
     */
    void displayGuiDescription() {
        String none = "<None specified.>";
        StringBuilder description = new StringBuilder();
        BiConsumer<String, Object> appendData = (t, u) -> description.
                append(t).append(": ").append(u).append("\n\n");

        appendData.accept("Name", getMetadata().getName());
        appendData.accept("Version", getMetadata().getVersion());
        appendData.accept("Description", getMetadata().getDescription());

        String dependencies = COMMA_JOINER.join(getMetadata().getDependencies());
        appendData.accept("Dependencies", dependencies.isEmpty() ? none : dependencies);

        String authors = COMMA_JOINER.join(getMetadata().getAuthors());
        appendData.accept("Authors", authors.isEmpty() ? none : authors);

        controller.getDescriptionArea().setText(description.toString());
    }

    /**
     * @return The plugin instance.
     */
    Plugin getPlugin() {
        return plugin;
    }

    /**
     * @return The plugin metadata.
     */
    PluginMetadata getMetadata() {
        return plugin.getMetadata();
    }
}