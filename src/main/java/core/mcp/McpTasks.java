package core.mcp;

import com.google.gson.JsonObject;
import core.util.HOLogger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The MCP tasks extension ({@code io.modelcontextprotocol/tasks}, revision 2026-07-28): a
 * {@code tools/call} that may outlast the client's request timeout answers with a task handle at
 * once, and the client polls {@code tasks/get} until the task reaches a terminal status.
 * <p>
 * In memory, like {@link McpDownloads}: a restart forgets every task, and so does the client's
 * next {@code tasks/get} (an unknown taskId, which the extension allows for a purged task). The
 * newest {@link #KEPT} are kept, finished ones go first, and none outlives {@link #TTL_MS}.
 * <p>
 * The work itself runs on one daemon thread, so tasks run one after another in the order they
 * were created. It still takes the server's tool lock like every other tool call: tasks and
 * plain calls must not interleave on HO's shared JDBC statement.
 * <p>
 * The task id is the only thing that names a task, so it is unguessable (128 random bits), as
 * the extension asks. Nothing else guards a task - the server has no authentication of its own
 * (see {@link McpHttpServer}).
 */
final class McpTasks {

    /** How long a task is kept after it was created, finished or not. */
    static final long TTL_MS = 60 * 60 * 1000L;
    /** What a client is told to wait between two {@code tasks/get}. */
    static final long POLL_INTERVAL_MS = 2_000L;
    static final int KEPT = 50;

    enum Status {
        WORKING("working"), COMPLETED("completed"), FAILED("failed"), CANCELLED("cancelled");

        final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        boolean terminal() {
            return this != WORKING;
        }
    }

    /**
     * What a finished task carries: the result of the original request, or the JSON-RPC error it
     * failed with. A tool that answered {@code isError: true} completed - only a protocol-level
     * error makes a task fail.
     */
    record Outcome(JsonObject result, JsonObject error) {
        static Outcome result(JsonObject result) {
            return new Outcome(result, null);
        }

        static Outcome error(JsonObject error) {
            return new Outcome(null, error);
        }
    }

    /** One task. Mutable state is guarded by the {@link McpTasks} instance. */
    static final class Task {
        final String id;
        final String toolName;
        final Instant createdAt = Instant.now();
        Instant lastUpdatedAt = createdAt;
        Status status = Status.WORKING;
        String statusMessage;
        boolean started;
        boolean cancelRequested;
        Outcome outcome;
        Future<?> future;

        Task(String id, String toolName) {
            this.id = id;
            this.toolName = toolName;
            this.statusMessage = "queued: " + toolName + " starts once the calls ahead of it finish";
        }

        boolean expired(Instant now) {
            return now.isAfter(createdAt.plusMillis(TTL_MS));
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        final var thread = new Thread(r, "mcp-task");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Create a task and queue its work. The task is in the store before this returns, so a
     * {@code tasks/get} for the id resolves from the first poll on, as the extension requires.
     *
     * @param work runs the tool and returns its outcome; it never throws
     */
    synchronized Task create(String toolName, Supplier<Outcome> work) {
        evict(Instant.now());
        final var task = new Task(newId(), toolName);
        tasks.put(task.id, task);
        task.future = executor.submit(() -> run(task, work));
        return task;
    }

    private void run(Task task, Supplier<Outcome> work) {
        synchronized (this) {
            task.started = true;
            task.statusMessage = task.cancelRequested
                    ? "cancellation requested, but " + task.toolName + " had already started"
                    : "running " + task.toolName;
            task.lastUpdatedAt = Instant.now();
        }
        Outcome outcome;
        try {
            outcome = work.get();
        } catch (RuntimeException e) {
            // work is not supposed to throw; if it does, the task must still end
            HOLogger.instance().error(McpTasks.class, e);
            outcome = Outcome.error(McpHttpServer.errorObject(-32603, "Internal error in tool " + task.toolName));
        }
        synchronized (this) {
            task.outcome = outcome;
            task.status = outcome.error() == null ? Status.COMPLETED : Status.FAILED;
            task.statusMessage = outcome.error() == null
                    ? task.toolName + " finished"
                    : task.toolName + " failed: " + outcome.error().get("message").getAsString();
            if (task.cancelRequested) {
                task.statusMessage += " (cancellation came too late: the call was already running"
                        + " and HO does not interrupt a running download or write)";
            }
            task.lastUpdatedAt = Instant.now();
        }
    }

    /** How long {@link #await} waits for a task's work at most. */
    static final long AWAIT_LIMIT_MS = 10_000L;

    /**
     * Wait until the task's work has run or was cancelled, at most {@link #AWAIT_LIMIT_MS}. Its
     * status is final by then: the work sets it before it returns.
     */
    void await(Task task) throws InterruptedException {
        final Future<?> future;
        synchronized (this) {
            future = task.future;
        }
        try {
            future.get(AWAIT_LIMIT_MS, TimeUnit.MILLISECONDS);
        } catch (CancellationException | ExecutionException | TimeoutException e) {
            // cancelled or ended either way; a timeout leaves the task as it is for the caller to read
        }
    }

    /** The task, or null when the id is unknown, purged or past its TTL. */
    synchronized Task get(String id) {
        evict(Instant.now());
        return id == null ? null : tasks.get(id);
    }

    /**
     * Cooperative cancel. A task still queued never starts and ends {@code cancelled}. A running
     * one is not interrupted - that could leave half a download in the database - and finishes
     * with its real outcome, its status message saying the cancel came too late.
     */
    synchronized void cancel(Task task) {
        if (task.status.terminal()) {
            return;
        }
        task.cancelRequested = true;
        task.lastUpdatedAt = Instant.now();
        if (!task.started && task.future.cancel(false)) {
            task.status = Status.CANCELLED;
            task.statusMessage = "cancelled before " + task.toolName + " started";
        } else {
            task.statusMessage = "cancellation requested; " + task.toolName
                    + " is already running and will finish";
        }
    }

    /**
     * The task as the extension's {@code Task} shape, plus {@code result} or {@code error} once it
     * is terminal when {@code detailed} (the {@code tasks/get} answer).
     */
    synchronized JsonObject describe(Task task, boolean detailed) {
        final var json = new JsonObject();
        json.addProperty("taskId", task.id);
        json.addProperty("status", task.status.wire);
        if (task.statusMessage != null) {
            json.addProperty("statusMessage", task.statusMessage);
        }
        json.addProperty("createdAt", task.createdAt.toString());
        json.addProperty("lastUpdatedAt", task.lastUpdatedAt.toString());
        json.addProperty("ttlMs", TTL_MS);
        json.addProperty("pollIntervalMs", POLL_INTERVAL_MS);
        if (detailed && task.outcome != null) {
            if (task.status == Status.COMPLETED) {
                json.add("result", task.outcome.result());
            } else if (task.status == Status.FAILED) {
                json.add("error", task.outcome.error());
            }
        }
        return json;
    }

    /** Drops expired tasks, then the oldest finished ones beyond {@link #KEPT}. */
    private void evict(Instant now) {
        tasks.values().removeIf(task -> task.expired(now) && task.status.terminal());
        // Expired but still running: kept until it finishes, then it goes on the next call.
        if (tasks.size() <= KEPT) {
            return;
        }
        for (final var task : new ArrayList<>(tasks.values())) {
            if (tasks.size() <= KEPT) {
                return;
            }
            if (task.status.terminal()) {
                tasks.remove(task.id);
            }
        }
    }

    private static String newId() {
        final var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
