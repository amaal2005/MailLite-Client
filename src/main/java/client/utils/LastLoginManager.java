package client.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class LastLoginManager {
    private static final String LAST_LOGIN_FILE = "last_login.properties";
    private Properties properties;
    private SimpleDateFormat dateFormat;

    public LastLoginManager() {
        this.properties = new Properties();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        loadLastLoginInfo();
    }

    private void loadLastLoginInfo() {
        File file = new File(LAST_LOGIN_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Failed to load last login info: " + e.getMessage());
            }
        }
    }

    public void saveLastLogin(String username) {
        String timestamp = dateFormat.format(new Date());
        properties.setProperty(username, timestamp);

        try (FileOutputStream fos = new FileOutputStream(LAST_LOGIN_FILE)) {
            properties.store(fos, "Last login timestamps");
        } catch (IOException e) {
            System.err.println("Failed to save last login: " + e.getMessage());
        }
    }

    public String getLastLogin(String username) {
        return properties.getProperty(username);
    }
}