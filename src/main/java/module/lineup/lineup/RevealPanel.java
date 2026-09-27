package module.lineup.lineup;

import javax.swing.*;
import java.awt.*;

/**
 * A panel that can make a short entrance: it fades in while rising into place, overshooting a
 * touch at the end. {@link #conceal} plays the same entrance backwards. Until either runs it
 * paints normally.
 */
public class RevealPanel extends JPanel {

    /** Linear time through the entrance, 0 before it and 1 once in place. */
    private float phase = 1f;
    private float rise;
    private transient Timer timer;

    public RevealPanel(LayoutManager layout) {
        super(layout);
    }

    /**
     * @param delayMs    wait before starting, to stagger several panels
     * @param durationMs length of the entrance
     * @param risePx     how far below its place the panel starts
     */
    public void reveal(int delayMs, int durationMs, float risePx) {
        rise = risePx;
        phase = 0f;
        play(1f, delayMs, durationMs);
    }

    /** The entrance in reverse: sinks and fades out, ending invisible. */
    public void conceal(int delayMs, int durationMs) {
        play(0f, delayMs, durationMs);
    }

    private void play(float target, int delayMs, int durationMs) {
        if (timer != null) timer.stop();
        repaint();
        final float from = phase;
        final long start = System.currentTimeMillis() + delayMs;
        timer = new Timer(15, e -> {
            final float t = (System.currentTimeMillis() - start) / (float) durationMs;
            if (t < 0) return;
            // same speed both ways, so a reverse from halfway takes half the time
            phase = target > from ? Math.min(target, from + t) : Math.max(target, from - t);
            repaint();
            if (phase == target) ((Timer) e.getSource()).stop();
        });
        timer.start();
    }

    /** Cubic ease-out for the fade. */
    static float easeOut(float t) {
        final float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Ease-out with a small overshoot, for the movement. */
    static float easeOutBack(float t) {
        final float c = 1.4f;
        final float u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    @Override
    public boolean isOpaque() {
        // half transparent while moving: what lies behind has to be painted first
        return phase >= 1f && super.isOpaque();
    }

    @Override
    public void paint(Graphics g) {
        if (phase >= 1f) {
            super.paint(g);
            return;
        }
        if (phase <= 0f) return;
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, easeOut(phase)));
        g2.translate(0, Math.round(rise * (1f - easeOutBack(phase))));
        super.paint(g2);
        g2.dispose();
    }
}
