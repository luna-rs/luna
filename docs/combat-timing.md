# Combat timing

Combat keeps the action lifecycle for interruption and pursuit cleanup. Its attack cooldown is an integer in
`CombatContext`, advanced once per world tick. Execution order is defined by `ActionPhase`, independently of
`ActionType` interruption and modal rules.

## Tick order

1. Advance active NPC and player attack cooldowns once, before input.
2. Consume client input. Eligible food and potions apply immediately through `ConsumeAction`; a strong action
   still suppresses consumption, and the normal consumption throttle still applies.
3. Process NPC turns, then player turns. Each turn resolves incoming impacts before phased actions:
   `EFFECT`, `NORMAL`, `INTERACTION`, `COMBAT`.
4. Process that mob's walking, then retry interactions and combat reach.
5. Synchronize and normalize action counters, then advance the world tick.

Controllers, NPC aggression, status processing, and bot decisions retain their surrounding world-loop positions.
This is a per-entity order, not a global pass that applies every hit before anybody attacks.

Each action phase takes a snapshot. Submissions to a later phase may execute in the same turn; submissions to
the current phase wait for its next pass. A just-reached interaction can establish combat after walking and
execute its first combat cycle immediately. Ordinary actions are not processed twice.

An existing combat action selects its attack and runs its processing hook once before walking. If out of range,
the reach retry reuses that attack. It rechecks health, locks, reach, single-combat rules, player combat legality,
and NPC leash boundaries before executing. It does not repeat selection hooks or decrement cooldowns. A
successful pre-movement swing is not eligible for the retry, including an attack that ignores its cooldown.

An ordinary frozen player may start melee when already cardinally adjacent. Diagonal and same-tile interactions
that require a corrective step still fail while frozen. Stuns and combat-disabling effects retain their guards.

## Attack and impact clocks

A four-tick weapon can launch at ticks 0, 4, and 8. Changing or interrupting combat intent does not reset that
cooldown. Food adds its attack delay before combat executes: ordinary food adds three ticks and karambwan two.

`CombatImpactQueue` belongs to the recipient and uses absolute world tick deadlines. Equal deadlines retain
submission order. New events submitted from an impact callback wait for another queue pass, preventing recursive
zero-delay delivery. Arrival failures are logged and finished as interruptions without stopping other entries.

| Ordinary attack | Damage delivery from launch tick T |
| --- | --- |
| Player to NPC melee | T+1 |
| NPC to player melee | T, when the later player queue pass runs |
| Player to player melee | T+1, independent of player index order |
| Profiled player ranged | T + floor(projectile end cycle / 30) + 1 |
| Spellbook magic | T + max(1, floor(projectile end cycle / 30) + 1) |
| Custom NPC magic opting into projectile timing | T + max(1, floor(projectile end cycle / 30)) |

Legacy projectile callbacks retain their configured execution offsets: launch counter zero means T+1 and
arrival counter two means T+3. Both callbacks now belong to the recipient, with one finish notification per flight.
Projectile timing is independent of the next outgoing attack's cooldown.

Committed projectiles survive shooter action interruption, queue clearing, death, and removal. They do not
retarget another entity that reuses an index. Recipient death prevents further delivery; despawn, removal, full
combat reset, player post-death reset, Duel Arena settlement, and Castle Wars exit discard incoming events.
A queue clear also invalidates already-due
entries awaiting delivery in its current pass. A removed attacker cannot become an auto-retaliation target.

## Retaliation

Fresh NPC retaliation uses half the configured NPC attack speed. Hits during a continuing engagement do not
restart that delay. An engaged NPC can flinch again after more than eight ticks beyond its previous attack
deadline. Low-health escape and leash rules take precedence.

Idle player retaliation uses half the weapon's base speed. Rapid ranged subtracts its rapid tick after halving
the base interval; very fast weapons can therefore have a zero-tick flinch. A cooldown still pending, or becoming
ready in this exact tick, is preserved. Only an accepted combat intent receives the delay.

For stationary ordinary player melee against a four-tick NPC:

```text
T     Player launches melee.
T+1   NPC receives damage; surviving NPC begins retaliation with delay 2.
T+2   NPC delay becomes 1.
T+3   NPC delay becomes 0; NPC attacks and its melee hit reaches the player's queue.
T+4   Player's four-tick weapon can attack again.
```

## Combat windows

Combat windows use `TickTimer`, not elapsed wall time. Server lag does not consume gameplay ticks.

| Predicate | Duration | Callers |
| --- | --- | --- |
| `isSingleCombatLocked()` | 8 ticks | Combat ownership checks, including the cannon |
| `inCombat()` | 17 ticks | General recent-combat state and existing content/bot decisions |
| `isLogoutBlocked()` | 17 ticks | Ordinary logout checks |

Windows exclude their upper boundary: ownership refreshed at T expires at T+8. Seventeen 600ms ticks round the
nominal ten seconds up to 10.2 seconds, removing the previous whole-seconds comparison that extended nearly to
eleven seconds. Logout and general combat have separate constants even though their current durations match.
Pursuit alone does not refresh these clocks. A committed attack, including a miss, does.

`CombatContext.getTimer()` now returns `TickTimer`. Callers use `duration` or `durationTicks` and check
`isRunning` before reading an unstarted timer. `CombatAttack.launchTick` is the gameplay recency clock; its
existing `timestamp` remains available for wall-clock observation.

## Revision choices retained

The comparison used Lost City Engine-TS commit `ca82af8a8f6afa80cf55356bda3bba794dfb189f` and Content commit
`8efdb336fac31d0d0ad1e1c2912544e972ef6b98`, both from branch 289. That reconstruction predates Luna's target
revision and is not authoritative for every content rule.

This series keeps Luna's consistent one-tick PvP melee, its player ranged projectile formula, legacy custom
projectile offsets, damage XP on delivery, and health capping at impact. It does not reproduce Lost City's
player-index-dependent queue quirks, its differing PvP ranged offset, launch-time XP, or historical PvP
launch-health cap. Those are explicit gameplay choices, separate from scheduler correctness. Specials and
secondary hits continue to use their existing attack-specific callbacks.

## Verification

Run `JAVA_HOME=<jdk21> ./gradlew test --no-daemon`. Cache-dependent checks require the native cache files under
`data/game/cache`; the cache is not committed. The new regressions cover frozen initiation, four-tick cadence,
phase handoffs, post-movement reach, lethal incoming damage, consumption throttles, interruption, recipient-owned
impacts, equal deadlines, nested scheduling, recipient lifecycle, directional melee, flinch boundaries, rapid
ranged, combat ownership, and logout expiry. Existing action, projectile, retreat, and skill timing tests remain
part of the full suite.

The complete suite is run with the existing native cache supplied locally. Shared test fixtures retain native
item metadata and use real equipment requirements rather than mutating the shared definition repository. This
keeps full-suite results independent of the potion, purchasing, and minigame test order.

Before deployment, inspect live tick traces for movement into range, due attacks plus consumption, simultaneous
lethal hits, NPC aggression and retaliation, multi-hit specials, projectile flight during death/logout/despawn,
and combat ownership versus logout expiry. Automated queue checks do not replace client/server encounter traces.
