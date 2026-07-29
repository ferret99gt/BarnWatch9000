package com.barnwatch9000.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraDeviceTest
{
    @Test
    void buildsNormalizedUrlsCredentialOptionsAndUpdatedSortOrder()
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

        CameraDevice reordered = device.withSortOrder(7);
        assertAll(
                () -> assertEquals("rtsp://192.168.2.10:88/videoSub", device.streamUrl(false)),
                () -> assertEquals("rtsp://192.168.2.10:88/videoMain", device.streamUrl(true)),
                () -> assertEquals("http://192.168.2.10:88/cgi-bin/CGIProxy.fcgi", device.controlBaseUrl()),
                () -> assertArrayEquals(
                        new String[]{":rtsp-user=viewer", ":rtsp-pwd=secret"},
                        device.vlcOptions()),
                () -> assertEquals(7, reordered.sortOrder()),
                () -> assertEquals(device.id(), reordered.id()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankPathFallsBackToSubstreamPath(String path)
    {
        CameraDevice device = new CameraDevice(
                "id", "Barn", "camera", 554, "", "", path, path, false, false, 0);

        assertAll(
                () -> assertEquals("rtsp://camera:554/videoSub", device.streamUrl(false)),
                () -> assertEquals("rtsp://camera:554/videoSub", device.streamUrl(true)),
                () -> assertArrayEquals(new String[0], device.vlcOptions()));
    }
}
