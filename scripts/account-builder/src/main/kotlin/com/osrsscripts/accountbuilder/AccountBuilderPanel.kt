package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.profile.BuildProfile
import com.osrsscripts.accountbuilder.engine.profile.TaskConfig
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
 * Sidebar tab: a checkbox per Woodcutting [TreeType]. Every tree is selectable — trees above the
 * account's current level are labelled "(unlocks at N)" and start being cut automatically once the
 * level is reached (the chop task only acts on [selectedTrees], which level-gates). This lets a build
 * be set up once and progress hands-off (e.g. pre-check Normal + Oak + Willow + Yew).
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
            box.toolTipText = "Cut once Woodcutting reaches ${type.levelReq}"
            checkBoxes[type] = box
            c.gridy = row++
            add(box, c)
        }
        refreshLabels()
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
            refreshLabels()
        }
    }

    /** Re-labels each tree to reflect whether it's currently cuttable or still locked. */
    private fun refreshLabels() {
        for ((type, box) in checkBoxes) {
            box.text = label(type)
        }
    }

    private fun levelText(): String = "Woodcutting level: $woodcuttingLevel"

    private fun label(type: TreeType): String {
        val members = if (type.members) ", members" else ""
        return if (woodcuttingLevel >= type.levelReq) {
            "${type.displayName} (lvl ${type.levelReq}$members)"
        } else {
            "${type.displayName} (unlocks at ${type.levelReq}$members)"
        }
    }

    /** Test hook: set a tree's checkbox state without a display. */
    internal fun setChecked(type: TreeType, checked: Boolean) {
        checkBoxes[type]?.isSelected = checked
    }

    /**
     * Snapshots the current selection + target as a persistable profile. Uses the raw checkbox state
     * (including pre-checked locked trees) so a hands-off build survives a restart.
     */
    fun toProfile(): BuildProfile {
        val trees = checkBoxes.filterValues { it.isSelected }.keys.joinToString(",") { it.name }
        val params = mapOf(TREES_PARAM to trees, TARGET_PARAM to targetField.text.trim())
        return BuildProfile(tasks = listOf(TaskConfig(WOODCUTTING_KEY, params)))
    }

    /**
     * Restores a saved profile into the controls. A profile with no woodcutting entry (e.g. a first
     * run's default) leaves the defaults untouched.
     */
    fun applyProfile(profile: BuildProfile) {
        val config = profile.tasks.firstOrNull { it.key == WOODCUTTING_KEY } ?: return
        val saved = config.params[TREES_PARAM]
            ?.split(",")
            ?.mapNotNull { name -> runCatching { TreeType.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
        for ((type, box) in checkBoxes) box.isSelected = type in saved
        config.params[TARGET_PARAM]?.toIntOrNull()?.let { targetField.text = it.toString() }
    }

    private companion object {
        const val WOODCUTTING_KEY = "woodcutting" // must match WoodcuttingTask.key
        const val TREES_PARAM = "trees"
        const val TARGET_PARAM = "target"
    }
}
