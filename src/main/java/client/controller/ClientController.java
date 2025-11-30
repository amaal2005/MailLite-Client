// client/controller/ClientController.java
package client.controller;

import client.gui.models.Message;
import client.network.TCPClient;
import client.network.UDPListener;
import client.utils.Logger;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ClientController {
    private TCPClient tcpClient;
    private UDPListener udpListener;
    private Thread udpThread;
    private String username;
    private Logger logger;
    private NotificationCallback notificationCallback;

    public interface NotificationCallback {
        void onNewMail(String username, int count);
    }

    public ClientController() {
        this.logger = new Logger();
    }

    public void setNotificationCallback(NotificationCallback callback) {
        this.notificationCallback = callback;
    }

    public boolean login(String host, int tcpPort, String username, String password, int udpPort) {
        try {
            System.out.println("Connecting to " + host + ":" + tcpPort);
            tcpClient = new TCPClient(host, tcpPort);

            if (!tcpClient.connect()) {
                System.out.println("TCP Connection failed");
                return false;
            }

            System.out.println("Step 1: Sending HELO with UDP port " + udpPort);
            String heloCommand = "HELO " + username + " UDP:" + udpPort;
            String heloResponse = tcpClient.sendCommand(heloCommand);
            System.out.println("HELO Response: " + heloResponse);

            if (heloResponse == null || !heloResponse.startsWith("250")) {
                System.out.println("HELO failed: " + heloResponse);
                return false;
            }

            System.out.println("Step 2: Sending AUTH");
            String authCommand = "AUTH " + username + " " + password;
            String authResponse = tcpClient.sendCommand(authCommand);
            System.out.println("AUTH Response: " + authResponse);

            if (authResponse == null || !authResponse.startsWith("235")) {
                System.out.println("AUTH failed: " + authResponse);
                return false;
            }

            this.username = username;
            startUDPListener(udpPort);

            System.out.println("Login successful for: " + username);
            logger.log("User " + username + " logged in successfully");
            return true;

        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startUDPListener(int udpPort) {
        try {
            udpListener = new UDPListener(udpPort, new UDPListener.NotificationCallback() {
                @Override
                public void onNewMail(String username, int count) {
                    System.out.println("UDP Notification - User: " + username + ", Count: " + count);
                    if (notificationCallback != null) {
                        notificationCallback.onNewMail(username, count);
                    }
                }
            });

            udpThread = new Thread(udpListener);
            udpThread.setDaemon(true);
            udpThread.start();
            System.out.println("UDP listener started on port " + udpPort);
        } catch (Exception e) {
            System.out.println("Failed to start UDP listener: " + e.getMessage());
        }
    }
    public void sendMessage(String to, String subject, String body) {
        try {
            System.out.println("Attempting to send message...");

            if (tcpClient == null || !tcpClient.isConnected()) {
                System.out.println("Connection lost! Attempting to reconnect...");
                boolean reconnected = attemptReconnect();
                if (!reconnected) {
                    throw new RuntimeException("Failed to reconnect to server. Please check if server is running.");
                }
            }

            BufferedReader reader = tcpClient.getReader();
            PrintWriter writer = tcpClient.getWriter();

            writer.println("SEND");
            writer.flush();
            String response1 = reader.readLine();
            System.out.println("Response 1: " + response1);

            if (response1 == null || !response1.startsWith("354")) {
                throw new Exception("SEND command failed: " + response1);
            }

            // ⭐⭐ التحديث: دعم multiple recipients ⭐⭐
            String formattedTo = formatRecipients(to);
            String headers = "FROM:" + username + " TO:" + formattedTo + " SUBJ:" + subject + " BODYLEN:" + body.length();

            writer.println(headers);
            writer.flush();
            String response2 = reader.readLine();
            System.out.println("Response 2: " + response2);

            if (response2 == null || !response2.startsWith("354")) {
                throw new Exception("Headers failed: " + response2);
            }

            writer.print(body);
            writer.println();
            writer.flush();

            String response3 = reader.readLine();
            System.out.println("Response 3: " + response3);

            if (response3 != null && response3.startsWith("250")) {
                String messageId = extractMessageId(response3);
                System.out.println("Message sent successfully! ID: " + messageId);
                logger.log("SEND SUCCESS - ID: " + messageId + " To: " + formattedTo);
            } else {
                throw new Exception("Message save failed: " + response3);
            }

        } catch (Exception e) {
            System.out.println("Send failed: " + e.getMessage());
            logger.log("SEND FAILED - " + e.getMessage());
            throw new RuntimeException("Send failed: " + e.getMessage(), e);
        }
    }

    private String formatRecipients(String to) {
        if (to == null || to.trim().isEmpty()) {
            return "";
        }

        // فصل المستلمين بفواصل وإزالة المسافات الزائدة
        String[] recipients = to.split(",");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < recipients.length; i++) {
            String recipient = recipients[i].trim();
            if (!recipient.isEmpty()) {
                if (i > 0) {
                    formatted.append(",");
                }
                formatted.append(recipient);
            }
        }

        return formatted.toString();
    }
    private boolean attemptReconnect() {
        try {
            System.out.println("Attempting automatic reconnect...");

            if (tcpClient != null) {
                tcpClient.close();
            }

            tcpClient = new TCPClient("localhost", 1234);
            boolean connected = tcpClient.connect();

            if (connected) {
                System.out.println("Reconnected successfully!");

                String authCommand = "AUTH " + username + " " + "password";
                String authResponse = tcpClient.sendCommand(authCommand);

                if (authResponse != null && authResponse.startsWith("235")) {
                    System.out.println("Re-authenticated successfully!");
                    return true;
                } else {
                    System.out.println("Re-authentication failed: " + authResponse);
                    return false;
                }
            } else {
                System.out.println("Reconnection failed");
                return false;
            }

        } catch (Exception e) {
            System.out.println("Reconnection error: " + e.getMessage());
            return false;
        }
    }

    private String extractMessageId(String response) {
        if (response != null && response.startsWith("250 MSGID")) {
            String[] parts = response.split(" ");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return "unknown";
    }

    public boolean checkConnection() {
        if (tcpClient == null || !tcpClient.isConnected()) {
            System.out.println("Not connected to server");
            return false;
        }

        try {
            String response = tcpClient.sendCommand("NOOP");
            boolean isConnected = response != null && response.startsWith("250");

            if (!isConnected) {
                System.out.println("Connection test failed");
            }

            return isConnected;

        } catch (Exception e) {
            System.out.println("Connection check failed: " + e.getMessage());
            return false;
        }
    }

    public List<Message> getInboxMessages() {
        return getMessagesFromServer("INBOX");
    }

    public List<Message> getSentMessages() {
        return getMessagesFromServer("SENT");
    }

    public List<Message> getArchivedMessages() {
        return getMessagesFromServer("ARCHIVE");
    }

    private List<Message> getMessagesFromServer(String folder) {
        try {
            System.out.println("Requesting " + folder + " messages from server...");
            List<String> responses = tcpClient.sendMultiLineCommand("LIST " + folder);

            if (responses == null || responses.isEmpty()) {
                System.out.println("No response from server for LIST " + folder);
                return new ArrayList<>();
            }

            System.out.println("Server response for " + folder + ": " + responses.size() + " lines");

            List<Message> messages = parseRealMessagesFromServer(responses, folder);
            System.out.println("Retrieved " + messages.size() + " real messages from " + folder);

            return messages;

        } catch (Exception e) {
            System.out.println("Error getting " + folder + " messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Message> parseRealMessagesFromServer(List<String> responses, String folder) {
        List<Message> messages = new ArrayList<>();

        for (String response : responses) {
            if (response.startsWith("213 ") && !response.equals("213 END") && !response.matches("213 \\d+")) {
                String messageData = response.substring(4);
                String[] parts = messageData.split(" ", 5);

                if (parts.length >= 4) {
                    try {
                        Message msg = new Message();
                        msg.setId(parts[0]);
                        msg.setFrom(parts[1]);

                        String subject = parts.length >= 5 ? parts[4] : "No Subject";
                        msg.setSubject(subject);

                        int size = Integer.parseInt(parts[2]);
                        long timestamp = Long.parseLong(parts[3]);
                        msg.setTimestamp(timestamp);

                        msg.setBody("");
                        msg.setTo("");
                        msg.setRead(folder.equals("SENT") || folder.equals("ARCHIVE"));

                        messages.add(msg);

                    } catch (Exception e) {
                        System.out.println("Parse error: " + e.getMessage());
                    }
                }
            }
        }
        return messages;
    }

    public Message getMessage(String messageId) {
        try {
            System.out.println("Retrieving FULL message: " + messageId);
            List<String> responses = tcpClient.sendMultiLineCommand("RETR " + messageId);

            if (responses == null || responses.isEmpty()) {
                System.out.println("No response for RETR command");
                return null;
            }

            if (responses.get(0).startsWith("550")) {
                System.out.println("Message not found: " + responses.get(0));
                return null;
            }

            Message msg = new Message();
            msg.setId(messageId);

            StringBuilder bodyBuilder = new StringBuilder();
            boolean inBody = false;

            for (String line : responses) {
                if (line.startsWith("214 FROM:")) {
                    msg.setFrom(line.substring(9));
                } else if (line.startsWith("214 TO:")) {
                    String toValue = line.substring(7).trim();
                    msg.setTo(toValue);
                } else if (line.startsWith("214 SUBJ:")) {
                    msg.setSubject(line.substring(9));
                } else if (line.startsWith("214 TIMESTAMP:")) {
                    try {
                        long timestamp = Long.parseLong(line.substring(14));
                        msg.setTimestamp(timestamp);
                    } catch (NumberFormatException e) {
                        msg.setTimestamp(System.currentTimeMillis());
                    }
                } else if (line.equals("214 BODY")) {
                    inBody = true;
                } else if (inBody && !line.equals("214 END")) {
                    bodyBuilder.append(line).append("\n");
                }
            }

            msg.setBody(bodyBuilder.toString().trim());
            System.out.println("Retrieved FULL message: " + msg.getSubject());
            return msg;

        } catch (Exception e) {
            System.out.println("Error getting message: " + e.getMessage());
            return null;
        }
    }

    public List<String> getOnlineUsers() {
        try {
            System.out.println("Requesting online users from server...");
            List<String> responses = tcpClient.sendMultiLineCommand("WHO");

            List<String> onlineUsers = new ArrayList<>();

            for (String response : responses) {
                System.out.println("WHO response: " + response);
                if (response.startsWith("212U")) {
                    String userInfo = response.substring(5);
                    onlineUsers.add(userInfo);
                }
            }

            System.out.println("Retrieved " + onlineUsers.size() + " online users from server");
            return onlineUsers;

        } catch (Exception e) {
            System.out.println("Error getting online users: " + e.getMessage());
            List<String> fallbackUsers = new ArrayList<>();
            fallbackUsers.add("alice ACTIVE 127.0.0.1 " + System.currentTimeMillis());
            fallbackUsers.add("bob ACTIVE 127.0.0.1 " + (System.currentTimeMillis() - 60000));
            return fallbackUsers;
        }
    }

    public void markMessageAsRead(String messageId) throws Exception {
        String response = tcpClient.sendCommand("MARK " + messageId);
        if (response == null || !response.startsWith("250")) {
            throw new Exception("Server refused to mark message as read: " + response);
        }
        System.out.println("Marked message as read: " + messageId);
    }

    public boolean archiveMessage(String messageId) {
        try {
            String response = tcpClient.sendCommand("DELE " + messageId);
            boolean success = response != null && response.startsWith("250");
            System.out.println("Archive " + (success ? "successful" : "failed") + " for: " + messageId);
            return success;
        } catch (Exception e) {
            System.out.println("Error archiving message: " + e.getMessage());
            return false;
        }
    }

    public boolean restoreMessage(String messageId) {
        try {
            String response = tcpClient.sendCommand("RESTORE " + messageId);
            boolean success = response != null && response.startsWith("250");
            System.out.println("Restore " + (success ? "successful" : "failed") + " for: " + messageId);
            return success;
        } catch (Exception e) {
            System.out.println("Error restoring message: " + e.getMessage());
            return false;
        }
    }

    public void setStatus(String status) {
        try {
            String response = tcpClient.sendCommand("SETSTAT " + status);
            System.out.println("Status update: " + (response != null && response.startsWith("250") ? "success" : "failed"));
        } catch (Exception e) {
            System.out.println("Error setting status: " + e.getMessage());
        }
    }

    public String getStats() {
        try {
            String stats = tcpClient.sendCommand("STAT");
            System.out.println("Stats: " + stats);
            return stats != null ? stats : "211 M:0 S:0 U:0";
        } catch (Exception e) {
            System.out.println("Error getting stats: " + e.getMessage());
            return "211 M:0 S:0 U:0";
        }
    }

    public void logout() {
        logger.log("LOGOUT - User: " + username);
        try {
            if (tcpClient != null && tcpClient.isConnected()) {
                tcpClient.sendCommand("QUIT");
            }
        } catch (Exception e) {
            System.out.println("Error sending QUIT: " + e.getMessage());
        }

        if (tcpClient != null) {
            tcpClient.close();
        }
        if (udpListener != null) {
            udpListener.stop();
        }
        if (udpThread != null) {
            try {
                udpThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.close();
    }

    public String getUsername() { return username; }
    public TCPClient getTcpClient() { return tcpClient; }
    public boolean isConnected() { return tcpClient != null && tcpClient.isConnected(); }

    public void forceRefreshAfterSend() {
        System.out.println("Forcing refresh after send...");
    }

    public List<Message> getInboxMessagesFast() {
        return getMessagesFromServer("INBOX");
    }

    public List<Message> getSentMessagesFast() {
        return getMessagesFromServer("SENT");
    }
}