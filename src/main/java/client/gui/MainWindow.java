package client.gui;

import java.awt.event.*;
import java.text.SimpleDateFormat;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import client.controller.ClientController;
import client.gui.models.Message;
import client.utils.Logger;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public class MainWindow extends JFrame {
    private ClientController controller;
    private Logger logger;

    private JList<String> folderList;
    private JList<String> onlineUsersList;
    private JTable messagesTable;
    private JTextArea messageContentArea;
    private JTextField searchField;
    private JTextField toField;
    private JTextField subjectField;
    private JTextArea composeArea;
    private JButton sendButton;
    private JLabel statusLabel;
    private JComboBox<String> statusComboBox;

    private Timer autoAwayTimer;
    private Timer autoRefreshTimer;
    private Timer statusUpdateTimer;
    private static final int AUTO_AWAY_TIMEOUT = 30000;
    private static final int STATUS_UPDATE_INTERVAL = 10000;

    private final Set<String> readMessageIds = new HashSet<>();
    private static final String READ_MESSAGES_FILE = "read_messages.dat";

    public MainWindow(ClientController controller) {
        this.controller = controller;
        this.logger = new Logger();
        loadReadMessages();
        initializeGUI();
        setupActivityTracking();
    }

    private void setupActivityTracking() {
        autoAwayTimer = new Timer(AUTO_AWAY_TIMEOUT, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (controller != null && controller.isConnected()) {
                    String currentStatus = (String) statusComboBox.getSelectedItem();
                    if (!"Away".equals(currentStatus)) {
                        System.out.println("Auto-Away activated after 30 seconds of inactivity");
                        setStatus("Away");
                        logger.log("Auto-Away activated due to inactivity");
                    }
                }
            }
        });
        autoAwayTimer.setRepeats(false);
        autoAwayTimer.start();

        statusUpdateTimer = new Timer(STATUS_UPDATE_INTERVAL, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (controller != null && controller.isConnected()) {
                    loadOnlineUsers();
                    updateStatusBar();
                }
            }
        });
        statusUpdateTimer.start();

        setupGlobalActivityListeners();
    }

    private void setupGlobalActivityListeners() {
        ActivityListener activityListener = new ActivityListener();
        addActivityListenerToAll(this.getContentPane(), activityListener);
    }

    private void addActivityListenerToAll(Container container, ActivityListener listener) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof Container) {
                addActivityListenerToAll((Container) comp, listener);
            }

            if (comp instanceof JTextComponent) {
                comp.addKeyListener(listener);
            } else if (comp instanceof AbstractButton) {
                comp.addMouseListener(listener);
            } else if (comp instanceof JList) {
                comp.addMouseListener(listener);
            } else if (comp instanceof JTable) {
                comp.addMouseListener(listener);
            } else if (comp instanceof JScrollPane) {
                comp.addMouseListener(listener);
                comp.addMouseMotionListener(listener);
            }
        }
    }

    private class ActivityListener extends MouseAdapter implements KeyListener {
        @Override
        public void mousePressed(MouseEvent e) {
            resetActivityTimers();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            resetActivityTimers();
        }

        @Override
        public void keyPressed(KeyEvent e) {
            resetActivityTimers();
        }

        @Override
        public void keyReleased(KeyEvent e) {
            resetActivityTimers();
        }

        @Override
        public void keyTyped(KeyEvent e) {
            resetActivityTimers();
        }
    }

    private void resetActivityTimers() {
        if (autoAwayTimer != null) {
            autoAwayTimer.restart();
        }

        String currentStatus = (String) statusComboBox.getSelectedItem();
        if ("Away".equals(currentStatus) && controller != null && controller.isConnected()) {
            setStatus("Active");
            System.out.println("User active - status reset to Active");
        }
    }

    private void setStatus(String status) {
        if (controller != null && controller.isConnected()) {
            try {
                controller.setStatus(status.toUpperCase());
                statusComboBox.setSelectedItem(status);
                logger.log("Status changed to: " + status);
            } catch (Exception e) {
                logger.log("Error setting status: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadReadMessages() {
        File file = new File(READ_MESSAGES_FILE);
        if (!file.exists()) {
            System.out.println("No read messages file found - starting fresh");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Set<String> loaded = (Set<String>) ois.readObject();
            readMessageIds.clear();
            readMessageIds.addAll(loaded);
            System.out.println("Loaded " + readMessageIds.size() + " read messages from disk");
        } catch (Exception e) {
            System.out.println("Failed to load read messages: " + e.getMessage());
        }
    }

    private void saveReadMessages() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(READ_MESSAGES_FILE))) {
            oos.writeObject(readMessageIds);
            System.out.println("Saved " + readMessageIds.size() + " read messages to disk");
        } catch (IOException e) {
            System.err.println("Failed to save read messages: " + e.getMessage());
        }
    }

    private void initializeGUI() {
        setTitle("MailLite - " + controller.getUsername());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export Conversation...");
        JMenuItem logoutItem = new JMenuItem("Logout");

        exportItem.addActionListener(e -> exportConversation());
        logoutItem.addActionListener(e -> logout());

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(logoutItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.addActionListener(e -> refreshAllData());
        viewMenu.add(refreshItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        setJMenuBar(menuBar);

        createComponents();
        layoutComponents();
        setupEventListeners();
        setupTableColors();

        loadRealData();
        loadOnlineUsers();
        updateStatusBar();
        autoRefreshData();
        setupButtonsColor();

        controller.setNotificationCallback((username, count) -> {
            if (username.equals(controller.getUsername())) {
                Toolkit.getDefaultToolkit().beep();
                statusLabel.setForeground(Color.RED);
                statusLabel.setText("NEW MAIL! " + count + " unread message(s)");

                resetActivityTimers();

                new Timer(5000, e -> {
                    statusLabel.setForeground(Color.BLACK);
                    updateStatusBar();
                }).start();

                new Timer(1000, e -> SwingUtilities.invokeLater(() -> {
                    loadOnlineUsers();
                    if (folderList.getSelectedValue() != null && folderList.getSelectedValue().contains("Inbox")) {
                        loadCurrentFolderMessages();
                    }
                })).start();
            }
        });

        logger.log("Main window opened for user: " + controller.getUsername());
    }

    private void createComponents() {
        String[] folders = {"Inbox", "Sent", "Archive"};
        folderList = new JList<>(folders);
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.setSelectedIndex(0);

        onlineUsersList = new JList<>(new DefaultListModel<>());

        String[] columnNames = {"From", "Subject", "Date", "Status", "ID"};

        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        messagesTable = new JTable(tableModel);
        messagesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messagesTable.getTableHeader().setReorderingAllowed(false);

        TableColumn idColumn = messagesTable.getColumnModel().getColumn(4);
        idColumn.setMinWidth(0);
        idColumn.setMaxWidth(0);
        idColumn.setPreferredWidth(0);
        idColumn.setWidth(0);

        messageContentArea = new JTextArea();
        messageContentArea.setEditable(false);
        messageContentArea.setLineWrap(true);
        messageContentArea.setWrapStyleWord(true);
        messageContentArea.setText("Select a message to read its content...");

        searchField = new JTextField(20);
        searchField.setToolTipText("Search messages...");

        toField = new JTextField();
        toField.setToolTipText("Enter single recipient or multiple separated by commas (user1,user2,user3)");
        subjectField = new JTextField();
        composeArea = new JTextArea();
        composeArea.setLineWrap(true);
        composeArea.setWrapStyleWord(true);

        statusLabel = new JLabel();
        String[] statusOptions = {"Active", "Busy", "Away"};
        statusComboBox = new JComboBox<>(statusOptions);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        add(createMenuBar(), BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(createLeftPanel());
        mainSplitPane.setRightComponent(createCenterRightSplitPane());
        mainSplitPane.setDividerLocation(250);

        add(mainSplitPane, BorderLayout.CENTER);

        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export Conversation...");
        JMenuItem logoutItem = new JMenuItem("Logout");

        // ⭐ أضيف هذا السطر الناقص ⭐
        exportItem.addActionListener(e -> exportConversation());

        logoutItem.addActionListener(e -> logout());

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(logoutItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.addActionListener(e -> refreshAllData());
        viewMenu.add(refreshItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        return menuBar;
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        leftPanel.setPreferredSize(new Dimension(250, 0));

        JPanel foldersPanel = new JPanel(new BorderLayout());
        foldersPanel.setBorder(BorderFactory.createTitledBorder("Folders"));
        foldersPanel.add(new JScrollPane(folderList), BorderLayout.CENTER);

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));

        JPanel usersHeaderPanel = new JPanel(new BorderLayout());
        usersHeaderPanel.add(new JLabel("Online Users"), BorderLayout.WEST);

        JButton refreshUsersBtn = new JButton("Refresh");
        refreshUsersBtn.setToolTipText("Refresh online users");
        refreshUsersBtn.addActionListener(e -> loadOnlineUsers());
        usersHeaderPanel.add(refreshUsersBtn, BorderLayout.EAST);

        usersPanel.add(usersHeaderPanel, BorderLayout.NORTH);
        usersPanel.add(new JScrollPane(onlineUsersList), BorderLayout.CENTER);

        leftPanel.add(foldersPanel, BorderLayout.NORTH);
        leftPanel.add(usersPanel, BorderLayout.CENTER);

        return leftPanel;
    }

    private JSplitPane createCenterRightSplitPane() {
        JSplitPane centerRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerRightSplit.setLeftComponent(createCenterPanel());
        centerRightSplit.setRightComponent(createRightPanel());
        centerRightSplit.setDividerLocation(600);

        return centerRightSplit;
    }

    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setPreferredSize(new Dimension(300, 0));

        JPanel composePanel = new JPanel(new BorderLayout());
        composePanel.setBorder(BorderFactory.createTitledBorder("Compose Message"));

        JPanel composeForm = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2;
        JLabel toLabel = new JLabel("To:");
        toLabel.setToolTipText("Enter single recipient or multiple separated by commas");
        composeForm.add(toLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 0.8;
        toField.setToolTipText("Example: user1 or user1,user2,user3");
        composeForm.add(toField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2;
        composeForm.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        composeForm.add(subjectField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        composeForm.add(new JLabel("Message:"), gbc);

        gbc.gridy = 3; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JScrollPane composeScroll = new JScrollPane(composeArea);
        composeScroll.setPreferredSize(new Dimension(280, 200));
        composeForm.add(composeScroll, gbc);

        gbc.gridy = 4; gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        sendButton = new JButton("Send Message");
        sendButton.setBackground(new Color(34, 139, 34));
        sendButton.setForeground(Color.WHITE);
        sendButton.addActionListener(e -> sendMessage());
        composeForm.add(sendButton, gbc);

        composePanel.add(composeForm, BorderLayout.CENTER);
        rightPanel.add(composePanel, BorderLayout.CENTER);

        return rightPanel;
    }

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setForeground(Color.BLACK);
        logoutBtn.setFont(new Font("Arial", Font.BOLD, 12));
        logoutBtn.setBackground(new Color(255, 100, 100));
        logoutBtn.addActionListener(e -> logout());
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(new JLabel("Search:"));
        leftPanel.add(searchField);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton searchButton = new JButton("Search");
        searchButton.setBackground(new Color(52, 100, 100));
        searchButton.addActionListener(e -> searchMessages());

        JButton exportButton = new JButton("Export");
        exportButton.setBackground(new Color(70, 130, 180));
        exportButton.setForeground(Color.WHITE);
        exportButton.setFont(new Font("Arial", Font.BOLD, 12));
        exportButton.addActionListener(e -> exportConversation());
        exportButton.setToolTipText("Export all messages to file");

        rightPanel.add(searchButton);
        rightPanel.add(exportButton);
        rightPanel.add(logoutBtn);

        searchPanel.add(leftPanel, BorderLayout.WEST);
        searchPanel.add(rightPanel, BorderLayout.EAST);

        JPanel messagesPanel = new JPanel(new BorderLayout());
        messagesPanel.setBorder(BorderFactory.createTitledBorder("Messages"));

        JPanel messageButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton archiveBtn = new JButton("Archive");
        JButton restoreBtn = new JButton("Restore");

        archiveBtn.addActionListener(e -> archiveSelectedMessage());
        restoreBtn.addActionListener(e -> restoreSelectedMessage());

        messageButtonsPanel.add(archiveBtn);
        messageButtonsPanel.add(restoreBtn);

        messagesPanel.add(messageButtonsPanel, BorderLayout.NORTH);
        messagesPanel.add(new JScrollPane(messagesTable), BorderLayout.CENTER);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createTitledBorder("Message Content"));
        contentPanel.add(new JScrollPane(messageContentArea), BorderLayout.CENTER);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, messagesPanel, contentPanel);
        centerSplit.setDividerLocation(300);

        centerPanel.add(searchPanel, BorderLayout.NORTH);
        centerPanel.add(centerSplit, BorderLayout.CENTER);

        return centerPanel;
    }

    private void setupButtonsColor() {
        UIManager.put("Button.foreground", Color.BLACK);
        UIManager.put("Button.font", new Font("Arial", Font.BOLD, 12));

        updateAllButtons(this.getContentPane());
    }

    private void updateAllButtons(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JButton) {
                JButton button = (JButton) comp;
                button.setForeground(Color.BLACK);
                button.setFont(new Font("Arial", Font.BOLD, 12));
            } else if (comp instanceof Container) {
                updateAllButtons((Container) comp);
            }
        }
    }

    private JPanel createStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setPreferredSize(new Dimension(getWidth(), 25));

        statusPanel.add(statusLabel, BorderLayout.WEST);

        JPanel statusControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusControlPanel.add(new JLabel("Status:"));
        statusControlPanel.add(statusComboBox);
        statusPanel.add(statusControlPanel, BorderLayout.EAST);

        return statusPanel;
    }

    private void setupEventListeners() {
        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedFolder = folderList.getSelectedValue();
                logger.log("Folder selected: " + selectedFolder);
                loadCurrentFolderMessages();
            }
        });

        messagesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && messagesTable.getSelectedRow() != -1) {
                displaySelectedMessage();
            }
        });

        statusComboBox.addActionListener(e -> {
            String selectedStatus = (String) statusComboBox.getSelectedItem();
            if (selectedStatus != null) {
                String status = selectedStatus.replaceAll("[^\\w]", "").toUpperCase();
                controller.setStatus(status);
                logger.log("Status changed to: " + status);
                loadOnlineUsers();
                updateStatusBar();
            }
        });

        setupKeyboardShortcuts();
    }

    private void setupKeyboardShortcuts() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl N"), "compose");
        getRootPane().getActionMap().put("compose", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toField.requestFocus();
                logger.log("Keyboard shortcut: Ctrl+N - New message");
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl F"), "search");
        getRootPane().getActionMap().put("search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.requestFocus();
                searchField.selectAll();
                logger.log("Keyboard shortcut: Ctrl+F - Search");
            }
        });
    }

    private void loadRealData() {
        if (controller == null) return;
        loadOnlineUsers();
        loadCurrentFolderMessages();
    }

    private void loadOnlineUsers() {
        if (controller == null || !controller.isConnected()) {
            DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement("Not connected");
            onlineUsersList.setModel(model);
            return;
        }

        try {
            List<String> responses = controller.getOnlineUsers();
            DefaultListModel<String> model = new DefaultListModel<>();

            if (responses == null || responses.isEmpty()) {
                model.addElement("No response from server");
            } else {
                for (String line : responses) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        String username = parts[0];
                        String status = parts[1].toUpperCase();

                        String statusText = switch (status) {
                            case "ACTIVE" -> "Active";
                            case "BUSY"   -> "Busy";
                            case "AWAY"   -> "Away";
                            default       -> "Unknown";
                        };

                        model.addElement(statusText + " " + username);
                    } else if (parts.length == 1 && !parts[0].startsWith("212")) {
                        model.addElement("Active " + parts[0]);
                    }
                }

                if (model.isEmpty()) {
                    model.addElement("No users online");
                }
            }

            onlineUsersList.setModel(model);

        } catch (Exception e) {
            DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement("Error: " + e.getMessage());
            onlineUsersList.setModel(model);
            e.printStackTrace();
        }
    }

    private void loadCurrentFolderMessages() {
        if (controller == null) return;

        String selectedFolder = folderList.getSelectedValue();
        if (selectedFolder == null) return;

        try {
            List<Message> messages = new ArrayList<>();

            switch (selectedFolder) {
                case "Inbox":
                    messages = controller.getInboxMessages();
                    break;
                case "Sent":
                    messages = controller.getSentMessages();
                    break;
                case "Archive":
                    messages = controller.getArchivedMessages();
                    break;
            }

            updateMessagesTable(messages);
            logger.log("Loaded " + messages.size() + " messages from " + selectedFolder);

        } catch (Exception e) {
            logger.log("ERROR loading messages: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to load messages", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displaySelectedMessage() {
        int selectedRow = messagesTable.getSelectedRow();
        if (selectedRow != -1 && controller != null) {
            try {
                int modelRow = messagesTable.convertRowIndexToModel(selectedRow);
                DefaultTableModel model = (DefaultTableModel) messagesTable.getModel();

                String from = (String) model.getValueAt(modelRow, 0);
                String subject = (String) model.getValueAt(modelRow, 1);
                String messageId = (String) model.getValueAt(modelRow, 4);

                System.out.println("Displaying message - From: " + from + ", Subject: " + subject + ", ID: " + messageId);

                Message selectedMessage = controller.getMessage(messageId);

                if (selectedMessage != null) {
                    displayActualMessageContent(selectedMessage);
                } else {
                    messageContentArea.setText("From: " + from + "\nSubject: " + subject +
                            "\n\nCould not load message content from server.");
                }

            } catch (Exception e) {
                System.out.println("Error displaying message: " + e.getMessage());
                messageContentArea.setText("Error loading message: " + e.getMessage());
            }
        }
    }

    private void updateMessagesTable(List<Message> messages) {
        DefaultTableModel model = (DefaultTableModel) messagesTable.getModel();
        model.setRowCount(0);

        if (messages.isEmpty()) {
            messageContentArea.setText("No messages in this folder.");
            return;
        }

        for (Message msg : messages) {
            boolean isMessageRead = readMessageIds.contains(msg.getId()) || msg.isRead();

            String statusText = isMessageRead ? "Seen" : "New";
            String dateStr = new SimpleDateFormat("MMM dd, HH:mm").format(new Date(msg.getTimestamp()));
            String from = msg.getFrom() != null ? msg.getFrom() : "Unknown";
            String subject = msg.getSubject() != null ? msg.getSubject() : "No Subject";

            model.addRow(new Object[]{
                    from,
                    subject,
                    dateStr,
                    statusText,
                    msg.getId()
            });
        }
    }

    private void displayActualMessageContent(Message msg) {
        if (msg == null) {
            messageContentArea.setText("Error: Message is null");
            return;
        }

        boolean wasNew = !readMessageIds.contains(msg.getId());
        readMessageIds.add(msg.getId());
        saveReadMessages();

        if (wasNew) {
            new Thread(() -> {
                try {
                    controller.markMessageAsRead(msg.getId());
                    System.out.println("Marked message as read on server: " + msg.getId());
                } catch (Exception ex) {
                    System.out.println("Failed to mark message as read on server: " + ex.getMessage());
                }
            }).start();
        }

        StringBuilder content = new StringBuilder();
        content.append("════════════════════════════════════\n");
        content.append("                MESSAGE DETAILS                \n");
        content.append("════════════════════════════════════\n\n");
        content.append("From       → ").append(msg.getFrom() != null ? msg.getFrom() : "Unknown").append("\n");
        content.append("To         → ").append(msg.getTo() != null ? msg.getTo() : controller.getUsername()).append("\n");
        content.append("Subject    → ").append(msg.getSubject() != null ? msg.getSubject() : "No Subject").append("\n");
        content.append("Date       → ").append(new Date(msg.getTimestamp())).append("\n");
        content.append("Status     → ").append("Read").append("\n");
        content.append("ID         → ").append(msg.getId()).append("\n\n");
        content.append("════════════════════════════════════\n");
        content.append("                  MESSAGE BODY                  \n");
        content.append("════════════════════════════════════\n\n");

        String body = msg.getBody();
        content.append(body == null || body.isEmpty() ? "(No message content)" : body);

        messageContentArea.setText(content.toString());
        messageContentArea.setCaretPosition(0);

        int viewRow = messagesTable.getSelectedRow();
        if (viewRow != -1) {
            int modelRow = messagesTable.convertRowIndexToModel(viewRow);
            DefaultTableModel model = (DefaultTableModel) messagesTable.getModel();
            model.setValueAt("Seen", modelRow, 3);
            messagesTable.repaint();
        }
    }

    private List<Message> getCurrentFolderMessages() {
        String selectedFolder = folderList.getSelectedValue();
        if (selectedFolder == null) return new ArrayList<>();

        System.out.println("Getting messages for folder: " + selectedFolder);

        try {
            List<Message> messages = new ArrayList<>();

            switch (selectedFolder) {
                case "Inbox":
                    messages = controller.getInboxMessages();
                    break;
                case "Sent":
                    messages = controller.getSentMessages();
                    break;
                case "Archive":
                    messages = controller.getArchivedMessages();
                    break;
            }

            System.out.println("Retrieved " + messages.size() + " messages from controller");
            return messages;

        } catch (Exception e) {
            System.out.println("Error getting messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void archiveSelectedMessage() {
        int viewRow = messagesTable.getSelectedRow();
        if (viewRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a message first", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int modelRow = messagesTable.convertRowIndexToModel(viewRow);
            DefaultTableModel model = (DefaultTableModel) messagesTable.getModel();

            if (modelRow < 0 || modelRow >= model.getRowCount()) {
                JOptionPane.showMessageDialog(this, "Invalid message selection", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String messageId = (String) model.getValueAt(modelRow, 4);

            if (messageId == null || messageId.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cannot archive: Message ID is missing", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to archive this message?",
                    "Confirm Archive", JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            boolean success = controller.archiveMessage(messageId.trim());

            if (success) {
                model.removeRow(modelRow);
                messageContentArea.setText("Message archived successfully");

                if (folderList.getSelectedValue() != null &&
                        folderList.getSelectedValue().equals("Inbox")) {
                    loadCurrentFolderMessages();
                }

                JOptionPane.showMessageDialog(this,
                        "Message archived successfully",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                logger.log("Message archived: " + messageId);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to archive message. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            logger.log("ERROR: Array index out of bounds in archive: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error: Invalid message selection. Please try selecting again.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            logger.log("ERROR archiving message: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error: " + ex.getMessage(),
                    "Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreSelectedMessage() {
        int viewRow = messagesTable.getSelectedRow();
        if (viewRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a message first", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int modelRow = messagesTable.convertRowIndexToModel(viewRow);
            DefaultTableModel model = (DefaultTableModel) messagesTable.getModel();

            if (modelRow < 0 || modelRow >= model.getRowCount()) {
                JOptionPane.showMessageDialog(this, "Invalid message selection", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String messageId = (String) model.getValueAt(modelRow, 4);

            if (messageId == null || messageId.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cannot restore: Message ID is missing", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to restore this message to inbox?",
                    "Confirm Restore", JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            boolean success = controller.restoreMessage(messageId.trim());

            if (success) {
                model.removeRow(modelRow);
                messageContentArea.setText("Message restored to inbox successfully");

                if (folderList.getSelectedValue() != null &&
                        folderList.getSelectedValue().equals("Archive")) {
                    loadCurrentFolderMessages();
                }

                JOptionPane.showMessageDialog(this,
                        "Message restored successfully",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                logger.log("Message restored: " + messageId);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to restore message. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            logger.log("ERROR: Array index out of bounds in restore: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error: Invalid message selection. Please try selecting again.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            logger.log("ERROR restoring message: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error: " + ex.getMessage(),
                    "Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void refreshAllData() {
        if (controller == null || !controller.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected to server!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.out.println("FORCE REFRESH - Reloading all data from server");

        JOptionPane.showMessageDialog(this, "Force refreshing data from server...", "Refreshing", JOptionPane.INFORMATION_MESSAGE);

        loadOnlineUsers();
        loadCurrentFolderMessages();
        updateStatusBar();

        JOptionPane.showMessageDialog(this,
                "Data force refreshed successfully!\n" +
                        "All messages should now be up-to-date.",
                "Refresh Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void autoRefreshData() {
        autoRefreshTimer = new Timer(8000, e -> {
            if (controller != null && controller.isConnected() &&
                    folderList.getSelectedValue() != null &&
                    folderList.getSelectedValue().contains("Inbox")) {

                loadCurrentFolderMessages();
            }
        });
        autoRefreshTimer.start();
    }

    private void searchMessages() {
        String searchText = searchField.getText().trim().toLowerCase();
        if (!searchText.isEmpty()) {
            logger.log("Searching for: " + searchText);

            TableRowSorter<DefaultTableModel> sorter =
                    new TableRowSorter<>((DefaultTableModel) messagesTable.getModel());
            messagesTable.setRowSorter(sorter);

            RowFilter<DefaultTableModel, Object> rowFilter = new RowFilter<DefaultTableModel, Object>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                    String sender = entry.getStringValue(0).toLowerCase();
                    String subject = entry.getStringValue(1).toLowerCase();

                    return sender.contains(searchText) || subject.contains(searchText);
                }
            };

            sorter.setRowFilter(rowFilter);

            int resultCount = messagesTable.getRowCount();
            if (resultCount > 0) {
                statusLabel.setText("Search found " + resultCount + " messages");
            } else {
                statusLabel.setText("No messages found matching: " + searchText);
            }

        } else {
            TableRowSorter<DefaultTableModel> sorter =
                    new TableRowSorter<>((DefaultTableModel) messagesTable.getModel());
            messagesTable.setRowSorter(sorter);
            sorter.setRowFilter(null);
            updateStatusBar();
        }
    }

    private void sendMessage() {
        if (sendButton == null) {
            System.err.println("sendButton is null! Check initialization.");
            return;
        }

        String to = toField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = composeArea.getText().trim();

        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (to.contains(",")) {
            String[] recipients = to.split(",");
            int validRecipients = 0;
            for (String recipient : recipients) {
                if (!recipient.trim().isEmpty()) {
                    validRecipients++;
                }
            }

            if (validRecipients == 0) {
                JOptionPane.showMessageDialog(this, "Please enter valid recipient(s)!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            System.out.println("Sending to " + validRecipients + " recipient(s): " + to);
        }

        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        resetActivityTimers();

        new Thread(() -> {
            try {
                System.out.println("Starting message send process...");
                controller.sendMessage(to, subject, body);

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Message sent successfully!\nTo: " + to + "\nSubject: " + subject,
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    toField.setText("");
                    subjectField.setText("");
                    composeArea.setText("");

                    System.out.println("Auto-refreshing after send...");
                    loadCurrentFolderMessages();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = e.getMessage();
                    if (errorMessage.contains("Not connected")) {
                        errorMessage = "Connection Lost!\n\n" +
                                "Please:\n" +
                                "1. Check if server is running\n" +
                                "2. Logout and login again\n" +
                                "3. Make sure port 1234 is available";
                    }

                    JOptionPane.showMessageDialog(MainWindow.this,
                            errorMessage,
                            "Send Failed", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (sendButton != null) {
                        sendButton.setEnabled(true);
                        sendButton.setText("Send Message");
                    }
                });
            }
        }).start();
    }
    private void exportConversation() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Conversation");

        final String folder = folderList.getSelectedValue() != null ? folderList.getSelectedValue() : "Unknown";
        final String searchText = searchField.getText() != null ? searchField.getText().trim() : "";

        String fileName;
        Date now = new Date();

        if (searchText != null && !searchText.isEmpty()) {
            fileName = "search_" + searchText + "_" + new SimpleDateFormat("yyyyMMdd").format(now) + ".txt";
        } else {
            fileName = folder.toLowerCase() + "_export_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(now) + ".txt";
        }

        fileChooser.setSelectedFile(new File(fileName));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            File finalExportFile;
            if (selectedFile != null) {
                String path = selectedFile.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".txt")) {
                    finalExportFile = new File(path + ".txt");
                } else {
                    finalExportFile = selectedFile;
                }
            } else {
                return;
            }

            final File exportFileFinal = finalExportFile;

            new Thread(() -> {
                performExport(exportFileFinal, folder, searchText);
            }).start();
        }
    }
    private void performExport(File file, String folder, String searchText) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {

            writer.write("=".repeat(60));
            writer.newLine();
            writer.write("                     MAILLITE CONVERSATION EXPORT                     ");
            writer.newLine();
            writer.write("=".repeat(60));
            writer.newLine();
            writer.newLine();

            writer.write("Export Date  : " + new Date());
            writer.newLine();
            writer.write("User         : " + controller.getUsername());
            writer.newLine();
            writer.write("Folder       : " + folder);
            writer.newLine();

            if (searchText != null && !searchText.isEmpty()) {
                writer.write("Search Query : " + searchText);
                writer.newLine();
            }

            writer.write("=".repeat(60));
            writer.newLine();
            writer.newLine();

            List<Message> messages = getCurrentFolderMessages();
            int exportedCount = 0;

            for (Message msg : messages) {
                if (msg != null && msg.getId() != null) {
                    writer.write("-".repeat(60));
                    writer.newLine();
                    writer.write("Message ID    : " + msg.getId());
                    writer.newLine();
                    writer.write("From          : " + (msg.getFrom() != null ? msg.getFrom() : "Unknown"));
                    writer.newLine();
                    writer.write("To            : " + (msg.getTo() != null ? msg.getTo() : controller.getUsername()));
                    writer.newLine();
                    writer.write("Subject       : " + (msg.getSubject() != null ? msg.getSubject() : "No Subject"));
                    writer.newLine();
                    writer.write("Date          : " + new Date(msg.getTimestamp()));
                    writer.newLine();
                    writer.newLine();

                    // Body
                    writer.write("Body:");
                    writer.newLine();
                    String body = "(No content available)";
                    try {
                        Message fullMessage = controller.getMessage(msg.getId());
                        if (fullMessage != null && fullMessage.getBody() != null) {
                            body = fullMessage.getBody();
                        }
                    } catch (Exception e) {
                        body = "(Error loading message content)";
                    }
                    writer.write(body);
                    writer.newLine();
                    writer.newLine();

                    exportedCount++;
                }
            }

            writer.write("=".repeat(60));
            writer.newLine();
            writer.write("Total messages exported: " + exportedCount);
            writer.newLine();
            writer.write("=".repeat(60));

            final int finalCount = exportedCount;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(MainWindow.this,
                        "Export successful!\nExported " + finalCount + " messages to:\n" + file.getAbsolutePath(),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            });

            logger.log("Successfully exported " + exportedCount + " messages to " + file.getName());

        } catch (Exception e) {
            logger.log("Export error: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(MainWindow.this,
                        "Export failed: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to logout?", "Confirm Logout", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            logger.log("User logging out");

            saveReadMessages();

            if (controller != null) {
                try {
                    if (controller.isConnected()) {
                        controller.getTcpClient().sendCommand("QUIT");
                    }
                    controller.logout();
                } catch (Exception e) {
                    logger.log("Error during logout: " + e.getMessage());
                }
            }

            dispose();

            SwingUtilities.invokeLater(() -> {
                new client.gui.LoginWindow().setVisible(true);
            });
        }
    }

    private void updateStatusBar() {
        if (statusComboBox != null && statusLabel != null) {
            int messageCount = messagesTable.getRowCount();
            String status = controller.getStats();

            String statusText = String.format(
                    "User: %s | Messages: %d | %s",
                    controller.getUsername(),
                    messageCount,
                    status
            );
            statusLabel.setText(statusText);
        }
    }

    private void setupTableColors() {
        messagesTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value,
                        isSelected, hasFocus, row, column);

                try {
                    int modelRow = table.convertRowIndexToModel(row);
                    String sender = (String) table.getModel().getValueAt(modelRow, 0);
                    String status = (String) table.getModel().getValueAt(modelRow, 3);
                    String currentUser = controller.getUsername();

                    if (isSelected) {
                        c.setBackground(new Color(100, 149, 237));
                        c.setForeground(Color.WHITE);
                    } else {
                        if (sender != null && !sender.equals(currentUser)) {
                            if ("New".equals(status)) {
                                c.setBackground(new Color(255, 230, 230));
                            } else {
                                c.setBackground(new Color(255, 240, 240));
                            }
                        } else {
                            if ("New".equals(status)) {
                                c.setBackground(new Color(230, 255, 230));
                            } else {
                                c.setBackground(new Color(240, 255, 240));
                            }
                        }
                        c.setForeground(Color.BLACK);

                        if ("New".equals(status)) {
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else {
                            setFont(getFont().deriveFont(Font.PLAIN));
                        }
                    }
                } catch (Exception e) {
                    if (isSelected) {
                        c.setBackground(new Color(100, 149, 237));
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });
    }

    @Override
    public void dispose() {
        if (autoAwayTimer != null) {
            autoAwayTimer.stop();
        }
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
        }
        if (statusUpdateTimer != null) {
            statusUpdateTimer.stop();
        }

        saveReadMessages();

        if (!composeArea.getText().trim().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "You have an unsent message. Are you sure you want to exit?",
                    "Confirm Exit", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (controller != null) {
            controller.logout();
        }

        super.dispose();
    }
}