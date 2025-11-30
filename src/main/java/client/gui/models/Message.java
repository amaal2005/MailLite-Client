package client.gui.models;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String from;
    private String to;
    private String subject;
    private String body;
    private long timestamp;
    private boolean isRead;
    private boolean isArchived;

    public Message() {}

    public Message(String id, String from, String to, String subject, String body, long timestamp) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.timestamp = timestamp;
        this.isRead = false;
        this.isArchived = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    @Override
    public String toString() {
        return String.format("Message{id='%s', from='%s', subject='%s'}", id, from, subject);
    }
}