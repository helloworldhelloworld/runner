package com.lightweightai.mcp.transport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 依赖无关的最小 RFC-6455 WebSocket 测试服务端（仅用于 transport 单元测试）。
 *
 * <p>完成 Upgrade 握手，解码客户端发来的（带掩码的）text 帧，向客户端发送（无掩码）text 帧。
 * 仅处理测试所需：单帧 text、close 帧；ping/pong 直接忽略。
 */
class MiniWebSocketServer implements AutoCloseable {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private volatile OutputStream out;
    private volatile Socket clientSocket;

    final CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
    private volatile Consumer<String> onMessage = msg -> {};

    MiniWebSocketServer() throws IOException {
        this.serverSocket = new ServerSocket(0);
        this.acceptThread = new Thread(this::run, "mini-ws-server");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    String url(String path) {
        return "ws://localhost:" + port() + path;
    }

    void onMessage(Consumer<String> handler) {
        this.onMessage = handler;
    }

    private void run() {
        try {
            Socket socket = serverSocket.accept();
            this.clientSocket = socket;
            InputStream in = socket.getInputStream();
            this.out = socket.getOutputStream();
            handshake(in, out);

            DataInputStream dis = new DataInputStream(in);
            while (!Thread.currentThread().isInterrupted()) {
                String msg = readTextFrame(dis);
                if (msg == null) {
                    break; // close or EOF
                }
                received.add(msg);
                onMessage.accept(msg);
            }
        } catch (IOException e) {
            // socket closed / test ended
        }
    }

    private void handshake(InputStream in, OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        String key = null;
        for (String line : sb.toString().split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                key = line.substring(idx + 1).trim();
            }
        }
        String accept = base64Sha1(key + MAGIC);
        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String base64Sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder().encodeToString(
                md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 读取一个客户端 text 帧（带掩码）；遇到 close/EOF 返回 null，ping/pong 跳过。 */
    private String readTextFrame(DataInputStream dis) throws IOException {
        while (true) {
            int b0 = dis.read();
            if (b0 == -1) {
                return null;
            }
            int opcode = b0 & 0x0F;
            int b1 = dis.readUnsignedByte();
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = dis.readUnsignedShort();
            } else if (len == 127) {
                len = dis.readLong();
            }
            byte[] mask = new byte[4];
            if (masked) {
                dis.readFully(mask);
            }
            byte[] payload = new byte[(int) len];
            dis.readFully(payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }
            if (opcode == 0x8) {
                return null; // close
            }
            if (opcode == 0x9 || opcode == 0xA) {
                continue; // ping/pong — ignore
            }
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    /** 向客户端发送一个无掩码 text 帧。 */
    synchronized void sendText(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        OutputStream o = this.out;
        if (o == null) {
            throw new IOException("client not connected yet");
        }
        o.write(0x81);
        if (payload.length < 126) {
            o.write(payload.length);
        } else if (payload.length < 65536) {
            o.write(126);
            o.write((payload.length >> 8) & 0xFF);
            o.write(payload.length & 0xFF);
        } else {
            throw new IOException("payload too large for test server");
        }
        o.write(payload);
        o.flush();
    }

    @Override
    public void close() {
        try {
            acceptThread.interrupt();
            if (clientSocket != null) {
                clientSocket.close();
            }
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
