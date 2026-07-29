package com.barnwatch9000;

public final class AppVersion
{
    public static final String CURRENT = "0.2.0";

    private AppVersion()
    {
    }

    public static String displayName()
    {
        return "Barn Watch 9000 " + CURRENT;
    }
}
