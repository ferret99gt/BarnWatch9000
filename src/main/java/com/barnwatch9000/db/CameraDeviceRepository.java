package com.barnwatch9000.db;

import com.barnwatch9000.model.CameraDevice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CameraDeviceRepository
{
    private final Connection connection;

    public CameraDeviceRepository(Connection connection)
    {
        this.connection = connection;
    }

    public List<CameraDevice> listAll() throws SQLException
    {
        List<CameraDevice> devices = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, name, host, port, username, password, sub_path, main_path, sort_order
                FROM camera_devices
                ORDER BY sort_order, name
                """);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                devices.add(new CameraDevice(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("host"),
                        rs.getInt("port"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("sub_path"),
                        rs.getString("main_path"),
                        rs.getInt("sort_order")));
            }
        }
        return devices;
    }

    public int nextSortOrder() throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM camera_devices");
             ResultSet rs = ps.executeQuery())
        {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void save(CameraDevice device) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO camera_devices (
                    id, name, host, port, username, password, sub_path, main_path, sort_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    host = excluded.host,
                    port = excluded.port,
                    username = excluded.username,
                    password = excluded.password,
                    sub_path = excluded.sub_path,
                    main_path = excluded.main_path,
                    sort_order = excluded.sort_order
                """))
        {
            ps.setString(1, device.id());
            ps.setString(2, device.name());
            ps.setString(3, device.host());
            ps.setInt(4, device.port());
            ps.setString(5, device.username());
            ps.setString(6, device.password());
            ps.setString(7, device.subPath());
            ps.setString(8, device.mainPath());
            ps.setInt(9, device.sortOrder());
            ps.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM camera_devices WHERE id = ?"))
        {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public void saveOrdering(List<CameraDevice> orderedDevices) throws SQLException
    {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE camera_devices
                SET sort_order = ?
                WHERE id = ?
                """))
        {
            for (int i = 0; i < orderedDevices.size(); i++)
            {
                CameraDevice device = orderedDevices.get(i);
                ps.setInt(1, i);
                ps.setString(2, device.id());
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        }
        catch (SQLException ex)
        {
            connection.rollback();
            throw ex;
        }
        finally
        {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
