package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.IdleReason;
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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

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
        PER_ITEM_CAPITAL_CAP("Max spend per item (gp)"),
        MIN_MARGIN_GP("Min margin (gp)"),
        MIN_MARGIN_PCT("Min margin (fraction)"),
        MIN_VOLUME("Min volume (units/h)"),
        MIN_DEPLOYMENT_GP("Min buy deployment (gp)"),
        MAX_SLOTS("Max GE slots (1-8)"),
        MAX_OFFER_AGE_MINUTES("Max offer age (minutes)"),
        SELL_EXIT_AFTER_RELISTS("Insta-sell after relists (0=off)"),
        AVOID_AFTER_LOSS_GP("Avoid item after net loss (gp, 0=off)");

        private final String label;

        Field(String label) {
            this.label = label;
        }
    }

    private static final String[] HISTORY_COLUMNS = {"Item", "Net P/L", "Flips", "Qty"};

    private final Map<Field, JTextField> fields = new EnumMap<>(Field.class);
    private final JCheckBox membersCheckBox = new JCheckBox("Buy members items");
    private final JButton applyButton = new JButton("Apply");
    private final JButton clearHistoryButton = new JButton("Clear history");
    private final JLabel errorLabel = new JLabel(" ");
    private final JTextArea statsArea = new JTextArea(8, 24);
    private final JLabel advisoryLabel = new JLabel(" ");
    private final DefaultTableModel historyModel = new DefaultTableModel(HISTORY_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final Consumer<FlipConfig> onApply;

    public FlipperPanel(FlipConfig initial, Consumer<FlipConfig> onApply,
                        Runnable onClearHistory) {
        super(new BorderLayout(0, 8));
        this.onApply = Objects.requireNonNull(onApply, "onApply");
        Objects.requireNonNull(onClearHistory, "onClearHistory");
        add(buildConfigSection(Objects.requireNonNull(initial, "initial")), BorderLayout.NORTH);
        add(buildStatsSection(), BorderLayout.CENTER);
        applyButton.addActionListener(e -> apply());
        clearHistoryButton.addActionListener(e -> onClearHistory.run());
    }

    private JPanel buildConfigSection(FlipConfig initial) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // The RuneLite sidebar is a fixed, narrow column, so label-beside-field squeezes the input
        // to an unreadable sliver. Stack the label above a full-width field so the typed numbers
        // are always visible.
        int row = 0;
        for (Field field : Field.values()) {
            JTextField input = new JTextField(initialValue(field, initial), 10);
            fields.put(field, input);
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 2;
            c.weightx = 1;
            section.add(new JLabel(field.label), c);
            c.gridy = row + 1;
            section.add(input, c);
            row += 2;
        }
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        membersCheckBox.setSelected(initial.membersItemsAllowed());
        section.add(membersCheckBox, c);
        c.gridy = row + 1;
        section.add(applyButton, c);
        c.gridy = row + 2;
        errorLabel.setForeground(new Color(0xB00020));
        section.add(errorLabel, c);
        return section;
    }

    private JPanel buildStatsSection() {
        JPanel stats = new JPanel(new BorderLayout());
        stats.setBorder(BorderFactory.createTitledBorder("Stats"));
        statsArea.setEditable(false);
        statsArea.setOpaque(false);
        stats.add(statsArea, BorderLayout.CENTER);
        // Amber advisory shown only when a config setting is keeping GE slots or gold idle.
        advisoryLabel.setForeground(new Color(0xB36B00));
        stats.add(advisoryLabel, BorderLayout.SOUTH);

        JPanel history = new JPanel(new BorderLayout(0, 4));
        history.setBorder(BorderFactory.createTitledBorder("Trade history"));
        JTable table = new JTable(historyModel);
        table.setFillsViewportHeight(true);
        history.add(new JScrollPane(table), BorderLayout.CENTER);
        history.add(clearHistoryButton, BorderLayout.SOUTH);

        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.add(stats, BorderLayout.NORTH);
        section.add(history, BorderLayout.CENTER);
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
            case MIN_DEPLOYMENT_GP:
                return Long.toString(config.minDeploymentGp());
            case MAX_SLOTS:
                return Integer.toString(config.maxSlots());
            case MAX_OFFER_AGE_MINUTES:
                return Long.toString(config.maxOfferAge().toMinutes());
            case SELL_EXIT_AFTER_RELISTS:
                return Integer.toString(config.sellExitAfterRelists());
            case AVOID_AFTER_LOSS_GP:
                return Long.toString(config.avoidAfterLossGp());
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
                .minDeploymentGp(parseLong(Field.MIN_DEPLOYMENT_GP, 0))
                .maxSlots(maxSlots)
                .maxOfferAge(Duration.ofMinutes(maxOfferAgeMinutes))
                .membersItemsAllowed(membersCheckBox.isSelected())
                .sellExitAfterRelists((int) Math.min(parseLong(Field.SELL_EXIT_AFTER_RELISTS, 0),
                        Integer.MAX_VALUE))
                .avoidAfterLossGp(parseLong(Field.AVOID_AFTER_LOSS_GP, 0))
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
        String advisory = advisory(snapshot.idleReason());
        SwingUtilities.invokeLater(() -> {
            statsArea.setText(text.toString());
            // Wrap in HTML so the sidebar's narrow column flows the advisory onto several lines
            // instead of clipping it to an unreadable "lower 'Min …".
            advisoryLabel.setText(advisory.isEmpty() ? " " : "<html>" + advisory + "</html>");
            historyModel.setRowCount(0);
            for (StatsSnapshot.TradeRow row : snapshot.tradeRows()) {
                historyModel.addRow(new Object[] {row.itemName(), gp(row.netProfit()),
                        row.flipsCompleted(), row.qtySold()});
            }
        });
    }

    /**
     * The advisory to show when a config setting is leaving GE slots or gold idle: which setting,
     * and which way to move it. Empty when nothing is being wasted (or the cause is the market or
     * an empty wallet, neither of which a setting can fix).
     */
    private static String advisory(IdleReason reason) {
        switch (reason) {
            case MAX_SLOTS:
                return "GE slots sit idle — raise \"" + Field.MAX_SLOTS.label
                        + "\" to use them.";
            case CAPITAL_CAP:
                return "Capital cap reached — raise \"" + Field.CAPITAL_CAP.label
                        + "\" to deploy more gold.";
            case PER_ITEM_CAP:
                return "Per-item cap is limiting buys — raise \"" + Field.PER_ITEM_CAPITAL_CAP.label
                        + "\" or loosen filters for more items.";
            case NO_CANDIDATES:
                return "No items pass your filters — lower \"" + Field.MIN_MARGIN_GP.label
                        + "\" or \"" + Field.MIN_VOLUME.label + "\".";
            case NONE:
            default:
                return "";
        }
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

    void setMembersAllowed(boolean allowed) {
        membersCheckBox.setSelected(allowed);
    }

    void clickClearHistory() {
        clearHistoryButton.doClick();
    }

    int historyRowCount() {
        return historyModel.getRowCount();
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

    String advisoryText() {
        return advisoryLabel.getText();
    }
}
