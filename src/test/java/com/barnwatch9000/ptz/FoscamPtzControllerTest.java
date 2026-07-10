package com.barnwatch9000.ptz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoscamPtzControllerTest
{
    @Test
    void parsesFoscamPointResponseAndIgnoresEmptySlots()
    {
        String response = """
                <CGI_Result>
                    <result>0</result>
                    <cnt>3</cnt>
                    <point0>TopMost</point0>
                    <point1>FeedStall</point1>
                    <point2>FeedStall</point2>
                    <point3></point3>
                </CGI_Result>
                """;

        assertEquals(List.of("TopMost", "FeedStall"), FoscamPtzController.parsePresetNames(response));
    }

    @Test
    void parsesAlternateNameAndJsonResponses()
    {
        assertEquals(List.of("Home"), FoscamPtzController.parsePresetNames("<name>Home</name>"));
        assertEquals(List.of("Gate"), FoscamPtzController.parsePresetNames("{\"name\":\"Gate\"}"));
        assertTrue(FoscamPtzController.parsePresetNames(null).isEmpty());
    }
}
