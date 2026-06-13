package com.osrsscripts.geflipper;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.tribot.automation.script.ScriptContext;
import org.tribot.automation.script.core.CameraMethod;
import org.tribot.automation.script.core.widgets.GameTab;

/**
 * One small human-looking idle action: a slight camera drift or a glance at a harmless side tab.
 * SDK-coupled, so verified live rather than by unit tests.
 */
final class SdkFidget implements Runnable {

    private static final List<GameTab> GLANCE_TABS = List.of(
            GameTab.SKILLS, GameTab.INVENTORY, GameTab.QUESTS, GameTab.EQUIPMENT,
            GameTab.FRIENDS);

    private final ScriptContext context;
    private final Random random;

    SdkFidget(ScriptContext context, Random random) {
        this.context = Objects.requireNonNull(context, "context");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public void run() {
        if (random.nextBoolean()) {
            int drift = random.nextInt(61) - 30; // -30..+30 degrees
            int rotation = Math.floorMod(context.getCamera().getRotation() + drift, 360);
            context.getCamera().setRotation(rotation, CameraMethod.MOUSE);
        } else {
            context.getTabs().open(GLANCE_TABS.get(random.nextInt(GLANCE_TABS.size())));
        }
    }
}
