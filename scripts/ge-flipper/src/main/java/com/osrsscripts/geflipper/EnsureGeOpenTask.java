package com.osrsscripts.geflipper;

import com.osrsscripts.core.task.Task;
import java.util.Objects;

/** Opens the Grand Exchange when it is closed. Runs ahead of {@link FlipTask}. */
public final class EnsureGeOpenTask implements Task {

    private final GeClient client;

    public EnsureGeOpenTask(GeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean shouldRun() {
        return !client.isOpen();
    }

    @Override
    public void execute() {
        client.open();
    }
}
