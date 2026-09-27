package module.lineup.assistant;

import core.datatype.CBItem;
import core.gui.HOMainFrame;
import core.gui.Refreshable;
import core.gui.comp.panel.ImagePanel;
import core.gui.theme.GroupTeamFactory;
import core.gui.theme.HOColorName;
import core.gui.theme.ImageUtilities;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.util.Helper;
import module.lineup.LineupAssistantSelectorOverlay;
import module.lineup.lineup.PlayerPositionPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static core.util.Helper.getTranslation;


//TODO check if it needs to implement Refreshable
public class LineupAssistantPanel extends ImagePanel implements Refreshable, ActionListener, ItemListener {

	UserParameter userParameter = core.model.UserParameter.instance();

	private final JCheckBox m_jcbxNotLast = new JCheckBox("",	userParameter.aufstellungsAssistentPanel_notLast);

	private final CBItem[] INCLUDE_EXCLUDE = {
			new CBItem(getTranslation("ls.module.lineup.exclude"), 0),
			new CBItem(getTranslation("ls.module.lineup.include"), 1),
 };

	private final JComboBox<CBItem> m_jcbIncludeExclude = new JComboBox<>(INCLUDE_EXCLUDE);

	private final JComboBox<String> m_jcbGroups = new JComboBox<>(GroupTeamFactory.TEAMS_GROUPS);
	private final JCheckBox m_jcbxFilterPlayerPositionCB = new JCheckBox("", userParameter.aufstellungsAssistentPanel_cbfilter);
	private final JCheckBox m_jcbxConsiderForm = new JCheckBox("", userParameter.aufstellungsAssistentPanel_form);
	private final JCheckBox isUsePlayerAverageRatingCheckbox = new JCheckBox("", userParameter.aufstellungsAssistentPanel_averagerating);
	private final JCheckBox m_jcbxConsiderInjuredPlayers = new JCheckBox("", userParameter.aufstellungsAssistentPanel_verletzt);
	private final JCheckBox m_jcbxConsiderSuspendedPlayers = new JCheckBox("", userParameter.aufstellungsAssistentPanel_gesperrt);
	private final JCheckBox m_jcbxIdealPositionFirst = new JCheckBox("", userParameter.aufstellungsAssistentPanel_idealPosition);

	private final CBItem[] PRIORITIES = {
			new CBItem(TranslationFacility.tr("AW-MF-ST"),
					LineupAssistant.AW_MF_ST),

			new CBItem(TranslationFacility.tr("AW-ST-MF"),
					LineupAssistant.AW_ST_MF),

			new CBItem(TranslationFacility.tr("MF-AW-ST"),
					LineupAssistant.MF_AW_ST),

			new CBItem(TranslationFacility.tr("MF-ST-AW"),
					LineupAssistant.MF_ST_AW),

			new CBItem(TranslationFacility.tr("ST-AW-MF"),
					LineupAssistant.ST_AW_MF),

			new CBItem(TranslationFacility.tr("ST-MF-AW"),
					LineupAssistant.ST_MF_AW) };
	private final JComboBox<CBItem> m_jcbPriority = new JComboBox<>(PRIORITIES);


	private final JButton m_jbClearLineup = new JButton();
	private final JButton m_jbStartAssistant = new JButton();


	private final HashMap<PlayerPositionPanel, LineupAssistantSelectorOverlay> positions = new HashMap<>();

	// UI items for additions to the LineupPositionsPanel

	private JPanel assistantBar = null;
	// the opening: the card unfolds, the position frames follow as a wave; closing plays it backwards
	private static final int WAVE_START_MS = 80;
	private static final int WAVE_STEP_MS = 25;
	private static final int FRAME_MS = 200;
	private List<PlayerPositionPanel> revealOrder = new ArrayList<>();
	private Timer pendingRemoval;
	JButton overlayOk = null;
	JButton overlayCancel = null;


	public LineupAssistantPanel() {
		initComponents();
	}

