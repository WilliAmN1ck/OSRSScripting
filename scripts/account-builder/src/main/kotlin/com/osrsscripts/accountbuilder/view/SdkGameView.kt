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

/**
 * Binds the engine's read-side [GameView] seam to live TRiBot SDK reads. The engine's skill names
 * mirror the SDK's, so they map by name.
 */
internal object SdkGameView : GameView {

    override val skills = object : SkillView {
        override fun level(skill: Skill): Int = SdkSkills.toSdk(skill).getActualLevel()
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
}
