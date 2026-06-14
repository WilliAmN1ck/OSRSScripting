package com.osrsscripts.accountbuilder

import com.osrsscripts.accountbuilder.engine.GatherResource
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
 * Sidebar tab: a checkbox per [GatherResource] for one gathering skill (trees for Woodcutting, rocks for
 * Mining). Every resource is selectable — those above the account's current level are labelled
 * "(unlocks at N)" and start being gathered automatically once the level is reached (the task only acts on
 * [selectedResources], which level-gates). Selection + target persist under [taskKey] / [resourceParamKey].
 */
internal class GatherConfigPanel(
    title: String,
    private val skillLabel: String,
    resources: List<GatherResource>,
    private val taskKey: String,
    private val resourceParamKey: String,
    initialLevel: Int,
    defaultSelected: Set<String> = emptySet(),
) : JPanel(GridBagLayout()) {

    @Volatile
    private var skillLevel: Int = initialLevel
    private val checkBoxes = LinkedHashMap<GatherResource, JCheckBox>()
    private val levelLabel = JLabel()
    private val targetField = JTextField("99", 3)

    init {
        border = BorderFactory.createTitledBorder(title)
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
        add(JLabel("Target $skillLabel level (1-99):"), c)
        c.gridy = row++
        add(targetField, c)

        for (resource in resources) {
            val box = JCheckBox(label(resource), resource.id in defaultSelected)
            box.toolTipText = "Gather once $skillLabel reaches ${resource.levelReq}"
            checkBoxes[resource] = box
            c.gridy = row++
            add(box, c)
        }
        refreshLabels()
    }

    /** Checked resources the account currently has the level for. Safe to call off the EDT. */
    fun selectedResources(): Set<GatherResource> =
        checkBoxes.entries
            .filter { (resource, box) -> box.isSelected && skillLevel >= resource.levelReq }
            .map { it.key }
            .toSet()

    /** The user's target level (1-99); defaults to 99 on invalid input. */
    fun targetLevel(): Int =
        targetField.text.trim().toIntOrNull()?.coerceIn(1, 99) ?: 99

    /** Updates the known skill level (e.g. after a level-up) and re-gates the checkboxes. */
    fun setSkillLevel(level: Int) {
        if (level == skillLevel) return
        skillLevel = level
        SwingUtilities.invokeLater {
            levelLabel.text = levelText()
            refreshLabels()
        }
    }

    private fun refreshLabels() {
        for ((resource, box) in checkBoxes) box.text = label(resource)
    }

    private fun levelText(): String = "$skillLabel level: $skillLevel"

    private fun label(resource: GatherResource): String {
        val members = if (resource.members) ", members" else ""
        return if (skillLevel >= resource.levelReq) {
            "${resource.displayName} (lvl ${resource.levelReq}$members)"
        } else {
            "${resource.displayName} (unlocks at ${resource.levelReq}$members)"
        }
    }

    /** Test hook: set a resource's checkbox state without a display. */
    internal fun setChecked(resource: GatherResource, checked: Boolean) {
        checkBoxes[resource]?.isSelected = checked
    }

    /**
     * Snapshots the current selection + target as a persistable profile. Uses the raw checkbox state
     * (including pre-checked locked resources) so a hands-off build survives a restart.
     */
    fun toProfile(): BuildProfile {
        val selected = checkBoxes.filterValues { it.isSelected }.keys.joinToString(",") { it.id }
        val params = mapOf(resourceParamKey to selected, TARGET_PARAM to targetField.text.trim())
        return BuildProfile(tasks = listOf(TaskConfig(taskKey, params)))
    }

    /**
     * Restores a saved profile into the controls. A profile with no config for this skill (e.g. a first
     * run, or a Woodcutting-only profile loaded by the Mining panel) leaves the defaults untouched.
     */
    fun applyProfile(profile: BuildProfile) {
        val config = profile.tasks.firstOrNull { it.key == taskKey } ?: return
        val saved = config.params[resourceParamKey]?.split(",")?.toSet() ?: emptySet()
        for ((resource, box) in checkBoxes) box.isSelected = resource.id in saved
        config.params[TARGET_PARAM]?.toIntOrNull()?.let { targetField.text = it.toString() }
    }

    private companion object {
        const val TARGET_PARAM = "target"
    }
}
