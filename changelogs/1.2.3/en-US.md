# MaKaDiMi 1.2.3

## Fixed
- **Discord Rich Presence is much more reliable.** It now reconnects on its own after a network drop or when Discord closes the session, detects a dead connection, and retries when an update could not be delivered.
- Pausing no longer closes the Discord connection right away: short pauses (between tracks, buffering, or while the DJ speaks) keep the card, it hides after a few seconds and disconnects after 5 minutes.

## Improved
- **DJ MaKa** no longer shows pop-up notices on screen (such as "preparing the greeting" or voice and AI errors). It keeps working the same and falls back to the phone's voice if something fails.
