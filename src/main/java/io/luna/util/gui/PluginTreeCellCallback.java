package io.luna.util.gui;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.util.Callback;

import java.util.Map;
import java.util.Optional;

/**
 * A callback listener model that will create cells for every item on the plugin tree. This allows for each
 * plugin item to display a description on the GUI when its cell is clicked.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginTreeCellCallback implements Callback<TreeView<String>, TreeCell<String>> {

    /**
     * A {@link CheckBoxTreeCell} implementation representing the cell of a plugin tree item.
     */
    private final class PluginTreeCell extends CheckBoxTreeCell<String> {

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (item != null) {
                TreeItem<String> value = treeItemProperty().getValue();
                disableProperty().unbind();
                disableProperty().bind(value.leafProperty());
            }
        }

        /**
         * Sets the mouse clicked listener to display the plugin description.
         */
        private void setMouseClickedListener() {
            setOnMouseClicked(evt -> Optional.ofNullable(getTreeItem())
                    .map(TreeItem::getValue)
                    .map(pluginItems::get)
                    .ifPresent(PluginTreeItem::displayGuiDescription));
        }
    }

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
        PluginTreeCell newCell = new PluginTreeCell();
        newCell.setMouseClickedListener();
        return newCell;
    }
}