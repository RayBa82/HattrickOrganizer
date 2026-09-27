package module.lineup;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


/**
 * A class that is used to overlay panels with green or red color to make
 * selection of positions for the lineup assistant to ignore.
 */
public class LineupAssistantSelectorOverlay extends module.lineup.lineup.RevealPanel {

	private static final long serialVersionUID = 1L;
	@lombok.Getter
	private boolean isSelected;

	public LineupAssistantSelectorOverlay() {
		super(null);
		init();
	}

	/**
	 * Clear on the green pitch: a position to fill gets a bright green frame, a position to empty
	 * is dimmed under a dark red veil with a red frame.
	 */
	@Override
	public void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		final Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int arc = 12;
		if (!isSelected) {
			g.setColor(new Color(70, 0, 0, 150));
			g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
		}
		g.setStroke(new BasicStroke(2.5f));
		g.setColor(new Color(isSelected ? 0x5ee06e : 0xff4d4d));
		g.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, arc, arc);
		g.dispose();
	}

	public void setSelected(boolean b) {
		isSelected = b;
		repaint();
	}


	private void init() {
		setOpaque(false);
		setLayout(null);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				// Flip color (and selected)
				isSelected = !isSelected;
				repaint();
			}
		});
	}
}
