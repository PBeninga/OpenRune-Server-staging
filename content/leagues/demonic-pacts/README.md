# demonic-pacts

The Demonic Pacts League (Leagues VI) pact tree, built from the cache's own data (rev 240 and
241). It is generic content: a server that wants different points, resets or loadouts layers them
on top through its own module.

## Switching it on

Pacts are off unless `game.yml` enables them, and they are decided per player, never for the world:

```yaml
gameplay:
  demonic-pacts:
    enabled: true        # default false
    starting-points: 2   # these three are optional; unset keeps the league's value
    free-resets: 2
    spend-cap: 40
```

`PactActivation.isActive(player)` is asked by every entry point. Its default, `ConfigPactActivation`,
answers `enabled` for everyone; a server whose worlds host several rulesets binds one that checks
the player's own. For an inactive player:
- `DemonicPactsTree.open` shows "Demonic pacts aren't available to you." and returns false;
- `DemonicPacts.commit` and `reset` are refused (`PactCommitRefusal.NotActive`,
  `DemonicPacts.NOT_ACTIVE`); grants and the admin commands change nothing;
- the login refresh, and any `DemonicPacts.refresh`, clears what an earlier active session left in
  the saved vars the client reads: `talent_points_earned`, `talent_points_spent`,
  `talent_resets_available`, the `talent_*` effect totals and `league_type` (only if it is 6). A var
  that is already 0 isn't sent, so a player who never had pacts is sent no pact var. The owned
  nodes (`combat_mastery_perm_*`) and the server-only counters are kept, so switching pacts back
  on restores the same tree, points and resets;
- `DemonicPacts.effects` is `PactEffects.NONE` and the points and resets read 0, so combat hooks
  stay registered and change nothing for them. The `PactEffectsListener`s are told the effects are
  gone, and a Defence boost the saved level still holds is taken off at login (see the Defence
  pacts below).

For an active player every refresh (login, commit, reset, grant) rebuilds the vars and sets
`league_type` to 6 (Leagues VI), which the tree's "Active Pact Info" panel and the skill guides
read. `league_type` doubles as the record of whether the player's pacts were active at logout
(`DemonicPacts.wasActiveAtLogout`, read in `onPlayerInit`).

## Tree model (package `org.rsmod.content.leagues.demonicpacts.tree`)

- `PactTreeLoader` builds the `PactTree` once from the cache:
  - node order from `enum.talent_tree_node_order` (the cache's unnamed `enum_5942`, named in
    `pack/src/main/resources/gamevals.toml`): node index → `dbtable.talent_tree` row;
  - each node's effect `(id, value)`, style, size, sprite, draw coordinates, tooltip text and
    links from its `TalentTreeRow`.
- Node *i* is owned when bit `i % 32` of varp `combat_mastery_perm_{i / 32}` is set
  (`PactNode.varpIndex` / `bitInVarp`). Node 0 is the root, `AA`.
- Wiki node ids (`AA`, `F7`, ...) come from `pack/src/main/resources/demonic-pacts/node-ids.toml`.
  The loader fails if it disagrees with the cache.
- Inject `PactTree` anywhere (`@ProvidedBy`, one shared instance). To tune a node's value, override
  that dbrow's `effect` column in a pack; the server and the client tooltip both read it.
- `PactTreeDataTest` (integration) pins the tree to the snapshot `demonic-pacts/pact-nodes.json`:
  132 nodes, 164 edges, 73 effect ids and every value; `AA` has 6 links; the deepest capstones
  (N4–N9) are 9 hops from `AA`.

## Tree interface (package `org.rsmod.content.leagues.demonicpacts`)

Jagex's own interface, `interface.talent_tree` (647), driven as the Leagues VI client expects.
- `DemonicPactsTree.open(player)` is how content opens it (a shrine, a command): an overlay in
  the toplevel floater slot, with the node, panel, close-button and `ScriptTrigger` events, then
  `[clientscript,talent_tree_init]`. The client draws the tree from the cache and the player's vars
  and redraws it when they change. Opening it also runs `DemonicPacts.refresh`. `close(player)`
  closes it. `::pactopen` opens it for administrators.
