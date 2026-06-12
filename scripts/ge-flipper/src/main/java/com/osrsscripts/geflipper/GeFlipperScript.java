package com.osrsscripts.geflipper;

import org.tribot.automation.TribotScript;
import org.tribot.automation.script.ScriptContext;

/**
 * Entry point for the Grand Exchange flipper.
 *
 * <p>Validation skeleton: confirms the dev plugin, SDK resolution, entry point, manifest
 * generation, and fat-JAR packaging all work end to end. The flipping loop
 * ({@code FlipEngine} → {@code FlipActionExecutor} over {@code GrandExchange}) is wired in
 * subsequent steps.
 */
public final class GeFlipperScript implements TribotScript {

    @Override
    public void execute(ScriptContext context) {
        // No-op for the validation build.
    }
}
