package module.lineup.lineup;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PitchStyleTest {

    @Test
    void greenShirtWithWhiteTrimReadsGreen() throws Exception {
        final BufferedImage kit = ImageIO.read(Objects.requireNonNull(getClass().getResource("/lineup/kit-green-white.png")));
        final Color shirt = PitchStyle.shirtColor(kit);
        assertThat(shirt).isNotNull();
        assertThat(shirt.getGreen()).isGreaterThan(shirt.getRed() + 40).isGreaterThan(shirt.getBlue() + 40);
    }

    @Test
    void plainWhiteShirtReadsWhite() {
        final BufferedImage kit = new BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 10; x++) kit.setRGB(x, y, 0xFFF8F8F8);
        assertThat(PitchStyle.shirtColor(kit)).isEqualTo(new Color(0xF8F8F8));
    }

    @Test
    void transparentImageHasNoColour() {
        assertThat(PitchStyle.shirtColor(new BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB))).isNull();
    }
}
