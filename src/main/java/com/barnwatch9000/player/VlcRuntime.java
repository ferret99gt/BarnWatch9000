package com.barnwatch9000.player;

import com.barnwatch9000.AppLog;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class VlcRuntime implements AutoCloseable
{
    private static final String[] FACTORY_OPTIONS = {
            "--quiet",
            "--no-video-title-show",
            "--rtsp-tcp",
            "--network-caching=1000",
            "--no-audio",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-frame-buffer-size=5000000",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--avcodec-hw=any"
    };

    private final ExecutorService lifecycleExecutor;
    private final MediaPlayerFactory mediaPlayerFactory;
    private boolean closing;

    public VlcRuntime()
    {
        lifecycleExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform()
                        .name("BarnWatch-VLC-Lifecycle")
                        .daemon(true)
                        .factory());

        try
        {
            mediaPlayerFactory = lifecycleExecutor.submit(() -> new MediaPlayerFactory(FACTORY_OPTIONS)).get();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            lifecycleExecutor.shutdownNow();
            throw new IllegalStateException("Interrupted while initializing VLC", ex);
        }
        catch (ExecutionException ex)
        {
            lifecycleExecutor.shutdownNow();
            throw new IllegalStateException("Failed to initialize VLC", ex.getCause());
        }
    }

    public synchronized boolean execute(String operation, Runnable task)
    {
        if (closing)
        {
            return false;
        }

        try
        {
            lifecycleExecutor.execute(() -> {
                try
                {
                    task.run();
                }
                catch (RuntimeException ex)
                {
                    AppLog.error("VLC operation failed: " + operation, ex);
                }
            });
            return true;
        }
        catch (RejectedExecutionException ex)
        {
            if (!closing)
            {
                AppLog.error("VLC operation was rejected: " + operation, ex);
            }
            return false;
        }
    }

    EmbeddedMediaPlayer newMediaPlayer()
    {
        return mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
    }

    CallbackVideoSurface newVideoSurface(BufferFormatCallback bufferFormatCallback, RenderCallback renderCallback)
    {
        return mediaPlayerFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true);
    }

    @Override
    public void close()
    {
        Future<?> releaseTask;
        synchronized (this)
        {
            if (closing)
            {
                return;
            }
            closing = true;
            releaseTask = lifecycleExecutor.submit(mediaPlayerFactory::release);
            lifecycleExecutor.shutdown();
        }

        try
        {
            releaseTask.get(15, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            AppLog.error("Interrupted while shutting down VLC", ex);
        }
        catch (ExecutionException | TimeoutException ex)
        {
            AppLog.error("Failed to shut down VLC cleanly", ex);
            lifecycleExecutor.shutdownNow();
        }
    }
}
