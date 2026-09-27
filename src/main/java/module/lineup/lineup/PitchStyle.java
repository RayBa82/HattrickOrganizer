package module.lineup.lineup;

import core.constants.player.Specialty;
import core.gui.theme.ThemeManager;
import core.gui.theme.dark.DarkTheme;
import core.model.HOVerwaltung;
import core.model.XtraData;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * The lineup tab's look, taken from the hattrick repo's "TV view" lineup page: a striped pitch with
 * chalk lines, kits in the club colours, dark name plates and small coloured flags.
 */
public final class PitchStyle {

    static final Color BRAND = new Color(0x1e5b2c);
    public static final Color BRAND_2 = new Color(0x2b7438);
    static final Color SAND = new Color(0xe2ad35);
    static final Color ON_SAND = new Color(0x2a1d00);
    static final Color SEA = new Color(0x1f9e92);
    static final Color PLATE = new Color(12, 26, 16, 220);
    static final Color CHALK = new Color(255, 255, 255, 184);
    static final Color INK = new Color(0x10200f);

    private static Font display;

    private PitchStyle() {
    }

    private static boolean dark() {
        return ThemeManager.getCurrentTheme() instanceof DarkTheme;
    }

    /**
     * Bold condensed face for names and numbers. The page uses Saira Condensed; HO does not bundle
     * it, so this takes the first condensed family the system has.
     */
    public static Font display(float size) {
        if (display == null) {
            final List<String> installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            final String family = Arrays.stream(new String[]{"Saira Condensed", "Barlow Condensed", "Roboto Condensed",
                            "Avenir Next Condensed", "DIN Condensed", "Arial Narrow", "Liberation Sans Narrow"})
                    .filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
            display = new Font(family, Font.BOLD, 12);
        }
        return display.deriveFont(size);
    }

    /** {@link #display} where it has the glyphs; arrows like ▲ fall back to the dialog font. */
    static Font display(float size, String text) {
        final Font font = display(size);
        return font.canDisplayUpTo(text) == -1 ? font : new Font(Font.DIALOG, Font.BOLD, Math.round(size));
    }

    /** A section heading as the TV-view page draws it: a green bar with condensed white capitals. */
    public static JPanel sectionBar(String text) {
        final JLabel label = new JLabel(text.toUpperCase());
        label.setFont(display(core.model.UserParameter.instance().fontSize + 2f));
        label.setForeground(Color.WHITE);
        final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        bar.setBackground(BRAND_2);
        bar.add(label);
        return bar;
    }

    /** The page's specialty colours; null for no specialty. */
    static @Nullable Color specialtyColor(int specialty) {
        if (specialty <= 0 || specialty >= Specialty.values().length) return null;
        final boolean dark = dark();
        return switch (Specialty.values()[specialty]) {
            case UNPREDICTABLE -> new Color(dark ? 0xa98bff : 0x7c4ddb);
            case TECHNICAL -> new Color(dark ? 0x5cb8ff : 0x1d7fc4);
            case HEAD -> new Color(dark ? 0xff9a52 : 0xcf6417);
            case POWERFUL -> new Color(dark ? 0xff6b7a : 0xcc3848);
            case QUICK -> new Color(dark ? 0x6fd585 : 0x2b9443);
            case SUPPORT -> SEA;
            case REGAINER -> SAND;
            default -> null;
        };
    }

    /**
     * Shirt colour, read off the club's kit image (dressURI) - Hattrick's club colours (TeamColors)
     * come empty even for Supporters. Until the image is in, or without one, the more colourful of
     * the two TeamColors; when both are grey or missing, brand green.
     */
    static Color clubShirt() {
        final XtraData xtra = HOVerwaltung.instance().getModel().getXtraDaten();
        final Color kit = xtra == null ? null : kitColor(xtra.getDressURI());
        if (kit != null) return kit;
        final Color bg = clubColor(XtraData::getTeamColorBackground);
        final Color fg = clubColor(XtraData::getTeamColorText);
        final Color best = saturation(bg) >= saturation(fg) ? bg : fg;
        return best != null && saturation(best) > 0.25f ? best : BRAND_2;
    }

    // dressURI -> shirt colour; an empty Optional while loading or when the image is unusable
    private static final java.util.Map<String, java.util.Optional<Color>> kitColors = new java.util.concurrent.ConcurrentHashMap<>();

