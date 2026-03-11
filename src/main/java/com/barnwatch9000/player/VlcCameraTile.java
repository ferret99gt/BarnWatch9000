package com.barnwatch9000.player;

import com.barnwatch9000.AppLog;
import com.barnwatch9000.model.CameraDevice;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public final class VlcCameraTile extends StackPane
{
    private final CameraDevice device;
    private final boolean mainStream;
    private final SwingNode swingNode = new SwingNode();
    private final Region inputLayer = new Region();
    private final Object playerLock = new Object();
    private CallbackMediaPlayerComponent mediaPlayerComponent;
    private JPanel playerPanel;
    private HierarchyListener playerHierarchyListener;
    private boolean disposed;
    private boolean playbackStarted;
    private double zoomFactor = 1.0;
    private double translateX;
    private double translateY;
    private double panAnchorSceneX;
    private double panAnchorSceneY;
    private double panAnchorTranslateX;
    private double panAnchorTranslateY;

    public VlcCameraTile(CameraDevice device, boolean mainStream)
    {
        this.device = device;
        this.mainStream = mainStream;

        setStyle("-fx-background-color: #080808; -fx-border-color: rgba(255,255,255,0.12); -fx-border-width: 1;");
        setMinSize(120, 90);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        Label nameLabel = new Label(device.name());
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 8, 0.6, 0, 1);");
        nameLabel.setMouseTransparent(true);

        Label modeLabel = new Label(mainStream ? "Main stream" : "Sub stream");
        modeLabel.setTextFill(Color.web("#c7d2da"));
        modeLabel.setStyle("-fx-font-size: 11px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 8, 0.6, 0, 1);");
        modeLabel.setMouseTransparent(true);

        Region labelSpacer = new Region();
        HBox.setHgrow(labelSpacer, javafx.scene.layout.Priority.ALWAYS);

        HBox overlay = new HBox(10, nameLabel, labelSpacer, modeLabel);
        overlay.setPadding(new Insets(0, 12, 8, 12));
        overlay.setAlignment(Pos.CENTER_LEFT);
        overlay.setMouseTransparent(true);
        overlay.setMaxWidth(Double.MAX_VALUE);
        overlay.setMaxHeight(Region.USE_PREF_SIZE);

        BorderPane overlayLayer = new BorderPane();
        overlayLayer.setPickOnBounds(false);
        overlayLayer.setMouseTransparent(true);
        overlayLayer.setBottom(overlay);

        inputLayer.setStyle("-fx-background-color: transparent;");
        inputLayer.prefWidthProperty().bind(widthProperty());
        inputLayer.prefHeightProperty().bind(heightProperty());
        inputLayer.setOnScroll(event -> {
            double delta = event.getDeltaY() > 0 ? 0.12 : -0.12;
            zoomFactor = clamp(zoomFactor + delta, 1.0, 2.5);
            applyZoomState();
            event.consume();
        });

        getChildren().addAll(swingNode, overlayLayer, inputLayer);

        if (VlcSupport.ensureAvailable())
        {
            initializePlayerAsync();
        }
        else
        {
            Label unavailableLabel = new Label("VLC not found");
            unavailableLabel.setTextFill(Color.WHITE);
            unavailableLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
            unavailableLabel.setMouseTransparent(true);
            getChildren().add(unavailableLabel);
            StackPane.setAlignment(unavailableLabel, Pos.CENTER);
        }
    }

    public Region interactionLayer()
    {
        return inputLayer;
    }

    public CameraDevice device()
    {
        return device;
    }

    public boolean mainStream()
    {
        return mainStream;
    }

    public void dispose()
    {
        CallbackMediaPlayerComponent componentToRelease;
        JPanel panelToClear;
        HierarchyListener hierarchyListenerToRemove;
        synchronized (playerLock)
        {
            disposed = true;
            playbackStarted = false;
            componentToRelease = mediaPlayerComponent;
            mediaPlayerComponent = null;
            panelToClear = playerPanel;
            hierarchyListenerToRemove = playerHierarchyListener;
            playerPanel = null;
            playerHierarchyListener = null;
        }

        Platform.runLater(() -> {
            swingNode.setContent(null);
            getChildren().removeIf(node -> node instanceof Label label && "VLC not found".equals(label.getText()));

            SwingUtilities.invokeLater(() -> {
                if (panelToClear != null)
                {
                    if (hierarchyListenerToRemove != null)
                    {
                        panelToClear.removeHierarchyListener(hierarchyListenerToRemove);
                    }
                    panelToClear.removeAll();
                }

                if (componentToRelease != null)
                {
                    try
                    {
                        componentToRelease.mediaPlayer().controls().stop();
                    }
                    catch (RuntimeException ex)
                    {
                        AppLog.error("Failed to stop VLC player during tile disposal for " + device.name(), ex);
                    }
                    componentToRelease.release();
                }
            });
        });
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isZoomed()
    {
        return zoomFactor > 1.001;
    }

    public void beginPan(double sceneX, double sceneY)
    {
        panAnchorSceneX = sceneX;
        panAnchorSceneY = sceneY;
        panAnchorTranslateX = translateX;
        panAnchorTranslateY = translateY;
    }

    public void panTo(double sceneX, double sceneY)
    {
        double deltaX = sceneX - panAnchorSceneX;
        double deltaY = sceneY - panAnchorSceneY;
        translateX = clamp(panAnchorTranslateX + deltaX, -maxPanX(), maxPanX());
        translateY = clamp(panAnchorTranslateY + deltaY, -maxPanY(), maxPanY());
        applyZoomState();
    }

    public void endPan()
    {
        panAnchorSceneX = 0;
        panAnchorSceneY = 0;
        panAnchorTranslateX = translateX;
        panAnchorTranslateY = translateY;
    }

    public void reconnect()
    {
        SwingUtilities.invokeLater(() -> {
            CallbackMediaPlayerComponent component;
            synchronized (playerLock)
            {
                if (disposed || mediaPlayerComponent == null)
                {
                    return;
                }
                playbackStarted = true;
                component = mediaPlayerComponent;
            }

            try
            {
                component.mediaPlayer().controls().stop();
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to stop VLC player during reconnect for " + device.name(), ex);
            }

            try
            {
                component.mediaPlayer().media().play(device.streamUrl(mainStream), device.vlcOptions());
            }
            catch (RuntimeException ex)
            {
                synchronized (playerLock)
                {
                    playbackStarted = false;
                }
                AppLog.error("Failed to reconnect stream for " + device.name(), ex);
            }
        });
    }

    private void initializePlayerAsync()
    {
        SwingUtilities.invokeLater(() -> {
            synchronized (playerLock)
            {
                if (disposed)
                {
                    return;
                }
                mediaPlayerComponent = new CallbackMediaPlayerComponent(
                        "--quiet",
                        "--no-video-title-show",
                        "--rtsp-tcp",
                        "--network-caching=3000",
                        "--no-audio",
                        "--no-drop-late-frames",
                        "--no-skip-frames",
                        "--rtsp-frame-buffer-size=5000000",
                        "--clock-jitter=0",
                        "--clock-synchro=0",
                        "--avcodec-hw=any");
            }

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(mediaPlayerComponent, BorderLayout.CENTER);
            HierarchyListener hierarchyListener = event -> {
                long flags = event.getChangeFlags();
                if ((flags & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 || (flags & HierarchyEvent.SHOWING_CHANGED) != 0)
                {
                    startPlaybackIfReady(panel);
                }
            };
            panel.addHierarchyListener(hierarchyListener);

            synchronized (playerLock)
            {
                if (disposed)
                {
                    panel.removeHierarchyListener(hierarchyListener);
                    panel.removeAll();
                    mediaPlayerComponent.release();
                    mediaPlayerComponent = null;
                    return;
                }
                playerPanel = panel;
                playerHierarchyListener = hierarchyListener;
            }

            Platform.runLater(() -> {
                synchronized (playerLock)
                {
                    if (disposed)
                    {
                        return;
                    }
                }
                swingNode.setContent(panel);
            });
        });
    }

    private void startPlaybackIfReady(JPanel panel)
    {
        CallbackMediaPlayerComponent component;
        synchronized (playerLock)
        {
            if (disposed || playbackStarted || mediaPlayerComponent == null)
            {
                return;
            }
            if (!panel.isDisplayable() || !panel.isShowing())
            {
                return;
            }
            playbackStarted = true;
            component = mediaPlayerComponent;
        }

        try
        {
            component.mediaPlayer().media().play(device.streamUrl(mainStream), device.vlcOptions());
        }
        catch (RuntimeException ex)
        {
            synchronized (playerLock)
            {
                playbackStarted = false;
            }
            AppLog.error("Failed to start playback for " + device.name(), ex);
        }
    }

    private void applyZoomState()
    {
        swingNode.setScaleX(zoomFactor);
        swingNode.setScaleY(zoomFactor);
        if (!isZoomed())
        {
            translateX = 0;
            translateY = 0;
        }
        else
        {
            translateX = clamp(translateX, -maxPanX(), maxPanX());
            translateY = clamp(translateY, -maxPanY(), maxPanY());
        }
        swingNode.setTranslateX(translateX);
        swingNode.setTranslateY(translateY);
    }

    private double maxPanX()
    {
        return Math.max(0, (getWidth() * (zoomFactor - 1.0)) / 2.0);
    }

    private double maxPanY()
    {
        return Math.max(0, (getHeight() * (zoomFactor - 1.0)) / 2.0);
    }
}
