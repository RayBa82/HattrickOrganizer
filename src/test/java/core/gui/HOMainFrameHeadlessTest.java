package core.gui;

import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * core.HeadlessMain relies on the download pipeline's status-line calls doing nothing without a
 * display. Building the frame there throws HeadlessException. Only meaningful in a headless JVM -
 * CI without DISPLAY, or -Djava.awt.headless=true - and skipped elsewhere.
 */
class HOMainFrameHeadlessTest {

    @Test
    void statusCallsDoNotBuildTheFrameWhenHeadless() {
        assumeTrue(GraphicsEnvironment.isHeadless());

        HOMainFrame.setWaitInformation();
        HOMainFrame.setInformation("x");
        HOMainFrame.setInformation("x", 5);
        HOMainFrame.setInformation("x", Color.RED);
        HOMainFrame.updateProgress(10);
        HOMainFrame.resetInformation();
        HOMainFrame.setInformationCompleted();

        assertThat(HOMainFrame.shown()).isNull();
        assertThat(HOMainFrame.isHOMainFrame_initialized()).isFalse();
    }
}
