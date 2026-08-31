package com.ugcs.geohammer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.status.Status;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Set;
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

    private final McpTools tools;

    private final PrefSettings prefSettings;

    private final Status status;

    private final ObjectMapper mapper = new ObjectMapper();

    @Nullable
    private HttpServer server;

    @Nullable
    private StartFailure startFailure;

    @Nullable
    private ExecutorService executor;

    public McpServer(McpTools tools, PrefSettings prefSettings, Status status) {
        this.tools = tools;
        this.prefSettings = prefSettings;
        this.status = status;
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
        return prefSettings.getBooleanOrDefault(PREF_MCP, PREF_ENABLED, DEFAULT_ENABLED);
    }

    public synchronized void setEnabled(boolean enabled) {
        prefSettings.setValue(PREF_MCP, PREF_ENABLED, enabled);
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
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            // notification, no response required
            exchange.sendResponseHeaders(202, -1);
            return;
        }
        String method = message.path("method").asText(Strings.empty());
        JsonNode params = message.path("params");
        switch (method) {
            case "initialize" -> sendResult(exchange, id, initialize(params));
            case "ping" -> sendResult(exchange, id, mapper.createObjectNode());
            case "tools/list" -> {
                ObjectNode result = mapper.createObjectNode();
                result.set("tools", tools.listTools());
                sendResult(exchange, id, result);
            }
            case "tools/call" -> sendResult(exchange, id, tools.callTool(params));
            default -> sendError(exchange, id, -32601, "Method not found: " + method);
        }
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
                + "Data modifications are visible in the UI immediately, support undo (see the undo "
                + "tool) and are NEVER written to the files on disk by these tools. "
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

    private void sendResult(HttpExchange exchange, JsonNode id, ObjectNode result) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        sendJson(exchange, response);
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
