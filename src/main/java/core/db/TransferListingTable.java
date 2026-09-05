package core.db;

import core.util.HODateTime;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Every transfer market listing HO has seen, one row per observation.
 * <p>
 * The corpus behind player valuation: repeated observation of the same listing yields a highest bid
 * ever closer to the sale price, and the resolve pass fills the sale price in for expired ones.
 */
public class TransferListingTable extends AbstractTable {

    /** Table name **/
    static final String TABLENAME = "TRANSFERLISTING";

    TransferListingTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 2;
    }

    @Override
    protected void initColumns() {
        this.columns = new ArrayList<>(List.of(
                intCol("PLAYERID", p -> ((TransferListing) p).getPlayerId(), (p, v) -> ((TransferListing) p).setPlayerId((Integer) v), false),
                timeCol("OBSERVEDAT", p -> HODateTime.toDbTimestamp(((TransferListing) p).getObservedAt()), (p, v) -> ((TransferListing) p).setObservedAt((HODateTime) v), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("PLAYERNAME").setGetter(p -> ((TransferListing) p).getPlayerName()).setSetter((p, v) -> ((TransferListing) p).setPlayerName((String) v)).setType(Types.VARCHAR).setLength(256).isNullable(true).build(),
                timeCol("DEADLINE", p -> HODateTime.toDbTimestamp(((TransferListing) p).getDeadline()), (p, v) -> ((TransferListing) p).setDeadline((HODateTime) v), true),
                longCol("ASKINGPRICE", p -> ((TransferListing) p).getAskingPrice(), (p, v) -> ((TransferListing) p).setAskingPrice(v == null ? 0L : ((Number) v).longValue()), false),
                longCol("HIGHESTBID", p -> ((TransferListing) p).getHighestBid(), (p, v) -> ((TransferListing) p).setHighestBid(v == null ? 0L : ((Number) v).longValue()), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("BIDDERNAME").setGetter(p -> ((TransferListing) p).getBidderName()).setSetter((p, v) -> ((TransferListing) p).setBidderName((String) v)).setType(Types.VARCHAR).setLength(256).isNullable(true).build(),
                intCol("TSI", p -> ((TransferListing) p).getTsi(), (p, v) -> ((TransferListing) p).setTsi((Integer) v), true),
                intCol("AGE", p -> ((TransferListing) p).getAge(), (p, v) -> ((TransferListing) p).setAge((Integer) v), true),
                intCol("AGEDAYS", p -> ((TransferListing) p).getAgeDays(), (p, v) -> ((TransferListing) p).setAgeDays((Integer) v), true),
                longCol("SALARY", p -> ((TransferListing) p).getSalary(), (p, v) -> ((TransferListing) p).setSalary(v == null ? null : ((Number) v).longValue()), true),
                intCol("SPECIALTY", p -> ((TransferListing) p).getSpecialty(), (p, v) -> ((TransferListing) p).setSpecialty((Integer) v), true),
                intCol("FORM", p -> ((TransferListing) p).getForm(), (p, v) -> ((TransferListing) p).setForm((Integer) v), true),
                intCol("EXPERIENCE", p -> ((TransferListing) p).getExperience(), (p, v) -> ((TransferListing) p).setExperience((Integer) v), true),
                intCol("LEADERSHIP", p -> ((TransferListing) p).getLeadership(), (p, v) -> ((TransferListing) p).setLeadership((Integer) v), true),
                intCol("CARDS", p -> ((TransferListing) p).getCards(), (p, v) -> ((TransferListing) p).setCards((Integer) v), true),
                intCol("INJURYLEVEL", p -> ((TransferListing) p).getInjuryLevel(), (p, v) -> ((TransferListing) p).setInjuryLevel((Integer) v), true),
                intCol("STAMINA", p -> ((TransferListing) p).getStamina(), (p, v) -> ((TransferListing) p).setStamina((Integer) v), true),
                intCol("KEEPER", p -> ((TransferListing) p).getKeeper(), (p, v) -> ((TransferListing) p).setKeeper((Integer) v), true),
                intCol("PLAYMAKER", p -> ((TransferListing) p).getPlaymaker(), (p, v) -> ((TransferListing) p).setPlaymaker((Integer) v), true),
                intCol("SCORER", p -> ((TransferListing) p).getScorer(), (p, v) -> ((TransferListing) p).setScorer((Integer) v), true),
                intCol("PASSING", p -> ((TransferListing) p).getPassing(), (p, v) -> ((TransferListing) p).setPassing((Integer) v), true),
                intCol("WINGER", p -> ((TransferListing) p).getWinger(), (p, v) -> ((TransferListing) p).setWinger((Integer) v), true),
                intCol("DEFENDER", p -> ((TransferListing) p).getDefender(), (p, v) -> ((TransferListing) p).setDefender((Integer) v), true),
                intCol("SETPIECES", p -> ((TransferListing) p).getSetPieces(), (p, v) -> ((TransferListing) p).setSetPieces((Integer) v), true),
                longCol("SALEPRICE", p -> ((TransferListing) p).getSalePrice(), (p, v) -> ((TransferListing) p).setSalePrice(v == null ? null : ((Number) v).longValue()), true),
                timeCol("RESOLVEDAT", p -> HODateTime.toDbTimestamp(((TransferListing) p).getResolvedAt()), (p, v) -> ((TransferListing) p).setResolvedAt((HODateTime) v), true)
        )).toArray(new ColumnDescriptor[0]);
    }

    private static ColumnDescriptor intCol(String name, java.util.function.UnaryOperator<Object> getter,
                                           java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.INTEGER).isNullable(nullable).build();
    }

    private static ColumnDescriptor longCol(String name, java.util.function.UnaryOperator<Object> getter,
                                            java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.BIGINT).isNullable(nullable).build();
    }

    private static ColumnDescriptor timeCol(String name, java.util.function.UnaryOperator<Object> getter,
                                            java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.TIMESTAMP).isNullable(nullable).build();
    }

    /** Snapshot one observed listing. */
    public void storeListing(TransferListing listing) {
        store(listing);
    }

    /**
     * Listings whose deadline has passed and whose sale price is still unknown, oldest first.
     *
     * @param max how many rows to return - the resolve pass is capped, one CHPP call per row
     */
    public List<TransferListing> loadUnresolved(int max) {
        // RESOLVEDAT IS NULL means "never looked up", not "never sold". Plenty of listings never
        // produce a sale - withdrawn, expired without a bid, relisted - and without this they would
        // sit at the head of an oldest-first queue and be retried on every single download, forever.
        // As those accumulate they would eat the whole call budget and new listings would stop
        // resolving, so the corpus would get worse as it got bigger.
        final var sql = createSelectStatement(
                "WHERE SALEPRICE IS NULL AND RESOLVEDAT IS NULL ORDER BY OBSERVEDAT ASC LIMIT "
                        + Math.max(max, 0));
        return load(TransferListing.class, connectionManager.executeQuery(sql));
    }

    /**
     * Note that HO looked this player up and found no sale for his expired listings, so they are
     * not asked about again. Only for a CHPP call that actually answered - a network failure must
     * not burn a listing's one attempt.
     */
    public void markResolveAttempted(int playerId, HODateTime now) {
        connectionManager.executeUpdate("UPDATE " + getTableName()
                + " SET RESOLVEDAT='" + HODateTime.toDbTimestamp(now) + "'"
                + " WHERE PLAYERID=" + playerId
                + " AND SALEPRICE IS NULL"
                + " AND RESOLVEDAT IS NULL"
                + " AND DEADLINE < '" + HODateTime.toDbTimestamp(now) + "'");
    }

    /** Whether any snapshot of this player carries skill data (a search hit, or the skills-fill pass). */
    public boolean hasSkillData(int playerId) {
        final var sql = createSelectStatement(
                "WHERE PLAYERID = " + playerId + " AND PLAYMAKER IS NOT NULL LIMIT 1");
        return !load(TransferListing.class, connectionManager.executeQuery(sql)).isEmpty();
    }

    /** The most recent snapshot of this player's market listing, or null if none was ever seen. */
    public TransferListing loadLatestForPlayer(int playerId) {
        final var sql = createSelectStatement(
                "WHERE PLAYERID = " + playerId + " ORDER BY OBSERVEDAT DESC LIMIT 1");
        final var rows = load(TransferListing.class, connectionManager.executeQuery(sql));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Record what a player actually sold for, on every unresolved observation of the listing that
     * produced the sale.
     * <p>
     * Keyed on the deadline rather than on one observation: a listing seen in four searches is four
     * rows, and one CHPP lookup should resolve all of them. A Hattrick transfer completes at its
     * deadline, so {@code window} around the sale date picks out exactly the listing that sold and
     * leaves an earlier, separately-listed spell of the same player alone.
     */
    public void storeSalePrice(int playerId, HODateTime saleDate, long salePrice,
                               java.time.Duration window) {
        final var from = new HODateTime(saleDate.getInstant().minus(window));
        final var to = new HODateTime(saleDate.getInstant().plus(window));
        connectionManager.executeUpdate("UPDATE " + getTableName()
                + " SET SALEPRICE=" + salePrice
                + ", RESOLVEDAT='" + HODateTime.toDbTimestamp(HODateTime.now()) + "'"
                + " WHERE PLAYERID=" + playerId
                + " AND SALEPRICE IS NULL"
                + " AND DEADLINE >= '" + HODateTime.toDbTimestamp(from) + "'"
                + " AND DEADLINE <= '" + HODateTime.toDbTimestamp(to) + "'");
    }

    /**
     * Observations of players comparable to one of ours, for valuation: an age window, two skill
     * windows and optionally a specialty.
     * <p>
     * {@code observedSince} is what stands in for pruning. Nothing deletes from this table, so a
     * listing from three seasons ago would otherwise drag an estimate towards a market that no
     * longer exists; bounding the read keeps the history for later without letting it vote now.
     */
    public List<TransferListing> loadComparables(int ageMin, int ageMax,
                                                 String skill1, int min1, int max1,
                                                 String skill2, int min2, int max2,
                                                 Integer specialty, HODateTime observedSince) {
        final var where = new StringBuilder("WHERE AGE BETWEEN " + ageMin + " AND " + ageMax
                + " AND " + skillColumn(skill1) + " BETWEEN " + min1 + " AND " + max1
                + " AND " + skillColumn(skill2) + " BETWEEN " + min2 + " AND " + max2
                + " AND OBSERVEDAT >= '" + HODateTime.toDbTimestamp(observedSince) + "'");
        if (specialty != null) {
            where.append(" AND SPECIALTY = ").append(specialty);
        }
        return load(TransferListing.class, connectionManager.executeQuery(
                createSelectStatement(where.toString())));
    }

    /**
     * The caller names the skill column, and it is concatenated into SQL, so it is checked against
     * the columns this table actually declares rather than trusted.
     */
    private String skillColumn(String name) {
        for (final var column : getColumns()) {
            if (column.getColumnName().equalsIgnoreCase(name)) {
                return column.getColumnName();
            }
        }
        throw new IllegalArgumentException("No column " + name + " in " + getTableName());
    }
}