	public final boolean isExcludeLastMatch() {
		return m_jcbxNotLast.isSelected();
	}

	public final boolean isConsiderForm() {
		return m_jcbxConsiderForm.isSelected();
	}

	public final boolean isUseAverageRating(){
		return isUsePlayerAverageRatingCheckbox.isSelected();
	}

	public final boolean isIgnoreSuspended() {
		return m_jcbxConsiderSuspendedPlayers.isSelected();
	}

	public final String getGroup() {
		return Objects.requireNonNull(m_jcbGroups.getSelectedItem()).toString();
	}

	public final boolean isGroupFilter() {
		return m_jcbxFilterPlayerPositionCB.isSelected();
	}

	public final void setGroupFilter(boolean isSelected) {m_jcbxFilterPlayerPositionCB.setSelected(isSelected);}

	public final boolean isIdealPositionZuerst() {
		return m_jcbxIdealPositionFirst.isSelected();
	}

	public final boolean isSelectedGroupExcluded() {
		return m_jcbIncludeExclude.getSelectedIndex() == 0;
	}

	public final int getOrder() {
		return ((CBItem) Objects.requireNonNull(m_jcbPriority.getSelectedItem())).getId();
	}

	public final boolean isIgnoreInjured() {
		return m_jcbxConsiderInjuredPlayers.isSelected();
	}

	public final void actionPerformed(java.awt.event.ActionEvent actionEvent) {
		final HOModel hoModel = HOVerwaltung.instance().getModel();
		final HOMainFrame mainFrame = core.gui.HOMainFrame.instance();

		if (actionEvent.getSource().equals(m_jbClearLineup)) {
			// Empty all positions
			var lineup = hoModel.getCurrentLineup();
            lineup.resetStartingLineup();
            lineup.resetPositionOrders();
            lineup.resetSubstituteBench();
            lineup.setKicker(0);
            lineup.setCaptain(0);
            HOMainFrame.instance().setInformation(TranslationFacility.tr("Aufstellung_geloescht"));
            mainFrame.getLineupPanel().update();
        }
		else if (actionEvent.getSource().equals(m_jbStartAssistant)) {
			displayGUI();
		}
		else if (actionEvent.getSource().equals(m_jcbxFilterPlayerPositionCB) || actionEvent.getSource().equals(m_jcbxNotLast)) {
			mainFrame.getLineupPanel().refreshLineupPositionsPanel();
		}
		else if (actionEvent.getSource().equals(m_jcbGroups) || actionEvent.getSource().equals(m_jcbIncludeExclude)) {
			// Only if filter active
			if (m_jcbxFilterPlayerPositionCB.isSelected()) {
				mainFrame.getLineupPanel().refreshLineupPositionsPanel();
			}
		}
		else if (actionEvent.getSource().equals(overlayOk)) {

			// Check that max 11 positions are sent
			Iterator<Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay>> it = positions
					.entrySet().iterator();
			int reds = 0;
			while (it.hasNext()) {
				if (!it.next().getValue().isSelected()) {
					reds++;
				}
			}
			if (reds < 3) {
				// We have more positions left than is allowed in the lineup.
				// Return.
				javax.swing.JOptionPane.showMessageDialog(HOMainFrame.instance()
						.getLineupPanel(),
						TranslationFacility.tr("lineupassist.Error"),
						TranslationFacility.tr("lineupassist.ErrorHeader"),
						javax.swing.JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			removeGUI();
			updateDefaultSelection();

			// TODO: Create LineupAssistant here (remove it from Lineup class)
			startAssistant(hoModel, mainFrame);

		} else if (actionEvent.getSource().equals(overlayCancel)) {
			removeGUI();
		}

	}

	@Override
	public final void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			// Wetter -> Refresh
			core.gui.HOMainFrame.instance().getLineupPanel().update();

			// gui.RefreshManager.instance ().doRefresh ();
		}
	}

	public void addToAssistant(PlayerPositionPanel positionPanel) {
		positions.put(positionPanel, null);
	}

