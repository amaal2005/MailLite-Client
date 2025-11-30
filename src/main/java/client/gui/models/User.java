package client.gui.models;

import java.util.Date;

public class User {
    private String username;
    private String status;
    private String ip;
    private Date lastSeen;

    public User() {}

    public User(String username, String status, String ip, Date lastSeen) {
        this.username = username;
        this.status = status;
        this.ip = ip;
        this.lastSeen = lastSeen;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Date getLastSeen() { return lastSeen; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }

    public String getStatusEmoji() {
        switch (status.toUpperCase()) {
            case "ACTIVE": return "🟢";
            case "BUSY": return "🔴";
            case "AWAY": return "🟡";
            default: return "⚪";
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s", getStatusEmoji(), username);
    }
}