package core.mcp;

import java.util.concurrent.locks.ReentrantLock;

/**
 * One tool handler at a time, plain call or task. Handlers reach straight into DBManager, and HO's
 * ConnectionManager shares one JDBC Statement and one StatementCache across every table, so two
 * tool calls in parallel would interleave on it. {@link McpHttpServer} takes the lock around every
 * handler except those of a tool marked {@link McpTool#outsideToolLock()}: they run their long
 * CHPP part unlocked and take it here around what reads the database. Fair, so calls run in the
 * order they arrived; reentrant, so a locked handler may call code that locks again.
 */
final class McpToolLock {

    private static final ReentrantLock LOCK = new ReentrantLock(true);

    /** What runs under the lock. */
    interface Body<T> {
        T run() throws McpToolException;
    }

    private McpToolLock() {
    }

    /** {@code body} under the lock. */
    static <T> T hold(Body<T> body) throws McpToolException {
        LOCK.lock();
        try {
            return body.run();
        } finally {
            LOCK.unlock();
        }
    }
}
