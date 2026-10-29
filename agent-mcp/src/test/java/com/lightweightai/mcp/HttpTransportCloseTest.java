package com.lightweightai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/1.1 sockets: assert peer-observed EOF, not just Reactor cancellation. */
@Timeout(15)
class HttpTransportCloseTest {
    @ParameterizedTest
    @EnumSource(Construction.class)
    void closesGetBeforeResponseHeadersArrive(Construction construction) throws Exception {
        try (Peer peer = new Peer(true, true, DeleteMode.REPLY);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            await(peer.getReceived);
            client.close();
            await(peer.getClosed); // Peer never sends headers or a first heartbeat.
            await(peer.deleteReceived);
            peer.awaitCleanup();
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void closesStatelessGetWithoutSendingDelete(Construction construction) throws Exception {
        try (Peer peer = new Peer(false, true, DeleteMode.REPLY);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            await(peer.getReceived);
            client.close();
            await(peer.getClosed);
            assertEquals(0, peer.deletes.get());
            peer.awaitCleanup();
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void closesToolPostBeforeResponseHeadersArrive(Construction construction) throws Exception {
        try (Peer peer = new Peer(true, false, DeleteMode.REPLY);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            CompletableFuture<Throwable> failed = new CompletableFuture<>();
            client.callToolReactive("slow", Map.of(), "close-test")
                .subscribe(result -> fail("The peer never returns a tool result"), failed::complete);
            await(peer.callReceived);
            client.close();
            await(peer.callClosed);
            assertNotNull(failed.get(2, TimeUnit.SECONDS));
            peer.awaitCleanup();
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void closesStreamsImmediatelyAndBoundsAnUnansweredDelete(Construction construction) throws Exception {
        try (Peer peer = new Peer(true, false, DeleteMode.WAIT);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            await(peer.getReceived);
            client.close();
            await(peer.deleteReceived);
            await(peer.getClosed); // Active streams are cancelled before DELETE cleanup.
            peer.deleteClosed.get(2, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void failedDeleteDoesNotLeaveStreamsOpen(Construction construction) throws Exception {
        try (Peer peer = new Peer(true, false, DeleteMode.FAIL);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            await(peer.getReceived);
            client.close();
            await(peer.deleteReceived);
            await(peer.getClosed);
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void concurrentCloseSendsOnlyOneDeleteAndRejectsNewRequests(Construction construction) throws Exception {
        try (Peer peer = new Peer(true, true, DeleteMode.REPLY);
             McpToolClient client = peer.client(McpHttpClients.shared(), construction)) {
            await(peer.getReceived);
            List<CompletableFuture<Void>> closes = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                closes.add(CompletableFuture.runAsync(client::close));
            }
            CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
            peer.transport.closeGracefully().block(Duration.ofSeconds(2));
            await(peer.getClosed);
            assertEquals(1, peer.deletes.get());
            assertThrows(IllegalStateException.class,
                () -> peer.transport.connect(message -> message).block(Duration.ofSeconds(1)));
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void closingOneTransportDoesNotCloseAnotherOrTheSharedClient(Construction construction) throws Exception {
        try (HttpClient shared = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
             Peer first = new Peer(true, true, DeleteMode.REPLY);
             Peer second = new Peer(true, true, DeleteMode.REPLY);
             McpToolClient a = first.client(shared, construction);
             McpToolClient b = second.client(shared, construction)) {
            await(first.getReceived);
            await(second.getReceived);
            a.close();
            await(first.getClosed);
            first.awaitCleanup();
            assertFalse(second.getClosed.isDone(), "Other transport's GET must remain open");
            assertEquals(0, b.discoverMcpTools().size(), "Shared client must still send requests");
            assertFalse(shared.isTerminated());
            b.close();
            await(second.getClosed);
            second.awaitCleanup();
        }
    }

    @ParameterizedTest
    @EnumSource(Construction.class)
    void legacySseAlsoCancelsConnectBeforeHeadersArrive(Construction construction) throws Exception {
        try (Peer peer = new Peer(false, true, DeleteMode.REPLY)) {
            McpClientTransport transport;
            if (construction == Construction.SDK_BUILDER) {
                transport = HttpClientSseClientTransport.builder(peer.baseUrl())
                    .clientBuilder(McpHttpClients.sharing(McpHttpClients.shared())).build();
            } else {
                McpConfiguration.ServerConfig config = new McpConfiguration.ServerConfig();
                config.setTransport("sse");
                config.setUrl(peer.baseUrl());
                transport = ToolClient.Builder.createTransport("legacy", config, null);
            }
            try (McpToolClient client = McpToolClient.builder().transport(transport).build()) {
                peer.transport = effectiveTransport(client);
                peer.transport.connect(message -> message).subscribe(null, ignored -> { });
                await(peer.getReceived);
                client.close();
                await(peer.getClosed);
                peer.awaitCleanup();
            }
        }
    }

    @Test
    void callerBuiltTransportWithDefaultHttpClientNeedsNoFactory() throws Exception {
        try (Peer peer = new Peer(true, true, DeleteMode.REPLY)) {
            var transport = HttpClientStreamableHttpTransport.builder(peer.baseUrl()).endpoint("/mcp").build();
            try (McpToolClient client = McpToolClient.builder().transport(transport).build().initialize()) {
                peer.transport = effectiveTransport(client);
                await(peer.getReceived);
                client.close();
                await(peer.getClosed);
                await(peer.deleteReceived);
                peer.awaitCleanup();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Registration.class)
    void toolClientAlsoManagesCallerTransportAndPreservesSettings(Registration registration) throws Exception {
        try (Peer peer = new Peer(true, true, DeleteMode.REPLY)) {
            var transport = HttpClientStreamableHttpTransport.builder(peer.baseUrl())
                .endpoint("/custom/mcp")
                .clientBuilder(McpHttpClients.sharing(McpHttpClients.shared()))
                .customizeRequest(request -> request.header("Authorization", "Bearer close-test"))
                .build();
            ToolClient.Builder builder = ToolClient.create();
            if (registration == Registration.BUILDER) {
                builder.addMcpServer("custom", transport);
            }
            try (ToolClient client = builder.build()) {
                if (registration == Registration.RUNTIME) {
                    client.addMcpServer("custom", transport);
                }
                peer.transport = effectiveTransport(client.getMcpClients().getFirst());
                assertSame(McpHttpClients.shared(),
                    ((ManagedHttpClientTransport) peer.transport).sharedHttpClient());
                await(peer.getReceived);
                client.close();
                await(peer.getClosed);
                await(peer.deleteReceived);
                peer.awaitCleanup();
                assertTrue(peer.requests.size() >= 4);
                for (Request request : peer.requests) {
                    assertEquals("/custom/mcp", request.line().split(" ")[1]);
                    assertEquals("Bearer close-test", request.headers().get("authorization"));
                }
            }
        }
    }

    private static McpClientTransport effectiveTransport(McpToolClient client) throws Exception {
        Field field = McpToolClient.class.getDeclaredField("transport");
        field.setAccessible(true);
        return (McpClientTransport) field.get(client);
    }

    private static void await(CompletableFuture<Void> event) throws Exception {
        event.get(2, TimeUnit.SECONDS);
    }

    private enum Construction { FACTORY, SDK_BUILDER }

    private enum Registration { BUILDER, RUNTIME }

    private enum DeleteMode { REPLY, WAIT, FAIL }

    private record Request(String line, Map<String, String> headers) { }

    /** A minimal MCP peer with explicit control over response headers and DELETE. */
    private static final class Peer implements AutoCloseable {
        private final ObjectMapper json = new ObjectMapper();
        private final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "mcp-close-test-peer");
            thread.setDaemon(true);
            return thread;
        });
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final boolean stateful;
        private final boolean holdGetHeaders;
        private final DeleteMode deleteMode;
        private final CompletableFuture<Void> getReceived = new CompletableFuture<>();
        private final CompletableFuture<Void> getClosed = new CompletableFuture<>();
        private final CompletableFuture<Void> callReceived = new CompletableFuture<>();
        private final CompletableFuture<Void> callClosed = new CompletableFuture<>();
        private final CompletableFuture<Void> deleteReceived = new CompletableFuture<>();
        private final CompletableFuture<Void> deleteClosed = new CompletableFuture<>();
        private final AtomicInteger deletes = new AtomicInteger();
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private McpClientTransport transport;

        private Peer(boolean stateful, boolean holdGetHeaders, DeleteMode deleteMode) throws IOException {
            this.stateful = stateful;
            this.holdGetHeaders = holdGetHeaders;
            this.deleteMode = deleteMode;
            workers.execute(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        workers.execute(() -> serve(socket));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + listener.getLocalPort();
        }

        private McpToolClient client(HttpClient shared, Construction construction) throws Exception {
            transport = construction == Construction.SDK_BUILDER
                ? HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp")
                    .clientBuilder(McpHttpClients.sharing(shared)).build()
                : ToolClient.Builder.createTransport("close-test",
                    McpConfiguration.ServerConfig.streamableHttp(baseUrl() + "/mcp"), null, shared);
            McpToolClient client = McpToolClient.builder().transport(transport)
                .requestTimeout(Duration.ofSeconds(3)).build();
            transport = effectiveTransport(client);
            try {
                return client.initialize();
            } catch (RuntimeException e) {
                client.close();
                throw e;
            }
        }

        private void awaitCleanup() {
            transport.closeGracefully().block(Duration.ofSeconds(2));
        }

        private void serve(Socket socket) {
            try (socket; InputStream input = new BufferedInputStream(socket.getInputStream())) {
                String request;
                while ((request = line(input)) != null) {
                    int length = 0;
                    String header;
                    Map<String, String> headers = new HashMap<>();
                    while ((header = line(input)) != null && !header.isEmpty()) {
                        int colon = header.indexOf(':');
                        headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT),
                            header.substring(colon + 1).trim());
                        if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                            length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
                        }
                    }
                    byte[] body = input.readNBytes(length);
                    requests.add(new Request(request, Map.copyOf(headers)));
                    if (request.startsWith("GET ")) {
                        if (!holdGetHeaders) {
                            write(socket, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
                                + "Transfer-Encoding: chunked\r\n\r\n9\r\n: ready\n\n\r\n");
                        }
                        getReceived.complete(null);
                        observeClose(input, getClosed);
                        return;
                    }
                    if (request.startsWith("DELETE ")) {
                        deletes.incrementAndGet();
                        deleteReceived.complete(null);
                        if (deleteMode == DeleteMode.FAIL) {
                            return;
                        }
                        if (deleteMode == DeleteMode.WAIT) {
                            observeClose(input, deleteClosed);
                            return;
                        }
                        write(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                        continue;
                    }
                    JsonNode message = json.readTree(body);
                    String method = message.path("method").asText();
                    if ("tools/call".equals(method)) {
                        callReceived.complete(null);
                        observeClose(input, callClosed);
                        return;
                    }
                    if (!message.has("id")) {
                        write(socket, "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n");
                        continue;
                    }
                    Object result = "initialize".equals(method)
                        ? Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of("tools", Map.of()),
                            "serverInfo", Map.of("name", "close-test", "version", "1"))
                        : Map.of("tools", List.of());
                    byte[] response = json.writeValueAsBytes(Map.of("jsonrpc", "2.0",
                        "id", message.get("id"), "result", result));
                    write(socket, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + (stateful ? "Mcp-Session-Id: close-test\r\n" : "")
                        + "Content-Length: " + response.length + "\r\n\r\n");
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                }
            } catch (IOException ignored) {
                // Closing the fixture or a cancelled pooled connection is expected.
            } finally {
                sockets.remove(socket);
            }
        }

        private static void observeClose(InputStream input, CompletableFuture<Void> closed) {
            try {
                if (input.read() == -1) {
                    closed.complete(null);
                } else {
                    closed.completeExceptionally(new AssertionError("Expected EOF on active HTTP stream"));
                }
            } catch (IOException e) {
                closed.complete(null); // A TCP reset also proves cancellation reached the peer.
            }
        }

        private static void write(Socket socket, String value) throws IOException {
            OutputStream output = socket.getOutputStream();
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        private static String line(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int c;
            while ((c = input.read()) != -1 && c != '\n') {
                if (c != '\r') bytes.write(c);
            }
            return c == -1 && bytes.size() == 0 ? null : bytes.toString(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            listener.close();
            for (Socket socket : sockets) socket.close();
            workers.shutdownNow();
        }
    }
}
