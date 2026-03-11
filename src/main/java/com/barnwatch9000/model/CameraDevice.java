package com.barnwatch9000.model;

import java.util.ArrayList;
import java.util.List;

public record CameraDevice(
        String id,
        String name,
        String host,
        int port,
        String username,
        String password,
        String subPath,
        String mainPath,
        int sortOrder)
{
    public String streamUrl(boolean mainStream)
    {
        String path = sanitizePath(mainStream ? mainPath : subPath);
        return "rtsp://" + host.trim() + ":" + port + path;
    }

    public String[] vlcOptions()
    {
        List<String> options = new ArrayList<>();

        if (!username.isBlank())
        {
            options.add(":rtsp-user=" + username);
        }
        if (!password.isBlank())
        {
            options.add(":rtsp-pwd=" + password);
        }

        return options.toArray(String[]::new);
    }

    public CameraDevice withSortOrder(int newSortOrder)
    {
        return new CameraDevice(id, name, host, port, username, password, subPath, mainPath, newSortOrder);
    }

    private static String sanitizePath(String raw)
    {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty())
        {
            return "/videoSub";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
