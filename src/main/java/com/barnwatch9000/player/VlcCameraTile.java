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
    private final CameraDevice device;
    private final boolean mainStream;
    private final VlcRuntime vlcRuntime;
    private final Region inputLayer = new Region();
    private final ImageView imageView = new ImageView();
    private final Object frameLock = new Object();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean frameUpdateScheduled = new AtomicBoolean();
    private EmbeddedMediaPlayer mediaPlayer;
    private CallbackVideoSurface videoSurface;
    private FrameState frameState;
    private double zoomFactor = 1.0;
    private double translateX;
    private double translateY;
    private double panAnchorSceneX;
    private double panAnchorSceneY;
    private double panAnchorTranslateX;
    private double panAnchorTranslateY;

    public VlcCameraTile(CameraDevice device, boolean mainStream, VlcRuntime vlcRuntime)
    {
        this.device = device;
        this.mainStream = mainStream;
        this.vlcRuntime = vlcRuntime;

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

        if (vlcRuntime != null)
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
        if (!disposed.compareAndSet(false, true))
        {
            return;
        }

        imageView.setImage(null);
        synchronized (frameLock)
        {
            frameState = null;
        }

        if (vlcRuntime != null)
        {
            vlcRuntime.execute("release player for " + device.name(), this::releasePlayer);
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
        if (disposed.get() || vlcRuntime == null)
        {
            return;
        }

        vlcRuntime.execute("reconnect " + device.name(), () -> {
            if (disposed.get() || mediaPlayer == null)
            {
                return;
            }

            try
            {
                mediaPlayer.controls().stop();
            }
            catch (RuntimeException ex)
            {
                AppLog.error("Failed to stop VLC player during reconnect for " + device.name(), ex);
            }
            mediaPlayer.media().play(device.streamUrl(mainStream), device.vlcOptions());
        });
    }

    private void initializePlayerAsync()
    {
        vlcRuntime.execute("initialize player for " + device.name(), () -> {
            if (disposed.get())
            {
                return;
            }

            EmbeddedMediaPlayer newPlayer = vlcRuntime.newMediaPlayer();
            boolean retained = false;
            try
            {
                CallbackVideoSurface newSurface = vlcRuntime.newVideoSurface(
                        new TileBufferFormatCallback(),
                        new TileRenderCallback());
                newPlayer.videoSurface().set(newSurface);

                if (disposed.get())
                {
                    return;
                }

                mediaPlayer = newPlayer;
                videoSurface = newSurface;
                retained = true;
                newPlayer.media().play(device.streamUrl(mainStream), device.vlcOptions());
            }
            finally
            {
                if (!retained)
                {
                    newPlayer.release();
                }
            }
        });
    }

    private void releasePlayer()
    {
        EmbeddedMediaPlayer playerToRelease = mediaPlayer;
        mediaPlayer = null;
        videoSurface = null;
        if (playerToRelease == null)
        {
            return;
        }

        try
        {
            playerToRelease.controls().stop();
        }
        catch (RuntimeException ex)
        {
            AppLog.error("Failed to stop VLC player during tile disposal for " + device.name(), ex);
        }
        finally
        {
            playerToRelease.release();
        }
    }

    private void scheduleFrameUpdate()
    {
        if (disposed.get() || !frameUpdateScheduled.compareAndSet(false, true))
        {
            return;
        }

        Platform.runLater(this::publishLatestFrame);
    }

    private void publishLatestFrame()
    {
        try
        {
            FrameState state;
            synchronized (frameLock)
            {
                state = frameState;
            }

            if (!disposed.get() && state != null)
            {
                state.pixelBuffer.updateBuffer(pixelBuffer -> {
                    synchronized (frameLock)
                    {
                        if (disposed.get() || frameState != state)
                        {
                            return null;
                        }

                        state.displayBuffer.clear();
                        ByteBuffer newestFrame = state.latestFrame.duplicate();
                        newestFrame.rewind();
                        state.displayBuffer.put(newestFrame);
                        state.displayBuffer.rewind();
                        state.displayedSequence = state.latestSequence;
                        return null;
                    }
                });
            }
        }
        catch (RuntimeException ex)
        {
            AppLog.error("Failed to update video frame for " + device.name(), ex);
            synchronized (frameLock)
            {
                if (frameState != null)
                {
                    frameState.displayedSequence = frameState.latestSequence;
                }
            }
        }
        finally
        {
            frameUpdateScheduled.set(false);
            if (hasUnpublishedFrame())
            {
                scheduleFrameUpdate();
            }
        }
    }

    private boolean hasUnpublishedFrame()
    {
        synchronized (frameLock)
        {
            return !disposed.get()
                    && frameState != null
                    && frameState.latestSequence != frameState.displayedSequence;
        }
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
            int frameSize = Math.multiplyExact(Math.multiplyExact(sourceWidth, sourceHeight), 4);
            FrameState newState = new FrameState(sourceWidth, sourceHeight, frameSize);
            synchronized (frameLock)
            {
                if (disposed.get())
                {
                    return new RV32BufferFormat(sourceWidth, sourceHeight);
                }
                frameState = newState;
            }

            Platform.runLater(() -> {
                synchronized (frameLock)
                {
                    if (!disposed.get() && frameState == newState)
                    {
                        imageView.setImage(newState.image);
                    }
                }
            });
            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }

        @Override
        public void newFormatSize(int bufferWidth, int bufferHeight, int displayWidth, int displayHeight)
        {
            // The frame state is replaced by getBufferFormat immediately before VLCJ reports these dimensions.
        }

        @Override
        public void allocatedBuffers(ByteBuffer[] buffers)
        {
            // LibVLC owns these buffers; the render callback copies only the newest frame into staging memory.
        }
    }

    private final class TileRenderCallback implements RenderCallback
    {
        @Override
        public void lock(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer)
        {
            // VLCJ owns and locks the native buffers; display copies a frame while that lock is held.
        }

        @Override
        public void display(
                uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer,
                ByteBuffer[] nativeBuffers,
                BufferFormat bufferFormat,
                int displayWidth,
                int displayHeight)
        {
            if (disposed.get() || nativeBuffers == null || nativeBuffers.length == 0)
            {
                return;
            }

            synchronized (frameLock)
            {
                FrameState state = frameState;
                if (state == null)
                {
                    return;
                }

                ByteBuffer source = nativeBuffers[0].duplicate();
                source.clear();
                if (source.remaining() < state.frameSize)
                {
                    return;
                }
                source.limit(state.frameSize);
                state.latestFrame.clear();
                state.latestFrame.put(source);
                state.latestFrame.rewind();
                state.latestSequence++;
            }
            scheduleFrameUpdate();
        }

        @Override
        public void unlock(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer)
        {
            // No application lock is retained after display returns.
        }
    }

    private static final class FrameState
    {
        private final int frameSize;
        private final ByteBuffer latestFrame;
        private final ByteBuffer displayBuffer;
        private final PixelBuffer<ByteBuffer> pixelBuffer;
        private final WritableImage image;
        private long latestSequence;
        private long displayedSequence;

        private FrameState(int width, int height, int frameSize)
        {
            this.frameSize = frameSize;
            latestFrame = ByteBuffer.allocateDirect(frameSize);
            displayBuffer = ByteBuffer.allocateDirect(frameSize);
            pixelBuffer = new PixelBuffer<>(width, height, displayBuffer, PixelFormat.getByteBgraPreInstance());
            image = new WritableImage(pixelBuffer);
        }
    }
}
