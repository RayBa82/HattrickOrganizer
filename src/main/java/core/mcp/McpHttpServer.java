package core.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import core.util.HOLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * MCP server over Streamable HTTP, single endpoint {@code POST /mcp}, answers as
 * {@code application/json}.
 * <p>
 * <b>Two protocol eras on one endpoint.</b> Revision {@value #LATEST_VERSION} made MCP stateless:
 * no {@code initialize} handshake, every request carries its protocol version and the client's
 * capabilities in {@code params._meta}, mirrored into the {@code MCP-Protocol-Version},
 * {@code Mcp-Method} and {@code Mcp-Name} headers, and {@code server/discover} names what the
 * server supports. Clients of {@code 2025-11-25} and earlier still open with {@code initialize}.
 * This server is "dual-era" in the specification's terms: a request carrying the modern
 * {@code _meta} version is served by the {@value #LATEST_VERSION} rules, an {@code initialize}
 * and everything without that {@code _meta} by the legacy ones. Nothing is kept per client either
 * way - the legacy handshake never stored anything here.
 * <p>
 * <b>Tasks.</b> The {@code io.modelcontextprotocol/tasks} extension ({@link McpTasks}) is modern
 * only. A tool marked {@link McpTool#asTask} answers a {@code tools/call} from a client that
 * declares the extension with a task handle, and the client polls {@code tasks/get}. Every other
 * call stays synchronous. The legacy era's experimental tasks (2025-11-25) are not offered - that
 * revision negotiated them, so a server without them is still a correct one.
 * <p>
 * No SSE: nothing here streams progress or pushes notifications, so every answer is one JSON
 * object, which the transport allows for any request. {@code GET} and {@code DELETE} on
 * {@code /mcp} answer 405 (the GET stream and sessions are gone in the modern era, and this
 * server never offered them in the legacy one).
 * <p>
 * The whole security perimeter is three rules, and none of them is optional: bind loopback only,
 * reject any request that carries an {@code Origin} header, and reject any request whose
 * {@code Host} is not a loopback name. Real MCP clients send no Origin and browsers always do; a
 * DNS rebound name resolves to 127.0.0.1 but still arrives with the attacker's name in Host.
 * Together they are the rebinding guard the MCP specification asks of a local server. Without them
 * any page open in the user's browser could read the whole squad.
 * <p>
 * There is no authentication beyond that: any process running as this user can call every tool,
 * and any of them that has a task id can read that task. The switch being off by default is what
 * bounds that.
 */
public final class McpHttpServer {

    /** The current MCP revision, served statelessly. */
    static final String LATEST_VERSION = "2026-07-28";
    static final List<String> MODERN_VERSIONS = List.of(LATEST_VERSION);
    /** The handshake-based revisions an {@code initialize} may ask for, newest first. */
    static final List<String> LEGACY_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");
    /** What an {@code initialize} that names no version gets - the oldest, as before. */
    private static final String DEFAULT_LEGACY_VERSION = LEGACY_VERSIONS.get(LEGACY_VERSIONS.size() - 1);

    static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    static final String META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    static final String TASKS_EXTENSION = "io.modelcontextprotocol/tasks";

    private static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String JSONRPC = "jsonrpc";
    private static final String JSONRPC_VERSION = "2.0";
    private static final String META = "_meta";
    private static final String RESULT_TYPE = "resultType";
    private static final String EXTENSIONS = "extensions";
    private static final String ARGUMENTS = "arguments";
    private static final String TOOLS_CALL = "tools/call";
    private static final String TASKS_GET = "tasks/get";
    private static final String TASKS_UPDATE = "tasks/update";
    private static final String TASKS_CANCEL = "tasks/cancel";

    static final int HEADER_MISMATCH = -32020;
    static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    /** Freshness hint on cacheable results: the tool list changes only with a new HO build. */
    static final long LIST_TTL_MS = 60 * 60 * 1000L;
    private static final int HTTP_THREADS = 4;

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
    private final McpTasks tasks = new McpTasks();
    /**
     * One tool handler at a time, plain call or task. Handlers reach straight into DBManager, and
     * HO's ConnectionManager shares one JDBC Statement and one StatementCache across every table,
     * so two tool calls in parallel would interleave on it. The HTTP side has several threads only
     * so that a {@code tasks/get} poll never waits behind a running tool; a plain call that arrives
     * while a task runs waits for it. Fair, so calls run in the order they arrived.
     */
    private final ReentrantLock toolLock = new ReentrantLock(true);
    private HttpServer server;
    private ExecutorService executor;

    /** An answer and the HTTP status it goes out with; a null body is a notification's 202. */
    record Reply(int status, JsonObject body) {
    }

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
        registerAll(WeekTools.tools());
        registerAll(MarketTools.tools());
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
        // Several threads, but tool handlers still run one at a time: see toolLock.
        executor = Executors.newFixedThreadPool(HTTP_THREADS);
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
            if ("GET".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().getPath().startsWith(McpDownloads.PREFIX)) {
                final var file = McpDownloads.get(exchange.getRequestURI().getPath());
                respond(exchange, file == null ? 404 : 200, file == null
                        ? "{\"error\":\"No such download - they are kept in memory, the newest "
                        + McpDownloads.KEPT + " only; call the tool again\"}" : file);
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

            final var reply = handle(request, exchange.getRequestHeaders()::getFirst);
            if (reply.body() == null) {
                respond(exchange, 202, "");          // a notification gets no body
                return;
            }
            respond(exchange, reply.status(), GSON.toJson(reply.body()));
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
     * Answer one JSON-RPC request without its HTTP headers - the in-process form the tests use.
     * The modern era's header checks are skipped, everything else applies.
     *
     * @return the response object, or null for a notification (a request without an id)
     */
    JsonObject handle(JsonObject request) {
        return handle(request, null).body();
    }

    /**
     * Wait until the task's work has ended, at most {@link McpTasks#AWAIT_LIMIT_MS}. For tests,
     * which would otherwise have to poll {@code tasks/get} on a timer; an unknown id returns at once.
     */
    void awaitTask(String taskId) throws InterruptedException {
        final var task = tasks.get(taskId);
        if (task != null) {
            tasks.await(task);
        }
    }

    /**
     * Answer one JSON-RPC request.
     *
     * @param headers HTTP request header lookup, case-insensitive; null skips the header checks
     */
    Reply handle(JsonObject request, UnaryOperator<String> headers) {
        final var id = request.get("id");
        if (id == null) {
            return new Reply(202, null);     // notifications/initialized and friends
        }
        final var method = request.has("method") ? request.get("method").getAsString() : "";
        final var params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();
        final var meta = params.has(META) && params.get(META).isJsonObject()
                ? params.getAsJsonObject(META)
                : null;
        final var bodyVersion = string(meta, META_PROTOCOL_VERSION);
        final var headerVersion = headers == null ? null : headers.apply(PROTOCOL_VERSION_HEADER);

        // initialize always means legacy. Otherwise a version in _meta, or a header naming a
        // revision newer than the last legacy one, means a modern client - and from then on the
        // modern rules decide, including the error when header and body disagree.
        final var modern = !"initialize".equals(method)
                && (bodyVersion != null
                || (headerVersion != null && headerVersion.compareTo(LEGACY_VERSIONS.get(0)) > 0));
        return modern
                ? handleModern(id, method, params, meta, bodyVersion, headers)
                : new Reply(200, handleLegacy(id, method, params));
    }

    private JsonObject handleLegacy(JsonElement id, String method, JsonObject params) {
        return switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, new JsonObject());
            case "tools/list" -> result(id, toolsList());
            case TOOLS_CALL -> legacyToolsCall(id, params);
            // A modern probe without _meta still learns what this server speaks.
            case "server/discover" -> result(id, complete(discover()));
            default -> error(id, -32601, "Unknown method: " + method);
        };
    }

    private Reply handleModern(JsonElement id, String method, JsonObject params, JsonObject meta,
                               String bodyVersion, UnaryOperator<String> headers) {
        if (headers != null) {
            final var mismatch = headerMismatch(method, params, bodyVersion, headers);
            if (mismatch != null) {
                return new Reply(400, error(id, HEADER_MISMATCH, "Header mismatch: " + mismatch));
            }
        }
        if (!MODERN_VERSIONS.contains(bodyVersion)) {
            final var data = new JsonObject();
            data.add("supported", supportedVersions());
            data.addProperty("requested", bodyVersion);
            return new Reply(400, error(id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data));
        }
        final var tasksDeclared = declaresTasks(meta);
        return switch (method) {
            case "server/discover" -> new Reply(200, result(id, complete(discover())));
            case "tools/list" -> new Reply(200, result(id, complete(cacheable(toolsList()))));
            case TOOLS_CALL -> new Reply(200, modernToolsCall(id, params, tasksDeclared));
            case TASKS_GET, TASKS_UPDATE, TASKS_CANCEL -> taskRequest(id, method, params, tasksDeclared);
            // The modern transport wants 404 here: it tells this server apart from a legacy one
            // that has no such endpoint, and the JSON-RPC body says which it is.
            default -> new Reply(404, error(id, -32601, "Method not found: " + method));
        };
    }

    /**
     * What is wrong with the mirrored headers, or null when they match the body. The standard
     * headers are required on every modern request; this server declares no {@code x-mcp-header}
     * parameters, so there are no {@code Mcp-Param-*} headers to check.
     */
    private static String headerMismatch(String method, JsonObject params, String bodyVersion,
                                         UnaryOperator<String> headers) {
        final var version = headers.apply(PROTOCOL_VERSION_HEADER);
        if (version == null) {
            return "the MCP-Protocol-Version header is missing";
        }
        if (!version.equals(bodyVersion)) {
            return mismatch(PROTOCOL_VERSION_HEADER, version, bodyVersion)
                    + " (_meta " + META_PROTOCOL_VERSION + ")";
        }
        final var mcpMethod = headers.apply("Mcp-Method");
        if (mcpMethod == null) {
            return "the Mcp-Method header is missing";
        }
        if (!mcpMethod.equals(method)) {
            return mismatch("Mcp-Method", mcpMethod, method);
        }
        final var nameField = switch (method) {
            case TOOLS_CALL -> "name";
            // the tasks extension routes on the task id
            case TASKS_GET, TASKS_UPDATE, TASKS_CANCEL -> "taskId";
            default -> null;
        };
        if (nameField == null) {
            return null;
        }
        final var raw = headers.apply("Mcp-Name");
        if (raw == null) {
            return "the Mcp-Name header is missing";
        }
        final var name = decodeHeaderValue(raw);
        if (name == null) {
            return "the Mcp-Name header value is not valid Base64";
        }
        final var expected = string(params, nameField);
        if (!name.equals(expected)) {
            return mismatch("Mcp-Name", name, expected);
        }
        return null;
    }

    private static String mismatch(String header, String headerValue, String bodyValue) {
        return header + " header value '" + headerValue + "' does not match body value '" + bodyValue + "'";
    }

    /** A header value with the {@code =?base64?...?=} sentinel decoded; null when that is not Base64. */
    static String decodeHeaderValue(String raw) {
        if (raw.length() >= 11 && raw.startsWith("=?base64?") && raw.endsWith("?=")) {
            try {
                return new String(Base64.getDecoder().decode(raw.substring(9, raw.length() - 2)),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return raw;
    }

    /** Whether the request's client capabilities name the tasks extension. */
    private static boolean declaresTasks(JsonObject meta) {
        if (meta == null || !(meta.get(META_CLIENT_CAPABILITIES) instanceof JsonObject capabilities)) {
            return false;
        }
        return capabilities.get(EXTENSIONS) instanceof JsonObject extensions
                && extensions.has(TASKS_EXTENSION);
    }

    private JsonObject initialize(JsonObject params) {
        // The client's version when this server knows it, else the newest legacy one: a legacy
        // client that cannot speak it disconnects, which is what the handshake prescribes.
        final var requested = string(params, "protocolVersion");
        final String version;
        if (requested == null) {
            version = DEFAULT_LEGACY_VERSION;
        } else {
            version = LEGACY_VERSIONS.contains(requested) ? requested : LEGACY_VERSIONS.get(0);
        }

        final var capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());

        final var result = new JsonObject();
        result.addProperty("protocolVersion", version);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo());
        return result;
    }

    /** {@code server/discover}: the versions, capabilities and identity, in one cacheable answer. */
    private static JsonObject discover() {
        final var extensions = new JsonObject();
        extensions.add(TASKS_EXTENSION, new JsonObject());
        final var capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        capabilities.add(EXTENSIONS, extensions);

        final var result = new JsonObject();
        result.add("supportedVersions", supportedVersions());
        result.add("capabilities", capabilities);
        return cacheable(result);
    }

    private static JsonArray supportedVersions() {
        final var versions = new JsonArray();
        final var all = new ArrayList<>(MODERN_VERSIONS);
        all.addAll(LEGACY_VERSIONS);
        all.forEach(versions::add);
        return versions;
    }

    private JsonObject toolsList() {
        final var list = new JsonArray();
        for (final var tool : tools.values()) {      // registration order: deterministic, as asked
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

    private JsonObject legacyToolsCall(JsonElement id, JsonObject params) {
        final var name = params.has("name") ? params.get("name").getAsString() : "";
        final var tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        return response(id, runTool(tool, arguments(params), false, false));
    }

    /**
     * A modern {@code tools/call}: a task handle when the tool marks this call long-running and
     * the client declared the tasks extension on this very request (an earlier declaration does
     * not count - the extension says so), else the plain result.
     */
    private JsonObject modernToolsCall(JsonElement id, JsonObject params, boolean tasksDeclared) {
        final var name = params.has("name") ? params.get("name").getAsString() : "";
        final var tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        final var arguments = arguments(params);
        if (tasksDeclared && tool.taskEligible(arguments)) {
            final var task = tasks.create(name, () -> runTool(tool, arguments, true, true));
            final var handle = tasks.describe(task, false);
            handle.addProperty(RESULT_TYPE, "task");
            handle.add(META, serverInfoMeta());
            return result(id, handle);
        }
        final var outcome = runTool(tool, arguments, false, true);
        return outcome.error() != null
                ? response(id, outcome)
                : result(id, withServerInfo(outcome.result()));
    }

    private Reply taskRequest(JsonElement id, String method, JsonObject params, boolean tasksDeclared) {
        if (!tasksDeclared) {
            final var extensions = new JsonObject();
            extensions.add(TASKS_EXTENSION, new JsonObject());
            final var required = new JsonObject();
            required.add(EXTENSIONS, extensions);
            final var data = new JsonObject();
            data.add("requiredCapabilities", required);
            return new Reply(400, error(id, MISSING_REQUIRED_CLIENT_CAPABILITY,
                    "Missing required client capability: " + method + " needs the " + TASKS_EXTENSION
                            + " extension in _meta " + META_CLIENT_CAPABILITIES, data));
        }
        final var taskId = string(params, "taskId");
        final var task = tasks.get(taskId);
        if (task == null) {
            return new Reply(200, error(id, -32602, "Task not found: " + taskId + ". Tasks are kept in "
                    + "memory for " + McpTasks.TTL_MS / 60_000 + " minutes, the newest " + McpTasks.KEPT
                    + ", and an HO restart forgets them; call the tool again."));
        }
        final JsonObject answer = switch (method) {
            case TASKS_GET -> tasks.describe(task, true);
            case TASKS_CANCEL -> {
                tasks.cancel(task);
                yield new JsonObject();
            }
            // No tool here ever asks for input mid-task, so there is never an outstanding
            // inputRequest; the extension says to ignore responses for unknown keys and ack.
            default -> new JsonObject();
        };
        return new Reply(200, result(id, complete(answer)));
    }

    /**
     * Run one tool call under the tool lock. Never throws: an expected failure is a result with
     * {@code isError}, bad arguments and crashes are JSON-RPC errors.
     */
    private McpTasks.Outcome runTool(McpTool tool, JsonObject arguments, boolean asTask, boolean modern) {
        toolLock.lock();
        try {
            final var answer = tool.handlerFor(asTask).call(arguments);
            return McpTasks.Outcome.result(toolResult(McpBudget.fit(tool, answer), false, modern));
        } catch (McpToolException e) {
            return McpTasks.Outcome.result(toolResult(e.getMessage(), true, modern));
        } catch (IllegalArgumentException e) {
            return McpTasks.Outcome.error(errorObject(-32602, e.getMessage()));
        } catch (Exception e) {
            HOLogger.instance().error(getClass(), e);      // no stack trace crosses the wire
            return McpTasks.Outcome.error(errorObject(-32603, "Internal error in tool " + tool.name()));
        } finally {
            toolLock.unlock();
        }
    }

    private static JsonObject arguments(JsonObject params) {
        return params.has(ARGUMENTS) && params.get(ARGUMENTS).isJsonObject()
                ? params.getAsJsonObject(ARGUMENTS)
                : new JsonObject();
    }

    private static JsonObject toolResult(String text, boolean isError, boolean modern) {
        final var content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        final var contents = new JsonArray();
        contents.add(content);
        final var result = new JsonObject();
        if (modern) {
            result.addProperty(RESULT_TYPE, "complete");
        }
        result.add("content", contents);
        result.addProperty("isError", isError);
        return result;
    }

    private static JsonObject serverInfo() {
        final var serverInfo = new JsonObject();
        serverInfo.addProperty("name", "ho");
        serverInfo.addProperty("version", String.valueOf(core.HO.VERSION));
        return serverInfo;
    }

    private static JsonObject serverInfoMeta() {
        final var meta = new JsonObject();
        meta.add(META_SERVER_INFO, serverInfo());
        return meta;
    }

    /** A modern ordinary result: {@code resultType: complete}, the server named in {@code _meta}. */
    private static JsonObject complete(JsonObject result) {
        if (!result.has(RESULT_TYPE)) {
            result.addProperty(RESULT_TYPE, "complete");
        }
        return withServerInfo(result);
    }

    private static JsonObject withServerInfo(JsonObject result) {
        result.add(META, serverInfoMeta());
        return result;
    }

    /** The modern {@code CacheableResult} fields; private, because the data is one user's team. */
    private static JsonObject cacheable(JsonObject result) {
        result.addProperty("ttlMs", LIST_TTL_MS);
        result.addProperty("cacheScope", "private");
        return result;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !(object.get(key) instanceof com.google.gson.JsonPrimitive value)
                || !value.isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static JsonObject response(JsonElement id, McpTasks.Outcome outcome) {
        if (outcome.error() == null) {
            return result(id, outcome.result());
        }
        final var response = new JsonObject();
        response.addProperty(JSONRPC, JSONRPC_VERSION);
        response.add("id", id);
        response.add("error", outcome.error());
        return response;
    }

    private static JsonObject result(JsonElement id, JsonObject result) {
        final var response = new JsonObject();
        response.addProperty(JSONRPC, JSONRPC_VERSION);
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    static JsonObject errorObject(int code, String message) {
        final var error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        return error;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        return error(id, code, message, null);
    }

    private static JsonObject error(JsonElement id, int code, String message, JsonObject data) {
        final var error = errorObject(code, message);
        if (data != null) {
            error.add("data", data);
        }
        final var response = new JsonObject();
        response.addProperty(JSONRPC, JSONRPC_VERSION);
        response.add("id", id);
        response.add("error", error);
        return response;
    }
}
