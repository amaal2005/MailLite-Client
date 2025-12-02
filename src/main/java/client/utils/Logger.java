package client.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final String LOG_FILE = "client_logs.txt";
    private PrintWriter writer;
    private SimpleDateFormat dateFormat;
    private String currentUser;

    public Logger() {
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        openLogFile();
    }

    private void openLogFile() {
        try {
            writer = new PrintWriter(new FileWriter(LOG_FILE, true));
            log("=".repeat(80));
            log("CLIENT LOG SESSION STARTED");
            log("=".repeat(80));
        } catch (IOException e) {
            System.err.println("❌ Failed to create log file: " + e.getMessage());
        }
    }

    public void setUser(String username) {
        this.currentUser = username;
        log("User session started: " + username);
    }

    public void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] [%s] %s",
                timestamp,
                (currentUser != null ? currentUser : "SYSTEM"),
                message);

        System.out.println("📝 " + logEntry);

        if (writer != null) {
            writer.println(logEntry);
            writer.flush();
        }
    }

    public void logAuth(String event, String details) {
        log(String.format("AUTH [%s] %s", event, details));
    }

    public void logWho(String action, int userCount) {
        log(String.format("WHO [%s] Users online: %d", action, userCount));
    }

    public void logSend(String messageId, String recipients, String subject) {
        log(String.format("SEND [ID:%s] To:%s Subject:'%s'",
                messageId, recipients, subject));
    }

    public void logRetr(String messageId, boolean success) {
        log(String.format("RETR [ID:%s] %s",
                messageId, success ? "SUCCESS" : "FAILED"));
    }

    public void logArchive(String messageId, boolean success, String action) {
        log(String.format("%s [ID:%s] %s",
                action.toUpperCase(), messageId, success ? "SUCCESS" : "FAILED"));
    }

    public void logUdp(String notification) {
        log(String.format("UDP [NOTIFY] %s", notification));
    }

    public void logError(String operation, String error) {
        log(String.format("ERROR [%s] %s", operation, error));
    }

    public void close() {
        if (writer != null) {
            log("=".repeat(80));
            log("CLIENT LOG SESSION ENDED");
            log("=".repeat(80));
            writer.close();
        }
    }
}