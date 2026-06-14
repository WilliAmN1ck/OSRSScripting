package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.GatherResource
import com.osrsscripts.accountbuilder.engine.Skill
import org.tribot.script.sdk.types.WorldTile

/** The Woodcutting task's key — the single source of truth shared by the panel and script. */
internal const val WOODCUTTING_KEY = "woodcutting"

/** Woodcutting as a [GatheringTask]: chop the selected trees with an axe. */
internal fun woodcuttingTask(
    allowedTrees: () -> Set<GatherResource>,
    targetLevel: () -> Int,
    initialChopSpot: WorldTile? = null,
): GatheringTask = GatheringTask(
    keyValue = WOODCUTTING_KEY,
    skill = Skill.WOODCUTTING,
    gatherAction = "Chop down",
    tool = Axes,
    allowedResources = allowedTrees,
    targetLevel = targetLevel,
    initialSpot = initialChopSpot,
)
