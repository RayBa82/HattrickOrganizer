package module.lineup.lineup;

import core.datatype.CBItem;
import core.gui.Updatable;
import core.gui.comp.table.PlayersTable;
import core.gui.model.PlayerCBItem;
import core.gui.model.PlayerCBItemRenderer;
import core.gui.theme.HOColorName;
import core.gui.theme.HOIconName;
import core.gui.theme.ImageUtilities;
import core.gui.theme.ThemeManager;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.model.match.Weather;
import core.model.player.IMatchRoleID;
import core.model.player.MatchRoleID;
import core.model.player.Player;
import core.training.TrainingPreviewPlayers;
import core.util.Helper;
import module.lineup.Lineup;
import module.lineup.LineupAssistantSelectorOverlay;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static core.model.UserParameter.GOALKEEPER_AT_TOP;
import static core.model.UserParameter.POSITIONNAMES_SHORT;
import static core.model.player.IMatchRoleID.*;


public class PlayerPositionPanel extends JPanel implements ItemListener, FocusListener {

    private static final PlayerCBItem oNullPlayer = new PlayerCBItem("", 0f, null, false, true);
    private static final String BEHAVIOUR_NORMAL = "ls.player.behaviour.normal";
    private static final String BEHAVIOUR_OFFENSIVE = "ls.player.behaviour.offensive";
    private static final String BEHAVIOUR_DEFENSIVE = "ls.player.behaviour.defensive";
    private static final String BEHAVIOUR_TOWARDS_WING = "ls.player.behaviour.towardswing";

