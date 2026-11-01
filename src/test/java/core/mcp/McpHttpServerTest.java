package core.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpServerTest {

    private static JsonObject request(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static McpHttpServer serverWithEchoTool() {
        final var server = new McpHttpServer();
        server.register(new McpTool("echo", "Echoes its argument.",
                McpTool.schema(McpTool.properties("text", McpTool.prop("string", "what to echo")), "text"),
                args -> {
                    final var result = new JsonObject();
                    result.addProperty("text", args.get("text").getAsString());
                    return result;
                }));
        return server;
    }

    @Test
    void initialize_answersAnUnknownVersionWithTheNewestLegacyOne() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2026-07-28","capabilities":{}}}"""));

        assertThat(response.getAsJsonObject("result").get("protocolVersion").getAsString())
                .isEqualTo("2025-11-25");
    }

    @Test
    void legacyPing_answersAnEmptyResult() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":1,"method":"ping"}"""));

        assertThat(response.getAsJsonObject("result").entrySet()).isEmpty();
    }

    @Test
    void initialize_echoesTheClientsProtocolVersion() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-06-18","capabilities":{}}}"""));

        assertThat(response.getAsJsonObject("result").get("protocolVersion").getAsString())
                .isEqualTo("2025-06-18");
        assertThat(response.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"))
                .isTrue();
    }

    @Test
    void toolsList_advertisesNameDescriptionAndSchema() {
        final var response = serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list"}"""));

        final var tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        assertThat(tools).hasSize(1);
        final var tool = tools.get(0).getAsJsonObject();
        assertThat(tool.get("name").getAsString()).isEqualTo("echo");
        assertThat(tool.get("description").getAsString()).isNotEmpty();
        assertThat(tool.getAsJsonObject("inputSchema").get("type").getAsString()).isEqualTo("object");
    }

    @Test
    void toolsCall_returnsTheHandlersJsonAsTextContent() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"echo","arguments":{"text":"hi"}}}"""));

        final var result = response.getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isFalse();
        final var text = result.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString();
        assertThat(JsonParser.parseString(text).getAsJsonObject().get("text").getAsString())
                .isEqualTo("hi");
    }

    @Test
    void toolsCall_reportsAnExpectedFailureAsIsErrorContent() {
        final var server = new McpHttpServer();
        server.register(new McpTool("boom", "Always fails.", McpTool.schema(new JsonObject()),
                args -> { throw new McpToolException("no model loaded"); }));

        final var response = server.handle(request("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"boom","arguments":{}}}"""));

        final var result = response.getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isTrue();
        assertThat(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString())
                .contains("no model loaded");
        assertThat(response.has("error")).isFalse();
    }

    @Test
    void unknownMethod_isMinus32601() {
        final var response = serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","id":5,"method":"nope/nope"}"""));

        assertThat(response.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32601);
    }

    @Test
    void unknownToolName_isMinus32602() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"missing"}}"""));

        assertThat(response.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32602);
    }

    @Test
    void aNotificationGetsNoResponse() {
        assertThat(serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","method":"notifications/initialized"}""")))
                .isNull();
    }

    @Test
    void malformedJson_isMinus32700() throws Exception {
        final var server = serverWithEchoTool();
        server.start(0);
        try {
            final var response = post(server.port(), "{not json", null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32700);
        } finally {
            server.stop();
        }
    }

    /** The DNS rebinding guard: a browser always sends Origin, a real MCP client never does. */
    @Test
    void aRequestWithAnOriginHeaderIsRejected() throws Exception {
        final var server = serverWithEchoTool();
        server.start(0);
        try {
            final var response = post(server.port(),
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}""", "http://evil.example");
            assertThat(response.statusCode()).isEqualTo(403);
        } finally {
            server.stop();
        }
    }

    // ---- the 2026-07-28 era: stateless, version and capabilities in _meta ----

    private static final String NO_TASKS = "{}";
    private static final String WITH_TASKS = "{\"extensions\":{\"io.modelcontextprotocol/tasks\":{}}}";

    /** A modern request body: params plus the per-request _meta. */
    private static JsonObject modern(int id, String method, String params, String capabilities) {
        final var request = request("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method
                + "\",\"params\":" + params + "}");
        final var meta = request("{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"test\",\"version\":\"1\"},"
                + "\"io.modelcontextprotocol/clientCapabilities\":" + capabilities + "}");
        request.getAsJsonObject("params").add("_meta", meta);
        return request;
    }

    /** The headers a conforming client mirrors from the body. */
    private static Map<String, String> headersFor(JsonObject request) {
        final var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        final var params = request.getAsJsonObject("params");
        headers.put("MCP-Protocol-Version", params.getAsJsonObject("_meta")
                .get("io.modelcontextprotocol/protocolVersion").getAsString());
        final var method = request.get("method").getAsString();
        headers.put("Mcp-Method", method);
        if (params.has("name")) {
            headers.put("Mcp-Name", params.get("name").getAsString());
        } else if (params.has("taskId")) {
            headers.put("Mcp-Name", params.get("taskId").getAsString());
        }
        return headers;
    }

    private static McpHttpServer.Reply send(McpHttpServer server, JsonObject request) {
        return server.handle(request, headersFor(request)::get);
    }

    /** A server with a tool that runs as a task and waits for {@code release} before it answers. */
    private static McpHttpServer serverWithSlowTool(CountDownLatch release) {
        final var server = serverWithEchoTool();
        server.register(new McpTool("slow", "Waits, then answers.", McpTool.schema(new JsonObject()),
                args -> {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("never released");
                    }
                    final var result = new JsonObject();
                    result.addProperty("done", true);
                    return result;
                }).asTask(args -> true));
        return server;
    }

    private static JsonObject pollUntilTerminal(McpHttpServer server, String taskId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            final var task = send(server, modern(50 + i, "tasks/get", "{\"taskId\":\"" + taskId + "\"}",
                    WITH_TASKS)).body().getAsJsonObject("result");
            if (!task.get("status").getAsString().equals("working")) {
                return task;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("task " + taskId + " never finished");
    }

    @Test
    void discover_namesTheVersionsTheToolsAndTheTasksExtension() {
        final var reply = send(serverWithEchoTool(), modern(1, "server/discover", "{}", NO_TASKS));

        assertThat(reply.status()).isEqualTo(200);
        final var result = reply.body().getAsJsonObject("result");
        assertThat(result.get("resultType").getAsString()).isEqualTo("complete");
        assertThat(result.getAsJsonArray("supportedVersions")).extracting(JsonElement::getAsString)
                .contains("2026-07-28", "2025-11-25");
        assertThat(result.getAsJsonObject("capabilities").has("tools")).isTrue();
        assertThat(result.getAsJsonObject("capabilities").getAsJsonObject("extensions")
                .has("io.modelcontextprotocol/tasks")).isTrue();
        assertThat(result.getAsJsonObject("_meta").getAsJsonObject("io.modelcontextprotocol/serverInfo")
                .get("name").getAsString()).isEqualTo("ho");
        assertThat(result.get("cacheScope").getAsString()).isEqualTo("private");
    }

    @Test
    void modernToolsList_isCacheableAndComplete() {
        final var result = send(serverWithEchoTool(), modern(2, "tools/list", "{}", NO_TASKS))
                .body().getAsJsonObject("result");

        assertThat(result.get("resultType").getAsString()).isEqualTo("complete");
        assertThat(result.get("ttlMs").getAsLong()).isPositive();
        assertThat(result.get("cacheScope").getAsString()).isEqualTo("private");
        assertThat(result.getAsJsonArray("tools")).hasSize(1);
    }

    @Test
    void anUnsupportedVersionIs400WithTheSupportedOnes() {
        final var request = modern(3, "tools/list", "{}", NO_TASKS);
        request.getAsJsonObject("params").getAsJsonObject("_meta")
                .addProperty("io.modelcontextprotocol/protocolVersion", "2099-01-01");

        final var reply = send(serverWithEchoTool(), request);

        assertThat(reply.status()).isEqualTo(400);
        final var error = reply.body().getAsJsonObject("error");
        assertThat(error.get("code").getAsInt()).isEqualTo(-32022);
        assertThat(error.getAsJsonObject("data").get("requested").getAsString()).isEqualTo("2099-01-01");
        assertThat(error.getAsJsonObject("data").getAsJsonArray("supported"))
                .extracting(JsonElement::getAsString).contains("2026-07-28");
    }

    @Test
    void aHeaderThatDisagreesWithTheBodyIs400HeaderMismatch() {
        final var request = modern(4, "tools/call", "{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}", NO_TASKS);
        final var headers = headersFor(request);
        headers.put("Mcp-Name", "something_else");

        final var reply = serverWithEchoTool().handle(request, headers::get);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32020);
    }

    @Test
    void aMissingStandardHeaderIs400HeaderMismatch() {
        final var request = modern(5, "tools/list", "{}", NO_TASKS);
        final var headers = headersFor(request);
        headers.remove("Mcp-Method");

        final var reply = serverWithEchoTool().handle(request, headers::get);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32020);
    }

    @Test
    void aBase64EncodedMcpNameIsDecodedBeforeTheComparison() {
        final var request = modern(6, "tools/call", "{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}", NO_TASKS);
        final var headers = headersFor(request);
        headers.put("Mcp-Name", "=?base64?ZWNobw==?=");

        assertThat(serverWithEchoTool().handle(request, headers::get).status()).isEqualTo(200);
    }

    @Test
    void aModernUnknownMethodIs404MethodNotFound() {
        final var reply = send(serverWithEchoTool(), modern(7, "nope/nope", "{}", NO_TASKS));

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.body().getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32601);
    }

    @Test
    void initializeWithModernMetaStillGetsTheLegacyHandshake() {
        final var reply = send(serverWithEchoTool(), modern(8, "initialize",
                "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}", NO_TASKS));

        assertThat(reply.body().getAsJsonObject("result").get("protocolVersion").getAsString())
                .isEqualTo("2025-11-25");
    }

    @Test
    void modernToolsCall_withoutTheTasksExtensionAnswersInline() {
        final var server = serverWithSlowTool(new CountDownLatch(0));

        final var result = send(server, modern(9, "tools/call", "{\"name\":\"slow\",\"arguments\":{}}", NO_TASKS))
                .body().getAsJsonObject("result");

        assertThat(result.get("resultType").getAsString()).isEqualTo("complete");
        assertThat(result.get("isError").getAsBoolean()).isFalse();
        assertThat(result.getAsJsonObject("_meta").has("io.modelcontextprotocol/serverInfo")).isTrue();
    }

    @Test
    void modernToolsCall_ofAToolThatIsNoTaskAnswersInlineEvenWithTheExtension() {
        final var result = send(serverWithEchoTool(), modern(10, "tools/call",
                "{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}", WITH_TASKS))
                .body().getAsJsonObject("result");

        assertThat(result.get("resultType").getAsString()).isEqualTo("complete");
    }

    @Test
    void aTaskToolAnswersWithAHandleAndTasksGetDeliversTheResult() throws Exception {
        final var release = new CountDownLatch(1);
        final var server = serverWithSlowTool(release);

        final var handle = send(server, modern(11, "tools/call", "{\"name\":\"slow\",\"arguments\":{}}", WITH_TASKS))
                .body().getAsJsonObject("result");

        assertThat(handle.get("resultType").getAsString()).isEqualTo("task");
        assertThat(handle.get("status").getAsString()).isEqualTo("working");
        assertThat(handle.get("ttlMs").getAsLong()).isPositive();
        assertThat(handle.get("pollIntervalMs").getAsLong()).isPositive();
        assertThat(handle.has("createdAt")).isTrue();
        final var taskId = handle.get("taskId").getAsString();

        // the task exists before the handle goes out, and stays working until the tool returns
        final var working = send(server, modern(12, "tasks/get", "{\"taskId\":\"" + taskId + "\"}", WITH_TASKS))
                .body().getAsJsonObject("result");
        assertThat(working.get("status").getAsString()).isEqualTo("working");
        assertThat(working.get("resultType").getAsString()).isEqualTo("complete");
        assertThat(working.has("result")).isFalse();

        release.countDown();
        final var done = pollUntilTerminal(server, taskId);

        assertThat(done.get("status").getAsString()).isEqualTo("completed");
        final var result = done.getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isFalse();
        final var text = result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        assertThat(JsonParser.parseString(text).getAsJsonObject().get("done").getAsBoolean()).isTrue();
    }

    @Test
    void aToolErrorCompletesTheTaskAndACrashFailsIt() throws Exception {
        final var server = new McpHttpServer();
        server.register(new McpTool("refuses", "Always refuses.", McpTool.schema(new JsonObject()),
                args -> { throw new McpToolException("cap used up"); }).asTask(args -> true));
        server.register(new McpTool("crashes", "Always crashes.", McpTool.schema(new JsonObject()),
                args -> { throw new IllegalStateException("boom"); }).asTask(args -> true));

        final var refusedId = send(server, modern(20, "tools/call", "{\"name\":\"refuses\"}", WITH_TASKS))
                .body().getAsJsonObject("result").get("taskId").getAsString();
        final var crashedId = send(server, modern(21, "tools/call", "{\"name\":\"crashes\"}", WITH_TASKS))
                .body().getAsJsonObject("result").get("taskId").getAsString();

        final var refused = pollUntilTerminal(server, refusedId);
        assertThat(refused.get("status").getAsString()).isEqualTo("completed");
        assertThat(refused.getAsJsonObject("result").get("isError").getAsBoolean()).isTrue();

        final var crashed = pollUntilTerminal(server, crashedId);
        assertThat(crashed.get("status").getAsString()).isEqualTo("failed");
        assertThat(crashed.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32603);
    }

    @Test
    void cancellingAQueuedTaskEndsItCancelled() throws Exception {
        final var release = new CountDownLatch(1);
        final var server = serverWithSlowTool(release);
        final var call = "{\"name\":\"slow\",\"arguments\":{}}";
        final var first = send(server, modern(30, "tools/call", call, WITH_TASKS))
                .body().getAsJsonObject("result").get("taskId").getAsString();
        final var second = send(server, modern(31, "tools/call", call, WITH_TASKS))
                .body().getAsJsonObject("result").get("taskId").getAsString();

        final var ack = send(server, modern(32, "tasks/cancel", "{\"taskId\":\"" + second + "\"}", WITH_TASKS));
        assertThat(ack.body().getAsJsonObject("result").get("resultType").getAsString()).isEqualTo("complete");

        release.countDown();
        assertThat(pollUntilTerminal(server, second).get("status").getAsString()).isEqualTo("cancelled");
        assertThat(pollUntilTerminal(server, first).get("status").getAsString()).isEqualTo("completed");
    }

    @Test
    void tasksUpdateIsAcknowledged() {
        final var server = serverWithSlowTool(new CountDownLatch(0));
        final var taskId = send(server, modern(40, "tools/call", "{\"name\":\"slow\"}", WITH_TASKS))
                .body().getAsJsonObject("result").get("taskId").getAsString();

        final var ack = send(server, modern(41, "tasks/update",
                "{\"taskId\":\"" + taskId + "\",\"inputResponses\":{}}", WITH_TASKS));

        assertThat(ack.status()).isEqualTo(200);
        assertThat(ack.body().getAsJsonObject("result").get("resultType").getAsString()).isEqualTo("complete");
    }

    @Test
    void anUnknownTaskIdIsInvalidParams() {
        final var reply = send(serverWithEchoTool(), modern(42, "tasks/get", "{\"taskId\":\"nope\"}", WITH_TASKS));

        assertThat(reply.body().getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32602);
    }

    @Test
    void tasksGetWithoutTheExtensionIsMissingRequiredClientCapability() {
        final var reply = send(serverWithEchoTool(), modern(43, "tasks/get", "{\"taskId\":\"x\"}", NO_TASKS));

        assertThat(reply.status()).isEqualTo(400);
        final var error = reply.body().getAsJsonObject("error");
        assertThat(error.get("code").getAsInt()).isEqualTo(-32021);
        assertThat(error.getAsJsonObject("data").getAsJsonObject("requiredCapabilities")
                .getAsJsonObject("extensions").has("io.modelcontextprotocol/tasks")).isTrue();
    }

    /** Over the wire: the headers come from the HTTP request, the status from the reply. */
    @Test
    void overHttpAModernUnknownMethodIs404() throws Exception {
        final var server = serverWithEchoTool();
        server.start(0);
        try {
            final var request = modern(44, "nope/nope", "{}", NO_TASKS);
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/mcp"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toString()));
            for (final var header : headersFor(request).entrySet()) {
                builder = builder.header(header.getKey(), header.getValue());
            }
            final var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32601);
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> post(int port, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) {
            builder = builder.header("Origin", origin);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
