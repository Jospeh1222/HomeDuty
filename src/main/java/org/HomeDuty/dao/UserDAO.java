package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;
import org.HomeDuty.model.User;
import java.sql.*;

public class UserDAO {

    public User login(String username) {
        // SELECT * kullanarak tüm sütunları (aile_id dahil) çekiyoruz
        String sql = "SELECT * FROM Users WHERE ad = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // KRİTİK NOKTA: rs.getInt("aile_id") kısmını unutmuş olabilirsin
                return new User(
                        rs.getInt("kullanici_id"),
                        rs.getString("ad"),
                        rs.getString("rol"),
                        rs.getInt("puan"),
                        rs.getInt("aile_id") // Aile ID'sini buradan alıyoruz
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean registerUser(String ad, String rol, int aileId) {
        String sql = "INSERT INTO Users (ad, rol, aile_id, puan) VALUES (?, ?, ?, 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, ad);
            pstmt.setString(2, rol);
            pstmt.setInt(3, aileId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Kayıt hatası: " + e.getMessage());
            return false;
        }
    }

    // UserDAO.java içine eklenecek
    public User getUserById(int userId) {
        String sql = "SELECT * FROM Users WHERE kullanici_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("kullanici_id"), rs.getString("ad"), rs.getString("rol"), rs.getInt("puan"), rs.getInt("aile_id"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
}