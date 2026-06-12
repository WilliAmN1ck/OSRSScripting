package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.FlipConfig;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The sidebar tab: editable {@link FlipConfig} fields with an Apply button, and a live stats
 * readout. Applying validates the input and hands a new config to {@code onApply} (the script
 * picks it up on its next tick); invalid input shows an error and leaves the running config
 * untouched. Pure Swing — no SDK types — so it is testable headless.
 */
public final class FlipperPanel extends JPanel {

    /** The editable config fields, in display order. */
    public enum Field {
        CAPITAL_CAP("Capital cap (gp)"),
        PER_ITEM_CAPITAL_CAP("Per-item capital cap (gp)"),
        MIN_MARGIN_GP("Min margin (gp)"),
        MIN_MARGIN_PCT("Min margin (fraction)"),
        MIN_VOLUME("Min volume (units/h)"),
        MAX_SLOTS("Max GE slots (1-8)"),
        MAX_OFFER_AGE_MINUTES("Max offer age (minutes)");

        private final String label;

        Field(String label) {
            this.label = label;
        }
    }

    private final Map<Field, JTextField> fields = new EnumMap<>(Field.class);
    private final JButton applyButton = new JButton("Apply");
    private final JLabel errorLabel = new JLabel(" ");
    private final JTextArea statsArea = new JTextArea(12, 24);
    private final Consumer<FlipConfig> onApply;

    public FlipperPanel(FlipConfig initial, Consumer<FlipConfig> onApply) {
        super(new BorderLayout(0, 8));
        this.onApply = Objects.requireNonNull(onApply, "onApply");
        add(buildConfigSection(Objects.requireNonNull(initial, "initial")), BorderLayout.NORTH);
        add(buildStatsSection(), BorderLayout.CENTER);
        applyButton.addActionListener(e -> apply());
    }

    private JPanel buildConfigSection(FlipConfig initial) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (Field field : Field.values()) {
            JTextField input = new JTextField(initialValue(field, initial), 10);
            fields.put(field, input);
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            section.add(new JLabel(field.label), c);
            c.gridx = 1;
            c.weightx = 1;
            section.add(input, c);
            row++;
        }
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        section.add(applyButton, c);
        c.gridy = row + 1;
        errorLabel.setForeground(new Color(0xB00020));
        section.add(errorLabel, c);
        return section;
    }

    private JPanel buildStatsSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBorder(BorderFactory.createTitledBorder("Stats"));
        statsArea.setEditable(false);
        statsArea.setOpaque(false);
        section.add(statsArea, BorderLayout.CENTER);
        return section;
    }

    private static String initialValue(Field field, FlipConfig config) {
        switch (field) {
            case CAPITAL_CAP:
                return Long.toString(config.capitalCap());
            case PER_ITEM_CAPITAL_CAP:
                return Long.toString(config.perItemCapitalCap());
            case MIN_MARGIN_GP:
                return Long.toString(config.minMarginGp());
            case MIN_MARGIN_PCT:
                return Double.toString(config.minMarginPct());
            case MIN_VOLUME:
                return Long.toString(config.minVolume());
            case MAX_SLOTS:
                return Integer.toString(config.maxSlots());
            case MAX_OFFER_AGE_MINUTES:
                return Long.toString(config.maxOfferAge().toMinutes());
            default:
                throw new AssertionError(field);
        }
    }

    private void apply() {
        FlipConfig config;
        try {
            config = parseFields();
        } catch (IllegalArgumentException invalid) {
            errorLabel.setText(invalid.getMessage());
            return;
        }
        errorLabel.setText("");
        onApply.accept(config);
    }

    private FlipConfig parseFields() {
        long maxOfferAgeMinutes = parseLong(Field.MAX_OFFER_AGE_MINUTES, 1);
        // Bound-check before narrowing: a value past Integer.MAX_VALUE would wrap negative.
        long maxSlotsValue = parseLong(Field.MAX_SLOTS, 1);
        if (maxSlotsValue > 8) {
            throw new IllegalArgumentException(Field.MAX_SLOTS.label + " must be 1-8");
        }
        int maxSlots = (int) maxSlotsValue;
        return FlipConfig.builder()
                .capitalCap(parseLong(Field.CAPITAL_CAP, 0))
                .perItemCapitalCap(parseLong(Field.PER_ITEM_CAPITAL_CAP, 0))
                .minMarginGp(parseLong(Field.MIN_MARGIN_GP, 0))
                .minMarginPct(parseDouble(Field.MIN_MARGIN_PCT))
                .minVolume(parseLong(Field.MIN_VOLUME, 0))
                .maxSlots(maxSlots)
                .maxOfferAge(Duration.ofMinutes(maxOfferAgeMinutes))
                .build();
    }

    private long parseLong(Field field, long min) {
        String text = fields.get(field).getText().trim();
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field.label + " is not a number");
        }
        if (value < min) {
            throw new IllegalArgumentException(field.label + " must be at least " + min);
        }
        return value;
    }

    private double parseDouble(Field field) {
        String text = fields.get(field).getText().trim();
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field.label + " is not a number");
        }
        if (value < 0 || !Double.isFinite(value)) {
            throw new IllegalArgumentException(field.label + " must be zero or positive");
        }
        return value;
    }

    /** Replaces the stats readout; safe to call from any thread. */
    public void update(StatsSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append("Runtime: ").append(formatRuntime(snapshot.runtime())).append('\n');
        text.append("Session profit: ").append(gp(snapshot.sessionProfit())).append('\n');
        text.append("All-time profit: ").append(gp(snapshot.allTimeProfit())).append('\n');
        text.append("Flips completed: ").append(snapshot.flipsCompleted()).append('\n');
        text.append("Cash: ").append(gp(snapshot.cash())).append('\n');
        text.append("Offers:\n");
        for (String line : snapshot.offerLines()) {
            text.append("  ").append(line).append('\n');
        }
        SwingUtilities.invokeLater(() -> statsArea.setText(text.toString()));
    }

    private static String formatRuntime(Duration runtime) {
        long seconds = runtime.getSeconds();
        return String.format(Locale.US, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String gp(long amount) {
        return String.format(Locale.US, "%,d gp", amount);
    }

    // Test hooks: drive the panel without a display.

    void setField(Field field, String text) {
        fields.get(field).setText(text);
    }

    void clickApply() {
        applyButton.doClick();
    }

    String errorText() {
        return errorLabel.getText();
    }

    String statsText() {
        return statsArea.getText();
    }
}
