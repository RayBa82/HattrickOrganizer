package core.gui.theme.light;

import com.github.weisj.darklaf.theme.IntelliJTheme;
import com.github.weisj.darklaf.theme.Theme;

/**
 * Neutral light theme on darklaf's IntelliJ palette; the HO colour mapping is the Solarized Light one.
 */
public class IntelliJLightTheme extends SolarizedLightTheme {
    public static final String THEME_NAME = "IntelliJ Light";

    @Override
    public String getName() {
        return THEME_NAME;
    }

    @Override
    protected Theme createDarklafTheme() {
        return new IntelliJTheme();
    }
}
