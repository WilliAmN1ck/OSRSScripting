package com.osrsscripts.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskRunnerTest {

    private static final class FakeTask implements Task {
        private final String name;
        private final boolean ready;
        private int executions;

        FakeTask(String name, boolean ready) {
            this.name = name;
            this.ready = ready;
        }

        @Override
        public boolean shouldRun() {
            return ready;
        }

        @Override
        public void execute() {
            executions++;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Test
    void runsOnlyTheFirstEligibleTask() {
        FakeTask a = new FakeTask("A", false);
        FakeTask b = new FakeTask("B", true);
        FakeTask c = new FakeTask("C", true);
        TaskRunner runner = new TaskRunner(Arrays.asList(a, b, c));

        Optional<Task> ran = runner.tick();

        assertTrue(ran.isPresent());
        assertEquals("B", ran.get().name());
        assertEquals(0, a.executions);
        assertEquals(1, b.executions);
        assertEquals(0, c.executions);
    }

    @Test
    void returnsEmptyWhenNoTaskIsEligible() {
        FakeTask a = new FakeTask("A", false);
        TaskRunner runner = new TaskRunner(Collections.singletonList(a));

        assertFalse(runner.tick().isPresent());
        assertEquals(0, a.executions);
    }
}
