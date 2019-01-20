package io.luna.util.gui;

import io.luna.game.plugin.Plugin;
import io.luna.game.plugin.PluginMetadata;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptDependency;
import javafx.scene.control.CheckBoxTreeItem;

import java.text.Collator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static io.luna.util.StringUtils.COMMA_JOINER;
import static io.luna.util.gui.PluginGuiImage.*;

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
     * The plugin {@link ScriptDependency} names.
     */
    private final Set<String> dependencyNames;

    /**
     * The plugin {@link Script} names.
     */
    private final Set<String> scriptNames;

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
        dependencyNames = plugin.getDependencies().stream().map(ScriptDependency::getName).collect(Collectors.toSet());
        scriptNames = plugin.getScripts().stream().map(Script::getName).collect(Collectors.toSet());
        addTreeItemIcons(this, PLUGIN_IMG);
    }

    @Override
    public int compareTo(PluginTreeItem o) {
        return Collator.getInstance().compare(getValue(), o.getValue());
    }

    /**
     * Adds all scripts and dependencies as leaf child nodes.
     */
    void addScriptChildren() {
        plugin.getScripts().stream().map(Script::getName).forEach(this::addChild);
        plugin.getDependencies().stream().map(ScriptDependency::getName).forEach(this::addChild);
    }

    /**
     * Displays a description of the contained metadata onto the GUI.
     */
    void displayGuiDescription() {
        String none = "None";
        StringBuilder description = new StringBuilder();
        BiConsumer<String, Object> appendData = (t, u) -> description.append(t).append(": ").append(u).append("\n\n");

        appendData.accept("Name", getMetadata().getName());
        appendData.accept("Version", getMetadata().getVersion());
        appendData.accept("Description", getMetadata().getDescription());

        String dependencies = COMMA_JOINER.join(dependencyNames);
        appendData.accept("Dependencies", dependencies.isEmpty() ? none : dependencies);

        String scripts = COMMA_JOINER.join(scriptNames);
        appendData.accept("Scripts", scripts.isEmpty() ? none : scripts);

        String authors = COMMA_JOINER.join(getMetadata().getAuthors());
        appendData.accept("Authors", authors.isEmpty() ? none : authors);

        controller.getDescriptionArea().setText(description.toString());
    }

    /**
     * Adds {@code name} as a leaf child node.
     *
     * @param name The name.
     */
    private void addChild(String name) {
        CheckBoxTreeItem<String> child = new CheckBoxTreeItem<>(name);
        addTreeItemIcons(child, SCRIPT_IMG);
        getChildren().add(child);
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