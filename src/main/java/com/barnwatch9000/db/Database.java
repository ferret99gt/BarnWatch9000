package com.barnwatch9000.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database
{
    private static final String DATA_DIR = "data";
    private static final String DB_FILE = "barnwatch9000.db";

    private Database()
    {
    }

    public static Connection open() throws SQLException
    {
        ensureDataDir();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:./" + DATA_DIR + "/" + DB_FILE);
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initialize(Connection connection) throws SQLException
    {
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS camera_devices (
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

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_camera_devices_sort
                    ON camera_devices(sort_order, name)
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        setting_key TEXT PRIMARY KEY,
                        setting_value TEXT NOT NULL
                    )
                    """);
        }
    }

    private static void ensureDataDir()
    {
        try
        {
            Files.createDirectories(Path.of(DATA_DIR));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create data directory", e);
        }
    }
}
