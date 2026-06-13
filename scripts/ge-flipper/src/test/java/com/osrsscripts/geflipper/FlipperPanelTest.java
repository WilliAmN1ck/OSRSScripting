package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.ge.IdleReason;
import com.osrsscripts.core.model.FlipConfig;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Exercises the panel headless: Swing components only acquire a native peer when shown, so
 * construction, text entry, button clicks, and label reads all work without a display.
 */
class FlipperPanelTest {

    private static FlipConfig initial() {
        return FlipConfig.builder()
                .capitalCap(1_000_000L)
                .perItemCapitalCap(250_000L)
                .minMarginGp(5L)
                .minMarginPct(0.01)
                .minVolume(1_000L)
                .maxSlots(4)
                .maxOfferAgeBuy(Duration.ofMinutes(30))
                .maxOfferAgeSell(Duration.ofMinutes(30))
                .build();
    }

    @Test
    void applyParsesEditedFieldsIntoConfig() {
        List<FlipConfig> applied = new ArrayList<>();
        FlipperPanel panel = new FlipperPanel(initial(), applied::add, () -> { });

        panel.setField(FlipperPanel.Field.CAPITAL_CAP, "2000000");
        panel.setField(FlipperPanel.Field.MAX_SLOTS, "6");
        panel.setField(FlipperPanel.Field.MAX_OFFER_AGE_BUY_MINUTES, "45");
        panel.setField(FlipperPanel.Field.MAX_OFFER_AGE_SELL_MINUTES, "75");
        panel.clickApply();

        assertEquals(1, applied.size());
        FlipConfig config = applied.get(0);
        assertEquals(2_000_000L, config.capitalCap());
        assertEquals(6, config.maxSlots());
        assertEquals(Duration.ofMinutes(45), config.maxOfferAgeBuy());
        assertEquals(Duration.ofMinutes(75), config.maxOfferAgeSell());
        // Untouched fields keep their initial values.
        assertEquals(250_000L, config.perItemCapitalCap());
        assertEquals("", panel.errorText());
    }

    @Test
    void invalidInputIsRejectedAndNothingApplied() {
        List<FlipConfig> applied = new ArrayList<>();
        FlipperPanel panel = new FlipperPanel(initial(), applied::add, () -> { });

        panel.setField(FlipperPanel.Field.CAPITAL_CAP, "lots of gp");
        panel.clickApply();
        assertTrue(applied.isEmpty(), "non-numeric input never applies");
        assertFalse(panel.errorText().isEmpty());

        panel.setField(FlipperPanel.Field.CAPITAL_CAP, "1000000");
        panel.setField(FlipperPanel.Field.MAX_SLOTS, "9");
        panel.clickApply();
        assertTrue(applied.isEmpty(), "the GE has 8 slots");

        panel.setField(FlipperPanel.Field.MAX_SLOTS, "4");
        panel.setField(FlipperPanel.Field.MIN_MARGIN_PCT, "-0.5");
        panel.clickApply();
        assertTrue(applied.isEmpty(), "negative margins are invalid");

        panel.setField(FlipperPanel.Field.MIN_MARGIN_PCT, "0.01");
        panel.setField(FlipperPanel.Field.MAX_SLOTS, "2147483656");
        panel.clickApply();
        assertTrue(applied.isEmpty(), "int-overflowing slot counts must not bypass the 1-8 bound");
    }

    @Test
    void minRoiIsShownAndEnteredAsAPercent() {
        List<FlipConfig> applied = new ArrayList<>();
        FlipperPanel panel = new FlipperPanel(initial(), applied::add, () -> { });

        // The 0.01 fraction displays as "1" and round-trips on a no-op apply.
        panel.clickApply();
        assertEquals(0.01, applied.get(0).minMarginPct(), 1e-9);

        // Typing 2 means 2%, stored as the 0.02 fraction.
        panel.setField(FlipperPanel.Field.MIN_MARGIN_PCT, "2");
        panel.clickApply();
        assertEquals(0.02, applied.get(1).minMarginPct(), 1e-9);
    }

    @Test
    void membersCheckboxFlowsIntoTheConfig() {
        List<FlipConfig> applied = new ArrayList<>();
        FlipperPanel panel = new FlipperPanel(initial(), applied::add, () -> { });

        panel.clickApply();
        assertTrue(applied.get(0).membersItemsAllowed(), "default config allows members items");

        panel.setMembersAllowed(false);
        panel.clickApply();
        assertFalse(applied.get(1).membersItemsAllowed());
    }

    @Test
    void errorClearsOnTheNextSuccessfulApply() {
        List<FlipConfig> applied = new ArrayList<>();
        FlipperPanel panel = new FlipperPanel(initial(), applied::add, () -> { });

        panel.setField(FlipperPanel.Field.MIN_VOLUME, "not a number");
        panel.clickApply();
        assertFalse(panel.errorText().isEmpty());

        panel.setField(FlipperPanel.Field.MIN_VOLUME, "500");
        panel.clickApply();
        assertEquals(1, applied.size());
        assertEquals("", panel.errorText());
    }

    @Test
    void statsUpdateLandsOnTheLabels() throws InterruptedException, InvocationTargetException {
        FlipperPanel panel = new FlipperPanel(initial(), config -> { }, () -> { });

        panel.update(new StatsSnapshot(Duration.ofMinutes(90), 470L, 12_470L, 3L, 250_000L,
                List.of("1 BUY Test item 4/10 @ 100"),
                List.of(new StatsSnapshot.TradeRow("Raw pike", 12L, 1, 2),
                        new StatsSnapshot.TradeRow("Gold bar", -300L, 2, 6)),
                IdleReason.NONE));
        // update marshals onto the EDT; wait for it to drain before asserting.
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(panel.statsText().contains("01:30:00"), "runtime shown");
        assertTrue(panel.statsText().contains("470"), "session profit shown");
        assertTrue(panel.statsText().contains("12,470"), "all-time profit shown");
        assertTrue(panel.statsText().contains("250,000"), "cash shown");
        assertTrue(panel.statsText().contains("Test item"), "offer line shown");
        assertEquals(2, panel.historyRowCount(), "trade history table populated");
        assertEquals(" ", panel.advisoryText(), "no advisory when nothing is wasted");
    }

    @Test
    void idleReasonSurfacesAnActionableAdvisory() throws InterruptedException,
            InvocationTargetException {
        FlipperPanel panel = new FlipperPanel(initial(), config -> { }, () -> { });

        panel.update(new StatsSnapshot(Duration.ZERO, 0L, 0L, 0L, 0L, List.of(), List.of(),
                IdleReason.MAX_SLOTS));
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(panel.advisoryText().contains("Max GE slots"),
                "advisory names the setting to change");
    }

    @Test
    void clearHistoryButtonFiresTheCallback() {
        java.util.concurrent.atomic.AtomicInteger clears =
                new java.util.concurrent.atomic.AtomicInteger();
        FlipperPanel panel = new FlipperPanel(initial(), config -> { },
                clears::incrementAndGet);

        panel.clickClearHistory();

        assertEquals(1, clears.get());
    }
}
