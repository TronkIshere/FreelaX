# D2.1 — Kinetic Workflow Motion Semantics

Status: **specification only**. No production animation or Flutter code is created. Motion conveys action acknowledgment, release of the old owner, changed state, receipt by the new owner, and persistence of history. It must answer what changed, who acts now, what can be done, and what follows. The static six-frame prototype communicates all of this without animation.

## Transition grammar

1. Acknowledge the completed action with an immediate state/text change; do not imply success before the server confirms it.
2. Retire the old CTA and owner emphasis. Move the workflow rail marker to the next visual step while keeping the actual `JobStatus` label explicit.
3. Introduce the next owner block and its permitted CTA. The waiting party gets a clear waiting sentence.
4. Insert the new submission version into a stable ascending history. Revision annotates V1; resubmission adds V2 without replacing V1. Approval annotates V2 and closes work actions.
5. Transfer focus to the new heading, feedback, or next action after the DOM/state update; state must not depend on an animation finishing.

Use a short opacity change and at most 8px translation over **140–180ms** for a confirmed transition. A small stagger may separate state, owner, and history updates, but the total handoff stays short. Hard graphic emphasis can switch immediately. No continuously moving element is needed. The rail is a visual sequence, not a source of new backend enum values:

| Rail step | Backend meaning |
| --- | --- |
| Assigned | `OPEN → IN_PROGRESS` assignment event |
| Working | `IN_PROGRESS` |
| Submitted | Submission `SUBMITTED`, job `SUBMITTED_FOR_REVIEW` |
| Review | Client decision position while job remains `SUBMITTED_FOR_REVIEW` |
| Revision | V1 `REVISION_REQUESTED`, job `REVISION_REQUESTED` |
| Resubmitted | New V2 `SUBMITTED`, job `SUBMITTED_FOR_REVIEW` |
| Approved | V2 `APPROVED`, job `COMPLETED` |

For revision, the feedback and V1 history are the stable continuity anchor. For approval, the backend capture must return `CAPTURED` before the completed work state appears; a payment form or payout animation is outside D2.

## Reduced motion and exclusions

Under `prefers-reduced-motion: reduce`, disable translation, stagger, and animated rail travel. Apply the state, owner, CTA, and history updates instantly, or use a short opacity-only change. Keep the same words, order, focus target, and structural markers when motion is completely off.

Prohibited: bounce, elastic easing, breathing glow, continuous pulse, decorative parallax, large zoom, confetti, long cinematic transitions, or motion for decoration. A status pill or color shift alone cannot carry the handoff. D4 has not locked global duration or easing tokens; these timings are D2 study guidance only.
