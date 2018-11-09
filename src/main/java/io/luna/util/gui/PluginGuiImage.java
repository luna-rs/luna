package io.luna.util.gui;

import com.google.common.io.Resources;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * A static-utility class that contains functions for the plugin GUI.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginGuiImage {

    /**
     * The scale height.
     */
    private static final int SCALE_HEIGHT = 20;

    /**
     * The scale width.
     */
    private static final int SCALE_WIDTH = 20;

    /**
     * The plugin image icon for the plugin viewer.
     */
    static final Image PLUGIN_IMG = new Image(getResource("plugin_icon.png"));

    /**
     * The package image icon for the plugin viewer.
     */
    static final Image PACKAGE_IMG = new Image(getResource("package_icon.png"));

    /**
     * The script image icon for the plugin viewer.
     */
    static final Image SCRIPT_IMG = new Image(getResource("script_icon.png"));

    /**
     * Retrieves a resource with {@code name}.
     *
     * @param name The name.
     * @return The resource stream.
     */
    static InputStream getResource(String name) {
        try {
            return Resources.getResource(name).openStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add {@code icon} image to {@code treeItem}.
     *
     * @param treeItem The tree item.
     * @param icon The icon.
     */
    static void addTreeItemIcons(CheckBoxTreeItem<String> treeItem, Image icon) {
        ImageView view = new ImageView(icon);
        view.setFitHeight(SCALE_HEIGHT);
        view.setFitWidth(SCALE_WIDTH);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        treeItem.setGraphic(view);
    }

    /**
     * Private constructor preventing instantiation.
     */
    private PluginGuiImage() {
    }
}