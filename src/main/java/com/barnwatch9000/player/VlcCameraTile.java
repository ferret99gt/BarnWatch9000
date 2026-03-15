package com.barnwatch9000.player;

import com.barnwatch9000.AppLog;
import com.barnwatch9000.model.CameraDevice;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VlcCameraTile extends StackPane
{
    private static final String[] FACTORY_OPTIONS = {
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
            "--avcodec-hw=any"
    };

    private final CameraDevice device;
    private final boolean mainStream;
    private final Region inputLayer = new Region();
    private final ImageView imageView = new ImageView();
    private final Object playerLock = new Object();
    private final Object frameLock = new Object();
    private final AtomicBoolean frameUpdateScheduled = new AtomicBoolean();
    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;
    private CallbackVideoSurface videoSurface;
    private PixelBuffer<ByteBuffer> pixelBuffer;
    private WritableImage writableImage;
    private ByteBuffer frameBuffer;
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

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());

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

        getChildren().addAll(imageView, overlayLayer, inputLayer);

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
        MediaPlayerFactory factoryToRelease;
        EmbeddedMediaPlayer playerToRelease;
        synchronized (playerLock)
        {
            disposed = true;
            playbackStarted = false;
            playerToRelease = mediaPlayer;
            factoryToRelease = mediaPlayerFactory;
            mediaPlayer = null;
            mediaPlayerFactory = null;
            videoSurface = null;
        }

        synchronized (frameLock)
        {
            pixelBuffer = null;
            writableImage = null;
            frameBuffer = null;
        }

        Platform.runLater(() -> imageView.setImage(null));

        if (playerToRelease != null)
        {
            try
            {
                playerToRelease.controls().stop();
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to stop VLC player during tile disposal for " + device.name(), ex);
            }

            try
            {
                playerToRelease.release();
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to release VLC media player for " + device.name(), ex);
            }
        }

        if (factoryToRelease != null)
        {
            try
            {
                factoryToRelease.release();
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to release VLC media player factory for " + device.name(), ex);
            }
        }
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
        EmbeddedMediaPlayer player;
        synchronized (playerLock)
        {
            if (disposed || mediaPlayer == null)
            {
                return;
            }
            playbackStarted = true;
            player = mediaPlayer;
        }

        try
        {
            player.controls().stop();
        }
        catch (RuntimeException ex)
        {
            AppLog.error("Failed to stop VLC player during reconnect for " + device.name(), ex);
        }

        try
        {
            player.media().play(device.streamUrl(mainStream), device.vlcOptions());
        }
        catch (RuntimeException ex)
        {
            synchronized (playerLock)
            {
                playbackStarted = false;
            }
            AppLog.error("Failed to reconnect stream for " + device.name(), ex);
        }
    }

    private void initializePlayerAsync()
    {
        Thread.ofPlatform().name("BarnWatch-VLC-" + device.id()).daemon(true).start(() -> {
            MediaPlayerFactory factory = new MediaPlayerFactory(FACTORY_OPTIONS);
            EmbeddedMediaPlayer player = factory.mediaPlayers().newEmbeddedMediaPlayer();
            CallbackVideoSurface surface = factory.videoSurfaces().newVideoSurface(new TileBufferFormatCallback(), new TileRenderCallback(), true);
            player.videoSurface().set(surface);

            synchronized (playerLock)
            {
                if (disposed)
                {
                    player.release();
                    factory.release();
                    return;
                }
                mediaPlayerFactory = factory;
                mediaPlayer = player;
                videoSurface = surface;
                playbackStarted = true;
            }

            try
            {
                player.media().play(device.streamUrl(mainStream), device.vlcOptions());
            }
            catch (RuntimeException ex)
            {
                synchronized (playerLock)
                {
                    playbackStarted = false;
                }
                AppLog.error("Failed to start playback for " + device.name(), ex);
            }
        });
    }

    private void scheduleFrameUpdate()
    {
        if (!frameUpdateScheduled.compareAndSet(false, true))
        {
            return;
        }

        Platform.runLater(() -> {
            try
            {
                PixelBuffer<ByteBuffer> currentPixelBuffer;
                synchronized (frameLock)
                {
                    currentPixelBuffer = pixelBuffer;
                    if (frameBuffer != null)
                    {
                        frameBuffer.rewind();
                    }
                }
                if (!disposed && currentPixelBuffer != null)
                {
                    currentPixelBuffer.updateBuffer(pixelBuffer -> null);
                }
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to update video frame for " + device.name(), ex);
            }
            finally
            {
                frameUpdateScheduled.set(false);
            }
        });
    }

    private void applyZoomState()
    {
        imageView.setScaleX(zoomFactor);
        imageView.setScaleY(zoomFactor);
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
        imageView.setTranslateX(translateX);
        imageView.setTranslateY(translateY);
    }

    private double maxPanX()
    {
        return Math.max(0, (getWidth() * (zoomFactor - 1.0)) / 2.0);
    }

    private double maxPanY()
    {
        return Math.max(0, (getHeight() * (zoomFactor - 1.0)) / 2.0);
    }

    private final class TileBufferFormatCallback implements BufferFormatCallback
    {
        @Override
        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight)
        {
            synchronized (frameLock)
            {
                frameBuffer = ByteBuffer.allocateDirect(sourceWidth * sourceHeight * 4);
                pixelBuffer = new PixelBuffer<>(sourceWidth, sourceHeight, frameBuffer, PixelFormat.getByteBgraPreInstance());
                writableImage = new WritableImage(pixelBuffer);
            }

            Platform.runLater(() -> {
                if (!disposed)
                {
                    imageView.setImage(writableImage);
                }
            });

            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }

        @Override
        public void allocatedBuffers(ByteBuffer[] buffers)
        {
            // Barn Watch uses its own JavaFX-facing frame buffer and copies only the newest frame.
        }
    }

    private final class TileRenderCallback implements RenderCallback
    {
        @Override
        public void display(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat)
        {
            if (disposed || nativeBuffers == null || nativeBuffers.length == 0)
            {
                return;
            }

            synchronized (frameLock)
            {
                if (frameBuffer == null)
                {
                    return;
                }

                ByteBuffer source = nativeBuffers[0].duplicate();
                source.clear();
                frameBuffer.clear();
                frameBuffer.put(source);
                frameBuffer.flip();
            }

            scheduleFrameUpdate();
        }
    }
}
