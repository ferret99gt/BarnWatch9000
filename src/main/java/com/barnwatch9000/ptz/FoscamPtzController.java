package com.barnwatch9000.ptz;

import com.barnwatch9000.AppLog;
import com.barnwatch9000.model.CameraDevice;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FoscamPtzController
{
    private static final Pattern XML_NAME_PATTERN = Pattern.compile("<name>([^<]+)</name>", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_POINT_PATTERN = Pattern.compile("<point\\d+>([^<]*)</point\\d+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(new PtzThreadFactory());
    private static final ConcurrentMap<String, ScheduledFuture<?>> ZOOM_STOPS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .executor(EXECUTOR)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private FoscamPtzController()
    {
    }

    public static void move(CameraDevice device, Direction direction)
    {
        if (!device.ptzCapable() || direction == Direction.NONE)
        {
            return;
        }
        send(device, direction.command());
    }

    public static void stop(CameraDevice device)
    {
        if (!device.ptzCapable())
        {
            return;
        }
        send(device, "ptzStopRun");
    }

    public static void reset(CameraDevice device)
    {
        if (!device.ptzCapable())
        {
            return;
        }
        send(device, "ptzReset");
    }

    public static void zoom(CameraDevice device, boolean zoomIn)
    {
        if (!device.opticalZoomCapable())
        {
            return;
        }

        send(device, zoomIn ? "zoomIn" : "zoomOut");
        ScheduledFuture<?> previousStop = ZOOM_STOPS.remove(device.id());
        if (previousStop != null)
        {
            previousStop.cancel(false);
        }

        ScheduledFuture<?> stopTask = EXECUTOR.schedule(() -> {
            send(device, "zoomStop");
            ZOOM_STOPS.remove(device.id());
        }, 900, TimeUnit.MILLISECONDS);
        ZOOM_STOPS.put(device.id(), stopTask);
    }

    public static CompletableFuture<List<String>> fetchPresets(CameraDevice device)
    {
        if (!device.ptzCapable())
        {
            return CompletableFuture.completedFuture(List.of());
        }

        return sendForText(device, "getPTZPresetPointList", null)
                .thenApply(FoscamPtzController::parsePresetNames)
                .exceptionally(throwable -> {
                    AppLog.error("Failed to fetch PTZ presets for " + device.name(), throwable);
                    return List.of();
                });
    }

    public static void goToPreset(CameraDevice device, String presetName)
    {
        if (!device.ptzCapable() || presetName == null || presetName.isBlank())
        {
            return;
        }
        send(device, "ptzGotoPresetPoint", "name=" + encode(presetName));
    }

    private static void send(CameraDevice device, String command)
    {
        send(device, command, null);
    }

    private static void send(CameraDevice device, String command, String extraQuery)
    {
        try
        {
            String uri = device.controlBaseUrl()
                    + "?cmd=" + encode(command)
                    + "&usr=" + encode(device.username())
                    + "&pwd=" + encode(device.password());
            if (extraQuery != null && !extraQuery.isBlank())
            {
                uri += "&" + extraQuery;
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null)
                        {
                            AppLog.error("PTZ command failed for " + device.name() + ": " + command, throwable);
                        }
                        else if (response.statusCode() >= 400)
                        {
                            AppLog.info("PTZ command returned HTTP " + response.statusCode() + " for " + device.name() + ": " + command);
                        }
                    });
        }
        catch (RuntimeException ex)
        {
            AppLog.error("PTZ command build failed for " + device.name() + ": " + command, ex);
        }
    }

    private static CompletableFuture<String> sendForText(CameraDevice device, String command, String extraQuery)
    {
        String uri = device.controlBaseUrl()
                + "?cmd=" + encode(command)
                + "&usr=" + encode(device.username())
                + "&pwd=" + encode(device.password());
        if (extraQuery != null && !extraQuery.isBlank())
        {
            uri += "&" + extraQuery;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 400)
                    {
                        throw new IllegalStateException("HTTP " + response.statusCode() + " for " + command);
                    }
                    return response.body();
                });
    }

    private static List<String> parsePresetNames(String body)
    {
        if (body == null || body.isBlank())
        {
            return List.of();
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        collectMatches(XML_NAME_PATTERN.matcher(body), names);
        collectMatches(XML_POINT_PATTERN.matcher(body), names);
        collectMatches(JSON_NAME_PATTERN.matcher(body), names);
        return new ArrayList<>(names);
    }

    private static void collectMatches(Matcher matcher, LinkedHashSet<String> names)
    {
        while (matcher.find())
        {
            String value = matcher.group(1);
            if (value == null)
            {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty())
            {
                names.add(trimmed);
            }
        }
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public enum Direction
    {
        LEFT("ptzMoveLeft"),
        RIGHT("ptzMoveRight"),
        UP("ptzMoveUp"),
        DOWN("ptzMoveDown"),
        NONE("");

        private final String command;

        Direction(String command)
        {
            this.command = command;
        }

        public String command()
        {
            return command;
        }
    }

    private static final class PtzThreadFactory implements ThreadFactory
    {
        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = new Thread(runnable, "BarnWatch9000-PTZ");
            thread.setDaemon(true);
            return thread;
        }
    }
}
