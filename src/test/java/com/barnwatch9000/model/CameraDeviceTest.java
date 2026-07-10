package com.barnwatch9000.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraDeviceTest
{
    @Test
    void buildsNormalizedStreamUrlsAndCredentialOptions()
    {
        CameraDevice device = new CameraDevice(
                "id",
                "Barn",
                " 192.168.2.10 ",
                88,
                "viewer",
                "secret",
                "videoSub",
                "/videoMain",
                false,
                false,
                0);

        assertEquals("rtsp://192.168.2.10:88/videoSub", device.streamUrl(false));
        assertEquals("rtsp://192.168.2.10:88/videoMain", device.streamUrl(true));
        assertArrayEquals(new String[]{":rtsp-user=viewer", ":rtsp-pwd=secret"}, device.vlcOptions());
    }

    @Test
    void blankPathFallsBackToSubstreamPath()
    {
        CameraDevice device = new CameraDevice(
                "id", "Barn", "camera", 554, "", "", "", "", false, false, 0);

        assertEquals("rtsp://camera:554/videoSub", device.streamUrl(false));
        assertEquals("rtsp://camera:554/videoSub", device.streamUrl(true));
        assertArrayEquals(new String[0], device.vlcOptions());
    }
}
