package org.HomeDuty.dao;

import org.HomeDuty.db.DatabaseConnection;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {


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


    public void showFamilyDetailedStats(int familyId) {

        String sql = "{call sp_get_family_details(?)}";

        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setInt(1, familyId);
            ResultSet rs = cstmt.executeQuery();


            StringBuilder stats = new StringBuilder("<html><body style='font-family:sans-serif;'>");
            stats.append("<h3>Aile Üyeleri ve Görev Dağılımı (Stored Function & View)</h3><hr>");

            String lastUser = "";
            while (rs.next()) {
                String currentUser = rs.getString("k_adi");
                String taskName = rs.getString("g_adi");
                String status = rs.getString("g_durumu");
                int taskPuan = rs.getInt("g_puani");

                if (!currentUser.equals(lastUser)) {
                    stats.append("<br><b>").append(currentUser.toUpperCase()).append(" ").append(rs.getInt("t_puan")).append("</b><br>");
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
            JOptionPane.showMessageDialog(null, new JScrollPane(editPane), "Aile Durumu", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) { e.printStackTrace(); }
    }


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


    public void deleteAssignment(int assignmentId, int familyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_delete_assignment(?, ?)")) {

            cstmt.setInt(1, assignmentId);
            cstmt.setInt(2, familyId);
            cstmt.execute();
            JOptionPane.showMessageDialog(null, "Atama sistemden kaldırıldı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }


    public void deleteGlobalTask(int gorevId) {

        String sql = "CALL sp_delete_global_task(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setInt(1, gorevId);
            cstmt.execute();

            JOptionPane.showMessageDialog(null, "Görev sistemden tamamen silindi.");
        } catch (SQLException e) {

            JOptionPane.showMessageDialog(null, "Hata: Bu görev birilerine atanmış olduğu için silinemez!");
            e.printStackTrace();
        }
    }


    public void addTaskWithAutoAssign(String baslik, int puan, int creatorId) {
        try (Connection conn = DatabaseConnection.getConnection()) {

            conn.clearWarnings();

            try (CallableStatement cstmt = conn.prepareCall("CALL add_and_assign_task(?, ?, ?)")) {
                cstmt.setString(1, baslik);
                cstmt.setInt(2, puan);
                cstmt.setInt(3, creatorId);

                cstmt.execute();

                StringBuilder msgLog = new StringBuilder();

                SQLWarning warn = cstmt.getWarnings();
                while (warn != null) {
                    msgLog.append(warn.getMessage()).append("\n");
                    warn = warn.getNextWarning();
                }

                SQLWarning connWarn = conn.getWarnings();
                while (connWarn != null) {
                    if (msgLog.indexOf(connWarn.getMessage()) == -1) {
                        msgLog.append(connWarn.getMessage()).append("\n");
                    }
                    connWarn = connWarn.getNextWarning();
                }

                if (msgLog.length() > 0) {
                    JOptionPane.showMessageDialog(null, "Veritabanı Bildirimleri:\n" + msgLog.toString());
                } else {
                    JOptionPane.showMessageDialog(null, "Görev eklendi ancak tetikleyici mesajı gelmedi.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


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

    public void markAsCompleted(int assignmentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_complete_task(?)")) {
            cstmt.setInt(1, assignmentId);
            cstmt.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void distributeTasksAtLogin(int familyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("{call distribute_tasks_load_sharing(?)}")) {
            cstmt.setInt(1, familyId);
            cstmt.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    public void resetSystemAndExit() {
        try (Connection conn = DatabaseConnection.getConnection();
             CallableStatement cstmt = conn.prepareCall("CALL sp_reset_system()")) {
            cstmt.execute();
            System.out.println("Sistem başarıyla sıfırlandı.");
        } catch (SQLException e) { e.printStackTrace(); }
    }
}