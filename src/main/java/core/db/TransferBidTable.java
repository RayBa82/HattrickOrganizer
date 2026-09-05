package core.db;

import core.util.HODateTime;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * The transfers this team has bid on, one row per player.
 * <p>
 * Fed from {@code currentbids} on every {@code get_open_bids} and from {@code place_bid}; the
 * companion history of full market observations lives in {@link TransferListingTable}.
 */
public class TransferBidTable extends AbstractTable {

    /** Table name **/
    static final String TABLENAME = "TRANSFERBID";

    TransferBidTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 1;
    }

    @Override
    protected void initColumns() {
        this.columns = new ArrayList<>(List.of(
                intCol("PLAYERID", p -> ((TransferBid) p).getPlayerId(), (p, v) -> ((TransferBid) p).setPlayerId((Integer) v), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("PLAYERNAME").setGetter(p -> ((TransferBid) p).getPlayerName()).setSetter((p, v) -> ((TransferBid) p).setPlayerName((String) v)).setType(Types.VARCHAR).setLength(256).isNullable(true).build(),
                longCol("OURBID", p -> ((TransferBid) p).getOurBid(), (p, v) -> ((TransferBid) p).setOurBid(v == null ? null : ((Number) v).longValue()), true),
                longCol("OURMAXBID", p -> ((TransferBid) p).getOurMaxBid(), (p, v) -> ((TransferBid) p).setOurMaxBid(v == null ? null : ((Number) v).longValue()), true),
                timeCol("BIDPLACEDAT", p -> HODateTime.toDbTimestamp(((TransferBid) p).getBidPlacedAt()), (p, v) -> ((TransferBid) p).setBidPlacedAt((HODateTime) v), true),
                timeCol("DEADLINE", p -> HODateTime.toDbTimestamp(((TransferBid) p).getDeadline()), (p, v) -> ((TransferBid) p).setDeadline((HODateTime) v), true),
                longCol("LATESTHIGHESTBID", p -> ((TransferBid) p).getLatestHighestBid(), (p, v) -> ((TransferBid) p).setLatestHighestBid(v == null ? 0L : ((Number) v).longValue()), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("LATESTLEADING").setGetter(p -> ((TransferBid) p).isLatestLeading()).setSetter((p, v) -> ((TransferBid) p).setLatestLeading(v != null && (Boolean) v)).setType(Types.BOOLEAN).isNullable(false).build(),
                intCol("LATESTTRACKINGTYPEID", p -> ((TransferBid) p).getLatestTrackingTypeId(), (p, v) -> ((TransferBid) p).setLatestTrackingTypeId(v == null ? 0 : (Integer) v), false),
                longCol("FINALPRICE", p -> ((TransferBid) p).getFinalPrice(), (p, v) -> ((TransferBid) p).setFinalPrice(v == null ? null : ((Number) v).longValue()), true),
                ColumnDescriptor.Builder.newInstance().setColumnName("OUTCOME").setGetter(p -> ((TransferBid) p).getOutcome()).setSetter((p, v) -> ((TransferBid) p).setOutcome((String) v)).setType(Types.VARCHAR).setLength(16).isNullable(true).build(),
                timeCol("FIRSTSEENAT", p -> HODateTime.toDbTimestamp(((TransferBid) p).getFirstSeenAt()), (p, v) -> ((TransferBid) p).setFirstSeenAt((HODateTime) v), true),
                timeCol("LASTSEENAT", p -> HODateTime.toDbTimestamp(((TransferBid) p).getLastSeenAt()), (p, v) -> ((TransferBid) p).setLastSeenAt((HODateTime) v), true)
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

    /** Insert or update the ledger row for a player. */
    public void storeBid(TransferBid bid) {
        bid.setIsStored(isStored(bid.getPlayerId()));
        store(bid);
    }

    /** The ledger row for a player, or null if this team never bid on or tracked him. */
    public TransferBid loadBid(int playerId) {
        return loadOne(TransferBid.class, playerId);
    }
}
