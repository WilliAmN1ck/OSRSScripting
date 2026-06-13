package com.osrsscripts.accountbuilder.engine

/**
 * What a task needs before it can run: minimum skill levels, items in the inventory, completed
 * quests, and whether it requires a members world. Drives [TaskSpec.validate], is surfaced in the
 * UI, and is the structured data a future auto-planner will order tasks by.
 */
data class Requirements(
    val skillLevels: Map<Skill, Int> = emptyMap(),
    val items: List<String> = emptyList(),
    val questsComplete: List<String> = emptyList(),
    val members: Boolean = false,
) {
    /** Whether [view] satisfies every requirement. */
    fun meets(view: GameView): Boolean {
        if (members && !view.isMembersWorld) return false
        if (skillLevels.any { (skill, level) -> view.skills.level(skill) < level }) return false
        if (items.any { !view.inventory.contains(it) }) return false
        if (questsComplete.any { !view.quests.isComplete(it) }) return false
        return true
    }
}
