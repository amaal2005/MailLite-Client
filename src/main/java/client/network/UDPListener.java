// client/network/UDPListener.java - تأكد من الكود كامل
package client.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class UDPListener implements Runnable {
    private DatagramSocket socket;
    private int port;
    private boolean running = false;
    private NotificationCallback callback;

    public interface NotificationCallback {
        void onNewMail(String username, int count);
    }

    public UDPListener(int port, NotificationCallback callback) {
        this.port = port;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(1000);
            running = true;
            System.out.println("✅ UDP Listener started successfully on port " + port);

            byte[] buffer = new byte[1024];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    System.out.println("📨 UDP Notification received: " + message);

                    processNotification(message);

                } catch (java.net.SocketTimeoutException e) {
                    continue;
                } catch (Exception e) {
                    if (running) {
                        System.err.println("⚠️ UDP Listener packet error: " + e.getMessage());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("❌ UDP Listener failed to start on port " + port + ": " + e.getMessage());
        } finally {
            safeClose();
        }
    }

    private void processNotification(String message) {
        System.out.println("🔍 Processing UDP notification: " + message);

        try {
            if (message.startsWith("NOTIFY NEWMAIL")) {
                String[] parts = message.split(" ");
                if (parts.length >= 4) {
                    String username = parts[2];
                    int count = Integer.parseInt(parts[3]);

                    System.out.println("🆕 New mail notification for " + username + ": " + count + " new messages");

                    if (callback != null) {
                        callback.onNewMail(username, count);
                    } else {
                        System.out.println("⚠️ UDP callback is null - notification not delivered");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error processing UDP notification: " + e.getMessage());
        }
    }

    private void safeClose() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("🔌 UDP Listener socket closed safely");
            } catch (Exception e) {
                System.err.println("⚠️ Error closing UDP socket: " + e.getMessage());
            }
        }
    }

    public void stop() {
        System.out.println("🛑 Stopping UDP Listener...");
        running = false;
        safeClose();
    }
}