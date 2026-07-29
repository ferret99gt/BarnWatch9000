package com.barnwatch9000;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppVersionTest
{
    @Test
    void currentReleaseVersionIsDisplayed()
    {
        assertEquals("0.2.0", AppVersion.CURRENT);
        assertEquals("Barn Watch 9000 0.2.0", AppVersion.displayName());
    }
}
