package client.gui;

import client.controller.ClientController;
import client.utils.LastLoginManager;
import client.utils.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class LoginWindow extends JFrame {
    private JTextField serverHostField;
    private JTextField serverPortField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JComboBox<String> udpPortComboBox;
    private JLabel lastLoginLabel;
    private Logger logger;
    private LastLoginManager lastLoginManager;

    public LoginWindow() {
        this.logger = new Logger();
        this.lastLoginManager = new LastLoginManager();
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("MailLite - Client Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 420);
        setLocationRelativeTo(null);
        setResizable(false);

        createComponents();
        layoutComponents();
        setupEventListeners();

        logger.log("Login window initialized");
    }

    private void createComponents() {
        serverHostField = new JTextField("localhost", 15);
        serverPortField = new JTextField("1234", 15);
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);

        String[] udpPorts = {"5555", "5556", "5557", "5558", "5559"};
        udpPortComboBox = new JComboBox<>(udpPorts);

        loginButton = new JButton("Login to Mail Server");
        styleLoginButton();

        lastLoginLabel = new JLabel("", SwingConstants.CENTER);
        lastLoginLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        lastLoginLabel.setForeground(Color.GRAY);
    }

    private void styleLoginButton() {
        loginButton.setBackground(new Color(70, 130, 180));
        loginButton.setForeground(Color.black);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.setFocusPainted(false);
        loginButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("MailLite Client", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(70, 130, 180));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        addFormField(formPanel, gbc, 0, "Server Host:", serverHostField);
        addFormField(formPanel, gbc, 1, "TCP Port:", serverPortField);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        formPanel.add(new JLabel("UDP Port:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        formPanel.add(udpPortComboBox, gbc);

        addFormField(formPanel, gbc, 3, "Username:", usernameField);
        addFormField(formPanel, gbc, 4, "Password:", passwordField);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel lastLoginPanel = new JPanel(new BorderLayout());
        lastLoginPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        lastLoginPanel.add(lastLoginLabel, BorderLayout.CENTER);
        mainPanel.add(lastLoginPanel, BorderLayout.SOUTH);

        mainPanel.add(loginButton, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(jLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        field.setFont(new Font("Arial", Font.PLAIN, 12));
        panel.add(field, gbc);
    }

    private void setupEventListeners() {
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performLogin();
            }
        });

        passwordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performLogin();
            }
        });

        usernameField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateLastLoginInfo();
            }
        });
    }

    private void updateLastLoginInfo() {
        String username = usernameField.getText().trim();
        if (!username.isEmpty()) {
            String lastLogin = lastLoginManager.getLastLogin(username);
            if (lastLogin != null) {
                lastLoginLabel.setText("Last login: " + lastLogin);
            } else {
                lastLoginLabel.setText("First time login for " + username);
            }
        } else {
            lastLoginLabel.setText("");
        }
    }

    private void performLogin() {
        String host = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        int udpPort = Integer.parseInt((String) udpPortComboBox.getSelectedItem());

        if (host.isEmpty() || portStr.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Please fill all fields!");
            return;
        }

        try {
            int tcpPort = Integer.parseInt(portStr);

            loginButton.setText("Connecting...");
            loginButton.setEnabled(false);

            System.out.println("Testing connection to " + host + ":" + tcpPort + "...");

            ClientController controller = new ClientController();
            boolean success = controller.login(host, tcpPort, username, password, udpPort);

            if (success) {
                lastLoginManager.saveLastLogin(username);

                logger.log("Login successful - Opening main window");
                showSuccess("Login successful!");

                openMainWindow(controller);
                dispose();
            } else {
                showError("Login failed! Check:\n" +
                        "1. Server is running\n" +
                        "2. Correct host and port\n" +
                        "3. Network connection");
                resetLoginButton();
            }

        } catch (NumberFormatException e) {
            showError("Invalid port number! Please enter a valid number.");
            resetLoginButton();
        } catch (Exception e) {
            showError("Connection error: " + e.getMessage() +
                    "\n\nMake sure server is running on " + host + ":" + portStr);
            resetLoginButton();
        }
    }

    private void resetLoginButton() {
        loginButton.setText("Login to Mail Server");
        loginButton.setEnabled(true);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Connection Error", JOptionPane.ERROR_MESSAGE);
        logger.log("ERROR: " + message);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openMainWindow(ClientController controller) {
        SwingUtilities.invokeLater(() -> {
            new MainWindow(controller).setVisible(true);
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginWindow().setVisible(true);
            }
        });
    }
}