	private void startAssistant(HOModel hoModel, HOMainFrame mainFrame) {

		// First, clear all positions that are not selected. We need to clear
		// the way.

		for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions.entrySet()) {
			if (!entry.getValue().isSelected()) {
				var lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
                lineup.setSpielerAtPosition(entry.getKey().getPositionsID(), 0);
            }
		}

		final List<Player> selectablePlayers = new ArrayList<>();
		final List<Player> allPlayers = hoModel.getCurrentPlayers();

		for (Player player: allPlayers) {

			//If the player is eligible to play and either all groups are selected or the one to which the player belongs
			if (player.getCanBeSelectedByAssistant()
					&& (((this.getGroup().trim().isEmpty()
					|| player.getTeamGroup().equals(this.getGroup())) && !isSelectedGroupExcluded())
					|| (!player.getTeamGroup().equals(this.getGroup()) && isSelectedGroupExcluded()))) {
				boolean include = true;
				if (isExcludeLastMatch()) {
					var previousLineup = hoModel.getPreviousLineup();
					if (previousLineup != null && previousLineup.getLineup().isPlayerInStartingEleven(player.getPlayerId())) {
						include = false;
					}
				}
				if (include &&
						! hoModel.getCurrentLineup().isPlayerInStartingEleven(player.getPlayerId())) { // not already in current lineup
					selectablePlayers.add(player);
				}
			}
		}

		hoModel.getCurrentLineupTeam()
				.getLineup()
				.optimizeLineup(
						selectablePlayers,
						(byte) ((CBItem)m_jcbPriority.getSelectedItem()).getId(),
						isConsiderForm(),
						isIdealPositionZuerst(),
						isIgnoreInjured(),
						isIgnoreSuspended(),
						isUseAverageRating()
				);

		mainFrame.setInformation(TranslationFacility.tr("Autoaufstellung_fertig"));
		mainFrame.getLineupPanel().update();

