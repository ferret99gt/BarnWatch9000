package com.barnwatch9000.db;

import com.barnwatch9000.model.CameraDevice;
import com.barnwatch9000.model.GridLayoutPreset;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest
{
    @Test
    void migratesLegacySchemaAndRoundTripsRepositories() throws Exception
    {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement())
        {
            statement.execute("""
                    CREATE TABLE camera_devices (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        sub_path TEXT NOT NULL,
                        main_path TEXT NOT NULL,
                        sort_order INTEGER NOT NULL
                    )
                    """);

            Database.initialize(connection);

            Set<String> columns;
            try (var resultSet = statement.executeQuery("PRAGMA table_info(camera_devices)"))
            {
                var names = new java.util.ArrayList<String>();
                while (resultSet.next())
                {
                    names.add(resultSet.getString("name"));
                }
                columns = names.stream().collect(Collectors.toSet());
            }
            assertTrue(columns.containsAll(Set.of("ptz_capable", "optical_zoom_capable")));

            CameraDeviceRepository devices = new CameraDeviceRepository(connection);
            CameraDevice first = camera("first", 0);
            CameraDevice second = camera("second", 1);
            devices.save(first);
            devices.save(second);
            devices.saveOrdering(List.of(second, first));
            assertEquals(List.of("second", "first"), devices.listAll().stream().map(CameraDevice::id).toList());

            AppSettingsRepository settings = new AppSettingsRepository(connection);
            settings.saveSelectedLayout(GridLayoutPreset.BIG_EIGHT);
            assertEquals(GridLayoutPreset.BIG_EIGHT, settings.loadSelectedLayout().orElseThrow());
        }
    }

    private static CameraDevice camera(String id, int order)
    {
        return new CameraDevice(id, id, "camera", 88, "user", "pass", "/videoSub", "/videoMain", true, true, order);
    }
}