    private static @Nullable Color kitColor(@Nullable String dressUri) {
        if (dressUri == null || dressUri.isBlank()) return null;
        final String url = dressUri.startsWith("//") ? "https:" + dressUri : dressUri;
        return kitColors.computeIfAbsent(url, u -> {
            // off the EDT: this is asked while painting
            new Thread(() -> {
                try {
                    final Color shirt = shirtColor(javax.imageio.ImageIO.read(new java.net.URL(u)));
                    if (shirt == null) return;
                    kitColors.put(u, java.util.Optional.of(shirt));
                    SwingUtilities.invokeLater(() -> Arrays.stream(Window.getWindows()).forEach(Window::repaint));
                } catch (Exception e) {
                    core.util.HOLogger.instance().warning(PitchStyle.class, "kit image " + u + ": " + e);
                }
            }, "kit-colour").start();
            return java.util.Optional.empty();
        }).orElse(null);
    }

    /**
     * The shirt's main colour in a Hattrick kit image (shirt on top, shorts and socks below): the
     * most common colour of the shirt, a real colour winning over white or grey trim when it covers
     * a quarter of the shirt.
     */
    static @Nullable Color shirtColor(@Nullable java.awt.image.BufferedImage kit) {
        if (kit == null) return null;
        final java.util.Map<Integer, int[]> buckets = new java.util.HashMap<>(); // count, r, g, b
        int total = 0;
        int colourful = 0;
        for (int y = 0; y < kit.getHeight() * 2 / 5; y++) {
            for (int x = 0; x < kit.getWidth(); x++) {
                final int argb = kit.getRGB(x, y);
                if ((argb >>> 24) < 200) continue;
                final Color c = new Color(argb);
                final int[] b = buckets.computeIfAbsent((argb >> 5) & 0x070707 | (saturation(c) > 0.25f ? 1 << 24 : 0), k -> new int[4]);
                b[0]++;
                b[1] += c.getRed();
                b[2] += c.getGreen();
                b[3] += c.getBlue();
                total++;
                if (saturation(c) > 0.25f) colourful++;
            }
        }
        if (total == 0) return null;
        final boolean preferColour = colourful * 4 >= total;
        return buckets.entrySet().stream()
                .filter(e -> !preferColour || (e.getKey() >> 24) == 1)
                .map(java.util.Map.Entry::getValue)
                .max(java.util.Comparator.comparingInt(b -> b[0]))
                .map(b -> new Color(b[1] / b[0], b[2] / b[0], b[3] / b[0]))
                .orElse(null);
    }

