package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class TaskDAO {

    // MADDE 4 & 8: INSERT işlemi (Sequence veri tabanında otomatik çalışır)
    public void addTask(String baslik, int puan) {
        String sql = "INSERT INTO Tasks (baslik, puan_degeri, zorluk_seviyesi) VALUES (?, ?, 'Belirlenmedi')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, baslik);
            pstmt.setInt(2, puan);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Ekleme hatası: " + e.getMessage());
        }
    }

    // MADDE 5 & 7: INDEX kullanarak arama (ILIKE büyük/küçük harf duyarsızdır)
    public void searchTasksWithIndex(String keyword) {
        String sql = "SELECT * FROM Tasks WHERE baslik ILIKE ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + keyword + "%");
            ResultSet rs = pstmt.executeQuery();

            StringBuilder results = new StringBuilder("Arama Sonuçları:\n");
            boolean found = false;
            while (rs.next()) {
                found = true;
                results.append("ID: ").append(rs.getInt("gorev_id"))
                        .append(" - ").append(rs.getString("baslik")).append("\n");
            }

            JOptionPane.showMessageDialog(null, found ? results.toString() : "Sonuç bulunamadı.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void showFamilyDetailedStats(int familyId) {
        String sql = "SELECT u.ad, u.puan, t.baslik, t.puan_degeri, a.durum " + // 'durum' eklendi
                "FROM Users u " +
                "JOIN Assignments a ON u.kullanici_id = a.kullanici_id " +
                "JOIN Tasks t ON a.gorev_id = t.gorev_id " +
                "WHERE u.aile_id = ? " +
                "ORDER BY u.ad ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, familyId);
            ResultSet rs = pstmt.executeQuery();

            // HTML formatında başlatıyoruz
            StringBuilder stats = new StringBuilder("<html><body style='font-family:sans-serif;'>");
            stats.append("<h3>Aile Üyeleri ve Görev Dağılımı</h3><hr>");

            String lastUser = "";
            while (rs.next()) {
                String currentUser = rs.getString("ad");
                String taskName = rs.getString("baslik");
                String status = rs.getString("durum");
                int taskPuan = rs.getInt("puan_degeri");

                if (!currentUser.equals(lastUser)) {
                    stats.append("<br><b>").append(currentUser.toUpperCase()).append(" ").append(rs.getInt("puan")).append("</b><br>");
                    lastUser = currentUser;
                }

                // EĞER GÖREV TAMAMLANDI İSE ÜSTÜNÜ ÇİZ (<s> tagı)
                if ("Tamamlandı".equals(status)) {
                    stats.append("&nbsp;&nbsp;<strike style='color:gray;'>- ").append(taskName).append(" (").append(taskPuan).append(")</strike><br>");
                } else {
                    stats.append("&nbsp;&nbsp; - ").append(taskName).append(" (").append(taskPuan).append(")<br>");
                }
            }
            stats.append("</body></html>");

            // HTML destekleyen JEditorPane kullanıyoruz
            JEditorPane editPane = new JEditorPane("text/html", stats.toString());
            editPane.setEditable(false);
            editPane.setBackground(new Color(245, 245, 245));

            JScrollPane scrollPane = new JScrollPane(editPane);
            scrollPane.setPreferredSize(new Dimension(350, 400));
            JOptionPane.showMessageDialog(null, scrollPane, "Ailemizin Detaylı Durumu", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 11: SQL Fonksiyonunu (Cursor & Record) çağırma
    public void callTaskCursorFunction(int userId) {
        String sql = "SELECT * FROM get_user_tasks_cursor(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder("Görev Detaylarınız (Cursor Kaydı):\n");
            while (rs.next()) {
                sb.append("Başlık: ").append(rs.getString(1))
                        .append(" | Durum: ").append(rs.getString(2)).append("\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Fonksiyon Hatası: " + e.getMessage());
        }
    }

    // SEÇENEK 1: Sadece aileye ait atamayı siler (Assignments tablosundan)
    public void deleteAssignment(int assignmentId, int familyId) {
        String sql = "DELETE FROM Assignments WHERE atama_id = ? AND kullanici_id IN " +
                "(SELECT kullanici_id FROM Users WHERE aile_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            pstmt.setInt(2, familyId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) JOptionPane.showMessageDialog(null, "Atama başarıyla kaldırıldı.");
            else JOptionPane.showMessageDialog(null, "Hata: Atama ID bulunamadı veya size ait değil.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // SEÇENEK 2: Görevi sistemden tamamen siler (Tasks tablosundan)
    public void deleteGlobalTask(int gorevId) {
        // Not: Assignments tablosunda bu göreve ait kayıtlar varsa ON DELETE CASCADE
        // ayarı yoksa hata verebilir. Bu yüzden önce atamaları da silebiliriz.
        String sql = "DELETE FROM Tasks WHERE gorev_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gorevId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) JOptionPane.showMessageDialog(null, "Görev sistemden tamamen silindi.");
            else JOptionPane.showMessageDialog(null, "Hata: Görev ID bulunamadı.");
        } catch (SQLException e) { JOptionPane.showMessageDialog(null, "Hata: Bu görev birilerine atanmış olduğu için silinemez."); }
    }

    public void addTaskWithAutoAssign(String baslik, int puan, int creatorId) {
        // SQL Prosedürünü çağıran komut
        String sql = "CALL add_and_assign_task(?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, baslik);
            pstmt.setInt(2, puan);
            pstmt.setInt(3, creatorId); // Giriş yapan kullanıcının ID'si

            pstmt.execute();
            JOptionPane.showMessageDialog(null, "Görev oluşturuldu ve aile bireylerine otomatik atandı!");

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Atama Hatası: " + e.getMessage());
        }
    }

    public java.util.List<Object[]> getMyPendingTasks(int userId) {
        java.util.List<Object[]> tasks = new java.util.ArrayList<>();
        // Madde 5 & 11 uyumlu: Cursor mantığına benzer bir listeleme
        String sql = "SELECT a.atama_id, t.baslik, t.puan_degeri FROM Assignments a " +
                "JOIN Tasks t ON a.gorev_id = t.gorev_id " +
                "WHERE a.kullanici_id = ? AND a.durum = 'Beklemede'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tasks.add(new Object[]{rs.getInt("atama_id"), rs.getString("baslik"), rs.getInt("puan_degeri")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    // Görevi tamamlandı yapar ve veritabanındaki TRIGGER'ı tetikler (Madde 12)
    public void markAsCompleted(int assignmentId) {
        String sql = "UPDATE Assignments SET durum = 'Tamamlandı' WHERE atama_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            pstmt.executeUpdate(); // Bu işlem SQL tarafındaki 'trg_assignment_puan' triggerını çalıştırır
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Hata: " + e.getMessage());
        }
    }

    public void distributeTasksAtLogin(int familyId) {
        // Soru işareti (?) mutlaka parantez içinde olmalı
        String sql = "SELECT distribute_tasks_load_sharing(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, familyId);
            pstmt.execute(); // SELECT fonksiyonu tetikler
            System.out.println("Sistem: Aile ID " + familyId + " için görevler dağıtıldı.");

        } catch (SQLException e) {
            System.err.println("Dağıtım Hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // TaskDAO.java içine ekle
    public void resetSystemAndExit() {
        // 1. Puanları sıfırla, 2. Atamaları temizle ve ID sayacını sıfırla
        String sqlUpdateUsers = "UPDATE Users SET puan = 0";
        String sqlTruncateAssignments = "TRUNCATE TABLE Assignments RESTART IDENTITY";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Transaction başlatıyoruz (İkisi birden yapılsın)
            conn.setAutoCommit(false);

            stmt.executeUpdate(sqlUpdateUsers);
            stmt.executeUpdate(sqlTruncateAssignments);

            conn.commit();
            System.out.println("Sistem verileri başarıyla sıfırlandı.");

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Sıfırlama Hatası: " + e.getMessage());
        }
    }
}