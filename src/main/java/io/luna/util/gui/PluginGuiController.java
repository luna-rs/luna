package io.luna.util.gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A model that builds and controls Javafx components. This class is linked with the "plugin_manager.fxml"
 * resource.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class PluginGuiController {

    /**
     * The plugin GUI.
     */
    private final PluginGui gui;

    /**
     * The change listener.
     */
    private PluginTreeChangeListener changeListener;

    /**
     * The main pane.
     */
    @FXML
    private VBox mainPane;

    /**
     * The plugin tree.
     */
    @FXML
    private TreeView<String> pluginTree;

    /**
     * The metadata text area.
     */
    @FXML
    private TextArea descriptionArea;

    /**
     * The show hidden menu item.
     */
    @FXML
    private CheckMenuItem showHidden;

    /**
     * The dark theme menu item.
     */
    @FXML
    private CheckMenuItem darkTheme;

    /**
     * The save on exit menu item.
     */
    @FXML
    private CheckMenuItem saveOnExit;

    /**
     * Creates a new {@link PluginGuiController}.
     *
     * @param gui The plugin GUI.
     */
    PluginGuiController(PluginGui gui) {
        this.gui = gui;
    }

    /**
     * Builds the tree within the plugin viewer.
     */
    void buildPluginViewer() {
        // TODO caching so that we don't have to redo this entire process every update?

        // Reset root and cell factory.
        pluginTree.setRoot(null);
        pluginTree.setCellFactory(null);

        // Create root and event listener for root.
        CheckBoxTreeItem<String> rootItem = new CheckBoxTreeItem<>("All plugins");
        rootItem.addEventHandler(CheckBoxTreeItem.checkBoxSelectionChangedEvent(), getChangeListener());

        // Create cell factory for the tree.
        PluginTreeCellCallback cellFactory = new PluginTreeCellCallback(gui.getPluginItems());
        pluginTree.setCellFactory(cellFactory);

        // And add plugins to the tree.
        gui.getPluginItems().forEach((k, v) -> {
            if (!v.getMetadata().isHidden() ||
                    gui.getSettings().isShowHidden()) {
                rootItem.getChildren().add(v);
            }
        });

        // Set the new root.
        pluginTree.setRoot(rootItem);
    }

    /**
     * Opens a URL with the default browser.
     */
    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            gui.openErrorAlert(e);
        }
    }

    /**
     * @return The change listener.
     */
    PluginTreeChangeListener getChangeListener() {
        if (changeListener == null) {
            // Cannot be instantiated in constructor or will throw NPE.
            changeListener = new PluginTreeChangeListener(gui.getSettings().getSelected());
        }
        return changeListener;
    }

    /**
     * @return The metadata text area.
     */
    TextArea getDescriptionArea() {
        return descriptionArea;
    }

    /**
     * @return The show hidden menu item.
     */
    CheckMenuItem getShowHidden() {
        return showHidden;
    }

    /**
     * @return The dark theme menu item.
     */
    CheckMenuItem getDarkTheme() {
        return darkTheme;
    }

    /**
     * @return The save on exit menu item.
     */
    CheckMenuItem getSaveOnExit() {
        return saveOnExit;
    }

    /**
     * Called when the "Dark theme" menu item is clicked.
     */
    @FXML
    private void onDarkTheme(ActionEvent evt) {
        CheckMenuItem menuItem = (CheckMenuItem) evt.getSource();
        if (menuItem.isSelected()) {
            gui.getSettings().setDarkMode(true);
            gui.getScene().getStylesheets().add("dark_theme.css");
        } else {
            gui.getSettings().setDarkMode(false);
            gui.getScene().getStylesheets().remove("dark_theme.css");
        }
    }

    /**
     * Called when the "Report a bug..." menu item is clicked.
     */
    @FXML
    private void onReportABug(ActionEvent evt) {
        openUrl("https://github.com/lare96/luna/issues/new");
    }

    /**
     * Saves the current settings.
     */
    @FXML
    private void onSave(ActionEvent evt) {
        if (gui.getSettings().save()) {
            Alert infoAlert = gui.createAlert(AlertType.INFORMATION);
            infoAlert.setTitle("Information");
            infoAlert.setContentText("Save completed successfully!");
            infoAlert.setHeaderText("Save settings");
            infoAlert.showAndWait();
        }
    }

    /**
     * Imports a compatible settings file.
     */
    @FXML
    private void onImport(ActionEvent evt) {
        gui.getFileManager().openFile(file -> {
            try {
                gui.getSettings().load(file.toPath());
                gui.getSettings().update();
            } catch (IllegalStateException | NullPointerException e) {
                gui.openErrorAlert(e);
            }
        });
    }

    /**
     * Exports the current settings.
     */
    @FXML
    private void onExport(ActionEvent evt) {
        gui.getFileManager().saveFile(file ->
                gui.getSettings().save(file.toPath()));
    }

    /**
     * Called when the "Show hidden" menu item is clicked.
     */
    @FXML
    private void onShowHidden(ActionEvent evt) {
        CheckMenuItem menuItem = (CheckMenuItem) evt.getSource();
        gui.getSettings().setShowHidden(menuItem.isSelected());
        buildPluginViewer();
    }

    /**
     * Called when the "Info" menu item is clicked.
     */
    @FXML
    private void onInfo(ActionEvent evt) {
        String infoContent = gui.getFileManager().getInfoFile();

        Alert infoAlert = gui.createAlert(AlertType.INFORMATION);
        infoAlert.setTitle("Information");
        infoAlert.setContentText(infoContent);
        infoAlert.setHeaderText("Info regarding this GUI");
        infoAlert.showAndWait();
    }

    /**
     * Called when the "License" menu item is clicked.
     */
    @FXML
    private void onLicense(ActionEvent evt) {
        String licenseContent = gui.getFileManager().getLicenseFile();

        Alert licenseAlert = gui.createAlert(AlertType.INFORMATION);
        licenseAlert.setTitle("License");
        licenseAlert.setContentText(licenseContent);
        licenseAlert.setHeaderText("The MIT License (MIT)");
        licenseAlert.showAndWait();
    }

    /**
     * Called when the "Github page..." menu item is clicked.
     */
    @FXML
    private void onGithubPage(ActionEvent evt) {
        openUrl("https://github.com/lare96/luna/");
    }

    /**
     * Called when the "Exit" menu item is clicked.
     */
    @FXML
    private void onExit(ActionEvent evt) {
        ButtonType buttonClicked = gui.openConfirmAlert("Are you sure you would like to exit?",
                ButtonType.YES, ButtonType.NO).orElse(ButtonType.NO);
        if (buttonClicked == ButtonType.YES) {
            gui.close();
        }
    }

    /**
     * Called when the "Finished" button is clicked.
     */
    @FXML
    private void onFinished(ActionEvent evt) {
        System.out.println("here1");
        gui.close();
    }

    /**
     * Called when the "Save on exit" menu item is clicked.
     */
    @FXML
    private void onSaveOnExit(ActionEvent evt) {
        CheckMenuItem menuItem = (CheckMenuItem) evt.getSource();
        gui.getSettings().setSaveOnExit(menuItem.isSelected());
    }

    /**
     * Called when the "Select all" menu item is clicked.
     */
    @FXML
    private void onSelectAll(ActionEvent evt) {
        changeListener.setFiringEvents(false);
        gui.getPluginItems().forEach((k, item) -> {
            item.setSelected(true);
            gui.getSettings().getSelected().add(k);
        });
        changeListener.setFiringEvents(true);
    }

    /**
     * Called when the "Deselect all" menu item is clicked.
     */
    @FXML
    private void onDeselectAll(ActionEvent evt) {
        changeListener.setFiringEvents(false);
        gui.getPluginItems().forEach((k, item) -> item.setSelected(false));
        gui.getSettings().getSelected().clear();
        changeListener.setFiringEvents(true);
    }
}