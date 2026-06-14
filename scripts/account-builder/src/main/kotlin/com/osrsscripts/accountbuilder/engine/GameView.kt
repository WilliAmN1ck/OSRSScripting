package com.osrsscripts.accountbuilder.engine

/**
 * Read-only view of the game state the engine's decisions ([TaskSpec.isComplete] / [TaskSpec.validate])
 * consult. This is the only seam between the pure engine and the SDK: production binds these to SDK
 * reads, tests use fakes. Actions (walking, chopping, banking) live in the SDK-bound task layer, not
 * here — the engine never *acts*, it only decides.
 */
interface GameView {
    val skills: SkillView
    val inventory: InventoryView
    val equipment: EquipmentView
    val quests: QuestView
    val isMembersWorld: Boolean
}

interface SkillView {
    fun level(skill: Skill): Int
}

interface InventoryView {
    fun contains(itemName: String): Boolean
    fun itemNames(): List<String>
    fun isFull(): Boolean
}

interface EquipmentView {
    fun itemNames(): List<String>
}

interface QuestView {
    fun isComplete(quest: String): Boolean
}
