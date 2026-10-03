package core.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import core.util.HOLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP server over Streamable HTTP, single endpoint {@code POST /mcp}, answers as
 * {@code application/json}.
 * <p>
 * No SSE: nothing here pushes server initiated messages, so the event stream half of the transport
 * would be dead weight. {@code GET /mcp} answers 405.
 * <p>
 * The whole security perimeter is three rules, and none of them is optional: bind loopback only,
 * reject any request that carries an {@code Origin} header, and reject any request whose
 * {@code Host} is not a loopback name. Real MCP clients send no Origin and browsers always do; a
 * DNS rebound name resolves to 127.0.0.1 but still arrives with the attacker's name in Host.
 * Together they are the rebinding guard the MCP specification asks of a local server. Without them
 * any page open in the user's browser could read the whole squad.
 * <p>
 * There is no authentication beyond that: any process running as this user can call every tool.
 * The switch being off by default is what bounds that.
 */
public final class McpHttpServer {

    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    /**
     * Nulls are serialized on purpose. Gson drops them by default, which silently discarded fields
     * this code explicitly writes as null - {@code details} on an expired market hit, {@code
     * salePrice} on a player who has not sold. A model then cannot tell "this player did not sell"
     * from "this build has no such field", and reading an absent key as a missing feature has
     * already caused a wrong conclusion downstream. It also makes {@code "id": null} appear on a
     * parse-error response, which is what JSON-RPC 2.0 requires.
     */
    static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static McpHttpServer instance;

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public static synchronized McpHttpServer instance() {
        if (instance == null) {
            instance = new McpHttpServer();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests build their own. */
    McpHttpServer() {
    }

    public void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public void registerAll(List<McpTool> toolList) {
        toolList.forEach(this::register);
    }

    /** Register the whole tool surface. Idempotent - the map is keyed on the tool name. */
    public void registerAllTools() {
        registerAll(TeamTools.tools());
        registerAll(TransferTools.tools());
        registerAll(LineupTools.tools());
        registerAll(TrainingTools.tools());
        registerAll(MatchTools.tools());
        registerAll(BackupTools.tools());
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int port() {
        return server == null ? 0 : server.getAddress().getPort();
    }

    /**
     * Bind the endpoint.
     *
     * @param port the TCP port, 0 to let the OS pick one (tests)
     * @throws IOException if the port is already in use - the caller reports that, it is not swallowed
     */
    public synchronized void start(int port) throws IOException {
        if (server != null) {
            return;
        }
        // Loopback only - except in the Docker image (-Dho.mcpBind=0.0.0.0), where the host cannot
        // reach the container's loopback; `docker run -p 127.0.0.1:...` keeps it host-local there.
        // The Host guard in serve() applies either way.
        final var bind = System.getProperty("ho.mcpBind");
        final var address = bind == null ? InetAddress.getLoopbackAddress() : InetAddress.getByName(bind);
        server = HttpServer.create(new InetSocketAddress(address, port), 0);
        // Single threaded on purpose. Handlers reach straight into DBManager, and HO's
        // ConnectionManager shares one JDBC Statement and one StatementCache across every table, so
        // two tool calls in parallel would interleave on it. A slow CHPP call therefore blocks the
        // next request - the cheaper of the two problems.
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/mcp", this::serve);
        server.start();
        HOLogger.instance().info(getClass(), "MCP server listening on http://" + address.getHostAddress() + ":" + port() + "/mcp");
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        executor.shutdownNow();
        server = null;
        executor = null;
        HOLogger.instance().info(getClass(), "MCP server stopped");
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (exchange.getRequestHeaders().getFirst("Origin") != null) {
                respond(exchange, 403, "{\"error\":\"Origin header not accepted\"}");
                return;
            }
            if (!isLoopbackHost(exchange.getRequestHeaders().getFirst("Host"))) {
                respond(exchange, 403, "{\"error\":\"Host not accepted\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"POST only\"}");
                return;
            }

            final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final JsonObject request;
            try {
                request = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                respond(exchange, 200, GSON.toJson(error(null, -32700, "Parse error")));
                return;
            }

            final var response = handle(request);
            if (response == null) {
                respond(exchange, 202, "");          // a notification gets no body
                return;
            }
            respond(exchange, 200, GSON.toJson(response));
        } catch (Exception e) {
            HOLogger.instance().error(getClass(), e);
            try {
                respond(exchange, 200, GSON.toJson(error(null, -32603, "Internal error")));
            } catch (Exception alreadyAnswered) {
                exchange.close();     // headers were already sent, there is nothing left to say
            }
        }
    }

    /**
     * The second half of the DNS rebinding guard: a rebound name resolves to 127.0.0.1, but the
     * browser still puts the attacker's name in {@code Host}.
     */
    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        final var colon = host.lastIndexOf(':');
        final var name = colon > host.lastIndexOf(']') ? host.substring(0, colon) : host;
        return name.equals("127.0.0.1") || name.equals("localhost") || name.equals("[::1]");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        // -1, not 0: com.sun.net.httpserver reads a length of 0 as "chunked, length unknown".
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (final var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Answer one JSON-RPC request.
     *
     * @return the response object, or null for a notification (a request without an id)
     */
    JsonObject handle(JsonObject request) {
        final var id = request.get("id");
        if (id == null) {
            return null;                     // notifications/initialized and friends
        }
        final var method = request.has("method") ? request.get("method").getAsString() : "";
        final var params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();

        return switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "tools/list" -> result(id, toolsList());
            case "tools/call" -> toolsCall(id, params);
            default -> error(id, -32601, "Unknown method: " + method);
        };
    }

    private JsonObject initialize(JsonObject params) {
        // Echo the client's protocol version so that a client of any generation is satisfied - this
        // server behaves the same under all of them, it speaks plain request/response JSON.
        final var version = params.has("protocolVersion")
                ? params.get("protocolVersion").getAsString()
                : DEFAULT_PROTOCOL_VERSION;

        final var capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());

        final var serverInfo = new JsonObject();
        serverInfo.addProperty("name", "ho");
        serverInfo.addProperty("version", String.valueOf(core.HO.VERSION));

        final var result = new JsonObject();
        result.addProperty("protocolVersion", version);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject toolsList() {
        final var list = new JsonArray();
        for (final var tool : tools.values()) {
            final var entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            list.add(entry);
        }
        final var result = new JsonObject();
        result.add("tools", list);
        return result;
    }

    private JsonObject toolsCall(com.google.gson.JsonElement id, JsonObject params) {
        final var name = params.has("name") ? params.get("name").getAsString() : "";
        final var tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        final var arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();
        try {
            return result(id, toolResult(GSON.toJson(tool.handler().call(arguments)), false));
        } catch (McpToolException e) {
            return result(id, toolResult(e.getMessage(), true));
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            HOLogger.instance().error(getClass(), e);      // no stack trace crosses the wire
            return error(id, -32603, "Internal error in tool " + name);
        }
    }

    private static JsonObject toolResult(String text, boolean isError) {
        final var content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        final var contents = new JsonArray();
        contents.add(content);
        final var result = new JsonObject();
        result.add("content", contents);
        result.addProperty("isError", isError);
        return result;
    }

    private static JsonObject result(com.google.gson.JsonElement id, JsonObject result) {
        final var response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private static JsonObject error(com.google.gson.JsonElement id, int code, String message) {
        final var error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        final var response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", error);
        return response;
    }
}
