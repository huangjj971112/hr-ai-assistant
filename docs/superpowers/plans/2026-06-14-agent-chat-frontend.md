# Agent Chat Frontend Implementation Plan

**Goal:** Add a visible multi-turn entry for Agent Memory leave confirmation.

### Task 1: Conversation State

- Add typed Agent response and pending leave DTOs.
- Track messages, sending state, and the active pending confirmation.
- Route all messages, including confirm and cancel, through `/api/ai/chat`.

### Task 2: Existing AI Entry Upgrade

- Replace the raw textarea/result panel with a conversation thread.
- Add common HR quick prompts.
- Render a pending leave confirmation card with confirm and cancel actions.
- Keep leave-history rendering and remove raw JSON output.

### Task 3: Verification

- Run the frontend type check and production build.
- Open the local app and verify the Agent entry layout and interaction.

