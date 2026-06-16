# Agent Chat Frontend Design

## Goal

Upgrade the existing AI chat entry into a multi-turn employee Agent UI for the
session-aware `/api/ai/chat` endpoint.

## Minimal Change

- Keep the existing `#ai` route and `App.vue` single-page structure.
- Reuse the existing assistant thread, quick prompt, and leave confirmation
  card styles.
- Keep one generated `sessionId` for the lifetime of the page.
- Send confirmation and cancellation through `/api/ai/chat`; do not call the
  leave submission API directly.
- Preserve the existing leave-history result rendering.
- Leave the separate Dify Workflow assistant unchanged.

## Interaction

1. A user message is appended to the local conversation and sent with the
   current `sessionId`.
2. `LEAVE_APPLY_PENDING` renders a confirmation card from the pending DTO.
3. The card's confirm and cancel buttons send `确认` or `取消` through the same
   conversation endpoint and session.
4. Submission, cancellation, or no-pending responses remove the local pending
   card. A successful submission refreshes the employee's leave applications.
5. Query responses are shown directly as assistant messages.

