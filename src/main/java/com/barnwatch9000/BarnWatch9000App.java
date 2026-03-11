package com.barnwatch9000;

import com.barnwatch9000.db.AppSettingsRepository;
import com.barnwatch9000.db.CameraDeviceRepository;
import com.barnwatch9000.db.Database;
import com.barnwatch9000.model.CameraDevice;
import com.barnwatch9000.model.GridLayoutPreset;
import com.barnwatch9000.player.VlcCameraTile;
import com.barnwatch9000.player.VlcSupport;
import com.barnwatch9000.ui.DeviceManagerDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BarnWatch9000App extends Application
{
    private final ObservableList<CameraDevice> devices = FXCollections.observableArrayList();
    private final List<VlcCameraTile> activeTiles = new ArrayList<>();

    private Connection connection;
    private CameraDeviceRepository deviceRepository;
    private AppSettingsRepository settingsRepository;

    private Stage primaryStage;
    private Stage theaterStage;
    private Scene appScene;
    private GridPane wallGrid;
    private HBox controlsBar;
    private ComboBox<GridLayoutPreset> layoutSelect;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button backToGridButton;
    private Label pageLabel;
    private Label statusLabel;

    private int currentPage;
    private CameraDevice focusedDevice;
    private GridLayoutPreset previousLayout = GridLayoutPreset.QUAD;
    private int previousPage;
    private boolean theaterMode;
    private double windowedX;
    private double windowedY;
    private double windowedWidth;
    private double windowedHeight;
    private boolean windowedMaximized;
    private static final double DRAG_THRESHOLD = 6.0;
    private final Map<Integer, javafx.scene.Node> slotTargets = new HashMap<>();

    @Override
    public void start(Stage stage)
    {
        primaryStage = stage;

        try
        {
            connection = Database.open();
            Database.initialize(connection);
            deviceRepository = new CameraDeviceRepository(connection);
            settingsRepository = new AppSettingsRepository(connection);
            devices.setAll(deviceRepository.listAll());
        }
        catch (SQLException ex)
        {
            showFatal("Failed to initialize database", ex.getMessage());
            return;
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #191b1f, #090b0e);");

        wallGrid = new GridPane();
        wallGrid.setHgap(3);
        wallGrid.setVgap(3);
        wallGrid.setPadding(new Insets(1, 2, 1, 2));
        wallGrid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane center = new StackPane(wallGrid);
        center.setPadding(Insets.EMPTY);
        center.setAlignment(Pos.TOP_LEFT);
        root.setCenter(center);
        StackPane.setAlignment(wallGrid, Pos.TOP_LEFT);
        wallGrid.prefWidthProperty().bind(Bindings.max(0, center.widthProperty().subtract(8)));
        wallGrid.prefHeightProperty().bind(Bindings.max(0, center.heightProperty().subtract(4)));

        Button devicesButton = new Button("Devices");
        devicesButton.setOnAction(event -> openDeviceManager());

        GridLayoutPreset initialLayout = loadInitialLayout();
        layoutSelect = new ComboBox<>();
        layoutSelect.getItems().addAll(
                GridLayoutPreset.SINGLE,
                GridLayoutPreset.QUAD,
                GridLayoutPreset.NINE,
                GridLayoutPreset.SIX_TALL,
                GridLayoutPreset.SIX_WIDE,
                GridLayoutPreset.BIG_SIX,
                GridLayoutPreset.BIG_EIGHT);
        layoutSelect.setValue(initialLayout);
        previousLayout = initialLayout;
        layoutSelect.setOnAction(event -> {
            GridLayoutPreset selectedLayout = layoutSelect.getValue();
            if (selectedLayout == null)
            {
                return;
            }

            previousLayout = selectedLayout;
            persistSelectedLayout(selectedLayout);
            currentPage = 0;
            if (focusedDevice == null)
            {
                refreshWall();
            }
        });

        previousPageButton = new Button("<");
        previousPageButton.setOnAction(event -> {
            currentPage--;
            refreshWall();
        });

        nextPageButton = new Button(">");
        nextPageButton.setOnAction(event -> {
            currentPage++;
            refreshWall();
        });

        pageLabel = new Label();
        pageLabel.setStyle("-fx-text-fill: #d6d0c4; -fx-font-size: 13px;");

        backToGridButton = new Button("Back To Grid");
        backToGridButton.setOnAction(event -> exitFocusMode());

        Button fullscreenButton = new Button("Fullscreen");
        fullscreenButton.setOnAction(event -> toggleFullScreen());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #bfb8aa; -fx-font-size: 12px;");

        controlsBar = new HBox(10,
                devicesButton,
                layoutSelect,
                previousPageButton,
                nextPageButton,
                pageLabel,
                backToGridButton,
                spacer(),
                statusLabel,
                fullscreenButton);
        controlsBar.setPadding(new Insets(2, 4, 2, 4));
        controlsBar.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(controlsBar);

        appScene = new Scene(root, 1280, 720, Color.BLACK);
        appScene.setOnKeyPressed(event -> {
            if (theaterMode && event.getCode() == KeyCode.ESCAPE)
            {
                exitTheaterMode();
                event.consume();
            }
        });
        stage.setTitle("Barn Watch 9000");
        stage.setScene(appScene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        refreshWall();
    }

    private GridLayoutPreset loadInitialLayout()
    {
        if (settingsRepository == null)
        {
            return GridLayoutPreset.QUAD;
        }

        try
        {
            return settingsRepository.loadSelectedLayout().orElse(GridLayoutPreset.QUAD);
        }
        catch (SQLException ex)
        {
            showError("Failed to load app settings", ex.getMessage());
            return GridLayoutPreset.QUAD;
        }
    }

    private void persistSelectedLayout(GridLayoutPreset layout)
    {
        if (settingsRepository == null)
        {
            return;
        }

        try
        {
            settingsRepository.saveSelectedLayout(layout);
        }
        catch (SQLException ex)
        {
            showError("Failed to save app settings", ex.getMessage());
        }
    }

    private void openDeviceManager()
    {
        try
        {
            DeviceManagerDialog.show(primaryStage, deviceRepository, this::reloadDevices);
        }
        catch (SQLException ex)
        {
            showError("Failed to open device manager", ex.getMessage());
        }
    }

    private void reloadDevices()
    {
        try
        {
            devices.setAll(deviceRepository.listAll());
            if (focusedDevice != null)
            {
                focusedDevice = devices.stream()
                        .filter(device -> device.id().equals(focusedDevice.id()))
                        .findFirst()
                        .orElse(null);
            }
            currentPage = 0;
            refreshWall();
        }
        catch (SQLException ex)
        {
            showError("Failed to reload devices", ex.getMessage());
        }
    }

    private void refreshWall()
    {
        releaseTiles();
        wallGrid.getChildren().clear();
        wallGrid.getColumnConstraints().clear();
        wallGrid.getRowConstraints().clear();
        slotTargets.clear();

        if (focusedDevice != null)
        {
            buildFocusedWall();
            return;
        }

        GridLayoutPreset layout = layoutSelect.getValue();
        int totalPages = totalPages(layout);
        currentPage = clampPage(currentPage, totalPages);

        for (int column = 0; column < layout.columns(); column++)
        {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / layout.columns());
            cc.setHgrow(Priority.ALWAYS);
            wallGrid.getColumnConstraints().add(cc);
        }

        for (int row = 0; row < layout.rows(); row++)
        {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / layout.rows());
            rc.setVgrow(Priority.ALWAYS);
            wallGrid.getRowConstraints().add(rc);
        }

        int startIndex = currentPage * layout.capacity();
        for (int slot = 0; slot < layout.placements().size(); slot++)
        {
            GridLayoutPreset.TilePlacement placement = layout.placements().get(slot);
            int column = placement.column();
            int row = placement.row();
            if (startIndex + slot < devices.size())
            {
                CameraDevice device = devices.get(startIndex + slot);
                int globalIndex = startIndex + slot;
                VlcCameraTile tile = createGridTile(device, globalIndex);
                wallGrid.add(tile, column, row, placement.columnSpan(), placement.rowSpan());
                GridPane.setHgrow(tile, Priority.ALWAYS);
                GridPane.setVgrow(tile, Priority.ALWAYS);
                activeTiles.add(tile);
            }
            else
            {
                int emptyIndex = startIndex + slot;
                StackPane placeholder = createPlaceholder("Empty", emptyIndex);
                wallGrid.add(placeholder, column, row, placement.columnSpan(), placement.rowSpan());
                GridPane.setHgrow(placeholder, Priority.ALWAYS);
                GridPane.setVgrow(placeholder, Priority.ALWAYS);
            }
        }

        previousPageButton.setDisable(currentPage <= 0);
        nextPageButton.setDisable(currentPage >= totalPages - 1);
        backToGridButton.setVisible(false);
        backToGridButton.setManaged(false);
        layoutSelect.setDisable(false);
        pageLabel.setText("Page " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(buildStatus(layout.toString(), false));
    }

    private void buildFocusedWall()
    {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(100);
        cc.setHgrow(Priority.ALWAYS);
        wallGrid.getColumnConstraints().add(cc);

        RowConstraints rc = new RowConstraints();
        rc.setPercentHeight(100);
        rc.setVgrow(Priority.ALWAYS);
        wallGrid.getRowConstraints().add(rc);

        VlcCameraTile tile = new VlcCameraTile(focusedDevice, true);
        configureTileInteractions(tile, 0, false, event -> {
            if (event.getClickCount() == 2)
            {
                exitFocusMode();
            }
        });
        wallGrid.add(tile, 0, 0);
        GridPane.setHgrow(tile, Priority.ALWAYS);
        GridPane.setVgrow(tile, Priority.ALWAYS);
        activeTiles.add(tile);

        previousPageButton.setDisable(true);
        nextPageButton.setDisable(true);
        pageLabel.setText("Focused");
        backToGridButton.setVisible(true);
        backToGridButton.setManaged(true);
        layoutSelect.setDisable(true);
        statusLabel.setText(buildStatus("1x1", true));
    }

    private void enterFocusMode(CameraDevice device)
    {
        previousLayout = layoutSelect.getValue();
        previousPage = currentPage;
        focusedDevice = device;
        refreshWall();
    }

    private void exitFocusMode()
    {
        focusedDevice = null;
        layoutSelect.setValue(previousLayout);
        currentPage = previousPage;
        refreshWall();
    }

    private VlcCameraTile createGridTile(CameraDevice device, int globalIndex)
    {
        VlcCameraTile tile = new VlcCameraTile(device, false);
        configureTileInteractions(tile, globalIndex, true, event -> {
            if (event.getClickCount() == 2)
            {
                enterFocusMode(device);
            }
        });
        registerSlotTarget(tile.interactionLayer(), globalIndex);
        return tile;
    }

    private StackPane createPlaceholder(String text, int targetIndex)
    {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-text-fill: #8d887e;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-border-color: rgba(255,255,255,0.09);");
        registerSlotTarget(pane, targetIndex);
        return pane;
    }

    private void registerSlotTarget(javafx.scene.Node target, int targetIndex)
    {
        slotTargets.put(targetIndex, target);
    }

    private boolean reorderDevices(int sourceIndex, int targetIndex)
    {
        if (sourceIndex < 0 || sourceIndex >= devices.size())
        {
            return false;
        }
        if (targetIndex < 0)
        {
            return false;
        }

        List<CameraDevice> reordered = new ArrayList<>(devices);
        if (targetIndex < reordered.size())
        {
            Collections.swap(reordered, sourceIndex, targetIndex);
        }
        else
        {
            CameraDevice moved = reordered.remove(sourceIndex);
            reordered.add(moved);
        }

        List<CameraDevice> normalized = new ArrayList<>(reordered.size());
        for (int i = 0; i < reordered.size(); i++)
        {
            normalized.add(reordered.get(i).withSortOrder(i));
        }

        try
        {
            deviceRepository.saveOrdering(normalized);
            devices.setAll(normalized);
            if (focusedDevice == null)
            {
                relayoutVisiblePageWithoutReconnect();
            }
            else
            {
                refreshWall();
            }
            return true;
        }
        catch (SQLException ex)
        {
            showError("Failed to save device order", ex.getMessage());
            return false;
        }
    }

    private String buildStatus(String layoutLabel, boolean focused)
    {
        String discovery = VlcSupport.status();
        if (devices.isEmpty())
        {
            return discovery + "  |  No devices configured";
        }
        return discovery + "  |  " + devices.size() + " devices  |  " + layoutLabel + (focused ? " main stream" : " sub streams");
    }

    private void releaseTiles()
    {
        for (VlcCameraTile tile : activeTiles)
        {
            tile.dispose();
        }
        activeTiles.clear();
    }

    private void shutdown()
    {
        releaseTiles();
        if (connection != null)
        {
            try
            {
                connection.close();
            }
            catch (SQLException ignored)
            {
            }
        }
    }

    private static Region spacer()
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static int clampPage(int page, int totalPages)
    {
        if (totalPages <= 0)
        {
            return 0;
        }
        return Math.max(0, Math.min(page, totalPages - 1));
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(activeStage());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showFatal(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args)
    {
        AppLog.installGlobalHandler();
        launch(args);
    }

    private void toggleFullScreen()
    {
        if (theaterMode)
        {
            exitTheaterMode();
        }
        else
        {
            enterTheaterMode();
        }
    }

    private void enterTheaterMode()
    {
        if (primaryStage == null)
        {
            return;
        }

        Screen screen = currentScreen(primaryStage);
        var bounds = screen.getBounds();

        windowedX = primaryStage.getX();
        windowedY = primaryStage.getY();
        windowedWidth = primaryStage.getWidth();
        windowedHeight = primaryStage.getHeight();
        windowedMaximized = primaryStage.isMaximized();

        if (theaterStage == null)
        {
            theaterStage = new Stage(StageStyle.UNDECORATED);
            theaterStage.setOnCloseRequest(event -> {
                event.consume();
                exitTheaterMode();
            });
        }

        theaterMode = true;
        controlsBar.setVisible(false);
        controlsBar.setManaged(false);

        primaryStage.hide();
        primaryStage.setScene(null);

        theaterStage.setScene(appScene);
        theaterStage.setX(bounds.getMinX());
        theaterStage.setY(bounds.getMinY());
        theaterStage.setWidth(bounds.getWidth());
        theaterStage.setHeight(bounds.getHeight());
        theaterStage.show();
        theaterStage.toFront();
        Platform.runLater(() -> {
            if (appScene.getRoot() != null)
            {
                appScene.getRoot().requestFocus();
            }
        });
    }

    private void exitTheaterMode()
    {
        if (!theaterMode)
        {
            return;
        }

        theaterMode = false;
        if (theaterStage != null)
        {
            theaterStage.hide();
            theaterStage.setScene(null);
        }

        primaryStage.setScene(appScene);
        controlsBar.setVisible(true);
        controlsBar.setManaged(true);
        primaryStage.show();
        primaryStage.setX(windowedX);
       primaryStage.setY(windowedY);
        primaryStage.setWidth(windowedWidth);
        primaryStage.setHeight(windowedHeight);
        primaryStage.setMaximized(windowedMaximized);
    }

    private Stage activeStage()
    {
        if (theaterMode && theaterStage != null)
        {
            return theaterStage;
        }
        return primaryStage;
    }

    private Screen currentScreen(Stage stage)
    {
        if (stage == null)
        {
            return Screen.getPrimary();
        }

        double x = stage.getX();
        double y = stage.getY();
        double width = Math.max(1, stage.getWidth());
        double height = Math.max(1, stage.getHeight());
        double centerX = x + (width / 2.0);
        double centerY = y + (height / 2.0);

        for (Screen screen : Screen.getScreensForRectangle(centerX, centerY, 1, 1))
        {
            return screen;
        }

        for (Screen screen : Screen.getScreensForRectangle(x, y, width, height))
        {
            return screen;
        }

        return Screen.getPrimary();
    }

    private void configureTileInteractions(VlcCameraTile tile, int globalIndex, boolean reorderEnabled, javafx.event.EventHandler<MouseEvent> clickHandler)
    {
        Region layer = tile.interactionLayer();
        ContextMenu menu = new ContextMenu();
        MenuItem reconnectItem = new MenuItem("Reconnect");
        reconnectItem.setOnAction(event -> tile.reconnect());
        menu.getItems().add(reconnectItem);

        final double[] pressSceneX = new double[1];
        final double[] pressSceneY = new double[1];
        final boolean[] panning = new boolean[1];
        final boolean[] swapArmed = new boolean[1];

        layer.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.SECONDARY)
            {
                menu.show(layer, event.getScreenX(), event.getScreenY());
                event.consume();
                return;
            }

            menu.hide();
            if (event.getButton() != MouseButton.PRIMARY)
            {
                return;
            }

            pressSceneX[0] = event.getSceneX();
            pressSceneY[0] = event.getSceneY();
            swapArmed[0] = false;
            if (tile.isZoomed())
            {
                tile.beginPan(event.getSceneX(), event.getSceneY());
                panning[0] = true;
            }
            else
            {
                panning[0] = false;
            }
        });

        layer.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown())
            {
                return;
            }

            if (panning[0] && tile.isZoomed())
            {
                if (isInside(layer, event.getX(), event.getY()))
                {
                    tile.panTo(event.getSceneX(), event.getSceneY());
                    event.consume();
                    return;
                }
                tile.endPan();
                panning[0] = false;
            }

            if (!reorderEnabled || swapArmed[0])
            {
                return;
            }

            double dx = event.getSceneX() - pressSceneX[0];
            double dy = event.getSceneY() - pressSceneY[0];
            if (Math.hypot(dx, dy) < DRAG_THRESHOLD)
            {
                return;
            }
            swapArmed[0] = true;
            event.consume();
        });

        layer.setOnMouseReleased(event -> {
            if (panning[0])
            {
                tile.endPan();
            }
            if (swapArmed[0] && reorderEnabled)
            {
                Integer targetIndex = findTargetIndex(event.getSceneX(), event.getSceneY());
                if (targetIndex != null && targetIndex != globalIndex)
                {
                    reorderDevices(globalIndex, targetIndex);
                }
            }
            panning[0] = false;
            swapArmed[0] = false;
        });

        layer.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY)
            {
                return;
            }
            menu.hide();
            clickHandler.handle(event);
        });
    }

    private void relayoutVisiblePageWithoutReconnect()
    {
        GridLayoutPreset layout = layoutSelect.getValue();
        int startIndex = currentPage * layout.capacity();
        java.util.Map<String, VlcCameraTile> tilesById = new java.util.HashMap<>();
        for (VlcCameraTile tile : activeTiles)
        {
            tilesById.put(tile.device().id(), tile);
        }

        List<VlcCameraTile> newVisibleTiles = new ArrayList<>();
        wallGrid.getChildren().clear();
        slotTargets.clear();
        for (int slot = 0; slot < layout.placements().size(); slot++)
        {
            GridLayoutPreset.TilePlacement placement = layout.placements().get(slot);
            int column = placement.column();
            int row = placement.row();
            int deviceIndex = startIndex + slot;
            if (deviceIndex < devices.size())
            {
                CameraDevice device = devices.get(deviceIndex);
                VlcCameraTile tile = tilesById.get(device.id());
                if (tile != null)
                {
                    configureTileInteractions(tile, deviceIndex, true, event -> {
                        if (event.getClickCount() == 2)
                        {
                            enterFocusMode(tile.device());
                        }
                    });
                    wallGrid.add(tile, column, row, placement.columnSpan(), placement.rowSpan());
                    GridPane.setHgrow(tile, Priority.ALWAYS);
                    GridPane.setVgrow(tile, Priority.ALWAYS);
                    registerSlotTarget(tile.interactionLayer(), deviceIndex);
                    newVisibleTiles.add(tile);
                }
                else
                {
                    VlcCameraTile replacement = createGridTile(device, deviceIndex);
                    wallGrid.add(replacement, column, row, placement.columnSpan(), placement.rowSpan());
                    GridPane.setHgrow(replacement, Priority.ALWAYS);
                    GridPane.setVgrow(replacement, Priority.ALWAYS);
                    newVisibleTiles.add(replacement);
                }
            }
            else
            {
                StackPane placeholder = createPlaceholder("Empty", deviceIndex);
                wallGrid.add(placeholder, column, row, placement.columnSpan(), placement.rowSpan());
                GridPane.setHgrow(placeholder, Priority.ALWAYS);
                GridPane.setVgrow(placeholder, Priority.ALWAYS);
            }
        }
        activeTiles.clear();
        activeTiles.addAll(newVisibleTiles);
        previousPageButton.setDisable(currentPage <= 0);
        nextPageButton.setDisable(currentPage >= totalPages(layout) - 1);
        pageLabel.setText("Page " + (currentPage + 1) + " / " + totalPages(layout));
        statusLabel.setText(buildStatus(layout.toString(), false));
    }

    private int totalPages(GridLayoutPreset layout)
    {
        return Math.max(1, (int) Math.ceil(devices.size() / (double) layout.capacity()));
    }

    private static boolean isInside(Region region, double x, double y)
    {
        return x >= 0 && y >= 0 && x <= region.getWidth() && y <= region.getHeight();
    }

    private Integer findTargetIndex(double sceneX, double sceneY)
    {
        for (Map.Entry<Integer, javafx.scene.Node> entry : slotTargets.entrySet())
        {
            javafx.scene.Node node = entry.getValue();
            if (node.getScene() == null)
            {
                continue;
            }
            var bounds = node.localToScene(node.getBoundsInLocal());
            if (bounds.contains(sceneX, sceneY))
            {
                return entry.getKey();
            }
        }
        return null;
    }
}