		// gui.RefreshManager.instance ().doRefresh ();
	}

	/** Which positions the assistant fills when there is no full eleven to start from. */
	private static boolean savedSelection(int posId) {
		if (UserParameter.instance().assistantSaved) {
			return UserParameter.instance().getBooleanValue(UserParameter.instance().getValues(), "assistant" + posId);
		}
		return posId != IMatchRoleID.centralForward
				&& posId != IMatchRoleID.centralInnerMidfield
				&& posId != IMatchRoleID.middleCentralDefender;
	}

	private void displayGUI() {
		// reopened while the last closing still plays: keep the frames it would remove
		if (pendingRemoval != null) pendingRemoval.stop();

		// Add overlays to player panels


		for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions.entrySet()) {
			if (entry.getValue() == null) {
				LineupAssistantSelectorOverlay laso = new LineupAssistantSelectorOverlay();
				laso.setSelected(savedSelection(entry.getKey().getPositionsID()));
				entry.setValue(laso);
			}
		}
		// a full eleven on the pitch is the formation the user plays: start from it. The keeper is
		// not among these positions, so a full eleven leaves exactly three of them empty.
		final var occupied = occupiedFieldPositions();
		if (occupied.size() == positions.size() - 3) {
			positions.forEach((panel, overlay) -> overlay.setSelected(occupied.contains(panel.getPositionsID())));
		}
		for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions.entrySet()) {
			entry.getKey().addAssistantOverlay(entry.getValue());
		}

		final var posPanel = Objects.requireNonNull(HOMainFrame.instance().getLineupPanel());

		// Instructions with Cancel and OK in a card in the free corner left of the pitch, where
		// captain and set pieces sit further down
		if (assistantBar == null) {
			final JTextArea info = new JTextArea(TranslationFacility.tr("lineupassist.Info")
					.replace("<br>", " ").replace("<html>", "").replace("</html>", ""), 0, 22);
			info.setLineWrap(true);
			info.setWrapStyleWord(true);
			info.setEditable(false);
			info.setFocusable(false);
			info.setOpaque(false);
			// Nimbus paints a text area's background even when it is not opaque
			info.setBackground(new Color(0, 0, 0, 0));
			info.setBorder(null);
			info.setFont(UIManager.getFont("Label.font"));

			overlayOk = new JButton(TranslationFacility.tr("ls.button.ok"));
			overlayOk.addActionListener(this);
			overlayCancel = new JButton(TranslationFacility.tr("ls.button.cancel"));
			overlayCancel.addActionListener(this);
			final JPanel buttons = new JPanel(new ButtonRow());
			buttons.setOpaque(false);
			buttons.add(overlayCancel);
			buttons.add(overlayOk);

			assistantBar = new JPanel(new BorderLayout(0, 8));
			assistantBar.setBorder(BorderFactory.createCompoundBorder(module.lineup.lineup.PitchStyle.card(),
					BorderFactory.createEmptyBorder(6, 6, 6, 6)));
			assistantBar.add(info, BorderLayout.CENTER);
			assistantBar.add(buttons, BorderLayout.SOUTH);
		}
		posPanel.showSideCard(assistantBar);

		// the position frames follow as a quick wave, top to bottom and left to right
		revealOrder = new ArrayList<>(positions.keySet());
		revealOrder.sort(Comparator.comparingInt(PlayerPositionPanel::getY).thenComparingInt(PlayerPositionPanel::getX));
		for (int i = 0; i < revealOrder.size(); i++) {
			positions.get(revealOrder.get(i)).reveal(frameStart(i), FRAME_MS, 8f);
		}
		SwingUtilities.invokeLater(() -> SwingUtilities.getRootPane(overlayOk).setDefaultButton(overlayOk));

	}

	private Set<Integer> occupiedFieldPositions() {
		final var lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
		final Set<Integer> occupied = new HashSet<>();
		for (PlayerPositionPanel panel : positions.keySet()) {
			final var position = lineup.getPositionById(panel.getPositionsID());
			if (position != null && position.getPlayerId() > 0) {
				occupied.add(panel.getPositionsID());
			}
		}
		return occupied;
	}

	/** The opening played backwards: the frames go last first, then the card folds away. */
	private void removeGUI() {
		final int end = timelineMillis();
		for (int i = 0; i < revealOrder.size(); i++) {
			positions.get(revealOrder.get(i)).conceal(end - frameStart(i) - FRAME_MS, FRAME_MS);
		}
		final var pane = Objects.requireNonNull(HOMainFrame.instance().getLineupPanel());
		pane.hideSideCard(end - module.lineup.lineup.LineupPositionsPanel.sideCardMillis());

		pendingRemoval = new Timer(end, e -> {
			for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions.entrySet()) {
				entry.getKey().removeAssistantOverlay(entry.getValue());
			}
			pane.repaint();
		});
		pendingRemoval.setRepeats(false);
		pendingRemoval.start();
	}

	private static int frameStart(int i) {
		return WAVE_START_MS + i * WAVE_STEP_MS;
	}

	/** From the first movement to the last frame in place. */
	private int timelineMillis() {
		return Math.max(module.lineup.lineup.LineupPositionsPanel.sideCardMillis(),
				frameStart(revealOrder.size() - 1) + FRAME_MS);
	}

	public Map<Integer, Boolean> getPositionsStatus() {
		HashMap<Integer, Boolean> returnMap = new HashMap<>();
		for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions.entrySet()) {
			returnMap.put(entry.getKey().getPositionsID(), entry.getValue().isSelected());
		}

		return returnMap;
	}

	private void updateDefaultSelection() {
		// There should be a more sensible way to do this. Merging maps or
		// something...
		// But brute force and ignorance should never be underestimated.

		UserParameter.instance().assistant101 = getStatusForPosition(101);
		UserParameter.instance().assistant102 = getStatusForPosition(102);
		UserParameter.instance().assistant103 = getStatusForPosition(103);
		UserParameter.instance().assistant104 = getStatusForPosition(104);
		UserParameter.instance().assistant105 = getStatusForPosition(105);
		UserParameter.instance().assistant106 = getStatusForPosition(106);
		UserParameter.instance().assistant107 = getStatusForPosition(107);
		UserParameter.instance().assistant108 = getStatusForPosition(108);
		UserParameter.instance().assistant109 = getStatusForPosition(109);
		UserParameter.instance().assistant110 = getStatusForPosition(110);
		UserParameter.instance().assistant111 = getStatusForPosition(111);
		UserParameter.instance().assistant112 = getStatusForPosition(112);
		UserParameter.instance().assistant113 = getStatusForPosition(113);
		UserParameter.instance().assistantSaved = true;
	}

	private boolean getStatusForPosition(int position) {
		for (Map.Entry<PlayerPositionPanel, LineupAssistantSelectorOverlay> entry : positions
				.entrySet()) {
			if (entry.getKey().getPositionsID() == position) {
				return entry.getValue().isSelected();
			}
		}
		return false;
	}

	private void initComponents() {
		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;

		// which players the assistant may pick: label | controls, the controls take the width
		Helper.setComboBoxFromID(m_jcbIncludeExclude, userParameter.lineupAssistentPanel_include_group ? 1 : 0);
		m_jcbIncludeExclude.addActionListener(this);
		m_jcbGroups.setSelectedItem(userParameter.aufstellungsAssistentPanel_gruppe);
		m_jcbGroups.setRenderer(new core.gui.comp.renderer.SmilieListCellRenderer());
		m_jcbGroups.addActionListener(this);
		final JPanel groupSelection = new JPanel(new GridLayout(1, 2, 6, 0));
		groupSelection.setOpaque(false);
		groupSelection.add(m_jcbIncludeExclude);
		groupSelection.add(m_jcbGroups);
		addRow(c, "ls.module.lineup.assistant.group", groupSelection);

		m_jcbPriority.setToolTipText(getTranslation("tt_AufstellungsAssistent_Reihenfolge"));
		Helper.setComboBoxFromID(m_jcbPriority, userParameter.aufstellungsAssistentPanel_reihenfolge);
		addRow(c, "ls.module.lineup.assistant.priority", m_jcbPriority);

		// the options as plain checkboxes with their text, two columns
		c.gridx = 0;
		c.gridwidth = 2;
		c.insets = new Insets(12, 0, 4, 0);
		add(module.lineup.lineup.PitchStyle.sectionBar(getTranslation("Optionen")), c);
		c.gridy++;

		final JPanel options = new JPanel(new GridLayout(0, 2, 12, 2));
		options.setOpaque(false);
		option(options, m_jcbxNotLast, "NotLast_aufstellen", "tt_AufstellungsAssistent_NotLast");
		option(options, m_jcbxFilterPlayerPositionCB, "ls.module.lineup.sync_lineup_panel", "ls.module.lineup.sync_lineup_panel.tooltip");
		option(options, m_jcbxConsiderForm, "Form_beruecksichtigen", "tt_AufstellungsAssistent_Form");
		option(options, m_jcbxIdealPositionFirst, "Idealposition_zuerst", "tt_AufstellungsAssistent_Idealposition");
		option(options, m_jcbxConsiderInjuredPlayers, "Verletze_aufstellen", "tt_AufstellungsAssistent_Verletzte");
		option(options, m_jcbxConsiderSuspendedPlayers, "Gesperrte_aufstellen", "tt_AufstellungsAssistent_Gesperrte");
		option(options, isUsePlayerAverageRatingCheckbox, "use_average_rating", "tt_use_average_rating");
		m_jcbxNotLast.addActionListener(this);
		m_jcbxFilterPlayerPositionCB.addActionListener(this);
		c.insets = new Insets(0, 0, 0, 0);
		add(options, c);
		c.gridy++;

		// the two actions as text buttons with a small icon
		m_jbClearLineup.setIcon(ImageUtilities.getClearLineupIcon(18, HOColorName.LINEUP_COLOR, HOColorName.CLEAR_LINEUP));
		m_jbStartAssistant.setIcon(ImageUtilities.getStartAssistantIcon(18, HOColorName.LINEUP_COLOR, HOColorName.START_ASSISTANT));
		final JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
		buttons.setOpaque(false);
		for (var entry : Map.of(m_jbClearLineup, "Aufstellung_leeren", m_jbStartAssistant, "Assistent_starten").entrySet()) {
			entry.getKey().setText(capitalize(getTranslation(entry.getValue())));
			entry.getKey().setToolTipText(getTranslation(entry.getValue()));
			entry.getKey().setIconTextGap(6);
			entry.getKey().addActionListener(this);
		}
		buttons.add(m_jbClearLineup);
		buttons.add(m_jbStartAssistant);
		c.insets = new Insets(14, 0, 0, 0);
		add(buttons, c);
		c.gridy++;

		// push everything to the top
		c.weighty = 1.0;
		add(Box.createGlue(), c);

		core.gui.RefreshManager.instance().registerRefreshable(this);
	}

	private void addRow(GridBagConstraints c, String labelKey, JComponent control) {
		c.gridwidth = 1;
		c.gridx = 0;
		c.weightx = 0.0;
		c.insets = new Insets(3, 0, 3, 12);
		add(new JLabel(getTranslation(labelKey)), c);
		c.gridx = 1;
		c.weightx = 1.0;
		c.insets = new Insets(3, 0, 3, 0);
		add(control, c);
		c.gridy++;
	}

	private static void option(JPanel panel, JCheckBox box, String textKey, String tooltipKey) {
		box.setText(capitalize(getTranslation(textKey)));
		box.setToolTipText(getTranslation(tooltipKey));
		box.setOpaque(false);
		panel.add(box);
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	@Override
	public void reInit() {}

	@Override
	public void refresh() {
		reInit();
	}

	/**
	 * Buttons side by side, right-aligned, or stacked full width once the panel is too narrow for
	 * one row. Unlike a wrapping FlowLayout it reports the height it will really use.
	 */
	private static final class ButtonRow implements LayoutManager {
		private static final int GAP = 6;

		private static int rowWidth(Container parent) {
			int width = 0;
			for (Component c : parent.getComponents()) {
				width += c.getPreferredSize().width + (width > 0 ? GAP : 0);
			}
			return width;
		}

		private static boolean stacked(Container parent) {
			final Insets in = parent.getInsets();
			return parent.getWidth() > 0 && parent.getWidth() - in.left - in.right < rowWidth(parent);
		}

		@Override
		public void addLayoutComponent(String name, Component comp) {
			// nothing to remember
		}

		@Override
		public void removeLayoutComponent(Component comp) {
			// nothing to remember
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			final Insets in = parent.getInsets();
			final boolean stacked = stacked(parent);
			int widest = 0;
			int tallest = 0;
			int stackHeight = -GAP;
			for (Component c : parent.getComponents()) {
				final Dimension d = c.getPreferredSize();
				widest = Math.max(widest, d.width);
				tallest = Math.max(tallest, d.height);
				stackHeight += d.height + GAP;
			}
			final int width = stacked ? widest : rowWidth(parent);
			final int height = stacked ? Math.max(0, stackHeight) : tallest;
			return new Dimension(width + in.left + in.right, height + in.top + in.bottom);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent) {
			final Insets in = parent.getInsets();
			final int inner = parent.getWidth() - in.left - in.right;
			if (stacked(parent)) {
				int y = in.top;
				for (Component c : parent.getComponents()) {
					final int h = c.getPreferredSize().height;
					c.setBounds(in.left, y, inner, h);
					y += h + GAP;
				}
			} else {
				int x = in.left + inner - rowWidth(parent);
				for (Component c : parent.getComponents()) {
					final Dimension d = c.getPreferredSize();
					c.setBounds(x, in.top, d.width, d.height);
					x += d.width + GAP;
				}
			}
		}
	}
}
