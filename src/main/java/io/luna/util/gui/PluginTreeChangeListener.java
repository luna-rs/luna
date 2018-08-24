package io.luna.util.gui;

import javafx.event.EventHandler;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.CheckBoxTreeItem.TreeModificationEvent;

import java.util.Set;

/**
 * An event handler that will listen for selection modifications to plugin items. We use this to keep track of
 * which plugins are selected.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeChangeListener implements EventHandler<TreeModificationEvent<String>> {

    /**
     * The set of selected plugins.
     */
    private final Set<String> selectedPlugins;

    /**
     * If selection events will be recorded.
     */
    private volatile boolean firingEvents = true;

    /**
     * Creates a new {@link PluginGui}.
     *
     * @param selectedPlugins The set of selected plugins.
     */
    PluginTreeChangeListener(Set<String> selectedPlugins) {
        this.selectedPlugins = selectedPlugins;
    }

    @Override
    public void handle(TreeModificationEvent<String> evt) {
        CheckBoxTreeItem<String> treeItem = evt.getTreeItem();
        if (firingEvents && treeItem instanceof PluginTreeItem) {
            PluginTreeItem pluginItem = (PluginTreeItem) evt.getTreeItem();
            if (pluginItem == null) {
                return;
            }
            handleSelectionChange(evt, pluginItem);
        }
    }

    /**
     * Sets whether or not to record selection events.
     */
   synchronized void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }

    /**
     * Determines which change happened and updates the selected plugin set.
     */
    private void handleSelectionChange(TreeModificationEvent<String> evt, PluginTreeItem item) {
        String name = item.getValue();
        if (evt.wasSelectionChanged()) {
            if (item.isSelected()) {
                selectedPlugins.add(name);
            } else {
                selectedPlugins.remove(name);
            }
        } else if (evt.wasIndeterminateChanged()) {
            if(item.isIndeterminate() || !item.isSelected()) {
                selectedPlugins.remove(name);
            } else if(item.isSelected()) {
                selectedPlugins.add(name);
            }
        }
    }
}