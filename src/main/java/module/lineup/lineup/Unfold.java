package module.lineup.lineup;

import javax.swing.*;
import java.awt.*;

/**
 * Holds a component whose room in the layout grows from nothing to its full size, or shrinks
 * back along the same curve, so the neighbours slide aside instead of jumping. The component
 * itself keeps its full size throughout and is uncovered as the room grows, fading in on the way.
 */
final class Unfold extends JComponent {

    static final int DURATION_MS = 280;

    private final JComponent content;
    private final transient Runnable onFrame;
    private final transient java.util.function.IntSupplier maxWidth;
    private Dimension natural = new Dimension();
    /** Linear time through the unfolding, 0 folded and 1 open. */
    private float phase;
    private transient Timer timer;

    /** @param maxWidth the widest the content may get; it wraps to fit when narrower */
    Unfold(JComponent content, Runnable onFrame, java.util.function.IntSupplier maxWidth) {
        this.content = content;
        this.onFrame = onFrame;
        this.maxWidth = maxWidth;
        setLayout(null);
        add(content);
    }

    /** Measured once on screen: before that, fonts and wrapping text do not know their size. */
    @Override
    public void addNotify() {
        super.addNotify();
        natural = measure(content, maxWidth.getAsInt());
    }

    /** Wrapping text only knows its height once it has a width: give it one, then ask. */
    private static Dimension measure(JComponent c, int maxWidth) {
        final int width = Math.min(c.getPreferredSize().width, maxWidth);
        c.setSize(width, Short.MAX_VALUE);
        c.validate();
        final Dimension size = new Dimension(width, c.getPreferredSize().height);
        c.setSize(size);
        c.validate();
        return size;
    }

    /** Unfolds after {@code delayMs}, or folds back the same way and then runs {@code done}. */
    void animate(boolean open, int delayMs, @org.jetbrains.annotations.Nullable Runnable done) {
        if (timer != null) timer.stop();
        final float from = phase;
        final long start = System.currentTimeMillis() + delayMs;
        timer = new Timer(15, e -> {
            final float t = (System.currentTimeMillis() - start) / (float) DURATION_MS;
            if (t < 0) return;
            phase = open ? Math.min(1f, from + t) : Math.max(0f, from - t);
            revalidate();
            onFrame.run();
            if (phase == (open ? 1f : 0f)) {
                ((Timer) e.getSource()).stop();
                if (done != null) done.run();
            }
        });
        timer.start();
    }

    private float openness() {
        return RevealPanel.easeOut(phase);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.round(natural.width * openness()), Math.round(natural.height * openness()));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void doLayout() {
        content.setBounds(0, 0, natural.width, natural.height);
    }

    @Override
    public void paint(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        // the content shows up once there is some room for it
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, openness() * openness()));
        super.paint(g2);
        g2.dispose();
    }
}
