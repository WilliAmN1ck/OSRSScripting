package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.GatherResource
import com.osrsscripts.accountbuilder.engine.Skill
import org.tribot.script.sdk.types.WorldTile

/** The Mining task's key — the single source of truth shared by the panel and script. */
internal const val MINING_KEY = "mining"

/** Mining as a [GatheringTask]: mine the selected rocks with a pickaxe. */
internal fun miningTask(
    allowedRocks: () -> Set<GatherResource>,
    targetLevel: () -> Int,
    initialMineSpot: WorldTile? = null,
): GatheringTask = GatheringTask(
    keyValue = MINING_KEY,
    skill = Skill.MINING,
    gatherAction = "Mine",
    tool = Picks,
    allowedResources = allowedRocks,
    targetLevel = targetLevel,
    initialSpot = initialMineSpot,
)
