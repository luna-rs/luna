package io.luna.util.gui;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;
import io.luna.game.plugin.Plugin;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static com.google.common.base.Preconditions.checkState;

/**
 * A Java representation of the plugin GUI. This model uses a hybrid of Swing and Javafx to display various
 * useful functions pertaining to managing plugins.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PluginGui {

    /**
     * An asynchronous Javafx task that initializes the scene.
     */
    private final class InitializeScene implements Runnable {

        @Override
        public void run() {
            try {
                // Load Javafx components.
                URL pluginManager = Resources.getResource("plugin_manager.fxml");
                FXMLLoader loader = new FXMLLoader(pluginManager);
                loader.setController(controller);

                Scene newScene = new Scene(loader.load());
                scene = Optional.of(newScene);
                mainPanel.setScene(newScene);

                // Update settings on Javafx components.
                settings.update();
            } catch (IOException e) {
                openErrorAlert(e);
            }
        }
    }

    /**
     * A window listener that will dispose the GUI on exit.
     */
    private final class WindowCloseListener extends WindowAdapter {

        @Override
        public void windowClosing(WindowEvent e) {
            close();
        }
    }

    /**
     * The settings.
     */
    private final PluginGuiSettings settings = new PluginGuiSettings(this);

    /**
     * The cached file data.
     */
    private final PluginGuiFileManager fileManager = new PluginGuiFileManager(this);

    /**
     * The Javafx controller.
     */
    private final PluginGuiController controller = new PluginGuiController(this);

    /**
     * The panel that will bridge Swing -> Javafx.
     */
    private final JFXPanel mainPanel = new JFXPanel();

    /**
     * The JFrame that will hold the main panel.
     */
    private final JFrame mainFrame = new JFrame("Plugin Manager");

    /**
     * The synchronization barrier.
     */
    private final CountDownLatch barrier = new CountDownLatch(1);

    /**
     * The Javafx scene.
     */
    private Optional<Scene> scene = Optional.empty();

    /**
     * A map of plugin names -> tree items.
     */
    private final ImmutableMap<String, PluginTreeItem> pluginItems;

    /**
     * Creates a new {@link PluginGui}.
     *
     * @param plugins The plugin data.
     */
    public PluginGui(Map<String, Plugin> plugins) {
        pluginItems = buildPluginItems(plugins);
    }

    /**
     * Launches this GUI. This method won't return until user input is received or until the
     * application is terminated.
     *
     * @return The selected plugins.
     * @throws IOException        If any I/O errors occur.
     */
    public Set<String> launch() throws IOException {
        checkState(!Platform.isFxApplicationThread(), "Cannot be called from Javafx thread.");

        // Load application.
        settings.load();
        loadMainFrame();
        loadScene();

        mainFrame.setVisible(true);

        // Wait here until user input is received.
        Uninterruptibles.awaitUninterruptibly(barrier);
        return settings.getSelected();
    }


    /**
     * Takes the map of all plugins in the plugin directory and returns an immutable map of
     * those plugins as tree items.
     *
     * @param plugins The plugins in the plugin dir.
     * @return The immutable map of tree items.
     */
    private ImmutableMap<String, PluginTreeItem> buildPluginItems(Map<String, Plugin> plugins) {
        Map<String, PluginTreeItem> treeItems = new LinkedHashMap<>();
        for (Plugin plugin : plugins.values()) {
            PluginTreeItem item = new PluginTreeItem(plugin, controller);
            item.addScriptChildren();

            treeItems.put(item.getValue(), item);
        }
        return ImmutableMap.copyOf(treeItems);
    }

    /**
     * Loads the main JFrame.
     *
     * @throws IOException If an error occurs while loading the GUI icon.
     */
    private void loadMainFrame() throws IOException {
        BufferedImage iconImage = ImageIO.read(Resources.getResource("plugin_icon.png"));
        mainFrame.setSize(505, 380);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setResizable(false);
        mainFrame.setAutoRequestFocus(true);
        mainFrame.setIconImage(iconImage);
        mainFrame.addWindowListener(new WindowCloseListener());
        mainFrame.add(mainPanel);
    }

    /**
     * Loads the Javafx components.
     */
    private void loadScene() {
        try {
            FutureTask<Boolean> delegateTask = new FutureTask<>(new InitializeScene(), true);
            Platform.runLater(delegateTask);
            Uninterruptibles.getUninterruptibly(delegateTask); // Wait for task to complete before continuing.
        } catch (ExecutionException e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Creates an alert instance.
     *
     * @param alertType The type of alert.
     * @param buttons The buttons to include on the alert.
     * @return The alert instance.
     */
    Alert createAlert(AlertType alertType, ButtonType... buttons) {
        Alert alert = new Alert(alertType, "", buttons);
        if (settings.isDarkMode()) {
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add("dark_theme.css");
        }
        return alert;
    }

    /**
     * Opens an error alert window.
     *
     * @param error The error that was thrown.
     * @return The clicked button, wrapped in an optional.
     */
    Optional<ButtonType> openErrorAlert(Throwable error) {
        error.printStackTrace();

        Alert errorAlert = createAlert(AlertType.ERROR);
        errorAlert.setContentText(Throwables.getStackTraceAsString(error));
        return errorAlert.showAndWait();
    }

    /**
     * Opens a confirmation alert window. Blocks until user input is received.
     *
     * @param content The text to display on the alert.
     * @param buttons The buttons to display on the alert.
     * @return An optional representing the user input.
     */
    Optional<ButtonType> openConfirmAlert(String content, ButtonType... buttons) {
        Alert optionAlert = createAlert(AlertType.CONFIRMATION, buttons);
        optionAlert.setContentText(content);
        return optionAlert.showAndWait();
    }


    /**
     * Called when the GUI is closed.
     */
    void close() {
        try {
            // Save settings if needed.
            if (settings.isSaveOnExit()) {
                settings.save();
            }

            // Dispose of Swing resources.
            Runnable swingTask = () -> {
                mainFrame.setVisible(false);
                mainFrame.dispose();
            };
            if (EventQueue.isDispatchThread()) {
                swingTask.run();
            } else {
                EventQueue.invokeAndWait(swingTask);
            }

            // Dispose of Javafx resources.
            Platform.exit();
        } catch (InvocationTargetException | InterruptedException ex) {
            openErrorAlert(ex);
        } finally {
            barrier.countDown();
        }
    }

    /**
     * @return The settings.
     */
    PluginGuiSettings getSettings() {
        return settings;
    }

    /**
     * @return The cached file data.
     */
    PluginGuiFileManager getFileManager() {
        return fileManager;
    }

    /**
     * @return The Javafx controller.
     */
    PluginGuiController getController() {
        return controller;
    }

    /**
     * @return The synchronization barrier.
     */
    CountDownLatch getBarrier() {
        return barrier;
    }

    /**
     * @return The Javafx scene.
     */
    Scene getScene() {
        return scene.orElseThrow(() ->
                new IllegalStateException("getScene() can only be called once the GUI finishes loading."));
    }

    /**
     * @return The Javafx window.
     */
    Window getWindow() {
        return scene.map(Scene::getWindow).orElseThrow(() ->
                new IllegalStateException("getWindow() can only be called once the GUI finishes loading."));
    }

    /**
     * @return A map of plugin names -> tree items.
     */
    ImmutableMap<String, PluginTreeItem> getPluginItems() {
        return pluginItems;
    }
}