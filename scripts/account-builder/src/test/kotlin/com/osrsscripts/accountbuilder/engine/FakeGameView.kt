package com.osrsscripts.accountbuilder.engine

/** In-memory [GameView] for engine tests. Unset skills default to level 1. */
class FakeGameView(
    private val levels: Map<Skill, Int> = emptyMap(),
    private val items: Set<String> = emptySet(),
    private val equipped: Set<String> = emptySet(),
    private val full: Boolean = false,
    private val completedQuests: Set<String> = emptySet(),
    private val membersWorld: Boolean = false,
) : GameView {

    override val skills = object : SkillView {
        override fun level(skill: Skill): Int = levels[skill] ?: 1
    }
    override val inventory = object : InventoryView {
        override fun contains(itemName: String): Boolean = itemName in items
        override fun itemNames(): List<String> = items.toList()
        override fun isFull(): Boolean = full
    }
    override val equipment = object : EquipmentView {
        override fun itemNames(): List<String> = equipped.toList()
    }
    override val quests = object : QuestView {
        override fun isComplete(quest: String): Boolean = quest in completedQuests
    }
    override val isMembersWorld: Boolean = membersWorld
}
