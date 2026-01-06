package org.HomeDuty.view;

import org.HomeDuty.model.User;
import org.HomeDuty.dao.TaskDAO;
import org.HomeDuty.dao.UserDAO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class MainFrame extends JFrame {
    private User currentUser;
    private TaskDAO taskDAO;
    private UserDAO userDAO;
    private JLabel lblInfo;
    private DefaultTableModel taskTableModel;

    public MainFrame(User user) {
        this.currentUser = user;
        this.taskDAO = new TaskDAO();
        this.userDAO = new UserDAO();

        // MADDE 8: Giriş anında sahipsiz görevleri aileye otomatik/rastgele dağıt
        taskDAO.distributeTasksAtLogin(user.getAileId());

        setTitle("HomeDuty Ana Panel - " + user.getAd());
        setSize(600, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- ÜST KISIM: KULLANICI BİLGİLERİ VE LOGOUT ---
        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setBackground(new Color(230, 230, 230));
        pnlTop.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        lblInfo = new JLabel();
        updateInfoLabel(); // Puan ve Rozet bilgisini yazar
        pnlTop.add(lblInfo, BorderLayout.WEST);

        JButton btnLogout = new JButton("Çıkış Yap " + "-> " + user.getAd());
        btnLogout.setBackground(new Color(255, 102, 102));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFocusable(false);
        pnlTop.add(btnLogout, BorderLayout.EAST);

        add(pnlTop, BorderLayout.NORTH);

        // --- MERKEZİ YENİLEME MANTIĞI (Refresh) ---
        Runnable refreshUI = () -> {
            User updatedUser = userDAO.getUserById(currentUser.getId());
            if (updatedUser != null) {
                this.currentUser = updatedUser;
                updateInfoLabel();
            }
            taskTableModel.setRowCount(0);
            java.util.List<Object[]> myTasks = taskDAO.getMyPendingTasks(currentUser.getId());
            for (Object[] row : myTasks) {
                taskTableModel.addRow(row);
            }
        };

        // --- ORTA KISIM: İŞLEM BUTONLARI ---
        JPanel pnlCenterWrapper = new JPanel(new BorderLayout());

        JPanel pnlCenter = new JPanel(new GridLayout(0, 2, 12, 12));
        pnlCenter.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));

        JButton btnList = new JButton("Aile İstatistikleri");
        JButton btnSearch = new JButton("Görev Ara");
        JButton btnAdd = new JButton("Yeni Görev Ekle");
        JButton btnDelete = new JButton("Görev Sil");
        JButton btnCursorFunc = new JButton("Detaylı Döküm");
        JButton btnComplete = new JButton("Seçili Görevi Tamamladım!");

        btnComplete.setBackground(new Color(144, 238, 144));
        btnComplete.setFont(new Font("Arial", Font.BOLD, 12));

        pnlCenter.add(btnList);
        pnlCenter.add(btnSearch);
        pnlCenter.add(btnAdd);
        pnlCenter.add(btnDelete);
        pnlCenter.add(btnCursorFunc);
        pnlCenter.add(btnComplete);

        // --- SIFIRLAMA BUTONU (Ortalanmış Alt Panel) ---
        JPanel pnlReset = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pnlReset.setBorder(BorderFactory.createEmptyBorder(0, 30, 20, 30));

        JButton btnResetAndClose = new JButton("Sistemi Sıfırla ve Kapat");
        btnResetAndClose.setPreferredSize(new Dimension(350, 45));
        btnResetAndClose.setBackground(new Color(45, 45, 45));
        btnResetAndClose.setForeground(Color.WHITE);
        btnResetAndClose.setFont(new Font("Arial", Font.BOLD, 12));
        pnlReset.add(btnResetAndClose);

        pnlCenterWrapper.add(pnlCenter, BorderLayout.CENTER);
        pnlCenterWrapper.add(pnlReset, BorderLayout.SOUTH);

        add(pnlCenterWrapper, BorderLayout.CENTER);

        // --- ALT KISIM: GÖREV TABLOSU (JTable) ---
        String[] columnNames = {"Atama ID", "Görev Adı", "Puan"};
        taskTableModel = new DefaultTableModel(columnNames, 0);
        JTable tblMyTasks = new JTable(taskTableModel);
        JScrollPane scrollPane = new JScrollPane(tblMyTasks);
        scrollPane.setPreferredSize(new Dimension(500, 180));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Üzerimdeki Bekleyen Görevler"));
        add(scrollPane, BorderLayout.SOUTH);

        // --- MADDE 13: YETKİLENDİRME KONTROLÜ ---
        if (currentUser.getRol().equalsIgnoreCase("Çocuk")) {
            btnDelete.setEnabled(false);
            btnDelete.setToolTipText("Görev silme yetkiniz yok.");
            btnResetAndClose.setEnabled(false);
            btnResetAndClose.setToolTipText("Sistemi sıfırlama yetkiniz yok.");
        }

        // --- BUTON AKSİYONLARI ---

        btnLogout.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Oturum kapatılsın mı?", "Çıkış", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                this.dispose();
                new LoginFrame().setVisible(true);
            }
        });

        // MADDE 6 & 10: View ve Aggregate kullanımı
        btnList.addActionListener(e -> taskDAO.showFamilyDetailedStats(currentUser.getAileId()));

        // MADDE 7: Index Kullanımı
        btnSearch.addActionListener(e -> {
            String keyword = JOptionPane.showInputDialog(this, "Aranacak görev adı:");
            if (keyword != null && !keyword.isEmpty()) taskDAO.searchTasksWithIndex(keyword);
        });

        // MADDE 4 & 8: Procedure & Auto-Assign
        btnAdd.addActionListener(e -> {
            String taskName = JOptionPane.showInputDialog(this, "Görev başlığı:");
            String pointsStr = JOptionPane.showInputDialog(this, "Puan değeri:");
            if (taskName != null && pointsStr != null) {
                taskDAO.addTaskWithAutoAssign(taskName, Integer.parseInt(pointsStr), currentUser.getId());
                refreshUI.run();
            }
        });

        // MADDE 4: İki Aşamalı Silme
        btnDelete.addActionListener(e -> {
            Object[] options = {"Sadece Ailemden Kaldır", "Sistemden Tamamen Sil", "İptal"};
            int choice = JOptionPane.showOptionDialog(this, "Silme türünü seçin:", "Görev Silme",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

            if (choice == 0) {
                String idStr = JOptionPane.showInputDialog(this, "Silinecek Atama ID:");
                if (idStr != null) {
                    taskDAO.deleteAssignment(Integer.parseInt(idStr), currentUser.getAileId());
                    refreshUI.run();
                }
            } else if (choice == 1) {
                String idStr = JOptionPane.showInputDialog(this, "Sistemden silinecek Görev ID:");
                if (idStr != null) {
                    taskDAO.deleteGlobalTask(Integer.parseInt(idStr));
                    refreshUI.run();
                }
            }
        });

        // MADDE 11: Cursor Kullanımı
        btnCursorFunc.addActionListener(e -> taskDAO.callTaskCursorFunction(currentUser.getId()));

        // MADDE 12: Trigger Tetikleyici
        btnComplete.addActionListener(e -> {
            int selectedRow = tblMyTasks.getSelectedRow();
            if (selectedRow != -1) {
                int assignmentId = (int) tblMyTasks.getValueAt(selectedRow, 0);
                taskDAO.markAsCompleted(assignmentId);
                JOptionPane.showMessageDialog(this, "Görev tamamlandı, puanınız güncellendi!");
                refreshUI.run();
            } else {
                JOptionPane.showMessageDialog(this, "Lütfen tablodan bir görev seçin.");
            }
        });

        // Sıfırlama ve Kapatma
        btnResetAndClose.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Tüm puanlar ve atamalar sıfırlanacaktır. Emin misiniz?", "Sıfırla", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                taskDAO.resetSystemAndExit();
                System.exit(0);
            }
        });

        refreshUI.run();
    }

    private void updateInfoLabel() {
        lblInfo.setText("<html>Hoş geldin: <b>" + currentUser.getAd() +
                "</b> | Rol: " + currentUser.getRol() +
                " | Puan: <font color='blue'>" + currentUser.getPuan() + "</font>" +
                " | Rozet: " + currentUser.getBadgeName(currentUser.getPuan()) + "</html>");
    }
}