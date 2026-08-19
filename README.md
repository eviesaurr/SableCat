# 🐱  SableCat
### *The Essential Toolkit for Create: Aeronautics Server Operators*

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/your-repo)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
[![Create: Aeronautics](https://img.shields.io/badge/Create%3A%20Aeronautics-latest-orange.svg)](https://modrinth.com/mod/create-aeronautics)
---
## Table of Contents:
- [Overview](#-overview)
- [Features](#-features-at-a-glance)
- [Core Fixes](#-core-reliability-fixes)
- [Tools](#-recovery--management-tools)
---
## Overview
Sable sub-levels are the physics objects that power airships, rovers, and other contraptions in Create: Aeronautics. Over time, servers accumulate abandoned builds, runaways, half-finished projects, and corrupted sub-levels, all of which can cause performance issues, data loss, or even crashes.

SableCat is a complete reimagining of the tools available for Sable sub-level management. It builds on the foundation laid by FuckSable, a mod that first brought visibility and management to Sable's internals, but takes a fundamentally different approach under the hood.

SableCat maintains the same user-friendly interface and command structure that FuckSable users already know, but replaces the internals with a more robust architecture that:

- Fixes root causes rather than applying bandaids, save-chain aborts, and async-save visibility issues are addressed at the source, 

- Handles edge cases properly through proper state management, coordinate-space calculations, and live-eviction steps,

- Adds preventative measures like automatic backup scheduling that FuckSable lacked,

- Maintains full compatibility with existing workflows, if you know how to use FuckSable, you already know how to use SableCat!

This toolkit provides server operators with the tools to see, manage, and recover every Sable sub-level on their server.

---
## Core Reliability Fixes
Async-Save Visibility

Sable sub-level save failures typically happen silently. Operators had no visibility into when data was being lost.

With SableCat, async-save operations now log all failures, track them in the server console, and flash alerts to operators. When a save fails, you know about it immediately, instead of only discovering the loss after a restart.

### Chain-Save-Guard

Sable groups physically-touching sub-levels together for save operations (its own getLoadingDependencyChain mechanism). Previously, a single corrupted sub-level in that group could throw an uncaught exception and silently abort the entire save batch, meaning every other sub-level scheduled to save afterward in that same cycle was skipped too, not just the failing one.

SableCat isolates each chain member's save independently. One corrupt ship no longer takes down unrelated, healthy ones sharing the same save cycle.

### Null-Data Deletion Crash

Sable has a documented mechanism where passing null as a sub-level's data to its save call means "delete this sub-level's storage slot", used internally by Sable itself, and by SableCat's own deletion commands. An earlier version of this reliability layer called .uuid() on that data unconditionally, without checking for the null case first, meaning any genuine deletion (Sable's own, or one triggered through SableCat) would crash the entire server on the very next save cycle.

This has been fixed: deletions are now recognized and handled as their own case, separate from a normal sub-level save.
---
## Recovery & Management Tools
### Recovery tools:
These are commands which a server operator can use to recover, and prune corrupt and unwanted sublevels from the world, without requiring a re-log, or external maintenance. Using these commands is simple, starting with /sablecat; 
- rescue: repairs pose-corrupted or bounds-corrupted sub-levels, restoring them to a functional state.

- purge & forcepurge: Live, clean removal of dead, corrupted, or unwanted sub-levels in world.

  - Safety features:
    - Purge provides a full audit trail before destructive actions,
    - Forcepurge is available for cases where standard removal fails, with extensive logging

- manifest: Tracks all alive sub-levels, their location, dimensions, and status.
  - Displays a complete inventory of every sub-level with UUID, name, coordinates, chunk count, and last save/load time. This provides similar visibility to the Sable Cleanup Tools mod, which shows every sub-level in the dimension (manifest list), including those in unloaded chunks (manifest missing).
  
- tphere: Safely teleports sub-levels without breaking physics, can also use a sublevel's name instead of it's UUID if it is named. Standard teleportation of Sable contraptions often results in them flailing wildly, and interacting with the world. 
  - tphere safely relocates them by snapping them to the operators location instantly, while safely zeroing momentum.

- import: Pull healthy sub-levels back from offline backups.

### Preventative Maintenance:

Automatic Periodic Backups
Typically back ups only occur after a disaster has already happened. SableCat uses non-destructive, automatic backups to an independent library location, running on a periodic schedule. This ensures the next incident doesn't depend on someone remembering to zip a folder by hand.

Key features:

- Runs automatically without intervention

- Backups go to a safe, separate location from active data

- Non-destructive, doesn't interfere with active saves

- Can be restored at any time using the import tool.

Developed through rigorous debugging, real-world testing, and a commitment to actually fixing problems rather than just applying bandaids.

### Happy Sabling!

> **Note:** We owe a debt of gratitude to the FuckSable team for proving this toolset was necessary and for building the foundation that made SableCat possible. This project stands on their shoulders, and we encourage users to check out the original mod for its own unique features and approach.

---

### Example commands:
```bash
/sablecat rescue list                                # List all sub-levels captured as failed loads
/sablecat rescue <UUID>                              # Dry run - shows what would be repaired
/sablecat rescue <UUID> confirm                      # Actually performs the repair
/sablecat rescue <UUID> purge                        # Dry run - shows whether this failed load can be safely deleted
/sablecat rescue <UUID> purge confirm                # Permanently removes an unrecoverable failed-load record

/sablecat manifest list                              # Shows every sub-level known this session
/sablecat manifest missing                           # Shows sub-levels on record that aren't currently loaded

/sablecat purge <UUID>                               # Dry run - shows whether this loaded, near-empty sub-level would be removed
/sablecat purge <UUID> confirm                       # Removes a currently-loaded, confirmed-empty sub-level

/sablecat forcepurge <UUID>                          # Dry run - shows bounding volume (does NOT check for real content)
/sablecat forcepurge <UUID> confirm                  # Permanently removes it regardless of content
/sablecat forcepurge <UUID> confirm <reason>         # Same, with a reason logged and broadcast to all ops

/sablecat import <UUID> /path/to/backup              # Dry run - scans the backup folder for this UUID
/sablecat import <UUID> /path/to/backup confirm      # Pulls it back into the live world

/sablecat tphere <UUID>                              # Teleport by UUID
/sablecat tphere MyShip                              # Teleport by name (if named)

/sablecat backup now                                 # Manually trigger a backup immediately
/sablecat backup list                                # Show the resolved library path and every backup in it
/sablecat backup config <hours> <max>                # Adjust backup interval and retention without restarting
