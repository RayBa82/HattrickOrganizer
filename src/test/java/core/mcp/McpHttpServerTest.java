package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
