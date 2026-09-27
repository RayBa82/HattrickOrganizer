package module.lineup.lineup;

import core.datatype.CBItem;
import core.gui.Updatable;
import core.gui.comp.table.PlayersTable;
import core.gui.model.PlayerCBItem;
import core.gui.model.PlayerCBItemRenderer;
import core.gui.theme.HOColorName;
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
    private static final Color defaultBorderColor = ThemeManager.getColor(HOColorName.PLAYER_POSITION_PANEL_BORDER);

    //~ Instance fields ----------------------------------------------------------------------------
    private final JComboBox<PlayerCBItem> m_jcbPlayer = new JComboBox<>() {
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

        {
            addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    final JList<PlayerCBItem> probe = new JList<>();
                    int widest = UserParameter.instance().fontSize * 18;
                    for (int i = 0; i < getItemCount(); i++) {
                        widest = Math.max(widest, getRenderer().getListCellRendererComponent(probe, getItemAt(i), i, false, false).getPreferredSize().width);
                    }
                    // room for the popup's scroll bar and border
                    popupWidth = widest + UIManager.getInt("ScrollBar.width") + 8;
                }

                @Override
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                @Override
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
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
    };
    private final CellRendererPane rendererPane = new CellRendererPane();
    private final JComboBox<CBItem> m_jcbTactic = new JComboBox<>();
    private final JLabel m_jlPosition = new JLabel();
    private final PlayerCBItem m_clSelectedPlayer = new PlayerCBItem("", 0f, null, false, true);
    private final Updatable m_clUpdater;
    private PlayerCBItem @Nullable [] m_clCBItems = new PlayerCBItem[0];
    private final int m_iPositionID;
    private int iSelectedPlayerId = -1;
    private final GridBagLayout layout = new GridBagLayout();
    private final JLayeredPane jlp = new JLayeredPane();
    private final int layerIndex = 0;
    private Weather m_weather;
    private boolean m_useWeatherImpact;
    private Integer matchMinute;

    //constructor
    protected PlayerPositionPanel(Updatable updater, int positionsID, @Nullable Weather weather, boolean useWeatherImpact, Integer matchMinute) {
        m_clUpdater = updater;
        m_iPositionID = positionsID;
        m_weather = weather;
        m_useWeatherImpact = useWeatherImpact;
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
        return m_iPositionID;
    }

    /** Field and bench slots show a kit with a name plate; captain and set pieces stay plain dropdowns. */
    private boolean isTile() {
        return m_iPositionID != IMatchRoleID.captain && m_iPositionID != IMatchRoleID.setPieces;
    }

    private boolean isOnPitch() {
        return IMatchRoleID.aFieldMatchRoleID.contains(m_iPositionID);
    }

    private byte getTactic() {
        CBItem cbTactic = (CBItem) m_jcbTactic.getSelectedItem();
        return (cbTactic != null) ? (byte)cbTactic.getId() : IMatchRoleID.NORMAL;
    }

    public @Nullable Player getSelectedPlayer() {
        final Object obj = m_jcbPlayer.getSelectedItem();

        if (obj instanceof PlayerCBItem) {
            return ((PlayerCBItem) obj).getPlayer();
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
    public void focusLost(FocusEvent event) { }

    private void initComponents() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0.0;

        setBorder(isTile() ? BorderFactory.createEmptyBorder(2, 2, 2, 2) : BorderFactory.createMatteBorder(3, 3, 3, 3, defaultBorderColor));

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
        m_jlPosition.setVisible(!isTile());
        jlp.add(m_jlPosition, constraints, layerIndex);

        constraints.gridy = 1;
        constraints.gridwidth = 2;
        m_jcbPlayer.addFocusListener(this);
        m_jcbPlayer.setMaximumRowCount(10);
        m_jcbPlayer.setRenderer(new TileRenderer());
        m_jcbPlayer.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));

        //Show only if more than one tactic is possible
        if (m_jcbTactic.getItemCount() > 1) {

            constraints.insets = new Insets(2, 4, 0, 4);
            jlp.add(m_jcbPlayer, constraints, layerIndex);

            constraints.gridy = 2;
            constraints.insets = new Insets(2, 4, 5, 4);
            // on a tile the behaviour dropdown is a compact one under the name plate
            if (isTile()) {
                constraints.fill = GridBagConstraints.NONE;
                constraints.anchor = GridBagConstraints.CENTER;
            }
            m_jcbTactic.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));
            jlp.add(m_jcbTactic, constraints, layerIndex);
        }
        else {
            constraints.insets = new Insets(2, 4, 5, 4);
            jlp.add(m_jcbPlayer, constraints, layerIndex);
        }

        add(jlp, BorderLayout.CENTER);
    }

    @Override
    public void itemStateChanged(java.awt.event.ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
            final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();

            final Player player = getSelectedPlayer();
            setPlayerTooltip(player);

            //set player
            if (itemEvent.getSource().equals(m_jcbPlayer)) {
                //set pieces
                if (m_iPositionID == IMatchRoleID.setPieces) {
                    if (player != null) {
                        lineup.setKicker(player.getPlayerId());
                    } else {
                        lineup.setKicker(0);
                    }
                }
                //captain
                else if (m_iPositionID == IMatchRoleID.captain) {
                    if (player != null) {
                        lineup.setCaptain(player.getPlayerId());
                    } else {
                        lineup.setCaptain(0);
                    }
                }
                //Others
                else {
                    if (player != null) {
                        lineup.setSpielerAtPosition(m_iPositionID, player.getPlayerId());
                    } else {
                        lineup.setSpielerAtPosition(m_iPositionID, 0);
                    }
                    // adjust backup players
                    lineup.adjustBackupPlayers();
                }

                //Adjust tactic values
                setTactic(getTactic(), player);
            } else if (itemEvent.getSource().equals(m_jcbTactic)) {
                Objects.requireNonNull(lineup.getPositionById(m_iPositionID)).setBehaviour(getTactic());
            }

            if (player != null) {
                PlayersTable.Companion.setSelectedPlayer(player);
            }

            //Update all other positions
            m_clUpdater.update();
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

            if (!((PlayerCBItem) Objects.requireNonNull(m_jcbPlayer.getSelectedItem())).isSetInBestPosition()) {
                tooltipMessage += "<br>" + getLangStr("ls.lineup.position.warning");
            }
            tooltipMessage +=  "</html>";
            m_jcbPlayer.setToolTipText(tooltipMessage);
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
        Player selectedPlayer = null;
        HOModel model = HOVerwaltung.instance().getModel();
        Lineup lineup = model.getCurrentLineup();

        m_weather = weather;
        m_useWeatherImpact = useWeatherImpact;
        iSelectedPlayerId = -1;
        this.matchMinute = matchMinute;

        if (m_iPositionID == IMatchRoleID.setPieces) {
            selectedPlayer = model.getCurrentPlayer(lineup.getKicker());
            if (selectedPlayer != null) {
                iSelectedPlayerId = selectedPlayer.getPlayerId();
            }

            // Filter keeper from the candidates for SetPieces taker (not allowed by HT)
            Player keeper = lineup.getPlayerByPositionID(IMatchRoleID.keeper);
            if (keeper != null) {
                int iKeeperID = keeper.getPlayerId();
                plCandidates.removeIf(pl -> pl.getPlayerId() == iKeeperID);
            }
        } else if (m_iPositionID == IMatchRoleID.captain) {
            selectedPlayer = model.getCurrentPlayer(lineup.getCaptain());
            if (selectedPlayer != null) {
                iSelectedPlayerId = selectedPlayer.getPlayerId();
            }

        } else {
            final MatchRoleID position = lineup.getPositionById(m_iPositionID);

            if (position != null) {
                selectedPlayer = model.getCurrentPlayer(position.getPlayerId());

                if (selectedPlayer != null) {
                    m_jcbPlayer.setEnabled(true); // To be sure
                    iSelectedPlayerId = selectedPlayer.getPlayerId();
                } else {
                    // We want to enable the combobox if there is room in the lineup or if it is a substitute position
                    m_jcbPlayer.setEnabled((lineup.hasFreePosition()) || (m_iPositionID >= IMatchRoleID.startReserves));
                }

                setTactic(position.getTactic(), selectedPlayer);
            }
        }

        setPlayersList(plCandidates, selectedPlayer);

        // for all players in the combobox set correct values for isSelect (starting 11) and isAssis (it is a subsitute)
        for (int i = 0; i < m_jcbPlayer.getModel().getSize(); i++) {
            PlayerCBItem obj = m_jcbPlayer.getItemAt(i);
            if (obj != null) {
                if (obj.getPlayer() != null) {
                    obj.getEntry().setIsSelect(false);
                    obj.getEntry().setIsAssit(false);
                }
            }
        }
        if (plStartingLineup != null && plSubstitutes != null) {
            for (int i = 0; i < m_jcbPlayer.getModel().getSize(); i++) {
                PlayerCBItem obj = m_jcbPlayer.getItemAt(i);
                boolean isInLineup = false;
                if (obj != null) {
                    if (obj.getPlayer() != null) {
                        for (Player value : plStartingLineup) {
                            if (obj.getPlayer().getPlayerId() == value.getPlayerId()) {
                                obj.getEntry().setIsSelect(true);
                                isInLineup = true;
                                break;
                            }
                        }
                        if (!isInLineup) {
                            for (Player value : plSubstitutes) {
                                if (obj.getPlayer().getPlayerId() == value.getPlayerId()) {
                                    obj.getEntry().setIsAssit(true);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        initLabel();
        repaint();
    }


    public void refresh2(List<Player> lPlayers, int playerIDcorrespondingSub, Weather weather, Boolean useWeatherImpact) {
        Player selectedPlayer = null;
        iSelectedPlayerId = -1;
        m_weather = weather;
        m_useWeatherImpact = useWeatherImpact;

        //Get currently setup player in that position
        var team = HOVerwaltung.instance().getModel().getCurrentLineup();
        final MatchRoleID position = team.getPositionById(m_iPositionID);
        if (position != null) {
            selectedPlayer = HOVerwaltung.instance().getModel().getCurrentPlayer(position.getPlayerId());
            setTactic(position.getTactic(), selectedPlayer);
        }
        setPlayersList2(lPlayers, selectedPlayer, playerIDcorrespondingSub);
        initLabel();
        repaint();
    }

    protected void setPlayersList(List<Player> oCandidates, @Nullable Player oSelectedPlayer) {

        m_jcbPlayer.removeItemListener(this);

        final DefaultComboBoxModel<PlayerCBItem> cbModel = ((DefaultComboBoxModel<PlayerCBItem>) m_jcbPlayer.getModel());

        //Remove all items
        cbModel.removeAllElements();

        //Ensure the number of m_clCBItems objects match what is needed
        assert m_clCBItems != null;
        if (m_clCBItems.length != oCandidates.size()) {
            PlayerCBItem[] tempCB = new PlayerCBItem[oCandidates.size()];

            //Fill with SpielerCBItem: Preferably reuse old ones
            for (int i = 0; i < tempCB.length; i++) {
                //Reuse
                if ((m_clCBItems.length > i) && (m_clCBItems[i] != null)) {
                    tempCB[i] = m_clCBItems[i];

                    //HOLogger.instance().log(getClass(), "Use old SpielerCBItem " + this.m_iPositionsID );
                }
                //Create new
                else {
                    tempCB[i] = new PlayerCBItem("", 0f, null, true, true);//HOLogger.instance().log(getClass(), "Create new SpielerCBItem " + this.m_iPositionsID );
                }
            }

            //Empty reference and reset
            m_clCBItems = tempCB;
        }

        //Put back current player if new filters allows it
        this.iSelectedPlayerId = -1;
        if (oSelectedPlayer != null) {
            int iSelectedPlayerID = oSelectedPlayer.getPlayerId();
            for (Player p : oCandidates) {
                if (p.getPlayerId() == iSelectedPlayerID) {
                    cbModel.addElement(createPlayerCBItem(m_clSelectedPlayer, oSelectedPlayer));
                    this.iSelectedPlayerId = iSelectedPlayerID;
                    break;
                }
            }
        }

        Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        if (iSelectedPlayerId == -1) {
            switch (m_iPositionID){
                case IMatchRoleID.setPieces -> lineup.setKicker(0);
                case IMatchRoleID.captain -> lineup.setCaptain(0);
                default -> lineup.setSpielerAtPosition(m_iPositionID, 0);
            }
        }

        //No Player
        cbModel.addElement(oNullPlayer);

        //Sort Player List
        PlayerCBItem[] cbItems = new PlayerCBItem[oCandidates.size()];

        for (int i = 0; i < oCandidates.size(); i++) {
            cbItems[i] = createPlayerCBItem(m_clCBItems[i], oCandidates.get(i));
        }

        Arrays.sort(cbItems);

        for (PlayerCBItem cbItem : cbItems) {
            //All Other players
            cbModel.addElement(cbItem);
        }


        m_jcbPlayer.addItemListener(this);

        setTactic(getTactic(), oSelectedPlayer);
        setPlayerTooltip(m_clSelectedPlayer.getPlayer());
    }

    protected void setPlayersList2(List<Player> allPlayers, @Nullable Player selectedPlayer, int playerIDcorrespondingSub) {

        m_jcbPlayer.removeItemListener(this);

        // list of all players currently set as subs
        List<Player> lSubs = new ArrayList<>();
        Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        for (Player player : allPlayers) {
            if (lineup.isPlayerASub(player.getPlayerId())) {
                lSubs.add(player);
            }
        }

        final DefaultComboBoxModel<PlayerCBItem> cbModel = ((DefaultComboBoxModel<PlayerCBItem>) m_jcbPlayer.getModel());

        //Remove all items
        cbModel.removeAllElements();

        //Ensure the number of m_clCBItems objects match what is needed
        assert m_clCBItems != null;
        if (m_clCBItems.length != lSubs.size()) {
            PlayerCBItem[] tempCB = new PlayerCBItem[lSubs.size()];

            //Fill with SpielerCBItem: Preferably reuse old ones
            for (int i = 0; i < tempCB.length; i++) {
                //Reuse
                if ((m_clCBItems.length > i) && (m_clCBItems[i] != null)) {
                    tempCB[i] = m_clCBItems[i];
                }
                //Create new
                else {
                    tempCB[i] = new PlayerCBItem("", 0f, null, true, true);
                }
            }

            //Empty reference and reset
            m_clCBItems = tempCB;
        }

        //Remove current Player if not a sub anymore
        if (selectedPlayer != null) {
            for (Player p : lSubs) {
                if (p.getPlayerId() == selectedPlayer.getPlayerId())
                    cbModel.addElement(createPlayerCBItem(m_clSelectedPlayer, selectedPlayer));
            }
        }

        //No Player
        cbModel.addElement(oNullPlayer);

        //Sort Player List
        PlayerCBItem[] cbItems = new PlayerCBItem[lSubs.size()];

        Player pp;
        for (int i = 0; i < lSubs.size(); i++) {
            pp = lSubs.get(i);
            if (pp.getPlayerId() != playerIDcorrespondingSub) {
                cbItems[i] = createPlayerCBItem(m_clCBItems[i], pp);
            }
        }

        cbItems = Arrays.stream(cbItems).filter(Objects::nonNull).toArray(PlayerCBItem[]::new);

        java.util.Arrays.sort(cbItems);

        for (PlayerCBItem cbItem : cbItems) {
            //All Other players
            cbModel.addElement(cbItem);
        }


        m_jcbPlayer.addItemListener(this);

        setTactic(getTactic(), selectedPlayer);
    }

    private void setTactic(byte tactic, @Nullable Player currentPlayer) {
        //remove listener
        m_jcbTactic.removeItemListener(this);

        //Update Tactic!
        initTaktik(currentPlayer);
        // an empty tile has no behaviour to set
        m_jcbTactic.setVisible(currentPlayer != null || !isTile());

        //Suche nach der Taktik
        Helper.setComboBoxFromID(m_jcbTactic, tactic);

        //Listener hinzu
        m_jcbTactic.addItemListener(this);
    }

    private void initLabel() {
        final Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
        final int nextWeekTrain = TrainingPreviewPlayers.instance().getNextWeekTraining();

        if (m_iPositionID == IMatchRoleID.setPieces) {
            m_jlPosition.setText(getLangStr("match.setpiecestaker"));
        }
        else if (m_iPositionID == IMatchRoleID.captain) {
            m_jlPosition.setText(getLangStr("Spielfuehrer"));
        }
        else {
            final MatchRoleID position = lineup.getPositionById(m_iPositionID);

            if (position != null) {
                Font defaultFont = m_jlPosition.getFont();
                int fontSize = defaultFont.getSize();
                String fontFamily = defaultFont.getFamily();
                String hexColor = ImageUtilities.getHexColor(HOColorName.RED);

                final String  nameForPosition1 = "<html> <font family=" + fontFamily +  "size=" + fontSize + "pt>";
                final String  nameForPosition2 = "</font> <font family=" + fontFamily + "size=" + fontSize + "pt color=" + hexColor + ">&nbsp&nbsp";
                final String  nameForPosition3 = "</font></html>";
                final String nameForPosition = nameForPosition1 + getNameForLineupPosition(position.getPosition()) + nameForPosition2 + getTacticSymbol() + nameForPosition3;

                // Players on the lineup
                if (IMatchRoleID.aFieldMatchRoleID.contains(position.getId())) {
                    m_jlPosition.setText(nameForPosition);

                    // a rounded frame on the pitch marks the positions next week's training covers
                    if (MatchRoleID.isFullTrainPosition(position.getPosition(), nextWeekTrain)) {
                        this.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(HOColorName.LINEUP_FULL_TRAINING), 2, true));
                    }
                    else if (MatchRoleID.isPartialTrainPosition(position.getPosition(), nextWeekTrain)) {
                        this.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(HOColorName.LINEUP_PARTIAL_TRAINING), 2, true));
                    }
                    else{
                        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                    }
                }
                // Subs
                else if (IMatchRoleID.aSubstitutesMatchRoleID.contains(position.getId())) {
                    m_jlPosition.setText(nameForPosition + " (#1)");
                }
                // Backups
                else {
                    m_jlPosition.setText(nameForPosition + " (#2)");
                }

                m_jlPosition.setToolTipText(MatchRoleID.getNameForPositionWithoutTactic(position.getPosition()));
            }
        }
        m_jlPosition.setFont(getFont().deriveFont(Font.BOLD));
    }

    private String getNameForLineupPosition(byte position) {
        if (UserParameter.instance().lineupPositionNamesSetting == POSITIONNAMES_SHORT) {
            return MatchRoleID.getShortNameForPosition(position);
        }
        return MatchRoleID.getNameForPosition(position);
    }

    private void initTaktik(@Nullable Player aktuellerPlayer) {
        m_jcbTactic.removeAllItems();

        switch (m_iPositionID) {
            case keeper -> m_jcbTactic.addItem(new CBItem(getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL));
            case IMatchRoleID.rightBack, IMatchRoleID.leftBack, IMatchRoleID.leftWinger, IMatchRoleID.rightWinger -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.offensive"), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.defensive"), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.towardsmiddle"), IMatchRoleID.TOWARDS_MIDDLE);
            }
            case IMatchRoleID.rightCentralDefender, IMatchRoleID.leftCentralDefender -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.offensive"), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.towardswing"), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.middleCentralDefender -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.offensive"), IMatchRoleID.OFFENSIVE);
            }
            case IMatchRoleID.rightInnerMidfield, IMatchRoleID.leftInnerMidfield -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.offensive"), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.defensive"), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.towardswing"), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.centralInnerMidfield -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.offensive"), IMatchRoleID.OFFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.defensive"), IMatchRoleID.DEFENSIVE);
            }
            case IMatchRoleID.rightForward, IMatchRoleID.leftForward -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.defensive"), IMatchRoleID.DEFENSIVE);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.towardswing"), IMatchRoleID.TOWARDS_WING);
            }
            case IMatchRoleID.centralForward -> {
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.normal"), IMatchRoleID.NORMAL);
                addTactic(aktuellerPlayer, getLangStr("ls.player.behaviour.defensive"), IMatchRoleID.DEFENSIVE);
            }
            default -> m_jcbTactic.addItem(new CBItem(getLangStr("ls.player.behaviour.normal"),
                    IMatchRoleID.NORMAL));
        }
    }

    private void addTactic(@Nullable Player currentPlayer, String text, byte playerPosition) {
        if (currentPlayer != null) {
            var ratingPredictionModel = HOVerwaltung.instance().getModel().getRatingPredictionModel();
            double rating;
            if ( matchMinute == null || matchMinute < 0 || matchMinute >= 120) {
                rating = ratingPredictionModel.getPlayerMatchAverageRating(currentPlayer, m_iPositionID, playerPosition);
            }
            else {
                rating = ratingPredictionModel.getPlayerRating(currentPlayer, m_iPositionID, playerPosition, matchMinute);
            }
            text += String.format(" (%.2f)", rating);
        }
        m_jcbTactic.addItem(new CBItem(text, playerPosition));
    }
    //-------------private-------------------------------------------------

    private PlayerCBItem createPlayerCBItem(PlayerCBItem item, @Nullable Player player) {
        if (player != null) {
            String playerName = player.getShortName();

            if (m_iPositionID == IMatchRoleID.setPieces) {
                var ratingPredictionModel = HOVerwaltung.instance().getModel().getRatingPredictionModel();
                var setPiecesRating = ratingPredictionModel.getPlayerSetPiecesStrength(player);
                item.setValues(playerName, (float) setPiecesRating, player, true);
                return item;
            } else if (m_iPositionID == IMatchRoleID.captain) {
                item.setValues(playerName,
                        Helper.round(
                                HOVerwaltung.instance().getModel().getCurrentLineup().getAverageExperience(player.getPlayerId()),
                                core.model.UserParameter.instance().nbDecimals),
                        player, true);
                return item;
            } else {
                var lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
                final var position = lineup.getPositionById(m_iPositionID);

                if (position != null) {
                    var roleId = position.getRoleId();

                    switch (roleId) {
                        case substGK1, substGK2 -> roleId = keeper;
                        case substCD1, substCD2 -> roleId = leftCentralDefender;
                        case substWB1, substWB2 -> roleId = leftBack;
                        case substWI1, substWI2 -> roleId = leftWinger;
                        case substIM1, substIM2 -> roleId = leftInnerMidfield;
                        case substFW1, substFW2 -> roleId = leftForward;
                    }

                    double value=0.;
                    boolean bestPosition = false;
                    if ( roleId != substXT2 && roleId != substXT1) {
                        var ratingPredictionModel = HOVerwaltung.instance().getModel().getRatingPredictionModel();
                        if (this.matchMinute == null || this.matchMinute < 0 || this.matchMinute > 120) {
                            value = ratingPredictionModel.getPlayerMatchAverageRating(player, roleId, position.getBehaviour(), this.m_useWeatherImpact ? this.m_weather : Weather.UNKNOWN);
                        } else {
                            value = ratingPredictionModel.getPlayerRating(player, roleId, position.getBehaviour(), this.matchMinute, this.m_useWeatherImpact ? this.m_weather : Weather.UNKNOWN);
                        }
                        var alternativePositions = player.getAlternativeBestPositions();
                        for (byte altPos : alternativePositions) {
                            if (altPos == position.getPosition()) {
                                bestPosition = true;
                                break;
                            }
                        }
                    }

                    item.setValues(playerName, (float) value, player, bestPosition);
                    return item;
                }

            }
        }
        return oNullPlayer;
    }

    public int getiSelectedPlayerId() {
        return iSelectedPlayerId;
    }


    // return a tactic symbol if a player is offensive, defensive, towards wings etc.
    // used for lineup export
    public String getTacticSymbol() {
        byte tactic = getTactic();
        int positionsID = getPositionsID();
        int playerID = getiSelectedPlayerId();
        var orientation = UserParameter.instance().lineupOrientationSetting;
        String symbol = "";
        if (playerID != -1) {
            if (tactic == IMatchRoleID.OFFENSIVE) {
                symbol = orientation == GOALKEEPER_AT_TOP ? "▼" : "▲";
            } else if (tactic == IMatchRoleID.DEFENSIVE) {
                symbol = orientation == GOALKEEPER_AT_TOP ? "▲" : "▼";
            } else if (tactic == IMatchRoleID.TOWARDS_MIDDLE) {
                if (positionsID == IMatchRoleID.rightBack
                        || positionsID == IMatchRoleID.rightWinger) {
                    symbol = orientation == GOALKEEPER_AT_TOP ? "▶" : "◀";
                } else {
                    symbol = orientation == GOALKEEPER_AT_TOP ? "◀" : "▶";
                }
            } else if (tactic == IMatchRoleID.TOWARDS_WING) {
                if (positionsID == IMatchRoleID.rightCentralDefender
                        || positionsID == IMatchRoleID.rightInnerMidfield
                        || positionsID == IMatchRoleID.rightForward) {
                    symbol = orientation == GOALKEEPER_AT_TOP ? "◀" : "▶";
                } else {
                    symbol = orientation == GOALKEEPER_AT_TOP ? "▶" : "◀";
                }
            }
        }
        return symbol;
    }

    /**
     * Exposes the player combo box to reset the swap button if needed.
     *
     * @return the player {@link JComboBox}.
     */
    protected JComboBox<PlayerCBItem> getPlayerComboBox() {
        return m_jcbPlayer;
    }

    public LayoutManager getSwapLayout() {
        return layout;
    }

    public void addSwapItem(Component c) {
        jlp.add(c, layerIndex);
        if (isTile() && c instanceof AbstractButton button) {
            showOnHover(button);
        }
    }

    /**
     * On a tile the swap arrows show only while the pointer is on the slot, or while a swap is
     * pending. The icon is blanked rather than the button hidden, so the tile keeps its place.
     */
    private void showOnHover(AbstractButton button) {
        final Icon arrows = button.getIcon();
        final Icon blank = new ImageIcon(new java.awt.image.BufferedImage(arrows.getIconWidth(), arrows.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB));
        final Runnable update = () -> button.setIcon(button.isSelected() || getMousePosition(true) != null ? arrows : blank);
        final java.awt.event.MouseAdapter hover = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                update.run();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                update.run();
            }
        };
        for (Component each : new Component[]{this, m_jcbPlayer, m_jcbTactic, button}) {
            each.addMouseListener(hover);
        }
        button.addItemListener(e -> update.run());
        update.run();
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
        jlp.add(overlay, constraints, layerIndex+1);
        repaint();
    }

    public void removeAssistantOverlay(LineupAssistantSelectorOverlay overlay) {
        jlp.remove(overlay);
        repaint();
    }


    public void addCaptainIcon(){

        JLabel jlCaptain = new JLabel();
        jlCaptain.setIcon(ImageUtilities.getSmileyIcon("smiley-coach"));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(5, 0, 0, 8);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;

        jlp.add(jlCaptain, constraints, layerIndex);

    }

    public void addSetPiecesIcon(){

        JLabel jlSetPieces = new JLabel();
        jlSetPieces.setIcon(ImageUtilities.getSetPiecesIcon(21, 21));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(2, 0, 0, 8);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;

        jlp.add(jlSetPieces, constraints, layerIndex);

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
        private @Nullable PlayerCBItem item;

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
            final MatchRoleID position = lineup.getPositionById(m_iPositionID);
            final String role = position != null ? MatchRoleID.getShortNameForPosition(position.getPosition()) : "";
            final Player player = item != null ? item.getPlayer() : null;
            if (player == null) {
                paintGhost(g, kx, kit, role);
            } else {
                final boolean keeper = m_iPositionID == IMatchRoleID.keeper || m_iPositionID == substGK1 || m_iPositionID == substGK2;
                PitchStyle.kit(keeper, player.getShirtNumber(), kit).paintIcon(this, g, kx, 2);
                int fx = kx + kit * 3 / 4;
                final float flagSize = fontSize() - 2f;
                if (player.getPlayerId() == lineup.getCaptain()) {
                    fx += 2 + PitchStyle.paintFlag(g, "C", Color.WHITE, PitchStyle.INK, fx, 0, flagSize);
                }
                if (player.getPlayerId() == lineup.getKicker()) {
                    fx += 2 + PitchStyle.paintFlag(g, "SP", PitchStyle.SEA, Color.WHITE, fx, 0, flagSize);
                }
                final String behaviour = getTacticSymbol();
                if (!behaviour.isEmpty()) {
                    PitchStyle.paintFlag(g, behaviour, PitchStyle.SAND, PitchStyle.ON_SAND, fx, 0, flagSize);
                }
                paintPlate(g, player, role, kit + 6);
            }
            g.dispose();
        }

        private void paintGhost(Graphics2D g, int kx, int kit, String role) {
            final Color faint = isOnPitch() ? Color.WHITE : ThemeManager.getColor(HOColorName.LABEL_FG);
            g.setColor(alpha(faint, 36));
            g.fillOval(kx, 2, kit, kit);
            g.setColor(alpha(faint, 110));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g.drawOval(kx, 2, kit, kit);
            g.setFont(PitchStyle.display(fontSize()));
            final FontMetrics fm = g.getFontMetrics();
            g.setColor(alpha(faint, 190));
            // extra substitutes are "Unbestimmt" and the like, longer than the circle
            final String shown = fit(role, fm, kit - 2);
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
