package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;
import org.HomeDuty.model.User;
import java.sql.*;

public class UserDAO {

    // Giriş İşlemi (SQL Sunucuda Fonksiyon Çağrısı)
    public User login(String username) {

        String sql = "{call sp_login_user(?)}";
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setString(1, username);
            ResultSet rs = cstmt.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("isim"),
                        rs.getString("gorev_rol"),
                        rs.getInt("skorpuan"),
                        rs.getInt("aile")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Kullanıcı Kaydı (SQL Sunucuda Prosedür Çağrısı)
    public boolean registerUser(String ad, String rol, int aileId) {
        String sql = "CALL sp_register_user(?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setString(1, ad);
            cstmt.setString(2, rol);
            cstmt.setInt(3, aileId);

            cstmt.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("Kayıt hatası: " + e.getMessage());
            return false;
        }
    }

    // ID ile Veri Tazeleme (SQL Sunucuda Fonksiyon Çağrısı)
    public User getUserById(int userId) {
        String sql = "{call sp_get_user_by_id(?)}";
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setInt(1, userId);
            ResultSet rs = cstmt.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("isim"),
                        rs.getString("gorev_rol"),
                        rs.getInt("skorpuan"),
                        rs.getInt("aile")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}