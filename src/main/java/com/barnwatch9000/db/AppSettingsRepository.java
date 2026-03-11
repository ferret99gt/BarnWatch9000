package com.barnwatch9000.db;

import com.barnwatch9000.model.GridLayoutPreset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class AppSettingsRepository
{
    private static final String SELECTED_LAYOUT_KEY = "selected_layout";

    private final Connection connection;

    public AppSettingsRepository(Connection connection)
    {
        this.connection = connection;
    }

    public Optional<GridLayoutPreset> loadSelectedLayout() throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT setting_value
                FROM app_settings
                WHERE setting_key = ?
                """))
        {
            ps.setString(1, SELECTED_LAYOUT_KEY);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                {
                    return Optional.empty();
                }
                return GridLayoutPreset.fromStoredValue(rs.getString("setting_value"));
            }
        }
    }

    public void saveSelectedLayout(GridLayoutPreset layout) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO app_settings (setting_key, setting_value)
                VALUES (?, ?)
                ON CONFLICT(setting_key) DO UPDATE SET
                    setting_value = excluded.setting_value
                """))
        {
            ps.setString(1, SELECTED_LAYOUT_KEY);
            ps.setString(2, layout.toString());
            ps.executeUpdate();
        }
    }
}
