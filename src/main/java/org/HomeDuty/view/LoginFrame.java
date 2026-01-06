package org.HomeDuty.view;

import org.HomeDuty.dao.UserDAO;
import org.HomeDuty.model.User;
import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private JTextField txtUsername;
    private JButton btnLogin;
    private UserDAO userDAO;

    public LoginFrame() {
        userDAO = new UserDAO();
        setTitle("HomeDuty - Giriş");
        setSize(300, 150);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 1));

        add(new JLabel("Kullanıcı Adı:", SwingConstants.CENTER));
        txtUsername = new JTextField();
        add(txtUsername);

        btnLogin = new JButton("Giriş Yap");
        add(btnLogin);

        btnLogin.addActionListener(e -> {
            String name = txtUsername.getText();
            User user = userDAO.login(name); // Veritabanından kullanıcıyı çek

            if (user != null) {
                JOptionPane.showMessageDialog(this, "Hoş geldin " + user.getAd() + " (" + user.getRol() + ")");
                new MainFrame(user).setVisible(true); // Ana ekranı aç
                this.dispose(); // Login ekranını kapat
            } else {
                JOptionPane.showMessageDialog(this, "Kullanıcı bulunamadı!");
            }
        });
    }
}