    //~ Instance fields ----------------------------------------------------------------------------
    private final JComboBox<PlayerCBItem> playerCombo = new JComboBox<>() {
        // Tile slots paint only the renderer: no box and no arrow, so the pitch shows through.
        // The combo still takes the clicks and opens its usual popup.
        @Override
        protected void paintComponent(Graphics g) {
            if (!isTile()) {
                super.paintComponent(g);
                return;
            }
            final Component c = getRenderer().getListCellRendererComponent(null, (PlayerCBItem) getSelectedItem(), -1, false, false);
            SwingUtilities.paintComponent(g, c, rendererPane, 0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintChildren(Graphics g) {
            if (!isTile()) super.paintChildren(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            if (!isTile()) super.paintBorder(g);
        }

        @Override
        public boolean isOpaque() {
            return !isTile() && super.isOpaque();
        }

        // BasicComboPopup sizes its list to getSize().width. A tile is narrower than a player row,
        // so report the widest row to everyone but the combo's own layout; measured as the popup opens.
        // ponytail: getSize() hack, a custom ComboPopup if another LAF computes the width differently
        private boolean layingOut;
        private int popupWidth;
        private boolean placingPopup;


        {
            addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    // the popup menu itself fires this once its location is set: placing is over
                    if (e.getSource() instanceof JPopupMenu) {
                        placingPopup = false;
                        return;
                    }
                    final JList<PlayerCBItem> probe = new JList<>();
                    int widest = UserParameter.instance().fontSize * 18;
                    for (int i = 0; i < getItemCount(); i++) {
                        widest = Math.max(widest, getRenderer().getListCellRendererComponent(probe, getItemAt(i), i, false, false).getPreferredSize().width);
                    }
                    // room for the popup's scroll bar and border
                    popupWidth = widest + UIManager.getInt("ScrollBar.width") + 8;
                    if (getUI().getAccessibleChild((JComponent) e.getSource(), 0) instanceof JPopupMenu menu) {
                        menu.removePopupMenuListener(this);
                        menu.addPopupMenuListener(this);
                        placingPopup = true;
                    }
                }

                @Override
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                    // only the opening matters
                }

                @Override
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                    // only the opening matters
                }
            });
        }

        @Override
        public void doLayout() {
            layingOut = true;
            super.doLayout();
            layingOut = false;
        }

        @Override
        public Dimension getSize() {
            final Dimension size = super.getSize();
            if (!layingOut) size.width = Math.max(size.width, popupWidth);
            return size;
        }

        // Every LAF places the popup at the combo's screen position; shifted left by half the overhang
        // while it is placed, a tile's popup opens centred under the tile. The shift stays on the
        // combo's own screen, or the popup would jump to a monitor left of it.
        @Override
        public Point getLocationOnScreen() {
            final Point at = super.getLocationOnScreen();
            if (placingPopup && isTile()) {
                final int screenLeft = getGraphicsConfiguration().getBounds().x;
                at.x = Math.max(screenLeft, at.x - Math.max(0, (popupWidth - getWidth()) / 2));
            }
            return at;
        }
    };
    private final CellRendererPane rendererPane = new CellRendererPane();
    private final JComboBox<CBItem> tacticCombo = new JComboBox<>();
    private final JLabel positionLabel = new JLabel();
    private final transient PlayerCBItem selectedPlayerItem = new PlayerCBItem("", 0f, null, false, true);
    private final transient Updatable updater;
    private transient PlayerCBItem[] playerItems = new PlayerCBItem[0];
    private final int positionId;
    private int iSelectedPlayerId = -1;
    private final GridBagLayout layout = new GridBagLayout();
    private final JLayeredPane jlp = new JLayeredPane();
    private static final int LAYER_INDEX = 0;
    private Weather weather;
    private boolean useWeatherImpact;
    private Integer matchMinute;

    //constructor
    protected PlayerPositionPanel(Updatable updater, int positionsID, @Nullable Weather weather, boolean useWeatherImpact, Integer matchMinute) {
        this.updater = updater;
        positionId = positionsID;
        this.weather = weather;
        this.useWeatherImpact = useWeatherImpact;
        this.matchMinute = matchMinute;

        setOpaque(!isTile());

        initTaktik(null);
        initLabel();
        initComponents();
    }

    protected PlayerPositionPanel(Updatable updater, int positionsID) {
        this(updater, positionsID, null, false, null);
    }

    public int getPositionsID() {
        return positionId;
    }

    /** The slot's short position name, e.g. "IV"; empty while the lineup does not know the slot. */
    String getRoleName() {
        final MatchRoleID position = HOVerwaltung.instance().getModel().getCurrentLineup().getPositionById(positionId);
        return position != null ? MatchRoleID.getShortNameForPosition(position.getPosition()) : "";
    }

    /** Field and bench slots show a kit with a name plate; captain and set pieces stay plain dropdowns. */
    private boolean isTile() {
        return positionId != IMatchRoleID.captain && positionId != IMatchRoleID.setPieces;
    }


    private byte getTactic() {
        CBItem cbTactic = (CBItem) tacticCombo.getSelectedItem();
        return (cbTactic != null) ? (byte)cbTactic.getId() : IMatchRoleID.NORMAL;
    }

    public @Nullable Player getSelectedPlayer() {
        if (playerCombo.getSelectedItem() instanceof PlayerCBItem item) {
            return item.getPlayer();
        }
        return null;
    }

    /**
     * Handle the focus gained event.
     * If a player is selected the PlayersTable's selection is updated
     * @param event the event to be processed
     */
    @Override
    public void focusGained(FocusEvent event) {
        if (getSelectedPlayer() != null) {
            PlayersTable.Companion.setSelectedPlayer(getSelectedPlayer());
        }
    }

    @Override
    public void focusLost(FocusEvent event) {
        // the table keeps the last selected player
    }

    private void initComponents() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0.0;

        setBorder(isTile() ? BorderFactory.createEmptyBorder(2, 2, 2, 2) : PitchStyle.card());

        jlp.setLayout(layout);

        BorderLayout bl = new BorderLayout();
        bl.setHgap(0);
        bl.setVgap(0);
        setLayout(bl);

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(5, 8, 0, 0);
        // cell 1 of this row belongs to the swap button (or the captain / set pieces icon); tiles
        // show the position on their name plate instead of this label
        positionLabel.setVisible(!isTile());
        jlp.add(positionLabel, constraints, LAYER_INDEX);

        constraints.gridy = 1;
        constraints.gridwidth = 2;
        playerCombo.addFocusListener(this);
        playerCombo.setMaximumRowCount(10);
        playerCombo.setRenderer(new TileRenderer());
        playerCombo.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));

        //Show only if more than one tactic is possible
        if (tacticCombo.getItemCount() > 1) {

            constraints.insets = new Insets(2, 4, 0, 4);
            jlp.add(playerCombo, constraints, LAYER_INDEX);

            constraints.gridy = 2;
            constraints.insets = new Insets(2, 4, 5, 4);
            // on a tile the behaviour dropdown is a compact one under the name plate
            if (isTile()) {
                constraints.fill = GridBagConstraints.NONE;
                constraints.anchor = GridBagConstraints.CENTER;
            }
            tacticCombo.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));
            jlp.add(tacticCombo, constraints, LAYER_INDEX);
            // an empty tile hides the behaviour dropdown; this keeps its room, so every tile in a
            // row has one height
            jlp.add(Box.createVerticalStrut(tacticCombo.getPreferredSize().height), constraints, LAYER_INDEX);
        }
        else {
            constraints.insets = new Insets(2, 4, 5, 4);
            jlp.add(playerCombo, constraints, LAYER_INDEX);
        }

        add(jlp, BorderLayout.CENTER);
    }

    @Override
    public void itemStateChanged(java.awt.event.ItemEvent itemEvent) {
        if (itemEvent.getStateChange() != ItemEvent.SELECTED) return;
        final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();

        final Player player = getSelectedPlayer();
        setPlayerTooltip(player);

        if (itemEvent.getSource().equals(playerCombo)) {
            assignPlayer(lineup, player);
            //Adjust tactic values
            setTactic(getTactic(), player);
        } else if (itemEvent.getSource().equals(tacticCombo)) {
            Objects.requireNonNull(lineup.getPositionById(positionId)).setBehaviour(getTactic());
        }

        if (player != null) {
            PlayersTable.Companion.setSelectedPlayer(player);
        }

        //Update all other positions
        updater.update();
    }

    /** Puts the player (or nobody) into this slot: set pieces taker, captain, or a position. */
    private void assignPlayer(Lineup lineup, @Nullable Player player) {
        final int playerId = player != null ? player.getPlayerId() : 0;
        if (positionId == IMatchRoleID.setPieces) {
            lineup.setKicker(playerId);
        } else if (positionId == IMatchRoleID.captain) {
            lineup.setCaptain(playerId);
        } else {
            lineup.setSpielerAtPosition(positionId, playerId);
            lineup.adjustBackupPlayers();
        }
    }

    private void setPlayerTooltip(@Nullable Player player) {
        if (player != null) {
            String tooltipMessage ="<html>";
            tooltipMessage += "<b>" + player.getFullName() + "</b>";
            String trainingMsg = TrainingPreviewPlayers.instance().getTrainPreviewPlayer(player).getText();
            if (trainingMsg != null) {
                tooltipMessage += "<br>" + trainingMsg;
            }

            if (!((PlayerCBItem) Objects.requireNonNull(playerCombo.getSelectedItem())).isSetInBestPosition()) {
                tooltipMessage += "<br>" + getLangStr("ls.lineup.position.warning");
            }
            tooltipMessage +=  "</html>";
            playerCombo.setToolTipText(tooltipMessage);
        }
    }


    /**
     * Update the list of player in the ComboBox except for backup
     *
     * @param inCandidates     the list of players answering all filters criteria
     * @param plStartingLineup the players in the starting 11
     * @param plSubstitutes    the substitute players (not the backup)
     * @param matchMinute      minute used for rating calculation
     */
    public void refresh(List<Player> inCandidates, List<Player> plStartingLineup, List<Player> plSubstitutes, Weather weather, Boolean useWeatherImpact, int matchMinute) {
        var plCandidates = new ArrayList<>(inCandidates);
        this.weather = weather;
        this.useWeatherImpact = useWeatherImpact;
        iSelectedPlayerId = -1;
        this.matchMinute = matchMinute;

        setPlayersList(plCandidates, currentPlayer(plCandidates));
        markLineupMembership(plStartingLineup, plSubstitutes);

        initLabel();
        repaint();
    }

    /**
     * The player now in this slot. Takes the keeper out of the set pieces candidates (Hattrick does
     * not allow it), and on a field or bench slot updates whether it takes a player and its behaviour.
     */
    private @Nullable Player currentPlayer(List<Player> candidates) {
        final HOModel model = HOVerwaltung.instance().getModel();
        final Lineup lineup = model.getCurrentLineup();
        if (positionId == IMatchRoleID.setPieces) {
            final Player keeper = lineup.getPlayerByPositionID(IMatchRoleID.keeper);
            if (keeper != null) {
                candidates.removeIf(pl -> pl.getPlayerId() == keeper.getPlayerId());
            }
            return selected(model.getCurrentPlayer(lineup.getKicker()));
        }
        if (positionId == IMatchRoleID.captain) {
            return selected(model.getCurrentPlayer(lineup.getCaptain()));
        }
        final MatchRoleID position = lineup.getPositionById(positionId);
        if (position == null) return null;
        final Player player = selected(model.getCurrentPlayer(position.getPlayerId()));
        // an empty slot takes a player while the lineup has room, and always on the bench
        playerCombo.setEnabled(player != null || lineup.hasFreePosition() || positionId >= IMatchRoleID.startReserves);
        setTactic(position.getTactic(), player);
        return player;
    }

    private @Nullable Player selected(@Nullable Player player) {
        if (player != null) iSelectedPlayerId = player.getPlayerId();
        return player;
    }

    /** Flags each listed player as in the starting eleven or on the bench. */
    private void markLineupMembership(@Nullable List<Player> plStartingLineup, @Nullable List<Player> plSubstitutes) {
        final boolean known = plStartingLineup != null && plSubstitutes != null;
        for (int i = 0; i < playerCombo.getItemCount(); i++) {
            final PlayerCBItem item = playerCombo.getItemAt(i);
            final Player player = item == null ? null : item.getPlayer();
            if (player != null) {
                final boolean starting = known && containsPlayer(plStartingLineup, player.getPlayerId());
                item.getEntry().setIsSelect(starting);
                item.getEntry().setIsAssit(known && !starting && containsPlayer(plSubstitutes, player.getPlayerId()));
            }
        }
    }


    public void refresh2(List<Player> lPlayers, int playerIDcorrespondingSub, Weather weather, Boolean useWeatherImpact) {
        Player selectedPlayer = null;
        iSelectedPlayerId = -1;
        this.weather = weather;
        this.useWeatherImpact = useWeatherImpact;

        //Get currently setup player in that position
        var team = HOVerwaltung.instance().getModel().getCurrentLineup();
        final MatchRoleID position = team.getPositionById(positionId);
        if (position != null) {
            selectedPlayer = HOVerwaltung.instance().getModel().getCurrentPlayer(position.getPlayerId());
            setTactic(position.getTactic(), selectedPlayer);
        }
        setPlayersList2(lPlayers, selectedPlayer, playerIDcorrespondingSub);
        initLabel();
        repaint();
    }

    protected void setPlayersList(List<Player> oCandidates, @Nullable Player oSelectedPlayer) {
        playerCombo.removeItemListener(this);
        final DefaultComboBoxModel<PlayerCBItem> cbModel = ((DefaultComboBoxModel<PlayerCBItem>) playerCombo.getModel());
        cbModel.removeAllElements();
        resizeItems(oCandidates.size());

        //Put back current player if new filters allows it
        this.iSelectedPlayerId = -1;
        if (oSelectedPlayer != null && containsPlayer(oCandidates, oSelectedPlayer.getPlayerId())) {
            cbModel.addElement(createPlayerCBItem(selectedPlayerItem, oSelectedPlayer));
            this.iSelectedPlayerId = oSelectedPlayer.getPlayerId();
        }

        Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        if (iSelectedPlayerId == -1) {
            switch (positionId){
                case IMatchRoleID.setPieces -> lineup.setKicker(0);
                case IMatchRoleID.captain -> lineup.setCaptain(0);
                default -> lineup.setSpielerAtPosition(positionId, 0);
            }
        }

        final PlayerCBItem[] cbItems = new PlayerCBItem[oCandidates.size()];
        for (int i = 0; i < oCandidates.size(); i++) {
            cbItems[i] = createPlayerCBItem(playerItems[i], oCandidates.get(i));
        }
        addSorted(cbModel, cbItems);

        playerCombo.addItemListener(this);
        setTactic(getTactic(), oSelectedPlayer);
        setPlayerTooltip(selectedPlayerItem.getPlayer());
    }

    protected void setPlayersList2(List<Player> allPlayers, @Nullable Player selectedPlayer, int playerIDcorrespondingSub) {
        playerCombo.removeItemListener(this);

        // list of all players currently set as subs
        final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        final List<Player> lSubs = allPlayers.stream().filter(player -> lineup.isPlayerASub(player.getPlayerId())).toList();

        final DefaultComboBoxModel<PlayerCBItem> cbModel = ((DefaultComboBoxModel<PlayerCBItem>) playerCombo.getModel());
        cbModel.removeAllElements();
        resizeItems(lSubs.size());

        //Remove current Player if not a sub anymore
        if (selectedPlayer != null && containsPlayer(lSubs, selectedPlayer.getPlayerId())) {
            cbModel.addElement(createPlayerCBItem(selectedPlayerItem, selectedPlayer));
        }

        final List<PlayerCBItem> cbItems = new ArrayList<>();
        for (int i = 0; i < lSubs.size(); i++) {
            if (lSubs.get(i).getPlayerId() != playerIDcorrespondingSub) {
                cbItems.add(createPlayerCBItem(playerItems[i], lSubs.get(i)));
            }
        }
        addSorted(cbModel, cbItems.toArray(PlayerCBItem[]::new));

        playerCombo.addItemListener(this);
        setTactic(getTactic(), selectedPlayer);
    }

    /** Grows or shrinks the reusable combo items to {@code count}, keeping the ones there are. */
    private void resizeItems(int count) {
        if (playerItems.length == count) return;
        final PlayerCBItem[] resized = Arrays.copyOf(playerItems, count);
        for (int i = 0; i < count; i++) {
            if (resized[i] == null) resized[i] = new PlayerCBItem("", 0f, null, true, true);
        }
        playerItems = resized;
    }

    /** The "no player" row, then the players sorted. */
    private static void addSorted(DefaultComboBoxModel<PlayerCBItem> cbModel, PlayerCBItem[] cbItems) {
        cbModel.addElement(oNullPlayer);
        Arrays.sort(cbItems);
        for (PlayerCBItem cbItem : cbItems) {
            cbModel.addElement(cbItem);
        }
    }

    private static boolean containsPlayer(List<Player> players, int playerId) {
        return players.stream().anyMatch(player -> player.getPlayerId() == playerId);
    }

    private void setTactic(byte tactic, @Nullable Player currentPlayer) {
        //remove listener
        tacticCombo.removeItemListener(this);

        //Update Tactic!
        initTaktik(currentPlayer);
        // an empty tile has no behaviour to set
        tacticCombo.setVisible(currentPlayer != null || !isTile());

        //Suche nach der Taktik
        Helper.setComboBoxFromID(tacticCombo, tactic);

        //Listener hinzu
        tacticCombo.addItemListener(this);
    }

    /** A rounded frame on the pitch marks the positions next week's training covers. */
    private static javax.swing.border.Border trainingBorder(byte position, int nextWeekTrain) {
        if (MatchRoleID.isFullTrainPosition(position, nextWeekTrain)) {
            return BorderFactory.createLineBorder(ThemeManager.getColor(HOColorName.LINEUP_FULL_TRAINING), 2, true);
        }
        if (MatchRoleID.isPartialTrainPosition(position, nextWeekTrain)) {
            return BorderFactory.createLineBorder(ThemeManager.getColor(HOColorName.LINEUP_PARTIAL_TRAINING), 2, true);
        }
        return BorderFactory.createEmptyBorder(2, 2, 2, 2);
    }

    private void initLabel() {
        final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        final int nextWeekTrain = TrainingPreviewPlayers.instance().getNextWeekTraining();

        if (positionId == IMatchRoleID.setPieces) {
            positionLabel.setText(getLangStr("match.setpiecestaker"));
        }
        else if (positionId == IMatchRoleID.captain) {
            positionLabel.setText(getLangStr("Spielfuehrer"));
        }
        else {
            final MatchRoleID position = lineup.getPositionById(positionId);

            if (position != null) {
                Font defaultFont = positionLabel.getFont();
                int fontSize = defaultFont.getSize();
                String fontFamily = defaultFont.getFamily();
                String hexColor = ImageUtilities.getHexColor(HOColorName.RED);

                final String  nameForPosition1 = "<html> <font family=" + fontFamily +  "size=" + fontSize + "pt>";
                final String  nameForPosition2 = "</font> <font family=" + fontFamily + "size=" + fontSize + "pt color=" + hexColor + ">&nbsp&nbsp";
                final String  nameForPosition3 = "</font></html>";
                final String nameForPosition = nameForPosition1 + getNameForLineupPosition(position.getPosition()) + nameForPosition2 + getTacticSymbol() + nameForPosition3;

                // Players on the lineup
                if (IMatchRoleID.aFieldMatchRoleID.contains(position.getId())) {
                    positionLabel.setText(nameForPosition);
                    this.setBorder(trainingBorder(position.getPosition(), nextWeekTrain));
                }
                // Subs
                else if (IMatchRoleID.aSubstitutesMatchRoleID.contains(position.getId())) {
                    positionLabel.setText(nameForPosition + " (#1)");
                }
                // Backups
                else {
                    positionLabel.setText(nameForPosition + " (#2)");
                }

                positionLabel.setToolTipText(MatchRoleID.getNameForPositionWithoutTactic(position.getPosition()));
            }
        }
        positionLabel.setFont(getFont().deriveFont(Font.BOLD));
    }

    private String getNameForLineupPosition(byte position) {
        if (UserParameter.instance().lineupPositionNamesSetting == POSITIONNAMES_SHORT) {
            return MatchRoleID.getShortNameForPosition(position);
        }
        return MatchRoleID.getNameForPosition(position);
    }

    private void initTaktik(@Nullable Player aktuellerPlayer) {
        tacticCombo.removeAllItems();

        switch (positionId) {
            case IMatchRoleID.rightBack, IMatchRoleID.leftBack, IMatchRoleID.leftWinger, IMatchRoleID.rightWinger -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_OFFENSIVE), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_DEFENSIVE), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.towardsmiddle"), IMatchRoleID.TOWARDS_MIDDLE);
            }
            case IMatchRoleID.rightCentralDefender, IMatchRoleID.leftCentralDefender -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_OFFENSIVE), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_TOWARDS_WING), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.middleCentralDefender -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_OFFENSIVE), IMatchRoleID.OFFENSIVE);
            }
            case IMatchRoleID.rightInnerMidfield, IMatchRoleID.leftInnerMidfield -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_OFFENSIVE), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_DEFENSIVE), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_TOWARDS_WING), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.centralInnerMidfield -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_OFFENSIVE), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_DEFENSIVE), IMatchRoleID.DEFENSIVE);
            }
            case IMatchRoleID.rightForward, IMatchRoleID.leftForward -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_DEFENSIVE), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_TOWARDS_WING), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.centralForward -> {
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr(BEHAVIOUR_DEFENSIVE), IMatchRoleID.DEFENSIVE);
            }
            // keeper, captain, set pieces and the bench: normal only, without a rating
            default -> tacticCombo.addItem(new CBItem(getLangStr(BEHAVIOUR_NORMAL), IMatchRoleID.NORMAL));
        }
    }

    private void addTactic(@Nullable Player currentPlayer, String text, byte playerPosition) {
        if (currentPlayer != null) {
            var ratingPredictionModel = HOVerwaltung.instance().getModel().getRatingPredictionModel();
            double rating;
            if ( matchMinute == null || matchMinute < 0 || matchMinute >= 120) {
                rating = ratingPredictionModel.getPlayerMatchAverageRating(currentPlayer, positionId, playerPosition);
            }
            else {
                rating = ratingPredictionModel.getPlayerRating(currentPlayer, positionId, playerPosition, matchMinute);
            }
            text += String.format(" (%.2f)", rating);
        }
        tacticCombo.addItem(new CBItem(text, playerPosition));
    }
    //-------------private-------------------------------------------------

    private PlayerCBItem createPlayerCBItem(PlayerCBItem item, @Nullable Player player) {
        if (player == null) return oNullPlayer;
        final String playerName = player.getShortName();
        final HOModel model = HOVerwaltung.instance().getModel();

        if (positionId == IMatchRoleID.setPieces) {
            item.setValues(playerName, (float) model.getRatingPredictionModel().getPlayerSetPiecesStrength(player), player, true);
            return item;
        }
        if (positionId == IMatchRoleID.captain) {
            final float experience = Helper.round(model.getCurrentLineup().getAverageExperience(player.getPlayerId()), UserParameter.instance().nbDecimals);
            item.setValues(playerName, experience, player, true);
            return item;
        }
        final var position = model.getCurrentLineup().getPositionById(positionId);
        if (position == null) return oNullPlayer;

        final int roleId = ratingRoleId(position.getRoleId());
        // the extra substitute has no role to rate
        final boolean rated = roleId != substXT1 && roleId != substXT2;
        final double value = rated ? playerRating(player, roleId, position.getBehaviour()) : 0;
        final boolean bestPosition = rated && player.getAlternativeBestPositions().contains(position.getPosition());
        item.setValues(playerName, (float) value, player, bestPosition);
        return item;
    }

    /** Substitutes are rated in the field role they stand in for. */
    private static int ratingRoleId(int roleId) {
        return switch (roleId) {
            case substGK1, substGK2 -> keeper;
            case substCD1, substCD2 -> leftCentralDefender;
            case substWB1, substWB2 -> leftBack;
            case substWI1, substWI2 -> leftWinger;
            case substIM1, substIM2 -> leftInnerMidfield;
            case substFW1, substFW2 -> leftForward;
            default -> roleId;
        };
    }

    /** Rating at the selected match minute, or the match average when none is selected. */
    private double playerRating(Player player, int roleId, byte behaviour) {
        final var ratingPredictionModel = HOVerwaltung.instance().getModel().getRatingPredictionModel();
        final Weather matchWeather = useWeatherImpact ? weather : Weather.UNKNOWN;
        if (matchMinute == null || matchMinute < 0 || matchMinute > 120) {
            return ratingPredictionModel.getPlayerMatchAverageRating(player, roleId, behaviour, matchWeather);
        }
        return ratingPredictionModel.getPlayerRating(player, roleId, behaviour, matchMinute, matchWeather);
    }

    public int getiSelectedPlayerId() {
        return iSelectedPlayerId;
    }


    // return a tactic symbol if a player is offensive, defensive, towards wings etc.
    // used for lineup export
    public String getTacticSymbol() {
        if (getiSelectedPlayerId() == -1) return "";
        final boolean keeperAtTop = UserParameter.instance().lineupOrientationSetting == GOALKEEPER_AT_TOP;
        final int positionsID = getPositionsID();
        return switch (getTactic()) {
            case IMatchRoleID.OFFENSIVE -> keeperAtTop ? "▼" : "▲";
            case IMatchRoleID.DEFENSIVE -> keeperAtTop ? "▲" : "▼";
            case IMatchRoleID.TOWARDS_MIDDLE -> sideways(keeperAtTop,
                    positionsID == IMatchRoleID.rightBack || positionsID == IMatchRoleID.rightWinger);
            case IMatchRoleID.TOWARDS_WING -> sideways(keeperAtTop, !(positionsID == IMatchRoleID.rightCentralDefender
                    || positionsID == IMatchRoleID.rightInnerMidfield || positionsID == IMatchRoleID.rightForward));
            default -> "";
        };
    }

    /** A sideways arrow, given where it points with the keeper at the bottom; keeper at the top mirrors it. */
    private static String sideways(boolean keeperAtTop, boolean leftWithKeeperAtBottom) {
        return leftWithKeeperAtBottom != keeperAtTop ? "◀" : "▶";
    }

    /**
     * Exposes the player combo box to reset the swap button if needed.
     *
     * @return the player {@link JComboBox}.
     */
    protected JComboBox<PlayerCBItem> getPlayerComboBox() {
        return playerCombo;
    }

    public LayoutManager getSwapLayout() {
        return layout;
    }

    public void addSwapItem(Component c) {
        if (isTile()) {
            // span both columns so the arrows sit centred over the kit, like the dropdown below
            final GridBagConstraints constraints = layout.getConstraints(c);
            constraints.gridx = 0;
            constraints.gridwidth = 2;
            constraints.anchor = GridBagConstraints.CENTER;
            constraints.insets = new Insets(5, 0, 0, 0);
            layout.setConstraints(c, constraints);
        }
        jlp.add(c, LAYER_INDEX);
        if (isTile() && c instanceof AbstractButton button) {
            showOnHover(button);
        }
    }

    /** Watches the pointer for {@link #showOnHover}; registered only while the tile is on screen. */
    private transient java.awt.event.@Nullable AWTEventListener hoverWatcher;

    /**
     * On a tile the swap arrows show only while the pointer is on the slot, or while a swap is
     * pending. The icon is blanked rather than the button hidden, so the tile keeps its place.
     * <p>
     * Enter/exit listeners are not enough here: the combos' own children swallow them and the
     * tiles' areas overlap, so the tile tests every pointer move against its own screen bounds.
     */
    private void showOnHover(AbstractButton button) {
        // the theme's green arrows vanish on the grass: white ones on the name plate's dark disc
        final Icon arrows = onPlate(ImageUtilities.getSvgIcon(HOIconName.SWAP, java.util.Map.of("fillColor", Color.WHITE), 16, 16));
        button.setSelectedIcon(onPlate(ImageUtilities.getSvgIcon(HOIconName.SWAP, java.util.Map.of("fillColor", PitchStyle.SAND), 16, 16)));
        final Dimension size = new Dimension(arrows.getIconWidth(), arrows.getIconHeight());
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        final Icon blank = new ImageIcon(new java.awt.image.BufferedImage(arrows.getIconWidth(), arrows.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB));
        final java.util.function.Consumer<@Nullable Point> update = pointer -> button.setIcon(
                button.isSelected() || pointer != null && isShowing()
                        && new Rectangle(getLocationOnScreen(), getSize()).contains(pointer) ? arrows : blank);
        hoverWatcher = e -> {
            if (e instanceof java.awt.event.MouseEvent mouse && isShowing()) {
                update.accept(mouse.getLocationOnScreen());
            }
        };
        button.addItemListener(e -> update.accept(null));
        update.accept(null);
        if (isDisplayable()) {
            watchHover();
        }
    }

    private static Icon onPlate(Icon icon) {
        final int d = Math.max(icon.getIconWidth(), icon.getIconHeight()) + 8;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                final Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(PitchStyle.PLATE);
                g.fillOval(x, y, d, d);
                icon.paintIcon(c, g, x + (d - icon.getIconWidth()) / 2, y + (d - icon.getIconHeight()) / 2);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return d;
            }

            @Override
            public int getIconHeight() {
                return d;
            }
        };
    }

    private void watchHover() {
        if (hoverWatcher != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(hoverWatcher);
            Toolkit.getDefaultToolkit().addAWTEventListener(hoverWatcher,
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        watchHover();
    }

    @Override
    public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(hoverWatcher);
        super.removeNotify();
    }

    public void addAssistantOverlay(LineupAssistantSelectorOverlay overlay) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.gridheight = 3;
        jlp.add(overlay, constraints, LAYER_INDEX+1);
        repaint();
    }

    public void removeAssistantOverlay(LineupAssistantSelectorOverlay overlay) {
        jlp.remove(overlay);
        repaint();
    }


    public void addCaptainIcon() {
        addCornerIcon(ImageUtilities.getSmileyIcon("smiley-coach"), 5);
    }

    public void addSetPiecesIcon() {
        addCornerIcon(ImageUtilities.getSetPiecesIcon(21, 21), 2);
    }

    /** An icon in the top right cell, where other slots have their swap button. */
    private void addCornerIcon(Icon icon, int top) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(top, 0, 0, 8);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;
        jlp.add(new JLabel(icon), constraints, LAYER_INDEX);
    }

    /**
     * Draws the closed player dropdown of a tile slot as a {@link SlotView}. The open list keeps HO's
     * usual rows, so the dropdown stays the way to pick a player.
     */
    private final class TileRenderer implements ListCellRenderer<PlayerCBItem> {
        private final PlayerCBItemRenderer listRenderer = new PlayerCBItemRenderer();
        private final SlotView slot = new SlotView();

        @Override
        public Component getListCellRendererComponent(JList<? extends PlayerCBItem> list, PlayerCBItem value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (index != -1 || !isTile()) {
                return listRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
            slot.item = value;
            return slot;
        }
    }

    /**
     * One slot the way the TV-view lineup page draws it: a kit in the club colours with the shirt
     * number, flags for captain, set pieces and behaviour, and a dark name plate with surname,
     * position and rating. An empty slot is a faint ghost circle.
     */
    private final class SlotView extends JComponent {
        private transient @Nullable PlayerCBItem item;

        private boolean isOnPitch() {
            return IMatchRoleID.aFieldMatchRoleID.contains(positionId);
        }

        private int fontSize() {
            return UserParameter.instance().fontSize;
        }

        private int kitSize() {
            return Math.round(fontSize() * 2.6f);
        }

        private Font nameFont() {
            return PitchStyle.display(fontSize() + 2f);
        }

        private Font infoFont() {
            return PitchStyle.display(fontSize() - 1f).deriveFont(Font.PLAIN);
        }

        @Override
        public Dimension getPreferredSize() {
            final int plate = getFontMetrics(nameFont()).getHeight() + getFontMetrics(infoFont()).getHeight() + 4;
            return new Dimension(fontSize() * 9, kitSize() + 6 + plate);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            final Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final int kit = kitSize();
            final int kx = (getWidth() - kit) / 2;
            final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
            final String role = getRoleName();
            final Player player = item != null ? item.getPlayer() : null;
            if (player == null) {
                paintGhost(g, kx, kit, role);
            } else {
                final boolean keeper = positionId == IMatchRoleID.keeper || positionId == substGK1 || positionId == substGK2;
                PitchStyle.kit(keeper, player.getShirtNumber(), kit).paintIcon(this, g, kx, 2);
                int fx = kx + kit * 3 / 4;
                final float flagSize = fontSize() - 2f;
                if (player.getPlayerId() == lineup.getCaptain()) {
                    fx += 2 + PitchStyle.paintFlag(g, "C", Color.WHITE, PitchStyle.INK, fx, flagSize);
                }
                if (player.getPlayerId() == lineup.getKicker()) {
                    fx += 2 + PitchStyle.paintFlag(g, "SP", PitchStyle.SEA, Color.WHITE, fx, flagSize);
                }
                final String behaviour = getTacticSymbol();
                if (!behaviour.isEmpty()) {
                    PitchStyle.paintFlag(g, behaviour, PitchStyle.SAND, PitchStyle.ON_SAND, fx, flagSize);
                }
                paintPlate(g, player, role, kit + 6);
            }
            g.dispose();
        }

        private void paintGhost(Graphics2D g, int kx, int kit, String role) {
            // pitch and bench board are both green; on the bench the column head names the position
            final Color faint = Color.WHITE;
            final String label = isOnPitch() ? role : "+";
            g.setColor(alpha(faint, 36));
            g.fillOval(kx, 2, kit, kit);
            g.setColor(alpha(faint, 110));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g.drawOval(kx, 2, kit, kit);
            g.setFont(PitchStyle.display(fontSize()));
            final FontMetrics fm = g.getFontMetrics();
            g.setColor(alpha(faint, 190));
            final String shown = fit(label, fm, kit - 2);
            g.drawString(shown, kx + (kit - fm.stringWidth(shown)) / 2, 2 + (kit + fm.getAscent()) / 2 - 1);
        }

        private void paintPlate(Graphics2D g, Player player, String fullRole, int y) {
            final FontMetrics nf = g.getFontMetrics(nameFont());
            final FontMetrics inf = g.getFontMetrics(infoFont());
            final String role = fit(fullRole, inf, fontSize() * 4);
            final String name = player.getLastName().toUpperCase();
            // extra substitutes have no position to rate
            final float stars = item != null ? item.getPositionsEvaluation() : 0;
            final String rating = stars > 0 ? String.format("  %.2f", stars) : "";
            final Color specialty = PitchStyle.specialtyColor(player.getSpecialty());
            final int dot = specialty != null ? inf.getAscent() * 2 / 3 : 0;
            final int infoWidth = (dot > 0 ? dot + 4 : 0) + inf.stringWidth(role + rating);

            final int w = getWidth();
            final int pw = Math.min(w - 2, Math.max(nf.stringWidth(name), infoWidth) + 14);
            final int ph = nf.getHeight() + inf.getHeight() + 2;
            final int px = (w - pw) / 2;
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(px, y + 2, pw, ph, 6, 6);
            g.setColor(PitchStyle.PLATE);
            g.fillRoundRect(px, y, pw, ph, 6, 6);

            final String shown = fit(name, nf, pw - 8);
            g.setFont(nameFont());
            g.setColor(Color.WHITE);
            g.drawString(shown, (w - nf.stringWidth(shown)) / 2, y + 1 + nf.getAscent());

            final int baseline = y + 1 + nf.getHeight() + inf.getAscent();
            int x = (w - infoWidth) / 2;
            if (specialty != null) {
                g.setColor(specialty);
                g.fillOval(x, baseline - dot, dot, dot);
                x += dot + 4;
            }
            g.setFont(infoFont());
            g.setColor(alpha(Color.WHITE, 215));
            g.drawString(role, x, baseline);
            // a player outside his best positions gets his rating in sand; the tooltip says why
            g.setColor(item != null && item.isSetInBestPosition() ? alpha(Color.WHITE, 215) : PitchStyle.SAND);
            g.drawString(rating, x + inf.stringWidth(role), baseline);
        }

        private static String fit(String text, FontMetrics fm, int width) {
            if (fm.stringWidth(text) <= width) return text;
            String s = text;
            while (s.length() > 1 && fm.stringWidth(s + "…") > width) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "…";
        }

        private static Color alpha(Color c, int a) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
        }
    }

    private String getLangStr(String key) {
        return TranslationFacility.tr(key);
    }

}
