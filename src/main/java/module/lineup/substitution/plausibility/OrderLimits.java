package module.lineup.substitution.plausibility;

import module.lineup.substitution.model.MatchOrderType;
import module.lineup.substitution.model.Substitution;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hattrick's limits on a match's team orders, shared by the team orders tab and the MCP tools so
 * both refuse the same orders.
 * <ul>
 *     <li>5 orders (substitutions, behaviour changes, position swaps), plus one per level of the
 *     Tactical Assistant (CHPP matchorders);</li>
 *     <li>of those, at most 3 substitutions from the bench, whatever the Tactical Assistant
 *     (wiki: Substitutions);</li>
 *     <li>at most 1 man marking order, which does not count against the 5.</li>
 * </ul>
 * Feed the orders in list order to {@link #add}; the order that breaks a limit is the one flagged.
 */
public final class OrderLimits {

    public static final int BASE_ORDERS = 5;
    public static final int MAX_SUBSTITUTIONS = 3;
    public static final int MAX_MAN_MARKINGS = 1;

    private final int maxOrders;
    private int substitutions;
    private int manMarkings;
    private int others;

    public OrderLimits(int tacticalAssistantLevels) {
        this.maxOrders = BASE_ORDERS + tacticalAssistantLevels;
    }

    /** Problem of every order in {@code orders}, index-aligned: {@link Error#TOO_MANY_ORDERS} or null. */
    public static List<Problem> check(List<Substitution> orders, int tacticalAssistantLevels) {
        final var limits = new OrderLimits(tacticalAssistantLevels);
        final var problems = new ArrayList<Problem>(orders.size());
        for (var order : orders) {
            problems.add(limits.add(order));
        }
        return problems;
    }

    /** Counts {@code order}; {@link Error#TOO_MANY_ORDERS} if it is one too many, else null. */
    public @Nullable Problem add(Substitution order) {
        final var type = order.getOrderType();
        if (type == MatchOrderType.MAN_MARKING) {
            manMarkings++;
            return manMarkings > MAX_MAN_MARKINGS ? Error.TOO_MANY_ORDERS : null;
        }
        if (type == MatchOrderType.SUBSTITUTION) {
            substitutions++;
            if (substitutions > MAX_SUBSTITUTIONS) {
                return Error.TOO_MANY_ORDERS;
            }
        } else {
            others++;
        }
        return substitutions + others > maxOrders ? Error.TOO_MANY_ORDERS : null;
    }

    public boolean canAddOrder() {
        return substitutions + others < maxOrders;
    }

    public boolean canAddSubstitution() {
        return canAddOrder() && substitutions < MAX_SUBSTITUTIONS;
    }

    public boolean canAddManMarking() {
        return manMarkings < MAX_MAN_MARKINGS;
    }
}
