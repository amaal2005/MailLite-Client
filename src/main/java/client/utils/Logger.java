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

    public Logger() {
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            writer = new PrintWriter(new FileWriter(LOG_FILE, true));
        } catch (IOException e) {
            System.err.println("❌ Failed to create log file: " + e.getMessage());
        }
    }

    public void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message;

        System.out.println("📝 " + logEntry);

        if (writer != null) {
            writer.println(logEntry);
            writer.flush();
        }
    }

    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
}