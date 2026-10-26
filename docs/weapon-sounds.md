# Weapon attack sounds

Ordinary player melee and ranged attacks select one deterministic launch sound from
`data/game/def/equipment/weapon_sounds.jsonc`. Each entry groups item IDs sharing
`sounds`, keyed by `STAB_ATTACK`, `SLASH_ATTACK`, `CRUSH_ATTACK`, or `RANGED_ATTACK`.
Values name existing `game.player.Sound` constants. Unarmed punch and block use
`UNARMED_PUNCH` (417); kick uses `UNARMED_KICK` (418).

`WeaponSoundDefinitionFileParser` loads the file at server startup. Unknown sound
or bonus constants, unsupported bonus categories, empty ID groups, and duplicate
item IDs reject publication. The complete immutable lookup replaces the old one
only after all entries load successfully. Omitted items, omitted styles, and empty
`sounds` objects produce silence. There is no inferred family or variant fallback.

## Playback contract

`CombatAttack.apply` captures the sound before damage preparation and `onAttack`,
so consuming the last thrown item cannot change the selected sound. A cancelled
attack sends no launch sound. Once committed, a player attack sends its sound
immediately before `attack()` to the attacker and, in PvP, the player victim.
Misses and accurate zero hits still play it. Other nearby players do not receive
this packet. `Player.playSound` retains Luna's repeat count of one and zero delay.

Melee and ranged specials built with `SpecialAttackBuilderReceiver` override normal
weapon selection using `SpecialAttackDataReceiver.launchSound`. Its default `null`
suppresses launch audio. Dedicated verified sounds are configured for Puncture
(385), Powershot (391), Energy Drain (1081), Quick Smash (1082), Cleave (390),
Shatter (387), Shove (388), and Impale (392). The original committed attack plays
one sound; secondary damage and projectiles do not call the launch pipeline again.
Snapshot (386) is verified in the reference but has no registered Luna special
script in this checkout. Activation-only specials keep their own behavior.

Spell, NPC, defender/pain, equipment, and impact audio stay on their existing paths.
Magic does not resolve an ordinary weapon launch sound.

## Reference and coverage

