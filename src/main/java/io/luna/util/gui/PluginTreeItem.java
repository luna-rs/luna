package io.luna.util.gui;

import io.luna.game.plugin.Plugin;
import io.luna.game.plugin.PluginMetadata;
import io.luna.game.plugin.Script;
import javafx.scene.control.CheckBoxTreeItem;

import java.text.Collator;
import java.util.function.BiConsumer;

import static io.luna.util.StringUtils.COMMA_JOINER;
import static io.luna.util.gui.PluginGuiImage.PLUGIN_IMG;
import static io.luna.util.gui.PluginGuiImage.SCRIPT_IMG;
import static io.luna.util.gui.PluginGuiImage.addTreeItemIcons;

/**
 * A model representing a {@link CheckBoxTreeItem} plugin within a plugin tree. This class provides additional
 * functions that allow access to plugin metadata and the GUI description text area.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeItem extends CheckBoxTreeItem<String> implements Comparable<PluginTreeItem> {

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
        addTreeItemIcons(this, PLUGIN_IMG);
    }

    @Override
    public int compareTo(PluginTreeItem o) {
        return Collator.getInstance().compare(getValue(), o.getValue());
    }

    /**
     * Adds all scripts as leaf child nodes.
     */
    void addScriptChildren() {
        for (Script script : plugin.getScripts()) {
            CheckBoxTreeItem<String> child = new CheckBoxTreeItem<>(script.getName());
            addTreeItemIcons(child, SCRIPT_IMG);
            getChildren().add(child);
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