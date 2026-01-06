package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {

    // MADDE 5 & 7: INDEX kullanarak arama
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
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 6: VIEW Kullanımı
    public void showFamilyDetailedStats(int familyId) {
        String sql = "SELECT * FROM vw_family_task_details WHERE aile_id = ? ORDER BY kullanici_adi ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, familyId);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder stats = new StringBuilder("<html><body style='font-family:sans-serif;'>");
            stats.append("<h3>Aile Üyeleri ve Görev Dağılımı (VIEW)</h3><hr>");

            String lastUser = "";
            while (rs.next()) {
                String currentUser = rs.getString("kullanici_adi");
                String taskName = rs.getString("gorev_adi");
                String status = rs.getString("gorev_durumu");
                int taskPuan = rs.getInt("gorev_puani");

                if (!currentUser.equals(lastUser)) {
                    stats.append("<br><b>").append(currentUser.toUpperCase()).append(" ").append(rs.getInt("toplam_puan")).append("</b><br>");
                    lastUser = currentUser;
                }

                if ("Tamamlandı".equals(status)) {
                    stats.append("&nbsp;&nbsp;<strike style='color:gray;'>- ").append(taskName).append(" (").append(taskPuan).append(")</strike><br>");
                } else {
                    stats.append("&nbsp;&nbsp; - ").append(taskName).append(" (").append(taskPuan).append(")<br>");
                }
            }
            stats.append("</body></html>");

            JEditorPane editPane = new JEditorPane("text/html", stats.toString());
            editPane.setEditable(false);
            JOptionPane.showMessageDialog(null, new JScrollPane(editPane), "Aile Durumu (View)", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) { e.printStackTrace(); }
    }



    // MADDE 10: AGGREGATE Fonksiyonu ve HAVING Kullanımı
    public void showFamilyStatsWithHaving() {
        // Puanı 0'dan büyük olan aileleri SUM yaparak listeler
        String sql = "SELECT aile_id, SUM(puan) as toplam FROM Users GROUP BY aile_id HAVING SUM(puan) >= 0 ORDER BY toplam DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder sb = new StringBuilder("Genel Aile Puan Durumu (HAVING):\n");
            while (rs.next()) {
                sb.append("Aile ID: ").append(rs.getInt("aile_id"))
                        .append(" | Toplam Puan: ").append(rs.getInt("toplam")).append("\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 9: UNION Kullanımı
    public void showUnifiedUserList() {
        // Ebeveynler ile en az bir görevi olan çocukları birleştirir
        String sql = "SELECT ad, rol FROM Users WHERE rol IN ('Baba', 'Anne') " +
                "UNION " +
                "SELECT u.ad, u.rol FROM Users u JOIN Assignments a ON u.kullanici_id = a.kullanici_id";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder sb = new StringBuilder("Aktif/Ebeveyn Kullanıcı Listesi (UNION):\n");
            while (rs.next()) {
                sb.append("- ").append(rs.getString("ad")).append(" (").append(rs.getString("rol")).append(")\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 11: CURSOR ve RECORD Kullanımı
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
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // DELETE İŞLEMLERİ (MADDE 4)
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

    public void deleteGlobalTask(int gorevId) {
        String sql = "DELETE FROM Tasks WHERE gorev_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gorevId);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(null, "Görev sistemden tamamen silindi.");
        } catch (SQLException e) { JOptionPane.showMessageDialog(null, "Hata: Görev birilerine atanmış!"); }
    }

    // PROCEDURE ve AUTO-ASSIGN (MADDE 4 & 8)
    public void addTaskWithAutoAssign(String baslik, int puan, int creatorId) {
        String sql = "CALL add_and_assign_task(?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, baslik);
            pstmt.setInt(2, puan);
            pstmt.setInt(3, creatorId);
            pstmt.execute();
            JOptionPane.showMessageDialog(null, "Görev oluşturuldu ve otomatik atandı!");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<Object[]> getMyPendingTasks(int userId) {
        List<Object[]> tasks = new ArrayList<>();
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
        } catch (SQLException e) { e.printStackTrace(); }
        return tasks;
    }

    // UPDATE ve TRIGGER (MADDE 4 & 12)
    public void markAsCompleted(int assignmentId) {
        String sql = "UPDATE Assignments SET durum = 'Tamamlandı' WHERE atama_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // FONKSİYON: Dağıtım Algoritması
    public void distributeTasksAtLogin(int familyId) {
        String sql = "SELECT distribute_tasks_load_sharing(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, familyId);
            pstmt.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // SIFIRLAMA (TRUNCATE & SEQUENCE RESTART)
    public void resetSystemAndExit() {
        String sql1 = "UPDATE Users SET puan = 0";
        String sql2 = "TRUNCATE TABLE Assignments RESTART IDENTITY";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.executeUpdate(sql1);
            stmt.executeUpdate(sql2);
            conn.commit();
            System.out.println("Sistem sıfırlandı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }
}