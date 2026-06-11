# Phase 0 — Workspace Setup  
# Specification Document

## Project Name
osrs-scripts-suite

## Purpose
Build a **closed‑source**, modular Java project that produces multiple standalone automation scripts (fat JARs) targeting the **Tribot Automation SDK**. Each script implements a single OSRS activity (e.g., miner, Lumbridge cow/chicken killer) and is runnable by a Tribot client/executable.

> “Every phase follows a strict lifecycle: Spec → Plan → Execute → Handoff.”  
> “No implementation without a confirmed spec and plan.”

## Scope
This phase delivers:
- `spec.md` (this file)
- `plan.md` (implementation plan)
- All decisions required to begin scaffolding the repository

## Confirmed Decisions
- **Priority scripts:** Miner, Lumbridge cow/chicken killer  
- **Source model:** Closed‑source  
- **SDK target:** Tribot Automation SDK  
- **Language:** Java 17+  
- **Build system:** Gradle Kotlin DSL  
- **Repository:** Private  
- **CI:** GitHub Actions  
- **Distribution:** Local deploy to Tribot automations folder  
- **Legal:** README must include botting risk warning

## Non‑Functional Requirements
- Modular multi‑project Gradle structure  
- Shared utilities in a `commons` module  
- TDD for all non‑trivial features  
- Fat JAR output for each script  
- Clear logging and configuration loading  
- Handoff documents for every phase

## Constraints
- No implementation before spec + plan approval  
- Phase folders created only when phase begins  
- All scripts must run under Tribot Automation SDK  
- No RWT‑related features  
- Closed‑source distribution only

## Acceptance Criteria
- This spec is complete and approved  
- Plan.md references all decisions above  
- No open questions remain  
