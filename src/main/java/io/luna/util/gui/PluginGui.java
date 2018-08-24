package io.luna.util.gui;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.luna.game.plugin.Plugin;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBoxTreeItem;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static com.google.common.base.Preconditions.checkState;

/**
 * A Java representation of the plugin GUI. This model uses a hybrid of Swing and Javafx to display
 * various useful functions pertaining to managing plugins.
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
     * The Jframe that will hold {@code mainPanel}.
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
     * Launches this GUI and blocks until user input is received.
     */
    public Set<String> launch() throws IOException, InterruptedException, ExecutionException {
        checkState(!Platform.isFxApplicationThread(), "Cannot be called from Javafx thread.");

        // Load application.
        settings.load();
        loadMainFrame();
        loadScene();

        mainFrame.setVisible(true);

        // Wait here until user input is received.
        barrier.await();

        return settings.getSelected();
    }


    /**
     * Returns a map of plugin names -> tree items.
     */
    private ImmutableMap<String, PluginTreeItem> buildPluginItems(Map<String, Plugin> plugins) {
        Map<String, PluginTreeItem> map = new LinkedHashMap<>();
        plugins.forEach((k, v) -> {
            PluginTreeItem item = new PluginTreeItem(v.getMetadata(), controller);
            for (String name : v.getFiles().keySet()) { // Add script leaves.
                item.getChildren().add(new CheckBoxTreeItem<>(name));
            }
            map.put(k, item);
        });
        return ImmutableMap.copyOf(map);
    }

    /**
     * Loads the main JFrame.
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
    private void loadScene() throws ExecutionException, InterruptedException {
        FutureTask<Boolean> delegateTask = new FutureTask<>(new InitializeScene(), true);
        Platform.runLater(delegateTask);
        delegateTask.get(); // Wait for task to complete before continuing.
    }

    /**
     * Creates an alert instance.
     */
    Alert createAlert(AlertType alertType) {
        return createAlert(alertType, null);
    }

    /**
     * Creates an alert instance.
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
     */
    Optional<ButtonType> openErrorAlert(Throwable error) {
        error.printStackTrace();

        Alert errorAlert = createAlert(AlertType.ERROR);
        errorAlert.setContentText(Throwables.getStackTraceAsString(error));
        return errorAlert.showAndWait();
    }

    /**
     * Opens a confirmation alert window.
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

            // Dispose of Javafx resources.
            FutureTask<?> hideWindow = new FutureTask<>(() -> getWindow().hide(), null);
            Platform.runLater(hideWindow);
            hideWindow.get();

            // Dispose of Swing resources.
            EventQueue.invokeAndWait(() -> {
                mainFrame.setVisible(false);
                mainFrame.dispose();
            });

            // Dispose remaining Javafx resources.
            Platform.exit();
        } catch (InvocationTargetException | ExecutionException | InterruptedException ex) {
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