    private static float saturation(@Nullable Color c) {
        return c == null ? -1 : Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1];
    }

    /** White on dark shirts, ink on light ones. */
    static Color contrast(Color c) {
        final double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return luminance > 150 ? INK : Color.WHITE;
    }

    private static @Nullable Color clubColor(java.util.function.Function<XtraData, String> getter) {
        final XtraData xtra = HOVerwaltung.instance().getModel().getXtraDaten();
        final String hex = xtra == null ? null : getter.apply(xtra);
        if (hex == null || hex.isBlank()) return null;
        try {
            return Color.decode("#" + hex.trim().replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Our half of the pitch inside {@code r}: grass stripes, touchlines, penalty and goal area at the
     * keeper's end, the halfway line with the centre circle at the other.
     */
    static void paintPitch(Graphics2D g0, Rectangle r, boolean keeperAtTop) {
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final boolean dark = dark();
        final int pad = 12;
        g.setColor(new Color(dark ? 0x1c3d1a : 0x2c5e28));
        g.fillRoundRect(r.x - pad, r.y - pad, r.width + 2 * pad, r.height + 2 * pad, 10, 10);

        final int stripes = 12;
        for (int i = 0; i < stripes; i++) {
            final int y0 = r.y + r.height * i / stripes;
            final int y1 = r.y + r.height * (i + 1) / stripes;
            final boolean even = i % 2 == 0;
            final int darkStripe = even ? 0x2f6f2b : 0x367a31;
            final int lightStripe = even ? 0x3f8a3a : 0x47953f;
            g.setColor(new Color(dark ? darkStripe : lightStripe));
            g.fillRect(r.x, y0, r.width, y1 - y0);
        }

        // draw with the keeper at the bottom, then mirror for keeper-at-top
        if (keeperAtTop) {
            g.translate(0, 2 * r.y + r.height);
            g.scale(1, -1);
        }
        g.setColor(CHALK);
        g.setStroke(new BasicStroke(2f));
        final double w = r.width;
        final double h = r.height;
        final double x = r.x;
        final double y = r.y;
        final double cx = x + w / 2;
        final double bottom = y + h;
        g.draw(new Rectangle.Double(x, y, w, h));
        final double boxW = w * 0.6;
        final double boxH = h * 0.2;
        final double areaW = w * 0.28;
        final double areaH = h * 0.07;
        final double circle = w * 0.13;
        g.draw(new Rectangle.Double(cx - boxW / 2, bottom - boxH, boxW, boxH));
        g.draw(new Rectangle.Double(cx - areaW / 2, bottom - areaH, areaW, areaH));
        g.fill(new Ellipse2D.Double(cx - 2.5, bottom - boxH * 0.7 - 2.5, 5, 5));
        g.draw(new Arc2D.Double(cx - circle * 0.7, bottom - boxH * 0.7 - circle * 0.7, circle * 1.4, circle * 1.4, 35, 110, Arc2D.OPEN));
        g.draw(new Arc2D.Double(cx - circle, y - circle, 2 * circle, 2 * circle, 180, 180, Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(cx - 2.5, y - 2.5, 5, 5));
        g.dispose();

        // direction marker, top left: our attack runs away from the keeper
        final Graphics2D t = (Graphics2D) g0.create();
        t.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        t.setColor(CHALK);
        final String arrow = keeperAtTop ? "▼" : "▲";
        t.setFont(display(12f, arrow));
        t.drawString(arrow, r.x + 6, r.y + 16);
        t.dispose();
    }

    /** A shirt in the club colours with the number on it; the keeper wears sea. */
    static Icon kit(boolean keeper, int number, int size) {
        final Color fill = keeper ? SEA : clubShirt();
        final Color trim = keeper ? new Color(0x0b5f58) : contrast(fill);
        final Color ink = keeper ? INK : contrast(fill);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                final Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.translate(x, y);
                final double s = size / 64.0;
                final Path2D shirt = new Path2D.Double();
                shirt.moveTo(22, 6);
                shirt.lineTo(32, 11);
                shirt.lineTo(42, 6);
                shirt.lineTo(58, 16);
                shirt.lineTo(52, 28);
                shirt.lineTo(46, 25);
                shirt.lineTo(46, 58);
                shirt.lineTo(18, 58);
                shirt.lineTo(18, 25);
                shirt.lineTo(12, 28);
                shirt.lineTo(6, 16);
                shirt.closePath();
                final Shape body = shirt.createTransformedShape(java.awt.geom.AffineTransform.getScaleInstance(s, s));
                g.setColor(new Color(0, 0, 0, 90));
                g.translate(0, 2);
                g.fill(body);
                g.translate(0, -2);
                g.setColor(fill);
                g.fill(body);
                g.setColor(trim);
                g.setStroke(new BasicStroke((float) Math.max(1, 2 * s)));
                g.draw(body);
                final Path2D collar = new Path2D.Double();
                collar.moveTo(26 * s, 7.5 * s);
                collar.quadTo(32 * s, 14 * s, 38 * s, 7.5 * s);
                g.draw(collar);
                if (number > 0 && number < 100) {
                    g.setFont(display((float) (18 * s)));
                    final String n = String.valueOf(number);
                    final FontMetrics fm = g.getFontMetrics();
                    g.setColor(ink);
                    g.drawString(n, (float) (size - fm.stringWidth(n)) / 2, (float) (40 * s + fm.getAscent() / 2.5));
                }
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** A small rounded flag with a short label, as the page puts on the shirt: C, SP, ▲. */
    static int paintFlag(Graphics2D g, String text, Color bg, Color fg, int x, float fontSize) {
        g.setFont(display(fontSize, text));
        final FontMetrics fm = g.getFontMetrics();
        final int w = fm.stringWidth(text) + 6;
        final int h = fm.getAscent() + 3;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x, 1, w, h, 5, 5));
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(x, 0, w, h, 5, 5));
        g.setColor(fg);
        g.drawString(text, x + 3, fm.getAscent() + 1);
        return w;
    }
}
