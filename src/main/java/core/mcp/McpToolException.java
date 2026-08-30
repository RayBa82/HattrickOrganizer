package core.mcp;

/**
 * An expected tool failure - no model loaded, search cap reached, unknown player id.
 * <p>
 * Rendered as an {@code isError: true} tool result with a plain English explanation rather than as a
 * JSON-RPC protocol error, so that the model can read it and act on it instead of treating it as a
 * transport fault.
 */
public class McpToolException extends Exception {
    public McpToolException(String message) {
        super(message);
    }
}
