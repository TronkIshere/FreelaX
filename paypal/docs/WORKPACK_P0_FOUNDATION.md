# WP-P0 — Marketplace Frontend Foundation

STATUS: PLANNED
MODE: RECON_ONLY
IMPLEMENTATION_AUTHORIZED: NO

## Objective

Prepare the minimum frontend foundation needed for the locked Marketplace Screen Architecture.

Potential later phases:

- P0.1 Auth role propagation
- P0.2 Registration contract alignment
- P0.3 Job repository/API alignment
- P0.4 Role-aware App Shell

This workpack does not yet authorize source modification.

## Current task

Reconnaissance only.

Codex must inspect actual source and return an exact proposed file manifest.

## Required recon

Inspect:

- auth frontend models/repositories/screens
- registration frontend
- `main.dart`
- current Home shell
- Marketplace Job models/repositories/services
- relevant tests
- exact backend DTO/API contracts required by the frontend

## Required output before authorization

Return:

### Proposed files to CREATE

Exact paths.

### Proposed files to MODIFY

Exact paths.

### Proposed files to DELETE

Expected: NONE.

### Backend files required

Expected: NONE.

If backend modification appears necessary:
STOP and report the contract gap.

### Dependency changes

Expected: NONE.

If a dependency appears necessary:
STOP and explain why.

### Test files

Exact paths.

### Risks

List only evidence-backed risks.

## Forbidden during RECON_ONLY

- Dart source writes
- Java source writes
- route changes
- dependency changes
- deletion
- rename
- commit
- push
- PR

## Authorization gate

Implementation may begin only after this file is explicitly changed to:

`IMPLEMENTATION_AUTHORIZED: YES`

and an approved exact file manifest is recorded here.

The AI must not change this value on its own.
