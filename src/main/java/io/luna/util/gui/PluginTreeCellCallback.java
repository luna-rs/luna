package io.luna.util.gui;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.util.Callback;

import java.util.Map;
import java.util.Optional;

/**
 * A callback model that will create cells for every item on the plugin tree. This allows for each plugin
 * item to display a description on the GUI when its cell is clicked.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeCellCallback implements Callback<TreeView<String>, TreeCell<String>> {

    /**
     * The plugin items.
     */
    private final Map<String, PluginTreeItem> pluginItems;

    /**
     * Creates a new {@link PluginTreeCellCallback}.
     *
     * @param pluginItems The plugin items.
     */
    PluginTreeCellCallback(Map<String, PluginTreeItem> pluginItems) {
        this.pluginItems = pluginItems;
    }

    @Override
    public TreeCell<String> call(TreeView<String> pluginTree) {
        CheckBoxTreeCell<String> newCell = createTreeCell();
        newCell.setOnMouseClicked(evt ->
                getPluginItem(newCell).ifPresent(PluginTreeItem::displayGuiDescription));
        return newCell;
    }

    /**
     * Creates and returns a new cell for the plugin tree.
     */
    private CheckBoxTreeCell<String> createTreeCell() {
        return new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                // TODO is this even needed?
                if (item != null) {
                    TreeItem<String> value = treeItemProperty().getValue();
                    disableProperty().unbind();
                    disableProperty().bind(value.leafProperty());
                }
            }
        };
    }

    /**
     * Returns an optional containing the plugin tree item on the argued cell.
     */
    private Optional<PluginTreeItem> getPluginItem(TreeCell<String> cell) {
        TreeItem<String> cellItem = cell.getTreeItem();
        if (cell.isEmpty() || cellItem == null) {
            return Optional.empty(); // There's nothing on the cell.
        }

        PluginTreeItem pluginItem = pluginItems.get(cellItem.getValue());
        return Optional.ofNullable(pluginItem);
    }
}