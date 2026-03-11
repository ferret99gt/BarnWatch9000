package com.barnwatch9000.player;

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VlcSupport
{
    private static boolean initialized;
    private static boolean available;
    private static String status = "VLC not checked";

    private VlcSupport()
    {
    }

    public static synchronized boolean ensureAvailable()
    {
        if (!initialized)
        {
            try
            {
                available = discoverWithHints();
            }
            catch (RuntimeException ex)
            {
                available = false;
                status = "VLC detection failed: " + ex.getMessage();
            }
            initialized = true;
        }
        return available;
    }

    public static synchronized String status()
    {
        ensureAvailable();
        return status;
    }

    private static boolean discoverWithHints()
    {
        Path candidate = findInstalledVlc();
        if (candidate != null)
        {
            if (is64BitJvm() && candidate.toString().contains("Program Files (x86)"))
            {
                status = "Found 32-bit VLC at " + candidate + ", but the app is running on 64-bit Java. Install 64-bit VLC.";
                return false;
            }

            System.setProperty("jna.library.path", candidate.toString());
            Path plugins = candidate.resolve("plugins");
            if (Files.isDirectory(plugins))
            {
                System.setProperty("VLC_PLUGIN_PATH", plugins.toString());
            }
        }

        boolean found = new NativeDiscovery().discover();
        if (found)
        {
            status = candidate == null ? "VLC detected" : "VLC detected at " + candidate;
            return true;
        }

        status = candidate == null
                ? "VLC not found. Install 64-bit VLC or add it to PATH."
                : "VLC was found at " + candidate + " but libvlc could not be loaded.";
        return false;
    }

    private static Path findInstalledVlc()
    {
        List<Path> candidates = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFiles != null && !programFiles.isBlank())
        {
            candidates.add(Path.of(programFiles, "VideoLAN", "VLC"));
        }
        if (programFilesX86 != null && !programFilesX86.isBlank())
        {
            candidates.add(Path.of(programFilesX86, "VideoLAN", "VLC"));
        }

        for (Path candidate : candidates)
        {
            if (Files.isRegularFile(candidate.resolve("libvlc.dll")))
            {
                return candidate;
            }
        }
        return null;
    }

    private static boolean is64BitJvm()
    {
        String dataModel = System.getProperty("sun.arch.data.model", "");
        String osArch = System.getProperty("os.arch", "");
        return "64".equals(dataModel) || osArch.contains("64") || osArch.equalsIgnoreCase("amd64");
    }
}
