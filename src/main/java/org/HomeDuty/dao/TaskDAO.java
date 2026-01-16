package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {

    // MADDE 5 & 7: INDEX Kullanarak Arama (SQL Sunucuda)
    public void searchTasksWithIndex(String keyword) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call sp_search_tasks(?)}")) {

            cstmt.setString(1, keyword);
            ResultSet rs = cstmt.executeQuery();

            StringBuilder results = new StringBuilder("Arama Sonuçları:\n");
            boolean found = false;
            while (rs.next()) {
                found = true;
                results.append("ID: ").append(rs.getInt("id"))
                        .append(" - ").append(rs.getString("isim")).append("\n");
            }
            JOptionPane.showMessageDialog(null, found ? results.toString() : "Sonuç bulunamadı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 6: VIEW ve HTML Gösterimi (SQL Sunucuda)
    public void showFamilyDetailedStats(int familyId) {
        // Java içinde SELECT/FROM/WHERE yok, sadece fonksiyonu çağırıyoruz
        String sql = "{call sp_get_family_details(?)}";

        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setInt(1, familyId);
            ResultSet rs = cstmt.executeQuery();

            // --- SENİN TASARIMIN (KORUNDU) ---
            StringBuilder stats = new StringBuilder("<html><body style='font-family:sans-serif;'>");
            stats.append("<h3>Aile Üyeleri ve Görev Dağılımı (Stored Function & View)</h3><hr>");

            String lastUser = "";
            while (rs.next()) {
                String currentUser = rs.getString("k_adi");
                String taskName = rs.getString("g_adi");
                String status = rs.getString("g_durumu");
                int taskPuan = rs.getInt("g_puani");

                // Kullanıcı Gruplama Mantığı
                if (!currentUser.equals(lastUser)) {
                    stats.append("<br><b>").append(currentUser.toUpperCase()).append(" ").append(rs.getInt("t_puan")).append("</b><br>");
                    lastUser = currentUser;
                }

                // Üstü Çizili Yazı Mantığı
                if ("Tamamlandı".equals(status)) {
                    stats.append("&nbsp;&nbsp;<strike style='color:gray;'>- ").append(taskName).append(" (").append(taskPuan).append(")</strike><br>");
                } else {
                    stats.append("&nbsp;&nbsp; - ").append(taskName).append(" (").append(taskPuan).append(")<br>");
                }
            }
            stats.append("</body></html>");

            // Görsel Bileşenler
            JEditorPane editPane = new JEditorPane("text/html", stats.toString());
            editPane.setEditable(false);
            JOptionPane.showMessageDialog(null, new JScrollPane(editPane), "Aile Durumu", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 10: AGGREGATE ve HAVING (SQL Sunucuda)
    public void showFamilyStatsWithHaving() {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call get_family_stats_having()}")) {

            ResultSet rs = cstmt.executeQuery();
            StringBuilder sb = new StringBuilder("Aile Puan Durumu (HAVING):\n");
            while (rs.next()) {
                sb.append("Aile ID: ").append(rs.getInt(1)).append(" | Puan: ").append(rs.getLong(2)).append("\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 9: UNION Kullanımı (SQL Sunucuda)
    public void showUnifiedUserList() {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call sp_get_unified_users()}")) {

            ResultSet rs = cstmt.executeQuery();
            StringBuilder sb = new StringBuilder("Aktif Sistem Kullanıcıları (UNION):\n");
            while (rs.next()) {
                sb.append("- ").append(rs.getString("isim")).append(" (").append(rs.getString("meslek")).append(")\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 11: CURSOR Kullanımı (Veritabanında önceden tanımlı)
    public void callTaskCursorFunction(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call get_user_tasks_cursor(?)}")) {

            cstmt.setInt(1, userId);
            ResultSet rs = cstmt.executeQuery();
            StringBuilder sb = new StringBuilder("Görev Detaylarınız (Cursor):\n");
            while (rs.next()) {
                sb.append("GÖREV: ").append(rs.getString(1)).append(" | DURUM: ").append(rs.getString(2)).append("\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 4: Atama Silme (SQL Sunucuda Prosedür)
    public void deleteAssignment(int assignmentId, int familyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_delete_assignment(?, ?)")) {

            cstmt.setInt(1, assignmentId);
            cstmt.setInt(2, familyId);
            cstmt.execute();
            JOptionPane.showMessageDialog(null, "Atama sistemden kaldırıldı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // MADDE 4: Global Görev Silme (SQL Sunucuda Prosedür Çağrısı)
    public void deleteGlobalTask(int gorevId) {
        // Java içinde artık DELETE, FROM, WHERE kelimeleri yok
        String sql = "CALL sp_delete_global_task(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setInt(1, gorevId);
            cstmt.execute();

            JOptionPane.showMessageDialog(null, "Görev sistemden tamamen silindi.");
        } catch (SQLException e) {
            // Veritabanından gelen kısıt hatalarını burada yakalıyoruz
            JOptionPane.showMessageDialog(null, "Hata: Bu görev birilerine atanmış olduğu için silinemez!");
            e.printStackTrace();
        }
    }

    // MADDE 4 & 8: Görev Ekleme ve Otomatik Atama (Procedure)
    public void addTaskWithAutoAssign(String baslik, int puan, int creatorId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL add_and_assign_task(?, ?, ?)")) {

            cstmt.setString(1, baslik);
            cstmt.setInt(2, puan);
            cstmt.setInt(3, creatorId);
            cstmt.execute();
            JOptionPane.showMessageDialog(null, "Görev oluşturuldu ve aile bireylerine dağıtıldı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Tablo Yenileme (SQL Sunucuda Fonksiyon)
    public List<Object[]> getMyPendingTasks(int userId) {
        List<Object[]> tasks = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call sp_get_user_pending_tasks(?)}")) {

            cstmt.setInt(1, userId);
            ResultSet rs = cstmt.executeQuery();
            while (rs.next()) {
                tasks.add(new Object[]{rs.getInt("atama_id"), rs.getString("baslik"), rs.getInt("puan_degeri")});
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return tasks;
    }

    // MADDE 12: Görev Tamamlama ve TRIGGER Tetikleme
    public void markAsCompleted(int assignmentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_complete_task(?)")) {
            cstmt.setInt(1, assignmentId);
            cstmt.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Otomatik Dağıtım (SQL Sunucuda Fonksiyon)
    public void distributeTasksAtLogin(int familyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call distribute_tasks_load_sharing(?)}")) {
            cstmt.setInt(1, familyId);
            cstmt.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Sistem Sıfırlama (SQL Sunucuda Prosedür)
    public void resetSystemAndExit() {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_reset_system()")) {
            cstmt.execute();
            System.out.println("Sistem başarıyla sıfırlandı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }
}