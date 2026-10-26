# Combat timing

Combat uses `CombatAction` for target validation, pursuit, interruption, and cleanup. `CombatContext` owns the
integer attack cooldown, and `CombatImpactQueue` owns incoming hits and projectile callbacks. Each clock advances
in game ticks; stopping an attack action does not reset weapon readiness or discard a committed projectile.

## Processing order

Before synchronization, the world processes combat in this order:

1. Decrement active NPC and player attack cooldowns once.
2. Handle client input. Eligible food and potions apply during submission through `ConsumeAction`.
3. Process NPC turns, then player turns. Each mob processes incoming impacts before its action phases.
4. Process that mob's walking, then retry its interactions and combat reach.

NPC aggression runs before its incoming queue. Player controllers and tolerance run before the player's incoming
queue; bot decisions run after walking and reach retries. Synchronization follows these turns, then action
counters are normalized and the world tick advances. Incoming hits precede outgoing combat within each
recipient's turn; there is no global pass that applies every hit before any mob may attack.

`ActionPhase` determines execution order independently of the interruption and modal rules in `ActionType`:

| Phase | Work |
| --- | --- |
| `EFFECT` | Deferred damage and other incoming effects |
| `NORMAL` | Skills, control actions, and ordinary queued work |
| `INTERACTION` | Reach-based listeners that may start combat |
| `COMBAT` | Target validation, pursuit, and outgoing attacks |

Each phase takes a candidate snapshot before execution. An action submitted after its phase's snapshot waits for
a later pass. An action submitted into a later phase may execute during the same turn. Strong actions interrupt
weak actions at each phase boundary. Normal actions retain their processing hooks while a modal is open, but
their execution counters do not advance.

After walking, existing interaction and combat actions receive `runAfterMovement()` without a second counter
update or processing hook. A newly reached interaction may create a combat action whose first cycle runs in that
same pass. Existing combat retries reuse the attack selected before walking, recheck reach and legality, and
cannot repeat an already successful swing. Ordinary actions are not processed a second time.

## Attack readiness and consumption

A four-tick weapon can attack at ticks 0, 4, and 8. Changing targets or interrupting its combat action preserves
the remaining cooldown. The cooldown is decremented before input so food and retaliation delays are applied to
the current tick's readiness.

`ConsumeAction` applies an eligible food or potion effect during submission. An unfinished strong action prevents
consumption without using the throttle. Otherwise, the shared consumption throttle decides whether the item
operation may run. Ordinary food adds three attack-delay ticks; karambwan adds two. Potion effects retain their
own behavior and do not receive a generic food delay.

Ordinary immobilization allows melee against a target already cardinally adjacent. Diagonal or same-tile
interactions that require a corrective step remain blocked while frozen. Combat-disabling stuns and special
immobilization effects retain their separate guards.

## Incoming hits

`CombatImpactQueue` schedules events against the recipient's world tick. Events with equal deadlines retain
submission order. A zero-delay event may run during the current tick if the recipient's queue pass has not yet
happened. Events created by an arrival callback wait for a later queue pass.

| Ordinary attack | Delivery after launch at tick T |
| --- | --- |
| Player to NPC melee | T+1 |
| NPC to player melee | T, during the later player queue pass |
| Player to player melee | T+1, independent of player index order |
| Profiled player ranged | T + floor(projectile end cycle / 30) + 1 |
| Spellbook magic | T + max(1, floor(projectile end cycle / 30) + 1) |
| Custom NPC magic using projectile timing | T + max(1, floor(projectile end cycle / 30)) |

Legacy projectile callbacks retain their configured offsets: launch counter zero runs at T+1 and arrival counter
two at T+3. Both callbacks belong to the recipient and share one completion notification for the flight.
Projectile travel time does not change the next outgoing attack's cooldown.

A committed projectile survives shooter interruption, queue clearing, death, and world removal. Delivery still
requires its original recipient to be alive and active. The queue holds that recipient directly, so index reuse
does not redirect a hit to another mob. A removed attacker cannot become an auto-retaliation target.

Recipient despawn, removal, full combat reset, player post-death reset, Duel Arena settlement, and Castle Wars
exit clear incoming events. Clearing the queue also invalidates due events waiting in a delivery snapshot; an
arrival callback already running may finish. A lethal hit prevents the remaining due entries from reaching the
recipient. Arrival failures are logged and reported as interrupted completion without stopping other entries.

## Auto-retaliation

Fresh NPC retaliation applies half the configured attack speed. Continuing hits do not restart this delay. An
engaged NPC may flinch again when more than eight ticks have passed beyond its previous attack deadline.
Low-health retreat and leash rules take precedence.

Idle player retaliation uses half the weapon's base interval. Rapid ranged subtracts its rapid tick after the
base interval is halved, which can produce a zero-tick delay for very fast weapons. A cooldown still pending,
or becoming ready in this exact tick, is preserved. The delay applies only after combat initiation is accepted.

For a stationary player attacking a four-tick NPC with ordinary melee:

```text
T     The player attacks.
T+1   The NPC receives damage and, if it survives, starts retaliation with delay 2.
T+2   The NPC's delay becomes 1.
T+3   The NPC's delay becomes 0. It attacks, and its hit reaches the player's queue this tick.
T+4   The player's four-tick weapon is ready again.
```

## Recent combat, ownership, and logout

`CombatContext` uses a `TickTimer` refreshed by qualifying combat interactions, including committed misses.
Each policy has its own predicate and duration:

| Predicate | Duration | Use |
| --- | --- | --- |
| `isSingleCombatLocked()` | 8 ticks | Opponent ownership checks, including cannon targeting |
| `inCombat()` | 17 ticks | General recent-combat state used by content and bots |
| `isLogoutBlocked()` | 17 ticks | Ordinary logout prevention |

The upper boundary is exclusive: ownership refreshed at T expires at T+8. Seventeen nominal 600ms ticks round
ten seconds up to 10.2 seconds. Delayed server processing does not consume extra gameplay ticks. Pursuit alone
does not refresh these windows.

`CombatContext.getTimer()` returns `TickTimer`. Check `isRunning()` before reading `getDuration()` or
`getDurationTicks()` on an unstarted timer. `CombatAttack.getLaunchTick()` supplies tick-based attack recency;
`getTimestamp()` remains available for wall-clock observation.

## Compatibility and verification

PvP melee retains its consistent one-tick delay. Player projectile profiles, custom legacy offsets, damage XP on
delivery, and health capping at impact also retain their existing rules. Specials and secondary hits continue
through their attack-specific callbacks.

The timing comparison used Lost City [Engine-TS](https://github.com/LostCityRS/Engine-TS/tree/ca82af8a8f6afa80cf55356bda3bba794dfb189f)
and [Content](https://github.com/LostCityRS/Content/tree/8efdb336fac31d0d0ad1e1c2912544e972ef6b98), branch 289. Luna
keeps the compatibility rules above rather than reproducing that revision's player-index-dependent queue timing,
different PvP ranged offset, launch-time damage XP, or launch-health cap.

Run `./gradlew test --no-daemon` with JDK 21. Cache-dependent checks need the native files in `data/game/cache`.
The regression suites cover action phases, reach retries, consumption, interruption, impact ownership, recipient
cleanup, directional melee, retaliation, and combat-window boundaries. Shared fixtures preserve native item
metadata so these checks can run alongside the minigame and purchasing suites.

Live encounter verification should include movement into range, a due attack plus consumption, simultaneous
lethal hits, retaliation, multi-hit specials, projectiles during death or logout, minigame exit, and the separate
ownership and logout expiry boundaries.