The mappings transcribe explicit object sound parameters in
[Lost City Content 377-wip at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50](https://github.com/LostCityRS/Content/tree/44eab09081ef0f6c5f3eaabd1897a588ad7d9e50),
resolved through `pack/obj.pack` and `pack/synth.pack`. The selection rule is
`scripts/skill_combat/scripts/combat.rs2`, procedure `combat_swing_anim_and_synth`.
Normal launch and recipient behavior comes from `player/player_melee.rs2`,
`player/player_ranged.rs2`, `pvp/pvp_melee.rs2`, and `pvp/pvp_ranged.rs2` under
`scripts/skill_combat/scripts`. Special sounds come from the `player/specs/scripts`
`pvm_<weapon>.rs2` scripts. Quick Smash and Energy Drain use their dedicated
attacker/PvM synth for both PvP participants; the unfinished reference PvP scripts
contain inconsistent `cleave` values for the victim, which are not copied.

The normal object fields are `stab_sound`, `slash_sound`, `crush_sound`, and
`rangeattack_sound`. The main source is `scripts/_unpack/377/all.obj`; additional
explicit entries are in the `scripts/skill_combat/configs/melee` object configs.
No mappings from the separately inspected 289 snapshot are merged into this file.
The unfinished 377 normal scripts' repeat-count argument is not used as a packet
transport specification; Luna's existing sound transport is retained.

There are 407 weapon IDs in Luna's inventory: 224 have at least one explicit
reference mapping, grouped into 25 JSON entries using 30 existing sounds. Of those,
208 cover every style currently configured by Luna and 16 have partial mappings.
183 IDs have no explicit mapping. Missing reference data does not establish that a
weapon was historically silent; silence here is a conservative implementation
choice until those mappings are verified. In particular, shortbows use
`ARROWLAUNCH2` (370), not the similarly named `SHORTBOW` (362).

### Partial mappings and style disagreements

The following items have source sounds but at least one Luna style cannot resolve
one. This patch preserves the combat styles and leaves those selections silent.
Verac's flail uses Luna's WHIP interface and slash styles, whereas the reference
only supplies flail stab/crush sounds. It therefore receives no ordinary sound.
The Tzhaar mace source labels a mace-crush sound as slash, which cannot be assumed
to override Luna's stab/crush style data.

| Item IDs | Weapon | Luna styles without an explicit sound |
| --- | --- | --- |
| 6527 | tzhaar_mace | MACE_POUND, MACE_PUMMEL, MACE_SPIKE, MACE_BLOCK |
| 2460 | flowers_waterfall_quest | MACE_SPIKE |
| 2462 | flowers_waterfall_quest_red | MACE_SPIKE |
| 2464 | flowers_waterfall_quest_blue | MACE_SPIKE |
| 2466 | flowers_waterfall_quest_yellow | MACE_SPIKE |
| 2468 | flowers_waterfall_quest_purple | MACE_SPIKE |
| 2470 | flowers_waterfall_quest_orange | MACE_SPIKE |
| 2472 | flowers_waterfall_quest_mixed | MACE_SPIKE |
| 2474 | flowers_waterfall_quest_white | MACE_SPIKE |
| 2476 | flowers_waterfall_quest_black | MACE_SPIKE |
| 466 | macro_pickaxehandle | PICKAXE_SPIKE, PICKAXE_IMPALE, PICKAXE_BLOCK |
| 4755 | barrows_verac_weapon | WHIP_FLICK, WHIP_LASH, WHIP_DEFLECT |
| 4982 | barrows_verac_weapon_100 | WHIP_FLICK, WHIP_LASH, WHIP_DEFLECT |
| 4983 | barrows_verac_weapon_75 | WHIP_FLICK, WHIP_LASH, WHIP_DEFLECT |
| 4984 | barrows_verac_weapon_50 | WHIP_FLICK, WHIP_LASH, WHIP_DEFLECT |
| 4985 | barrows_verac_weapon_25 | WHIP_FLICK, WHIP_LASH, WHIP_DEFLECT |

### Items without explicit reference mappings

Grouped by Luna weapon family. These IDs remain silent for ordinary attacks.
Dedicated special audio can still play when configured independently.

| Family | Item IDs |
| --- | --- |
| BATTLEAXE | 4718, 4886, 4887, 4888, 4889, 5056, 5057, 5058, 5059, 6589, 7807 |
| CLAWS | 2997, 6587, 7671, 7673 |
| CROSSBOW | 4734, 4934, 4935, 4936, 4937, 6082 |
| DAGGER | 5668, 5670, 5672, 5674, 5676, 5678, 5680, 5682, 5684, 5686, 5688, 5690, 5692, 5694, 5696, 5698, 5700, 5702, 6591, 6593, 6595, 6597 |
| DART | 5628, 5629, 5630, 5631, 5632, 5633, 5634, 5635, 5636, 5637, 5638, 5639, 5640, 5641 |
| HALBERD | 3190, 3192, 3194, 3196, 3198, 3200, 3202, 3204, 6599 |
| JAVELIN | 5642, 5643, 5644, 5645, 5646, 5647, 5648, 5649, 5650, 5651, 5652, 5653 |
| KNIFE | 2425, 5654, 5655, 5656, 5657, 5658, 5659, 5660, 5661, 5662, 5663, 5664, 5665, 5666, 5667, 6522, 7447 |
| LONGBOW | 4236, 4827, 6724 |
| LONGSWORD | 6607, 6745, 6746, 6897 |
| MACE | 6601, 7808 |
| SCIMITAR | 3899, 6611 |
| SHORTBOW | 4212, 4214, 4215, 4216, 4217, 4218, 4219, 4220, 4221, 4222, 4223 |
| SPEAR | 3170, 3171, 3172, 3173, 3174, 3175, 3176, 4580, 4582, 4584, 4726, 4910, 4911, 4912, 4913, 5016, 5704, 5706, 5708, 5710, 5712, 5714, 5716, 5718, 5720, 5722, 5724, 5726, 5728, 5730, 5734, 5736, 7809 |
| STAFF | 1409, 4037, 4039, 4675, 4710, 4862, 4863, 4864, 4865, 6562, 6563, 6603, 6908, 6910, 6912, 6914 |
| SWORD | 6605, 7675, 7806 |
| TWO_HANDED_SWORD | 6609, 6818, 7158 |
| WARHAMMER | 4747, 4958, 4959, 4960, 4961, 6613, 6760, 6762, 6764, 7668, 7804 |

## Validation

Client validation should cover unarmed punch/kick, weapon style changes, misses
and accurate zero hits, rejected ammunition checks, the final thrown item, both
PvP participants, and one launch sound for multi-hit specials. Check that unknown
mappings remain silent and spells retain their existing audio. Listening in the
client is still required to confirm the sound mix and perceived alignment.
