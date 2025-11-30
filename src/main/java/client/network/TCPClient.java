package client.network;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TCPClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String host;
    private int port;

    public TCPClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(30000);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("✅ Connected to server " + host + ":" + port);
            return true;

        } catch (IOException e) {
            System.err.println("❌ Connection failed to " + host + ":" + port + " - " + e.getMessage());
            return false;
        }
    }

    public String sendCommand(String command) {
        try {
            System.out.println("📤 SENDING: " + command);
            writer.println(command);
            writer.flush();

            String response = reader.readLine();
            System.out.println("📥 RECEIVED: " + response);
            return response;

        } catch (IOException e) {
            System.err.println("❌ Command failed: " + command + " - " + e.getMessage());
            return null;
        }
    }

    public List<String> sendMultiLineCommand(String command) {
        List<String> responses = new ArrayList<>();
        try {
            System.out.println("📤 SENDING MULTI: " + command);
            writer.println(command);
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("📥 MULTI LINE: " + line);
                responses.add(line);

                if (line.endsWith("END") ||
                        line.startsWith("250") ||
                        line.startsWith("550") ||
                        line.startsWith("211") ||
                        line.startsWith("212 END")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Multi-line command failed: " + command + " - " + e.getMessage());
        }
        return responses;
    }

    public BufferedReader getReader() { return reader; }
    public PrintWriter getWriter() { return writer; }
    public boolean isConnected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
            System.out.println("🔌 TCP connection closed");
        } catch (IOException e) {
            System.err.println("❌ Error closing connection: " + e.getMessage());
        }
    }
}