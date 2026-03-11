package com.barnwatch9000;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLog
{
    private static final Path LOG_PATH = Path.of("data", "barnwatch9000.log");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppLog()
    {
    }

    public static void installGlobalHandler()
    {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                error("Uncaught exception on thread " + thread.getName(), throwable));
    }

    public static void info(String message)
    {
        write("INFO", message, null);
    }

    public static void error(String message, Throwable throwable)
    {
        write("ERROR", message, throwable);
    }

    private static synchronized void write(String level, String message, Throwable throwable)
    {
        try
        {
            Files.createDirectories(LOG_PATH.getParent());
            StringBuilder entry = new StringBuilder()
                    .append('[')
                    .append(TIMESTAMP.format(LocalDateTime.now()))
                    .append("] ")
                    .append(level)
                    .append(' ')
                    .append(message)
                    .append(System.lineSeparator());

            if (throwable != null)
            {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                entry.append(sw).append(System.lineSeparator());
            }

            Files.writeString(
                    LOG_PATH,
                    entry.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        }
        catch (IOException ignored)
        {
        }
    }
}