- `DemonicPactsTreeScript` handles the close button and the two `if_runscript` triggers, only while
  the tree is open, for every player:
  - **commit**, `PactCommitArgs` (the selected node indices, on `talent_tree:data_layer`):
    `DemonicPacts.commit`; a refusal is shown as its `PactCommitRefusal` message. The first commit
    also sets `league_initial_points_spent`, which turns off the client's "pick a style node" nudge.
  - **reset**, `PactResetArgs` (no arguments, on `talent_tree:perm_floating_panel_content`): the
    client sends it without asking, so the server asks first ("Reset all your pacts? You have N
    resets.", yes or no), then calls `DemonicPacts.reset`. With no reset available or nothing but
    the root owned it refuses without asking.
- Vars: the state (`combat_mastery_perm_*`, `talent_points_earned`/`spent`, `talent_perm_1` with
  `talent_resets_available`) is a cache varp, so it is saved and sent already. The `talent_*` effect
  varbits' varps (`talent_active_temp_0..8`) are packed as `Temp` in
  `pack/src/main/resources/pack/configs/demonic_pacts.toml`; run `buildCache` after changing it.
- Tests: `DemonicPactsTreeScriptTest` (open, close, commits and resets sent as the client sends
  them, through `GameTestScope.ifScriptTrigger`), `DemonicPactsVarsTest` (lifetimes and varbit widths).

## State and rules (package `org.rsmod.content.leagues.demonicpacts.state`)

`DemonicPacts` is the API content uses. It keeps a player's pacts in the Leagues VI vars, so the
cache's tree interface reads them as they are:

| State | Var | Saved | Sent |
|---|---|---|---|
| Owned nodes | `combat_mastery_perm_0..4` (bit `i % 32` of varp `i / 32`) | yes | yes |
| Points earned (shown capped at the spend cap) | `talent_points_earned` | yes | yes |
| Points spent (owned nodes except the root) | `talent_points_spent` | yes | yes |
| Resets available | `talent_resets_available` (6 bits: at most 63, `DemonicPacts.MAX_RESETS`) | yes | yes |
| Effect totals for "Active Pact Info" | the 73 `talent_*` effect varbits (`PactEffectVarbits`) | rebuilt on login | yes |
| Points and resets granted through the API, resets used | `demonic_pacts_points_granted`, `_resets_granted`, `_resets_used` (62090–62092) | yes | no |

The generic module's own ids are the block **62090–62099** (varp/varbit, server-only); see
`pack/src/main/resources/gamevals.toml`.

Rules:
- **Points earned** are the sum of every `PactPointSource`. **Resets available** are the sum of every
  `PactResetSource` minus the resets used, at most 63. Both are recomputed by
  `DemonicPacts.refresh`, which runs on login and after every commit, reset and grant, so a source
  never has to remember what it gave.
- **Commit** (`DemonicPacts.commit(player, indices)`, the indices the tree sends): refused unless the
  selection is non-empty, distinct, known and unowned, every node links to an owned node or one in
  the same commit, and the new spent total fits both the points earned and the `PactSpendCap`. The
  root, `AA`, needs no link and costs no point.
- **Resets available are capped at 63** (`DemonicPacts.MAX_RESETS`), what Jagex's 6-bit
  `talent_resets_available` holds, so the server, the tree and chat always agree. `grantReset` adds
  only up to the cap and returns how many it added (content that consumes an item for a reset can
  refuse at the cap); `::pactresets` clamps and says so. Resets that sources give past the cap wait
  until the count drops below it, but using a reset at the cap leaves 62, as Jagex's counter would.
  Jagex's league never came near it (6 resets at most), so clamping the
  count rather than showing a number the client can't hold is the Jagex-consistent choice.
- **Reset** (`DemonicPacts.reset`): refunds every node except the root and uses one reset. Refused
  with nothing but the root owned, when the `PactResetGuard` refuses, or with no reset available
  ("You need at least one pact reset point to do that.").
- **Effects**: `DemonicPacts.effects(player)` is an immutable `PactEffects` (effect id → summed
  value). Effect code reads it, never the `talent_*` varbits, which are clamped to their widths.

Extension points, bound in `DemonicPactsModule`:

| Extension point | Kind | Default (Leagues VI) |
|---|---|---|
| `PactPointSource` | set | `StartingPactPoints` (2) + `GrantedPactPoints` (`DemonicPacts.grantPoints`) |
| `PactResetSource` | set | `FreePactResets` (2) + `GrantedPactResets` (`DemonicPacts.grantReset`) |
| `PactSpendCap` | single (`@ImplementedBy`) | `PactSettings.spendCap`, 40 |
| `PactResetGuard` | single (`@ImplementedBy`) | always allowed |
| `PactEffectValues` | single (`@ImplementedBy`) | `CachePactEffectValues`: each node's `talent_tree` value (tooltips keep showing it) |
| `PactCommitListener`, `PactResetListener` | sets | none |
| `PactActivation` | single (`@ImplementedBy`) | `ConfigPactActivation`: `game.yml`'s `enabled` for everyone |
| `PactSettings` | single (`@ProvidedBy`, from `game.yml`) | off, 2 starting points, 2 free resets, cap 40 |

A server adds set elements with `addSetBinding` in its own `PluginModule` and replaces a single
hook with an explicit `bind`, which wins over the annotation default.

Admin commands (`DemonicPactsStateScript`): `::pactset <id ...>` owns exactly those nodes (wiki ids
or indices, `all` or `none`), ignoring links and points, as the effect tests do;
`::pactpoints [n]` sets the granted points (no number: shows the state); `::pactresets <n>` makes
`n` resets available.

Choices where the original league is unknown: the root
is free and isn't counted as spent; a reset with nothing but the root owned is refused rather than
spent; points over the cap are kept but can't be spent.

## Effects (packages `org.rsmod.content.leagues.demonicpacts.effects.*`, one per batch)

Effect code reads `DemonicPacts.effects(player)` only, so a player whose pacts are inactive (an empty
snapshot) gets nothing. Each batch keeps its effect ids in one object and registers them in
`PactEffectCoverageTest` (integration), which checks them against the tree.
`PactEffectsListener` (a set, declared in `DemonicPactsModule`) is told after every
`DemonicPacts.refresh` (login, commit, reset, grants, `::pactset`), for effects with their own state.

**PvP** (Jagex's limit: "Stat boosts, attack speed
increases, and melee range increases apply, but effects like Thorns, Echoes, and Blindbag do not
work against players"). The combat modifier pipeline runs for a player's attacks on players too,
but keeps only their accuracy, max hit, prayer penetration, attack speed and attack range
(`AttackModifiers.pvp`); min hits, max-hit chances, crits and every proc (extra hits, echoes,
Blindbag, burns, heals, drains, special energy) stay PvN, and Thorns, recoil and reflect only answer
npc hits. Against a player, prayer penetration ignores that share of the 40% a protection prayer
blocks. So in PvP the flat stat pacts (accuracy, style damage, prayer penetration; Defence is a
stat boost anyway) apply in full, and the batches below note what else does. A provider checks
`AttackContext.isPvp` only for a stat that a proc pays for or builds (I3, H6, the bow stacks, C3).

**Defence, overheal and prayer** (`effects.defence`: `DefencePactEffects`, `DefencePactsModule`, `DefencePactsScript`):

| Nodes | Effect id | Implementation |
|---|---|---|
| F2, F13, H9 | 18 | `PactOverheal`, an `OverhealProvider`: pact healing (`PlayerHealSource.Pact`) overheals up to +30% of base Hitpoints per node. The nodes stack (+90% with all three), so the server gives what "Active Pact Info" shows. Overheal decays like boosted Hitpoints (`PlayerHealing`). |
| D1 | 19 | `PactRetaliation`: with a shield (`WeaponClass.Shield`) worn, every hitsplat an npc deals, 0s included, hits it back for 3 melee damage on the next tick. |
| M1 | 84 | Thorns hits a second time for half its damage, rounded down. |
| G1 | 23 | With a shield worn, a roll of `random.of(1000) < Defence level × value` deals the hit's damage back as typeless damage. The player still takes the hit; a 0 is never reflected. |
| J1 | 64 | Thorns and reflect hits deal 1% of the summed defence bonuses (all five styles, negatives count) more. |
| J8 | 75 | `PactBlockHeal`: an attack (npc or player) that deals 0 while a two-handed weapon or an off-hand is worn heals 2 (pact healing, so it overheals with F2) and restores 2 Prayer. |
| I6 | 61 | `PactPrayerRestore`: soft timer `timer.demonic_pacts_prayer_restore` (32059). Every `max(1, 15 − prayer bonus / 7)` ticks, restores 1 Prayer unless a protection prayer is on. |

- **Overheal stacks**: the three nodes share effect id 18 with
  value 30 in `dbtable.talent_tree`, and Jagex's own panel (`[proc,script9458]`) shows one total
  per effect id from `talent_overhealing_via_talents`, a 7-bit varbit that holds the 90 three nodes
  sum to (a non-stacking unlock would be a 1-bit flag, like L5–L8). The node text has a "+" like
  every other additive node and states no cap, while Jagex's capped effects say so in their text
  ("up to 100%" for prayer penetration, "cannot exceed" for B1 and C4, "cannot reduce attack rate
  below" for F7). No source gives a cap, so the server sums the nodes with none. The wiki says
  nothing either way, so this is inferred from the cache.
- **Defence boost and activation:** the saved Defence level holds the pact boost only if the
  player's pacts were active at logout. `StatPactsScript` compares that (`wasActiveAtLogout`) with
  the activation at login: switched off, the boost from the saved nodes is taken off (never below
  the resting level, so a drain stays); switched back on, it is added again. A refresh while logged
  in does the same through `PactDefenceBoostRefresh`.
- Thorns and reflect skip mechanic damage (`Hit.isMechanic`, carried from `HitBuilder.isMechanic`)
  and never trigger on hits from players (Jagex's PvP limit). Their hits go through the npc's
  normal hit queue, so they count as the player's damage.
- Choices where the wiki is silent: "two-handed shield" (J8) means a two-handed weapon; reflect is
  extra damage, not a block; J1 also scales reflect; the I6 timer keeps running while a protection
  prayer is on and only skips the restore.

**Melee** (`effects.melee`: `MeleePactEffects`, `MeleePactsModule`):

| Nodes | Effect id | Implementation |
|---|---|---|
| D2 | 43 | `LightWeaponDoubleHit`: a melee attack with a weapon under 1kg hits again (`HitSource.DoubleStrike`), rolling accuracy again and dealing 40% of the rolled damage, at least 1 on a hit (`ExtraHit.reroll(source, damagePercent)`). |
| D3 | 44 | `Blindbag`: a 15% chance per attack with a heavy melee weapon to attack again at once (`HitSource.Blindbag`) as if a random heavy melee weapon from the inventory were worn. Blindbag attacks chain at the same chance, up to 10 in a row. |
| J3 | 72 | +2% Blindbag chance per unique heavy melee weapon in the inventory, 5 at most. |
| M3 | 68 | +2% (the value) Blindbag max hit per unique heavy melee weapon, 5 at most. |
| G4 | 62 | `SpecialEnergyPacts`: an attack of any style from 2 or more tiles restores 2% special energy. |
| M2 | 66 | `SpecialEnergyPacts`: each melee hit that deals damage, extra hits included, restores 2% (the value). |
| G8 | 67 | +5 min hit while 5 hitpoints are overhealed; `OverhealStrike` spends them (`PlayerHealing.consumeOverheal`). |
| G5, G10 | 26 | `DistanceHealing`: each melee attack, and each Thorns hit (`PactThornsListener`), has the summed chance (10% each) to heal 1 per tile to the target, as pact healing (it overheals with F2). |
| G6 | 77 | +20% of the Strength level as melee strength with a melee weapon under 1kg or one-handed. |
| G7 | 73 | +50% of the worn prayer bonus as melee strength. |
| B3 | 25 | Min hit +3 (the value) per tile to the target. |
| J4 | 70 | Max hit +4% (the value), +4% more per 3 tiles. |
| D4 | 20 | Two-handed melee weapons' range is multiplied by the value (2). |
| M4 | 69 | Then a melee range of 4 or more becomes 7 (a halberd with D4); halberds attack every 5 ticks at most. |
| J2 | 65 | Melee weapons under 1kg attack 1 tick faster. |
| H4 | 16 | With an off-hand (`WeaponClass.OffHand`): +5 melee strength, +5 ranged strength or +2% magic damage for the attack's style. |

The stat effects are `MeleePactModifiers`, a `CombatModifierProvider`, so like the ranged batch they
apply to a player's attacks on npcs, and their PvP stats to attacks on players. Distances are
counted to the nearest tile of the target (adjacent = 1).

Choices where the wiki and the cache text are silent (plan §4):
- **Blindbag** (§4.4): the bag weapon's best attack bonus of stab, slash and crush and its melee
  strength replace the worn weapon's (the worn weapon's bonus for the attack's type), and the
  Blindbag hit keeps the attack's type against the npc's defence, its stance and the worn
  weapon's passive effects. A bag weapon that is two-handed doesn't take off a worn shield. It
  ignores requirements, rolls no special, and doesn't delay the next attack. "Unique" counts
  distinct objs. The chain cap (10, the pipeline's most) is ours; the wiki gives none.
- **D2:** the extra hit rolls accuracy; "40% of the base max hit" is applied to the rolled damage.
- **B3** (§4.6): "each additional tile" counts from 2, so +3 adjacent and +6 at 2 tiles. B3 and G8
  add to the engine's min hit of 1.
- **G8:** spends the 5 hitpoints once per attack, when its first hit is rolled, hit or miss; only
  the attack's own hits get the +5.
- **G4, G5, G10:** once per attack (its first hit), special attacks included. G6 reads the current
  (boosted) Strength level; G7 ignores a negative prayer bonus.
- **"Melee weapon"** means a melee-style attack (unarmed included) for B3, J4, G5, G8 and M2; D2,
  D3, G6, J2, D4 and M4 check the weapon's class.
- **PvP:** J2's and M4's speed, D4's and M4's range, J4's max hit and G6's, G7's and H4's strength
  apply against players, as in the league. B3's and G8's min hit, Blindbag (D3, J3, M3), D2's
  second hit and the G4, M2, G5 and G10 procs don't.

**Ranged and echo** (`effects.ranged`):

B2, K9, K10, E1–E3, K3, G9, N9, N4, N5, H6, K1, K2, K6, N8, K4, K8, N6, N7 and H1 (19 effect ids,
`RangedPactEffects`), bound by `RangedPactsModule` into the combat modifier pipeline
(`api/combat/combat-modifiers`), so they apply to a player's attacks on npcs, and their PvP stats to
attacks on players.
- `RangedPactModifiers` (a `CombatModifierProvider`) gives the stat effects: H1's max accuracy
  roll chance, H6's +25%, K1's prayer effect, K2/K6's ranged strength, N8's attack bonus, the bow
  and crossbow speeds and damage, N6/N7, and the echo chances and chain cap.
- `RangedEchoes` fires echoes (`HitSource.Echo`, a re-roll of the attack): on a Regenerated ammo
  shot (`ResourceConsumedEvent.regenerated`, so the Regenerate pacts must give their chance as a
  `ResourceModifiers.regenerateChancePercent`), or on a landed two-handed melee hit for G9. K3 lets
  each echo roll half the chance again, up to 4 times. Special attacks never echo.
- `ThrownExtraTarget` (N9) re-makes a thrown attack on the closest other attackable npc within 3
  tiles of the target (`HitSource.ExtraTarget`).
- `BowStacks` (N4/N5) and `StyleSwap` (H6) keep per-session state; `RangedPactResets` clears it on
  a pact reset.

Weapon classes come from `WeaponClasses`: the Eclipse atlatl is a thrown weapon (E3, K6, N8, N9)
and never a bow (E1, K4, N4, N5); ballistae are crossbows.

Choices where the wiki and the cache text are silent (plan §4):
- **Echo chance:** E2's +15% adds to the echo chance on Regenerate. G9 rolls 5% (plus E2's) on every
  landed two-handed melee hit; its echoes re-roll the melee attack (melee stats and hitsplat) and
  count as bow, crossbow and thrown echoes for E1 and E3. A chained echo (K3) rolls half of the
  attack's echo chance, needs no Regenerate, and K3's "up to 4 times" is 4 echoes after the first.
- **E1 "never miss":** echoes ignore the defence roll and roll max accuracy, so they always hit.
- **H1:** 5% + 5% per tile, counted to the nearest tile of the target (adjacent = 1, so 10%).
- **H6:** "3 tiles" uses the same distance; the boost is +25% max hit on the next base hit of
  another style, kept until used, logout or a pact reset. Echoes don't prime or use it.
- **K2:** the difference counts both ways (below or above the Hitpoints level).
- **N4/N5:** one stack counter, +1 per landed base bow hit, capped at 15% of the max hit without
  N5's bonus. N4 makes the min hit `1 + stacks` (the engine's min hit is 1). Any npc hit that still
  deals damage after protection prayers halves the stacks.
- **N8:** "+60 ranged accuracy" is +60 ranged attack bonus (`AttackModifiers.attackBonusFlat`).
- **N9:** like chinchompas, the second throw needs both npcs in multi-combat and a line of sight
  between them; the closest npc within 3 tiles wins, and specials don't throw twice.
- The stat effects also apply to special attacks; echoes and N9 don't.
- The bow stacks, H6's primed style and the Regenerate hand-off are runtime state on the player's
  attributes (cleared on logout), not temp varps, so this batch needs no cache rebuild.
- **PvP:** H1, K1, K2, K6, N8, K4, K8 and N7 apply against players. Echoes (B2, K9, K10, E1–E3,
  K3, G9) and N9 don't, nor N6's max-hit chance or N4's min hit. H6 and the bow stacks (N4, N5)
  give nothing against players: they are primed and built by hits on npcs, and spending H6 is a
  proc.

**Magic** (`effects.magic`):

I1–I4, L1–L8, F7 and F8 (14 effect ids, `MagicPactEffects`), bound by `MagicPactsModule` into the
combat modifier pipeline, so they apply to a player's attacks on npcs, and their PvP stats to
attacks on players.
- `PactSpellElements` gives a spell's element: a standard elemental spell is its own; an Ancient
  spell counts as air, water, fire or earth only while L5 (smoke), L6 (ice), L7 (blood) or L8
  (shadow) is owned. Every element pact below reads it.
- `MagicPactModifiers` (a `CombatModifierProvider`): I1 (+7% per active prayer), I2 (up to +20% by
  current over base Hitpoints), I3's damage (twice the Hitpoints burned), L1 (1% max hit chance per
  prayer bonus, doubled when the npc is weak to air), L4 (+1 per 12 current Defence levels), F7
  (spellbook spells −2 ticks, not below 2) and F8 (powered staves −3 ticks, not below 1; one-handed
  powered staves −8 max hit).
- `MagicPactProcs` (a `CombatProcListener`): I3 takes the burned Hitpoints, I4 drains, L2 heals and
  L3 burns and bounces. `PactBurns` keeps L3's burns, run by `MagicPactsScript` on the caster's soft
  timer `timer.demonic_pacts_burn` (32058).
- The attack scope carries the spell (`CombatModifierPipeline.withAttack(..., spell)`, passed by
  `PvNCombat`), so F7's attack-speed query knows a spellbook spell from a powered staff's spell.

Choices where the wiki and the cache text are silent (plan §4):
- **"Damage added to a hit"** (I3, L4) raises both the min and the max hit, so a landed hit rolls in
  `bonus..max + bonus`; I1 and I2 are max hit percentages.
- **I3:** burns `min(6% of the Hitpoints level, Hitpoints − 1)` as a typeless hitsplat, once per cast,
  even on a splash. **I2:** linear in current/base Hitpoints, capped at 100%.
- **I4, L2, L3:** "hits" are hits that deal damage. I4 drains both levels by 2, not below 0. L2's heal
  is pact healing (it overheals with F2, F13 or H9), 60% rounded down.
- **L3 burn:** 1 typeless damage per stack every 4 ticks, 5 times after the last stack (plan §4.5);
  at most 5 stacks per npc and caster; the burns end on logout or when the npc dies.
- **L3 bounce:** like ancient bursts and N9, it needs both npcs in multi-combat and a clear line from
  the target; the 2 closest npcs within 3 tiles of the target get a full re-roll of the spell
  (`HitSource.Chain`). Bounced hits don't burn, heal, drain or bounce again, and only the first
  target of a multi-target spell bounces.
- **F8:** "one-handed" is the cache's wield slots, so the Trident of the seas, Sanguinesti staff and
  Warped sceptre lose 8 max hit and Tumeken's shadow doesn't. Only the built-in spell is affected.
- **PvP:** F7's and F8's attack speed and F8's max hit, I1, I2 and L4 apply against players (L4 to
  the max hit only, as min hits stay PvN). I3 gives nothing, because its Hitpoints cost is taken
  by a proc; L1's max-hit chance and the I4, L2 and L3 procs don't apply.

**Regenerate and resources** (`effects.resources`):

AA, BA–BC, F3, H2, J6 (Regenerate), B1, C1–C4, F5, F6, F9, F10 and G2 (11 effect ids,
`ResourcePactEffects`), bound by `ResourcePactsModule`.
- `ResourcePactModifiers` (a `CombatModifierProvider`) gives Regenerate as
  `ResourceModifiers.regenerateChancePercent` (summed, capped at 100% by the pipeline, rolled once
  per rune stack, shot or charge use; "Active Pact Info" shows the uncapped sum, see below), so every "on Regenerate" effect (these and the ranged echoes)
  sees `ResourceConsumedEvent.regenerated`. It works for every rune spell, teleports included, and
  adds C3's stored fire damage to the next magic hit.
- `ResourcePactProcs` (a `CombatProcListener`) reacts to a Regenerate: B1, C1–C4 for runes a combat
  spell (a rune spent inside a magic attack) Regenerated, and F5/F6/F9/F10 for a
  Regenerated powered staff or wand charge, adding the rune with `MagicRuneManager.addRunes`.
- `PactRegenerateBoosts` (a `StatBoostProvider`) holds B1's and C4's timed boosts as runtime
  attributes and raises the stat's resting level while they last; `ResourcePactsScript` ends them
  on the soft timers `timer.demonic_pacts_magic_boost` (32057) and `timer.demonic_pacts_defence_boost`
  (32056).
- `PactFreeSpecials` (a `SpecialAttackEnergyHook`): G2's 20% free special attacks.

Choices where the wiki and the cache text are silent:
- **Regenerate past 100%:** the tree offers 155% (AA 50, BA–BC 5 each, F3, H2 and J6 30 each), and
  Jagex sized `talent_regen_ammo_chance` (10 bits) for the sum, so the panel showing "+120%" is
  Jagex's display, kept as is. No source says what Regenerate past 100% did: the text is "a %
  chance to generate an additional resource", one resource per use, and unlike the capped effects
  it states no limit. The chance is capped at 100% because a chance can't be more; points past it
  are wasted, as they were in Jagex's tree as far as the sources show. If evidence of a second
  resource turns up, the pipeline's refund is the place to change.
- **B1** triggers once per cast, however many rune stacks were Regenerated (+1 Magic, at most +10).
- **C1** rolls its 15% once per air rune. **C2** is pact healing (it overheals with F2, F13, H9).
- **C3** "next spell hit" is the next magic hit that lands, the Regenerating cast's own hit included;
  a splash keeps it. **C4** caps at 20% of the base Defence level, rounded down.
- Combination runes count as both their elements, sunfire runes as fire (`PactRuneElements`).
- **F5, F6, F9, F10:** the added rune counts as a rune the spell Regenerated, so it triggers B1 and
  C1–C4. No rune is added (or counted) when neither the rune pouch nor the inventory has room.
- **PvP:** Regenerate itself and G2 work everywhere. A magic attack on a player opens the attack
  scope too, so its Regenerated runes trigger the stat boosts B1 and C4, as Jagex's stat boosts
  applied in PvP; C1, C2 and C3 (a restore, a heal and stored damage, spent by a proc) stay PvN, and
  C3's stored damage only goes to a hit on an npc.
- Not implemented here: Jagex made the abyssal tentacle, the crystal bow, the bow of faerdhinen and
  the Gauntlet's bows and staves Regenerate. On this server the tentacle spends no charges and the
  crystal bows (`category.chargebow`) and Gauntlet staves have no attack handler yet. Their handlers
  should spend charges through `CombatChargeManager`, or for an ammo-less bow call
  `CombatModifierPipeline.isRefunded(player, ResourceKind.Ammo, weapon, 1)` once per shot, and the
  Regenerate pacts (and the ranged echoes) then work with them.
