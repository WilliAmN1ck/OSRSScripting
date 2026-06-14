package com.osrsscripts.accountbuilder.view

import com.osrsscripts.accountbuilder.engine.Skill
import org.tribot.script.sdk.Skill as SdkSkill

/** Maps the engine's [Skill] to the SDK's. Explicit + compile-checked so a name change is caught here. */
internal object SdkSkills {
    fun toSdk(skill: Skill): SdkSkill = when (skill) {
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
