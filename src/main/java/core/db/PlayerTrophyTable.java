package core.db;

import core.model.player.PlayerTrophy;
import core.util.HODateTime;

import java.sql.Types;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * The players' trophies (playerdetails 3.3 TrophyList). Not a snapshot: a trophy is stored once
 * and kept, a re-download adds only the new ones. Type, season and competition identify one.
 */
public class PlayerTrophyTable extends AbstractTable {

    static final String TABLENAME = "PLAYERTROPHY";
    private static final int NAME_LENGTH = 256;

    PlayerTrophyTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 4;
    }

    @Override
    protected void initColumns() {
        columns = new ColumnDescriptor[]{
                col("PLAYERID", Types.INTEGER, false, t -> row(t).getPlayerId(), (t, v) -> row(t).setPlayerId((Integer) v)),
                col("TROPHYTYPEID", Types.INTEGER, false, t -> row(t).getTrophyTypeId(), (t, v) -> row(t).setTrophyTypeId((Integer) v)),
                col("TROPHYSEASON", Types.INTEGER, false, t -> row(t).getTrophySeason(), (t, v) -> row(t).setTrophySeason((Integer) v)),
                col("LEAGUELEVELUNITID", Types.INTEGER, false, t -> row(t).getLeagueLevelUnitId(), (t, v) -> row(t).setLeagueLevelUnitId((Integer) v)),
                col("TEAMID", Types.INTEGER, false, t -> row(t).getTeamId(), (t, v) -> row(t).setTeamId((Integer) v)),
                name("TEAMNAME", t -> row(t).getTeamName(), (t, v) -> row(t).setTeamName((String) v)),
                col("LEAGUELEVEL", Types.INTEGER, true, t -> row(t).getLeagueLevel(), (t, v) -> row(t).setLeagueLevel((Integer) v)),
                name("LEAGUELEVELUNITNAME", t -> row(t).getLeagueLevelUnitName(), (t, v) -> row(t).setLeagueLevelUnitName((String) v)),
                col("GAINEDDATE", Types.TIMESTAMP, true, t -> HODateTime.toDbTimestamp(row(t).getGainedDate()),
                        (t, v) -> row(t).setGainedDate((HODateTime) v))
        };
    }

    private static PlayerTrophy row(Object o) {
        return (PlayerTrophy) o;
    }

    private static ColumnDescriptor col(String name, int type, boolean nullable,
                                        UnaryOperator<Object> getter, BiConsumer<Object, Object> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(type).isNullable(nullable).build();
    }

    private static ColumnDescriptor name(String name, UnaryOperator<Object> getter, BiConsumer<Object, Object> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.VARCHAR).setLength(NAME_LENGTH).isNullable(true).build();
    }

    @Override
    protected String[] getConstraintStatements() {
        return new String[]{"  PRIMARY KEY (PLAYERID, TROPHYTYPEID, TROPHYSEASON, LEAGUELEVELUNITID)"};
    }

    private final String loadByPlayerSql = createSelectStatement("WHERE PLAYERID=? ORDER BY TROPHYSEASON DESC, TROPHYTYPEID");

    /** The player's trophies, newest season first. */
    List<PlayerTrophy> loadPlayerTrophies(int playerId) {
        return load(PlayerTrophy.class, connectionManager.executePreparedQuery(loadByPlayerSql, playerId));
    }

    /** Adds the trophies not stored yet; a stored one is left as it is. */
    void storePlayerTrophies(List<PlayerTrophy> trophies) {
        for (var trophy : trophies) {
            if (!isStored(trophy.getPlayerId(), trophy.getTrophyTypeId(), trophy.getTrophySeason(),
                    trophy.getLeagueLevelUnitId())) {
                trophy.setIsStored(false);
                store(trophy);
            }
        }
    }
}
