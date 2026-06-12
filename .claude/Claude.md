## Phase Planning Process

Every phase follows a strict lifecycle: **Spec → Plan → Execute → Handoff**. All artifacts live in `docs/plans/<phase-folder>/`.

### Folder Structure

```
docs/plans/
├── phase-0-workspace-setup/
│   ├── spec.md              # Requirements and decisions from Q&A
│   └── handoff.md           # What was built, what changed, what's next
├── phase-1-project-scaffold/
│   ├── spec.md
│   └── handoff.md
└── ...
```

### Lifecycle

1. **Spec** (`spec.md`) — Before any implementation, ask targeted questions about the phase to fill knowledge gaps. Continue asking until all ambiguity is resolved. Capture every decision, requirement, and constraint in the spec. Do not proceed to planning until the spec is confirmed.

2. **Plan** — From the spec, create a detailed implementation plan containing all sub-phases, their tasks, dependencies, and acceptance criteria. Get confirmation before executing.

3. **Execute** — Implement according to the plan. Keep `tasks/todo.md` updated in real-time as tasks are completed. If the plan needs to change during execution, update `plan.md` and note deviations.

4. **Handoff** (`handoff.md`) — Once a phase is complete, create a handoff document covering:
   - **Date, Branch, Plan reference, Status**
   - **What Was Built** — Organized by area (shared package, components, screens, etc.)
   - **What Changed From the Spec** — Deviations and why
   - **What the Next Phase Needs to Know** — Critical context for continuity
   - **Files Changed** — Key files table (file, change type, notes)
   - **Test Coverage** — What was tested and how
   - **Known Issues / Tech Debt** — Anything deferred
   - **Verification Commands** — How to confirm everything works

   ### Rules

- **No implementation without a confirmed spec and plan**
- **Phase folders are created when a phase begins**, not before
- **Handoff is mandatory** — Every completed phase gets a handoff, no exceptions
- **Follow UI/UX Themes** - If one does not already exist, consult the user. Utilize the /frontend-design skill for UI design help.
- **Code Comments** - Follow industry standard code comments practices, and documentation. Do not write phase specific comments
- **Architecture/Infrastructure/Code Decisions** should only be made based on the proper solution, not how long it takes to implement.
- **No Performative Agreements** Decisions should only be made based on what is the correct solution.

## Process Rules

### Before Building: Design First

- **Non-trivial features (3+ steps or architectural decisions):** Enter plan mode. Explore context, ask clarifying questions one at a time, propose 2-3 approaches with trade-offs, present recommended design, get user approval. Never implement without a reviewed design.
- **Simple changes (typo, single-line fix, rename):** Just do it. Don't over-process.

### Planning: Self-Contained Tasks

- Write implementation plans with bite-sized tasks (2-5 min each) including exact file paths and code snippets — no "TBD" placeholders.
- Each task must be executable by someone with zero prior context about what happened earlier in the session.
- Review the plan critically for gaps before starting execution. If something goes sideways, STOP and re-plan.
- When the user answers 'yes' to a factual question, treat it as acknowledgment—do NOT interpret it as approval to make code changes unless changes were explicitly proposed.
- When exploring a codebase to create a plan, produce incremental written output (outline, phase list, or notes) within the first 5 minutes. Do NOT explore silently for extended periods.

### Building: TDD + Subagents

- **TDD for new features:** Write the failing test first. Watch it fail. Then write the implementation. If the test passes immediately, you're testing existing behavior — not building new behavior.
- **Subagents:** Use liberally to keep main context clean. One task per subagent with precisely crafted context (never session history). Subagents should run in the background, and follow the rules in `CLAUDE.md`.
- **Parallel work:** Dispatch independent tasks to parallel subagents. Two-stage review per task: spec compliance first, then code quality.

### Debugging: Investigate Before Fixing

- Complete root cause investigation before attempting any fix. Instrument at layer boundaries to find WHERE the problem is.
- If 3+ fix attempts fail, stop patching symptoms and question the architecture.
- When given a bug report: just fix it autonomously. Point at logs, errors, failing tests — then resolve.

### Review: Before Every Commit

- Perform the skill /code-review max on all code changes. Investigate any fix any findings at their root.
- Keep `README.md` up to date if changes were relevant.
- When running the /parallel-adversarial-review skill, always run against the working tree (not branch diff) unless explicitly told otherwise. Use `git diff HEAD` or unstaged files, not `git diff main..branch`.
- When receiving code review feedback: verify claims against the codebase before implementing. Push back with reasoning if a suggestion breaks things or violates YAGNI. Never give performative agreement.

### Verification: Evidence Before Assertions

- Never claim work is complete without running tests and confirming output. "Should work" and "looks correct" are not evidence.
- Run the actual command, read the full output, confirm it matches the claim. Then mark complete.
- Ask yourself: "Would a staff engineer approve this?"
- After modifying backend DTOs or query handlers, always verify that frontend types, components consuming those types, AND tests are updated in the same pass. Pay special attention to constructor parameter changes breaking test mocks.

### Self-Improvement

- After ANY correction from the user: update `docs/lessons.md` with the pattern, trigger, and rule.
- Review `docs/lessons.md` at session start.

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary.
- **Demand Elegance (Balanced)**: For non-trivial changes, pause and ask "is there a more elegant way?" Skip for simple, obvious fixes.
- **Small PRs** — Keep changes focused and reviewable. One concern per PR
- **Conventional commits** — Use prefixes: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`