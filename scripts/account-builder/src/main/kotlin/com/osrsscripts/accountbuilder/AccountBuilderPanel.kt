package com.osrsscripts.accountbuilder

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Sidebar tab: a checkbox per Woodcutting [TreeType]. Trees the account lacks the level for are
 * disabled, and re-enable automatically as Woodcutting levels up during a run. The chop task reads
 * [selectedTrees] each tick.
 */
internal class AccountBuilderPanel(initialWoodcuttingLevel: Int) : JPanel(GridBagLayout()) {

    @Volatile
    private var woodcuttingLevel: Int = initialWoodcuttingLevel
    private val checkBoxes = LinkedHashMap<TreeType, JCheckBox>()
    private val levelLabel = JLabel()
    private val targetField = JTextField("99", 3)

    init {
        border = BorderFactory.createTitledBorder("Trees to cut")
        val c = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(2, 4, 2, 4)
        }
        var row = 0

        levelLabel.text = levelText()
        c.gridy = row++
        add(levelLabel, c)

        c.gridy = row++
        add(JLabel("Target Woodcutting level (1-99):"), c)
        c.gridy = row++
        add(targetField, c)

        for (type in TreeType.values()) {
            val box = JCheckBox(label(type), type == TreeType.NORMAL)
            box.toolTipText = "Requires Woodcutting ${type.levelReq}"
            checkBoxes[type] = box
            c.gridy = row++
            add(box, c)
        }
        refreshEnabled()
    }

    /** Checked trees the account currently has the level for. Safe to call off the EDT. */
    fun selectedTrees(): Set<TreeType> =
        checkBoxes.entries
            .filter { (type, box) -> box.isSelected && woodcuttingLevel >= type.levelReq }
            .map { it.key }
            .toSet()

    /** The user's target Woodcutting level (1-99); defaults to 99 on invalid input. */
    fun targetLevel(): Int =
        targetField.text.trim().toIntOrNull()?.coerceIn(1, 99) ?: 99

    /** Updates the known Woodcutting level (e.g. after a level-up) and re-gates the checkboxes. */
    fun setWoodcuttingLevel(level: Int) {
        if (level == woodcuttingLevel) return
        woodcuttingLevel = level
        SwingUtilities.invokeLater {
            levelLabel.text = levelText()
            refreshEnabled()
        }
    }

    private fun refreshEnabled() {
        for ((type, box) in checkBoxes) {
            box.isEnabled = woodcuttingLevel >= type.levelReq
        }
    }

    private fun levelText(): String = "Woodcutting level: $woodcuttingLevel"

    private fun label(type: TreeType): String {
        val members = if (type.members) ", members" else ""
        return "${type.displayName} (lvl ${type.levelReq}$members)"
    }
}
