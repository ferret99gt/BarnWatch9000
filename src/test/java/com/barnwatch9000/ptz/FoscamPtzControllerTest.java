package com.barnwatch9000.ptz;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoscamPtzControllerTest
{
    @ParameterizedTest
    @MethodSource("presetResponses")
    void parsesSupportedPresetResponses(String response, List<String> expected)
    {
        assertEquals(expected, FoscamPtzController.parsePresetNames(response));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void emptyResponsesHaveNoPresets(String response)
    {
        assertTrue(FoscamPtzController.parsePresetNames(response).isEmpty());
    }

    private static Stream<Arguments> presetResponses()
    {
        return Stream.of(
                Arguments.of("""
                <CGI_Result>
                    <result>0</result>
                    <cnt>3</cnt>
                    <point0>TopMost</point0>
                    <point1>FeedStall</point1>
                    <point2>FeedStall</point2>
                    <point3></point3>
                </CGI_Result>
                """, List.of("TopMost", "FeedStall")),
                Arguments.of("<name>Home</name>", List.of("Home")),
                Arguments.of("{\"name\":\"Gate\"}", List.of("Gate")),
                Arguments.of(
                        "<name> Barn </name><point0>Barn</point0>{\"name\":\"Gate\"}",
                        List.of("Barn", "Gate")));
    }
}
