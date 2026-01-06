package org.HomeDuty;

import org.HomeDuty.view.LoginFrame;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        // Swing arayüzlerinin thread-safe çalışması için önerilen yöntem
        SwingUtilities.invokeLater(() -> {
            new LoginFrame().setVisible(true);
        });
    }
}