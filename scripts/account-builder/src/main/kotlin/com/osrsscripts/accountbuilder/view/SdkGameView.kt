package com.osrsscripts.accountbuilder.view

import com.osrsscripts.accountbuilder.engine.EquipmentView
import com.osrsscripts.accountbuilder.engine.GameView
import com.osrsscripts.accountbuilder.engine.InventoryView
import com.osrsscripts.accountbuilder.engine.QuestView
import com.osrsscripts.accountbuilder.engine.Skill
import com.osrsscripts.accountbuilder.engine.SkillView
import org.tribot.script.sdk.Equipment
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Worlds
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.Skill as SdkSkill

/**
 * Binds the engine's read-side [GameView] seam to live TRiBot SDK reads. The engine's skill names
 * mirror the SDK's, so they map by name.
 */
internal object SdkGameView : GameView {

    override val skills = object : SkillView {
        override fun level(skill: Skill): Int = toSdk(skill).getActualLevel()
    }

    override val inventory = object : InventoryView {
        override fun isFull(): Boolean = Inventory.isFull()
        override fun contains(itemName: String): Boolean =
            Query.inventory().filter { it.name.equals(itemName, ignoreCase = true) }.isAny
        override fun itemNames(): List<String> = Query.inventory().toList().map { it.name }
    }

    override val equipment = object : EquipmentView {
        override fun itemNames(): List<String> = Equipment.getAll().map { it.name }
    }

    override val quests = object : QuestView {
        // No quest tasks yet — bind to the SDK quest API when quest tasks are added.
        override fun isComplete(quest: String): Boolean = false
    }

    override val isMembersWorld: Boolean
        get() = Worlds.getCurrent().map { it.isMembers }.orElse(false)

    /** Explicit, compile-checked mapping so a name change in either enum is caught here, not at runtime. */
    private fun toSdk(skill: Skill): SdkSkill = when (skill) {
        Skill.ATTACK -> SdkSkill.ATTACK
        Skill.STRENGTH -> SdkSkill.STRENGTH
        Skill.DEFENCE -> SdkSkill.DEFENCE
        Skill.HITPOINTS -> SdkSkill.HITPOINTS
        Skill.RANGED -> SdkSkill.RANGED
        Skill.PRAYER -> SdkSkill.PRAYER
        Skill.MAGIC -> SdkSkill.MAGIC
        Skill.RUNECRAFT -> SdkSkill.RUNECRAFT
        Skill.CONSTRUCTION -> SdkSkill.CONSTRUCTION
        Skill.AGILITY -> SdkSkill.AGILITY
        Skill.HERBLORE -> SdkSkill.HERBLORE
        Skill.THIEVING -> SdkSkill.THIEVING
        Skill.CRAFTING -> SdkSkill.CRAFTING
        Skill.FLETCHING -> SdkSkill.FLETCHING
        Skill.SLAYER -> SdkSkill.SLAYER
        Skill.HUNTER -> SdkSkill.HUNTER
        Skill.MINING -> SdkSkill.MINING
        Skill.SMITHING -> SdkSkill.SMITHING
        Skill.FISHING -> SdkSkill.FISHING
        Skill.COOKING -> SdkSkill.COOKING
        Skill.FIREMAKING -> SdkSkill.FIREMAKING
        Skill.WOODCUTTING -> SdkSkill.WOODCUTTING
        Skill.FARMING -> SdkSkill.FARMING
    }
}
