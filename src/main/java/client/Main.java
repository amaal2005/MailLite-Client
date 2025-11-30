package client;

import client.gui.LoginWindow;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 MailLite Client Starting...");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new LoginWindow().setVisible(true);
        });

    }
}