package com.da.demo.keycloak.captcha;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight, zero-dependency Valkey / Redis RESP client for Keycloak SPI.
 * Connects directly to Valkey cluster with aggressive timeouts to ensure
 * zero latency impact on authentication flows.
 */
public class ValkeyClient {

    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ValkeyClient() {
        this(
            System.getenv().getOrDefault("VALKEY_HOST", System.getProperty("valkey.host", "localhost")),
            Integer.parseInt(System.getenv().getOrDefault("VALKEY_PORT", System.getProperty("valkey.port", "6379"))),
            1500
        );
    }

    public ValkeyClient(String host, int port, int timeoutMillis) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Atomically attempts to set a key with EX (seconds) and NX (only if not exists).
     * Used for distributed single-use CAPTCHA replay protection.
     * 
     * @return true if key was set (first use), false if key already existed or Valkey is down.
     */
    public boolean setNxEx(String key, String value, int seconds) {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String cmd = buildRespCommand("SET", key, value, "EX", String.valueOf(seconds), "NX");
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readLine(in);
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sets a key with TTL in seconds.
     */
    public boolean setEx(String key, int seconds, String value) {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String cmd = buildRespCommand("SETEX", key, String.valueOf(seconds), value);
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readLine(in);
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets value of a key.
     */
    public String get(String key) {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String cmd = buildRespCommand("GET", key);
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String header = readLine(in);
            if (header == null || header.startsWith("$-1")) {
                return null;
            }
            if (header.startsWith("$")) {
                int length = Integer.parseInt(header.substring(1));
                byte[] data = new byte[length];
                int read = 0;
                while (read < length) {
                    int r = in.read(data, read, length - read);
                    if (r == -1) break;
                    read += r;
                }
                readLine(in); // Consume trailing \r\n
                return new String(data, 0, read, StandardCharsets.UTF_8);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deletes a key.
     */
    public boolean del(String key) {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String cmd = buildRespCommand("DEL", key);
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readLine(in);
            return response != null && response.startsWith(":");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if Valkey is reachable.
     */
    public boolean ping() {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String cmd = buildRespCommand("PING");
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readLine(in);
            return response != null && response.startsWith("+PONG");
        } catch (Exception e) {
            return false;
        }
    }

    private Socket openSocket() throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        return socket;
    }

    private String buildRespCommand(String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(args.length).append("\r\n");
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            sb.append("$").append(bytes.length).append("\r\n");
            sb.append(arg).append("\r\n");
        }
        return sb.toString();
    }

    private String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                int next = in.read(); // Read '\n'
                break;
            }
            sb.append((char) c);
        }
        return sb.length() > 0 || c != -1 ? sb.toString() : null;
    }
}
