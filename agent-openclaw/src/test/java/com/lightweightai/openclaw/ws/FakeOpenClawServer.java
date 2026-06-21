package com.lightweightai.openclaw.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 忠实假 OpenClaw 对端：真 RFC-6455 WebSocket 服务端 + OpenClaw chat 协议行为（ADR-014 阶段3）。
 * 仿 agent-mcp 的 {@code MiniWebSocketServer}（守 CLAUDE.md 集成接缝规约：假网络对端、不 mock 自己层）。
 *
 * <p>行为：完成 Upgrade 握手；收到 {@code chat.send} → 推若干 {@code chat:delta} + 可选 {@code chat:final}；
 * 收到 {@code chat.abort} → 推 {@code chat:aborted}。{@code connect} 忽略（简化，真网关有 challenge）。
 * 所有收到的帧记入 {@link #received} 供断言。
 */
final class FakeOpenClawServer implements AutoCloseable {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final ObjectMapper M = new ObjectMapper();

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private volatile OutputStream out;
    private volatile Socket clientSocket;

    final CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
    private volatile List<String> tokens = List.of();
    private volatile boolean autoFinal = true;
    private volatile String stopReason = "end_turn";

    FakeOpenClawServer() throws IOException {
        this.serverSocket = new ServerSocket(0);
        this.acceptThread = new Thread(this::run, "fake-openclaw");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    /** chat.send 时回的 token 序列。 */
    FakeOpenClawServer reply(String... tokens) {
        this.tokens = List.of(tokens);
        return this;
    }

    /** 是否在 token 后自动收 final（false → 流保持在途，用于 barge-in 测试）。 */
    FakeOpenClawServer autoFinal(boolean v) {
        this.autoFinal = v;
        return this;
    }

    String url() {
        return "ws://localhost:" + serverSocket.getLocalPort() + "/gateway";
    }

    boolean received(String methodOrSubstring) {
        return received.stream().anyMatch(f -> f.contains(methodOrSubstring));
    }

    // ── OpenClaw 协议行为 ──

    private void onMessage(String raw) {
        if (raw.contains("\"method\":\"chat.send\"")) {
            for (String t : tokens) {
                sendQuietly(chatEvent("delta", n -> n.put("deltaText", t)));
            }
            if (autoFinal) {
                sendQuietly(chatEvent("final", n -> n.put("stopReason", stopReason)));
            }
        } else if (raw.contains("\"method\":\"chat.abort\"")) {
            sendQuietly(chatEvent("aborted", n -> { }));
        }
        // connect / 其它：忽略
    }

    private static String chatEvent(String state, java.util.function.Consumer<ObjectNode> fill) {
        ObjectNode root = M.createObjectNode();
        root.put("type", "event").put("event", "chat");
        ObjectNode pl = root.putObject("payload");
        pl.put("state", state);
        fill.accept(pl);
        try {
            return M.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── RFC-6455 framing（同 MiniWebSocketServer） ──

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
                    break;
                }
                received.add(msg);
                onMessage(msg);
            }
        } catch (IOException e) {
            // socket closed / test ended
        }
    }

    private void handshake(InputStream in, OutputStream o) throws IOException {
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
        o.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        o.flush();
    }

    private static String base64Sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
                return null;   // close
            }
            if (opcode == 0x9 || opcode == 0xA) {
                continue;      // ping/pong
            }
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    private synchronized void sendText(String text) throws IOException {
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

    private void sendQuietly(String text) {
        try {
            sendText(text);
        } catch (IOException e) {
            // client gone; ignore
        }
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
