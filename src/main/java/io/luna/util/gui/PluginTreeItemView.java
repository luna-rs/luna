package io.luna.util.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeMultimap;
import javafx.scene.control.CheckBoxTreeItem;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;
import static io.luna.util.gui.PluginGuiImage.PACKAGE_IMG;
import static io.luna.util.gui.PluginGuiImage.addTreeItemIcons;

/**
 * A model that builds a link between all packages and the plugins contained within them.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeItemView {

    /**
     * A map of cached packages.
     */
    private final Map<String, CheckBoxTreeItem<String>> packages = new HashMap<>();

    /**
     * A map of package names -> plugin tree items.
     */
    private final ImmutableMap<String, PluginTreeItem> pluginMap;

    /**
     * The root item.
     */
    private final CheckBoxTreeItem<String> root;

    /**
     * Creates a new {@link PluginTreeItemView}.
     *
     * @param root The root item.
     * @param pluginMap A map of package names -> plugin tree items.
     */
    public PluginTreeItemView(CheckBoxTreeItem<String> root,
                              ImmutableMap<String, PluginTreeItem> pluginMap) {
        this.root = root;
        this.pluginMap = pluginMap;
    }

    /**
     * Builds a flat package view on the plugin viewer.
     */
    void buildFlat() {

        // Build multimap of fully qualified package names -> plugins.
        TreeMultimap<String, PluginTreeItem> packages =
                TreeMultimap.create(Collator.getInstance(), Comparator.naturalOrder());

        pluginMap.forEach((k, v) ->
                packages.put(v.getPlugin().getPackageName(), v));

        // Add plugin items to packages as children.
        for (Entry<String, Collection<PluginTreeItem>> entry : packages.asMap().entrySet()) {

            // Create package.
            CheckBoxTreeItem<String> head = new CheckBoxTreeItem<>(entry.getKey());
            addTreeItemIcons(head, PACKAGE_IMG);

            // Add plugin tree items as children to package.
            for (PluginTreeItem item : entry.getValue()) {
                addNonDuplicateChild(item, head);
            }

            // Then add package back to root.
            addNonDuplicateChild(head, root);
        }
    }

    /**
     * Builds a nested package view on the plugin viewer.
     */
    void buildNested() {
        // Use recursion here to obtain nested view.
        pluginMap.forEach((k, v) ->
                addNonDuplicateChild(buildNestedPackage(v), root));
    }

    /**
     * Recursively builds the nested plugin view.
     *
     * @param treeItem The tree item to build it for.
     * @return The package head.
     */
    private CheckBoxTreeItem<String> buildNestedPackage(PluginTreeItem treeItem) {
        String packageName = treeItem.getPlugin().getPackageName();
        checkState(!packageName.isEmpty(), "All plugins except the API must have a package.");

        ImmutableList<String> packageDir = ImmutableList.copyOf(packageName.split("\\."));
        CheckBoxTreeItem<String> head = getPackage(packageDir.get(0));
        walkNestedPackage(1, head, treeItem, packageDir);
        return head;
    }

    /**
     * Recursively walks through the packages and builds them.
     *
     * @param index The current index.
     * @param head The package head.
     * @param plugin The plugin tree item.
     * @param packageDir The immutable list of packages.
     */
    private void walkNestedPackage(int index,
                                   CheckBoxTreeItem<String> head,
                                   PluginTreeItem plugin,
                                   ImmutableList<String> packageDir) {
        if (index == packageDir.size()) {
            head.getChildren().add(plugin);
            return;
        }
        CheckBoxTreeItem<String> next = getPackage(packageDir.get(index));
        addNonDuplicateChild(next, head);
        walkNestedPackage(++index, next, plugin, packageDir);
    }

    /**
     * Returns the cached tree item for package {@code name}.
     *
     * @param name The package name.
     * @return The cached tree item.
     */
    private CheckBoxTreeItem<String> getPackage(String name) {
        CheckBoxTreeItem<String> treeItem = packages.get(name);

        // If no cache entry, create one.
        if (treeItem == null) {
            treeItem = new CheckBoxTreeItem<>(name);
            packages.put(name, treeItem);
        }

        // Add icon if needed.
        if (treeItem.getGraphic() == null) {
            addTreeItemIcons(treeItem, PACKAGE_IMG);
        }
        return treeItem;
    }

    /**
     * Adds a child to a root only if it isn't already contained in the root.
     *
     * @param newChild The child to add.
     * @param root The root to add the child to.
     */
    private void addNonDuplicateChild(CheckBoxTreeItem<String> newChild, CheckBoxTreeItem<String> root) {
        String newChildName = newChild.getValue();
        boolean hasChild = root.getChildren().stream().anyMatch(child ->
                child.getValue().equals(newChildName));
        if (!hasChild) {
            root.getChildren().add(newChild);
        }
    }
}