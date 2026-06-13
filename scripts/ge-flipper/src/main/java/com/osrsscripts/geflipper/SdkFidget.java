package com.osrsscripts.geflipper;

import com.osrsscripts.core.humanize.FidgetType;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.tribot.automation.script.ScriptContext;
import org.tribot.automation.script.core.CameraMethod;
import org.tribot.automation.script.core.widgets.GameTab;

/**
 * Executes one {@link FidgetType} as a small, human-looking action — a camera drift, a glance at a
 * harmless side tab (returning home), or an idle mouse drift within the game canvas. All actions are
 * side-effect-free (no click that changes game or offer state) and any failure is swallowed so a
 * fidget can never disrupt the flip loop. SDK-coupled, so verified live rather than by unit tests.
 */
final class SdkFidget {

    private static final List<GameTab> GLANCE_TABS = List.of(
            GameTab.SKILLS, GameTab.QUESTS, GameTab.EQUIPMENT, GameTab.FRIENDS);
    private static final GameTab HOME_TAB = GameTab.INVENTORY;

    private final ScriptContext context;
    private final Random random;

    SdkFidget(ScriptContext context, Random random) {
        this.context = Objects.requireNonNull(context, "context");
        this.random = Objects.requireNonNull(random, "random");
    }

    void run(FidgetType type) {
        try {
            switch (type) {
                case CAMERA:
                    cameraDrift();
                    break;
                case TAB_GLANCE:
                    tabGlance();
                    break;
                case MOUSE_DRIFT:
                    mouseDrift();
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            // A fidget is cosmetic — never let it interrupt flipping.
            context.getLogger().warn("Fidget " + type + " failed", e);
        }
    }

    private void cameraDrift() {
        int drift = random.nextInt(61) - 30; // -30..+30 degrees
        int rotation = Math.floorMod(context.getCamera().getRotation() + drift, 360);
        context.getCamera().setRotation(rotation, CameraMethod.MOUSE);
    }

    private void tabGlance() {
        context.getTabs().open(GLANCE_TABS.get(random.nextInt(GLANCE_TABS.size())));
        context.getTabs().open(HOME_TAB);
    }

    private void mouseDrift() {
        Rectangle canvas = context.getScreen().getCanvasDimensions();
        int insetX = canvas.width / 5;
        int insetY = canvas.height / 5;
        Rectangle region = new Rectangle(canvas.x + insetX, canvas.y + insetY,
                canvas.width - 2 * insetX, canvas.height - 2 * insetY);
        context.getMouse().drift(region, 300L + random.nextInt(700));
    }
}
