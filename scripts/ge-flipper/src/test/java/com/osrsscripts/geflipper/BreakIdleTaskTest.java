package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.task.Task;
import com.osrsscripts.core.task.TaskRunner;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BreakIdleTaskTest {

    private final AtomicBoolean onBreak = new AtomicBoolean(false);
    private final BreakIdleTask idle = new BreakIdleTask(onBreak::get);

    @Test
    void shouldRunOnlyWhileOnBreak() {
        onBreak.set(true);
        assertTrue(idle.shouldRun());
        onBreak.set(false);
        assertFalse(idle.shouldRun());
    }

    @Test
    void shadowsLaterTasksWhileOnBreak() {
        AtomicInteger flipTicks = new AtomicInteger();
        Task flip = new Task() {
            @Override
            public boolean shouldRun() {
                return true;
            }

            @Override
            public void execute() {
                flipTicks.incrementAndGet();
            }

            @Override
            public String name() {
                return "flip";
            }
        };
        TaskRunner runner = new TaskRunner(List.of(idle, flip));

        onBreak.set(true);
        runner.tick();
        assertEquals(0, flipTicks.get(), "no flipping during a break");

        onBreak.set(false);
        runner.tick();
        assertEquals(1, flipTicks.get(), "normal flow resumes after the break");
    }
}
