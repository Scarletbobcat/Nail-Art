# Nail-Art Documentation Index

This directory is the canonical map of the Nail Art & Spa appointment book. Root files (`README.md`, `AGENTS.md`, `CLAUDE.md`) stay lean and point here.

## Start here

- [Architecture](reference/architecture.md) — system shape, request flow, deployment.
- [Conventions](reference/conventions.md) — naming, structure, and patterns that already exist in the code.
- [Lessons](reference/lessons.md) — gotchas and rationale collected from previous work.

## Modules

- [Client (Vite + React + MUI)](modules/client.md)
- [API (Spring Boot + MongoDB)](modules/api.md)
- [Cron (Python maintenance scripts)](modules/cron.md)

## Operations

- [Local development](reference/local-development.md) — how to run the stack and what each port serves.
- [Docker / deployment notes](reference/deployment.md) — published images and runtime expectations.

## Changelog

- [docs/updates.md](updates.md) — accretive log of significant documentation changes.

## Maintenance & Accretion

Update this index whenever a new module, reference document, or major operational doc is added or removed. Keep the section headings stable so root files can link directly. Significant restructures should be recorded in `docs/updates.md` with the date and a one-line summary.
