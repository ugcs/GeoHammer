package com.ugcs.geohammer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.status.Status;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Serves the Model Context Protocol over streamable HTTP (JSON-RPC 2.0 on POST /mcp).
// Connect from Claude Code with:
// claude mcp add --transport http geohammer http://127.0.0.1:41693/mcp
@Service
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private static final String LATEST_PROTOCOL_VERSION = "2025-06-18";

    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2024-11-05", "2025-03-26", LATEST_PROTOCOL_VERSION);

    private static final boolean DEFAULT_ENABLED = false;

    private static final int DEFAULT_PORT = 41693;

    private static final String PREF_MCP = "mcp";

    private static final String PREF_ENABLED = "enabled";

    private static final String SESSION_ID_HEADER = "Mcp-Session-Id";

    private static final Duration SESSION_IDLE_TIMEOUT = Duration.ofHours(1);

    private final McpTools tools;

    private final Settings settings;

    private final Status status;

    private final UndoModel undoModel;

    private final EventSender eventSender;

    private final EventsFactory eventsFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    @Nullable
    private HttpServer server;

    @Nullable
    private StartFailure startFailure;

    @Nullable
    private ExecutorService executor;

    public McpServer(McpTools tools, Settings settings, Status status, UndoModel undoModel,
                     EventSender eventSender, EventsFactory eventsFactory) {
        this.tools = tools;
        this.settings = settings;
        this.status = status;
        this.undoModel = undoModel;
        this.eventSender = eventSender;
        this.eventsFactory = eventsFactory;
    }

    private InetSocketAddress getServerAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), DEFAULT_PORT);
    }

    public McpIdentity getIdentity() {
        InetSocketAddress serverAddress = getServerAddress();
        String serverUrl = String.format("http://%s:%d/mcp",
                serverAddress.getHostName(),
                serverAddress.getPort());
        return new McpIdentity("geohammer", serverUrl);
    }

    public boolean isEnabled() {
        return settings.getBooleanOrDefault(PREF_MCP, PREF_ENABLED, DEFAULT_ENABLED);
    }

    public synchronized void setEnabled(boolean enabled) {
        settings.setValue(PREF_MCP, PREF_ENABLED, enabled);
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public @Nullable StartFailure getStartFailure() {
        return startFailure;
    }

    @PostConstruct
    public synchronized void startIfEnabled() {
        if (isEnabled()) {
            start();
        }
    }

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        startFailure = null;
        InetSocketAddress serverAddress = getServerAddress();
        try {
            server = HttpServer.create(serverAddress, 0);
            Check.notNull(server);
        } catch (IOException e) {
            log.error("Failed to start MCP server at " + serverAddress, e);
            startFailure = new StartFailure(serverAddress, e);
            status.showMessage("MCP server was not started: " + startFailure.getMessage(), "MCP");
            return;
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/mcp", this::handle);
        server.start();
        status.showMessage("MCP server started at " + serverAddress, "MCP");
    }

    @PreDestroy
    public synchronized void stop() {
        startFailure = null;
        if (server != null) {
            server.stop(0);
            server = null;
            status.showMessage("MCP server stopped", "MCP");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sessions.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!isAllowedOrigin(exchange)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            JsonNode message;
            try {
                message = mapper.readTree(exchange.getRequestBody());
            } catch (IOException e) {
                sendError(exchange, null, -32700, "Parse error");
                return;
            }
            handleMessage(exchange, message);
        } catch (Exception e) {
            log.error("MCP request failed", e);
        }
    }

    private void handleMessage(HttpExchange exchange, JsonNode message) throws IOException {
        if (!message.isObject()) {
            sendError(exchange, null, -32600, "Batch requests are not supported");
            return;
        }
        String method = message.path("method").asText(Strings.empty());
        JsonNode params = message.path("params");
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            // notification, no response required
            if ("notifications/cancelled".equals(method)) {
                cancelCall(exchange, params);
            }
            exchange.sendResponseHeaders(202, -1);
            return;
        }
        switch (method) {
            case "initialize" -> {
                McpSession session = createSession();
                exchange.getResponseHeaders().set(SESSION_ID_HEADER, session.getId());
                sendResult(exchange, id, initialize(params));
                JsonNode clientInfo = params.path("clientInfo");
                eventSender.send(eventsFactory.createMcpSessionInitEvent(
                        clientInfo.path("name").asText(Strings.empty()),
                        clientInfo.path("version").asText(Strings.empty())));
            }
            case "ping" -> sendResult(exchange, id, mapper.createObjectNode());
            case "tools/list" -> {
                ObjectNode result = mapper.createObjectNode();
                result.set("tools", tools.listTools());
                sendResult(exchange, id, result);
            }
            case "tools/call" -> callTool(exchange, id, params);
            default -> sendError(exchange, id, -32601, "Method not found: " + method);
        }
    }

    private void callTool(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        McpSession session = getSession(exchange.getRequestHeaders().getFirst(SESSION_ID_HEADER));
        ToolCallResponse response = new ToolCallResponse(exchange, id);
        JsonNode progressToken = getProgressToken(exchange, params);
        McpCall.ProgressListener progressListener = progressToken != null
                ? (progress, message) -> response.sendProgress(progressToken, progress, message)
                : null;
        McpCall call = new McpCall(progressListener);
        String requestId = id.toString();
        session.startCall(requestId, call);
        ObjectNode result;
        try {
            result = tools.callTool(session, params, call);
        } finally {
            session.finishCall(requestId);
        }
        if (call.isCancelled()) {
            response.sendCancelled();
        } else {
            response.sendResult(result);
        }
    }

    // progress notifications are sent in an event stream,
    // so only to a client that asked for them and accepts the stream
    private static @Nullable JsonNode getProgressToken(HttpExchange exchange, JsonNode params) {
        JsonNode token = params.path("_meta").path("progressToken");
        if (!token.isTextual() && !token.isIntegralNumber()) {
            return null;
        }
        // the accepted types may come in one header or in several
        for (String accept : Nulls.toEmpty(exchange.getRequestHeaders().get("Accept"))) {
            if (accept.contains("text/event-stream")) {
                return token;
            }
        }
        return null;
    }

    private void cancelCall(HttpExchange exchange, JsonNode params) {
        String sessionId = exchange.getRequestHeaders().getFirst(SESSION_ID_HEADER);
        McpSession session = sessionId != null ? sessions.get(sessionId) : null;
        JsonNode requestId = params.get("requestId");
        if (session != null && requestId != null) {
            session.cancelCall(requestId.toString());
        }
    }

    private McpSession createSession() {
        McpSession session = new McpSession(UUID.randomUUID().toString(), Instant.now());
        sessions.put(session.getId(), session);
        return session;
    }

    // an unknown id is adopted as a new session instead of rejecting the request,
    // so that clients keep working after the session expired or the server restarted
    private McpSession getSession(@Nullable String id) {
        Instant now = Instant.now();
        sessions.values().removeIf(session -> session.isIdle(now, SESSION_IDLE_TIMEOUT));
        if (Strings.isNullOrEmpty(id)) {
            // client without session support: the session lasts for a single call
            return new McpSession(UUID.randomUUID().toString(), now);
        }
        McpSession session = sessions.computeIfAbsent(id, key -> new McpSession(key, now));
        session.touch(now);
        return session;
    }

    private ObjectNode initialize(JsonNode params) {
        String requestedVersion = params.path("protocolVersion").asText(LATEST_PROTOCOL_VERSION);
        String protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
                ? requestedVersion
                : LATEST_PROTOCOL_VERSION;

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", protocolVersion);
        result.putObject("capabilities").putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "geohammer");
        serverInfo.put("title", "UgCS GeoHammer");
        serverInfo.put("version", "0.1.0");
        result.put("instructions", "GeoHammer is a desktop application for geophysical survey data "
                + "processing, running on the user's machine. Files are opened and saved by the user "
                + "in the application UI; these tools operate on the in-memory data of open files. "
                + "Every tool that works on a file takes its name or path in the file argument: "
                + "get it from list_files first and never assume which file the user is looking at. "
                + "Data modifications are visible in the UI immediately and are NEVER written to the "
                + "files on disk by these tools. "
                + "Other clients may work on the same files: a modification is rejected with a "
                + "\"modified\" error if the file was changed by another client or in the app since "
                + "you last read it; read the file again and redo the change on the current data. "
                + "The undo tool reverts only your own modifications, newest first, and is rejected "
                + "when a later modification of another client or in the app follows yours. "
                + "Two kinds of files exist (see list_files): \"data\" files (CSV, SVLOG sonar, NMEA) "
                + "hold a sequence of points; each point has values in named columns called series "
                + "(e.g. magnetic field, depth, latitude); points belong to survey lines (list_lines). "
                + "\"gpr\" files (ground penetrating radar: SGY, DZT) hold a sequence of traces; each "
                + "trace is a column of amplitude samples along the time/depth axis. Point and trace "
                + "indices are 0-based positions in file order. Marks (flags) annotate notable points "
                + "and are shown on charts and the map. "
                + "Processing scripts (Python) are an opt-in feature and are NOT a part of the normal "
                + "workflow: do the work with the regular tools above. Only touch the script tools "
                + "(list_scripts, get_script, run_script, create_script) when the user explicitly asks "
                + "for scripts, either to run one (by name or by asking to pick a suitable one) or to "
                + "save an algorithm as a GeoHammer script. Never list, inspect, run or create scripts "
                + "on your own initiative, and never create a script to keep intermediate or scratch "
                + "results.");
        return result;
    }

    private ObjectNode response(JsonNode id, ObjectNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private void sendResult(HttpExchange exchange, JsonNode id, ObjectNode result) throws IOException {
        sendJson(exchange, response(id, result));
    }

    private void sendError(HttpExchange exchange, @Nullable JsonNode id, int code, String message)
            throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        } else {
            response.putNull("id");
        }
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        sendJson(exchange, response);
    }

    private void sendJson(HttpExchange exchange, JsonNode body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // reject non-local origins to prevent DNS rebinding attacks
    private static boolean isAllowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (Strings.isNullOrEmpty(origin)) {
            return true;
        }
        try {
            String host = URI.create(origin).getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        SgyFile file = event.getFile();
        if (file != null) {
            for (McpSession session : sessions.values()) {
                session.untrack(file);
                session.removeStaleUndoFrames(undoModel);
            }
        }
    }

    // a tools/call response: plain JSON, unless the tool reports progress, which turns
    // the response into an event stream of progress notifications ended by the result
    private final class ToolCallResponse {

        private final HttpExchange exchange;

        private final JsonNode id;

        @Nullable
        private OutputStream events;

        ToolCallResponse(HttpExchange exchange, JsonNode id) {
            this.exchange = exchange;
            this.id = id;
        }

        synchronized void sendProgress(JsonNode progressToken, double progress, String message)
                throws IOException {
            ObjectNode notification = mapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/progress");
            ObjectNode params = notification.putObject("params");
            params.set("progressToken", progressToken);
            params.put("progress", progress);
            params.put("message", message);
            sendEvent(notification);
        }

        synchronized void sendResult(ObjectNode result) throws IOException {
            if (events == null) {
                McpServer.this.sendResult(exchange, id, result);
            } else {
                sendEvent(response(id, result));
            }
        }

        // a cancelled request gets no response
        synchronized void sendCancelled() throws IOException {
            if (events == null) {
                exchange.sendResponseHeaders(202, -1);
            }
        }

        private void sendEvent(JsonNode message) throws IOException {
            if (events == null) {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);
                events = exchange.getResponseBody();
            }
            String event = "event: message\ndata: " + mapper.writeValueAsString(message) + "\n\n";
            events.write(event.getBytes(StandardCharsets.UTF_8));
            events.flush();
        }
    }

    public record StartFailure(InetSocketAddress address, Exception e) {

        public String getMessage() {
            if (e instanceof BindException) {
                return "Port " + address.getPort()
                        + " is already in use. Another GeoHammer instance is most likely running:"
                        + " only one instance at a time can serve MCP.";
            }
            return e.getMessage();
        }
    }
}
