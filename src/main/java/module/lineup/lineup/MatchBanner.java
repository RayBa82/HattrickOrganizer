package module.lineup.lineup;

import core.gui.Refreshable;
import core.gui.model.MatchOrdersCBItem;
import core.gui.theme.HOIconName;
import core.gui.theme.ThemeManager;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.model.enums.MatchType;
import core.model.match.Weather;
import core.net.HattrickLink;
import core.util.HOLogger;
import core.util.Helper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Match header over the pitch, after the TV-view lineup page: a brand-green band with our crest,
 * "Home vs Away", date and match type, and a tactic bar with formation, tactic, attitude and the
 * number of team orders.
 */
public class MatchBanner extends JPanel implements Refreshable {

    private static final Color MUTED = new Color(255, 255, 255, 200);

    MatchAndLineupSelectionPanel m_jpMatchSelectionPanel;
    MatchOrdersCBItem m_clSelectedMatch;
    private final JLabel jlCrest = new JLabel();
    private final JLabel jlHomeTeam = new JLabel();
    private final JLabel jlAwayTeam = new JLabel();
    private final JLabel jlMatchTypeIcon = new JLabel();
    private final JLabel jlWeather = new JLabel();
    private final JLabel jlMatchSchedule = new JLabel();
    private final JLabel jlTactic = new JLabel();

    public MatchBanner(MatchAndLineupSelectionPanel _matchSelectionPanel) {
        m_jpMatchSelectionPanel = _matchSelectionPanel;

        initComponents();
        refresh();
        core.gui.RefreshManager.instance().registerRefreshable(this);
    }

    private void initComponents() {
        setOpaque(false);
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        final float fontSize = UserParameter.instance().fontSize;
        final JLabel vs = new JLabel("vs");
        for (JLabel team : new JLabel[]{jlHomeTeam, jlAwayTeam}) {
            team.setFont(PitchStyle.display(fontSize + 5f));
            team.setForeground(Color.WHITE);
            team.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        vs.setFont(PitchStyle.display(fontSize + 2f).deriveFont(Font.PLAIN));
        vs.setForeground(MUTED);
        jlMatchSchedule.setForeground(MUTED);
        jlTactic.setForeground(Color.WHITE);
        jlTactic.setFont(PitchStyle.display(fontSize + 1f).deriveFont(Font.PLAIN));

        // two rows: teams with date and league beside them, then the match orders
        final JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        title.setOpaque(false);
        title.add(jlHomeTeam);
        title.add(vs);
        title.add(jlAwayTeam);
        title.add(Box.createHorizontalStrut(10));
        title.add(jlMatchSchedule);

        final JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(jlTactic);

        final JPanel icons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        icons.setOpaque(false);
        icons.add(jlMatchTypeIcon);
        icons.add(jlWeather);

        add(jlCrest, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        add(icons, BorderLayout.EAST);

        initListeners();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(PitchStyle.BRAND);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g.dispose();
    }

    private void initListeners() {
        jlHomeTeam.addMouseListener(teamLink(jlHomeTeam, true));
        jlAwayTeam.addMouseListener(teamLink(jlAwayTeam, false));
    }

    private MouseAdapter teamLink(JLabel label, boolean home) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                HattrickLink.showTeam(String.valueOf(home ? m_clSelectedMatch.getHomeTeamID() : m_clSelectedMatch.getGuestTeamID()));
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setText("<html><u>" + teamName(home) + "</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setText(teamName(home));
            }
        };
    }

    private String teamName(boolean home) {
        return (home ? m_clSelectedMatch.getHomeTeamName() : m_clSelectedMatch.getGuestTeamName()).toUpperCase();
    }

    @Override
    public void refresh() {
        m_clSelectedMatch = m_jpMatchSelectionPanel.getSelectedMatch();
        if (m_clSelectedMatch == null){
            this.setVisible(false);
            return;
        }
        this.setVisible(true);
        MatchType matchType = m_clSelectedMatch.getMatchType();

        if (m_clSelectedMatch.getWeather() == Weather.NULL){
            m_clSelectedMatch.setWeather(Weather.UNKNOWN);
            HOLogger.instance().info(this.getClass(), "Weather for the selected game is unknown !");
        }

        jlWeather.setIcon(ThemeManager.getIcon(HOIconName.WEATHER[m_clSelectedMatch.getWeather().getId()]));
        jlMatchTypeIcon.setIcon(ThemeManager.getScaledIcon(HOIconName.MATCHICONS[m_clSelectedMatch.getMatchTypeExtended().getIconArrayIndex()], 22, 22));
        jlCrest.setIcon(ThemeManager.instance().getClubLogo(HOVerwaltung.instance().getModel().getBasics().getTeamId(), 34));
        jlHomeTeam.setText(teamName(true));
        jlAwayTeam.setText(teamName(false));

        var datetime = m_clSelectedMatch.getMatchSchedule();
        String schedule = datetime.toLocaleDateTime();
        if (matchType == MatchType.LEAGUE) {
            schedule += " · " + String.format(Helper.getTranslation("ls.module.lineup.matchSchedule"),
                    datetime.toHTWeek().week, HOVerwaltung.instance().getModel().getLeague().getLiga()).replace("<br>", " ");
        } else {
            schedule += " · " + matchType.getName();
        }
        jlMatchSchedule.setText(schedule);
        jlTactic.setText(String.join("   ·   ", m_jpMatchSelectionPanel.getTacticBarItems()));
    }

    @Override
    public void reInit() {

    }
}
