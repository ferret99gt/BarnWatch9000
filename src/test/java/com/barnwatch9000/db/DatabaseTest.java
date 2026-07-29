package com.barnwatch9000.db;

import com.barnwatch9000.model.CameraDevice;
import com.barnwatch9000.model.GridLayoutPreset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest
{
    @TempDir
    Path tempDirectory;

    @Test
    void opensAnIsolatedDatabaseWithForeignKeysEnabled() throws Exception
    {
        try (Connection connection = Database.open(tempDirectory);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA foreign_keys"))
        {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }

        assertTrue(Files.isRegularFile(tempDirectory.resolve("barnwatch9000.db")));
    }

    @Test
    void migratesLegacySchemaIdempotently() throws Exception
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
            Database.initialize(connection);

            Set<String> columns = tableColumns(statement);
            assertAll(
                    () -> assertTrue(columns.contains("ptz_capable")),
                    () -> assertTrue(columns.contains("optical_zoom_capable")));
        }
    }

    @Test
    void repositoriesRoundTripUpdatesOrderingSettingsAndDeletes() throws Exception
    {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"))
        {
            Database.initialize(connection);
            CameraDeviceRepository devices = new CameraDeviceRepository(connection);
            CameraDevice first = camera("first", 0);
            CameraDevice second = camera("second", 1);

            assertEquals(0, devices.nextSortOrder());
            devices.save(first);
            devices.save(second);
            assertEquals(2, devices.nextSortOrder());

            CameraDevice updatedFirst = new CameraDevice(
                    first.id(),
                    "Updated first",
                    first.host(),
                    first.port(),
                    first.username(),
                    first.password(),
                    first.subPath(),
                    first.mainPath(),
                    first.ptzCapable(),
                    first.opticalZoomCapable(),
                    first.sortOrder());
            devices.save(updatedFirst);
            devices.saveOrdering(List.of(second, first));

            assertAll(
                    () -> assertEquals(
                            List.of("second", "first"),
                            devices.listAll().stream().map(CameraDevice::id).toList()),
                    () -> assertEquals(
                            "Updated first",
                            devices.listAll().stream()
                                    .filter(device -> device.id().equals("first"))
                                    .findFirst()
                                    .orElseThrow()
                                    .name()));

            AppSettingsRepository settings = new AppSettingsRepository(connection);
            assertTrue(settings.loadSelectedLayout().isEmpty());
            settings.saveSelectedLayout(GridLayoutPreset.BIG_EIGHT);
            assertEquals(GridLayoutPreset.BIG_EIGHT, settings.loadSelectedLayout().orElseThrow());

            devices.delete("second");
            assertEquals(List.of("first"), devices.listAll().stream().map(CameraDevice::id).toList());
        }
    }

    private static Set<String> tableColumns(java.sql.Statement statement) throws Exception
    {
        try (var resultSet = statement.executeQuery("PRAGMA table_info(camera_devices)"))
        {
            var names = new java.util.ArrayList<String>();
            while (resultSet.next())
            {
                names.add(resultSet.getString("name"));
            }
            return names.stream().collect(Collectors.toSet());
        }
    }

    private static CameraDevice camera(String id, int order)
    {
        return new CameraDevice(id, id, "camera", 88, "user", "pass", "/videoSub", "/videoMain", true, true, order);
    }
}
