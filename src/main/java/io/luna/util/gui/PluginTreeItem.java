package io.luna.util.gui;

import io.luna.game.plugin.PluginMetadata;
import javafx.scene.control.CheckBoxTreeItem;

import java.util.function.BiConsumer;

import static io.luna.util.StringUtils.COMMA_JOINER;

/**
 * A model representing a {@code CheckBoxTreeItem<String>} plugin within a plugin tree. This class provides
 * additional functions that allow access to plugin metadata and the GUI description text area.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeItem extends CheckBoxTreeItem<String> {

    /**
     * The plugin metadata.
     */
    private final PluginMetadata metadata;

    /**
     * The plugin controller.
     */
    private final PluginGuiController controller;

    /**
     * Creates a new {@link PluginTreeItem}.
     *
     * @param metadata The plugin metadata.
     * @param controller The plugin controller.
     */
    PluginTreeItem(PluginMetadata metadata, PluginGuiController controller) {
        super(metadata.getName());
        this.metadata = metadata;
        this.controller = controller;
    }

    /**
     * Displays a description of the contained metadata onto the GUI.
     */
    void displayGuiDescription() {
        String none = "<None specified.>";
        StringBuilder description = new StringBuilder();
        BiConsumer<String, Object> appendData = (t, u) -> description.
                append(t).append(": ").append(u).append("\n\n");

        appendData.accept("Name", metadata.getName());
        appendData.accept("Description", metadata.getDescription());

        String dependencies = COMMA_JOINER.join(metadata.getDependencies());
        appendData.accept("Dependencies", dependencies.isEmpty() ? none : dependencies);

        String authors = COMMA_JOINER.join(metadata.getAuthors());
        appendData.accept("Authors", authors.isEmpty() ? none : authors);

        controller.getDescriptionArea().setText(description.toString());
    }

    /**
     * @return The plugin metadata.
     */
    PluginMetadata getMetadata() {
        return metadata;
    }
}