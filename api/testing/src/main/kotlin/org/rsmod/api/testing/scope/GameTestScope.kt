package org.rsmod.api.testing.scope

import com.google.inject.Injector
import com.google.inject.Module
import dev.openrune.ServerCacheManager
import dev.openrune.map.MapSingletons
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.StatType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.aconverted.interf.IfSubType
import io.netty.buffer.Unpooled
import jakarta.inject.Inject
import java.util.IdentityHashMap
import kotlin.contracts.contract
import kotlin.reflect.KClass
import net.rsprot.buffer.extensions.pSmart1or2
import net.rsprot.buffer.extensions.pVarInt2s
import net.rsprot.buffer.extensions.pjstr
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.buttons.IfButtonD
import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.npcs.OpNpcV2
import net.rsprot.protocol.game.incoming.resumed.ResumePCountDialog
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.Assertions
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameCycle
import org.rsmod.api.inv.map.InvMapInit
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.net.rsprot.handlers.IfButtonDHandler
import org.rsmod.api.net.rsprot.handlers.IfScriptTriggerHandler
import org.rsmod.api.net.rsprot.handlers.MoveGameClickHandler
import org.rsmod.api.net.rsprot.handlers.OpLocHandler
import org.rsmod.api.net.rsprot.handlers.OpNpcHandler
import org.rsmod.api.net.rsprot.handlers.ResumePCountDialogHandler
import org.rsmod.api.net.rsprot.handlers.ResumePauseButtonHandler
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMain
import org.rsmod.api.player.ui.ifOpenSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.controller.isSuccess
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.factory.TestCacheTypes
import org.rsmod.api.testing.random.SequenceRandom
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.addLoc
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.region.Region
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * The receiver of [GameTestState.runGameTest]: one isolated game world with a single registered
 * [player], driven tick by tick with [advance].
 *
 * Cache types come from the global `ServerCacheManager`, looked up by RSCM name: [objType],
 * [npcType], [locType], [invType], or the [objTypes], [npcTypes] and [locTypes] maps
 * (`npcTypes["npc.man"]`). Cache types are mutable data classes shared by every test: never
 * modify one, `copy()` it instead.
 */
public class GameTestScope
@Inject
constructor(
    public val random: VariableGameRandom,
    public val mapClock: MapClock,
    public val eventBus: EventBus,
    public val players: PlayerList,
    public val conRepo: ControllerRepository,
    public val npcRepo: NpcRepository,
    private val gameCycle: GameCycle,
    private val collision: CollisionFlagMap,
    private val locZoneStorage: LocZoneStorage,
    private val locRegistry: LocRegistry,
    private val conRegistry: ControllerRegistry,
    private val npcRegistry: NpcRegistry,
    private val invMapInit: InvMapInit,
    private val protectedAccess: ProtectedAccessLauncher,
    private val ifButtonHandler: If3ButtonHandler,
    private val ifButtonDHandler: IfButtonDHandler,
    private val ifScriptTriggerHandler: IfScriptTriggerHandler,
    private val gameClickHandler: MoveGameClickHandler,
    private val resumePCountDialog: ResumePCountDialogHandler,
    private val resumePauseButtonHandler: ResumePauseButtonHandler,
    private val opLocHandler: OpLocHandler,
    private val opNpcHandler: OpNpcHandler,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val npcHitModifier: NpcHitModifier,
    private val regionRegistry: RegionRegistry,
    private val regionRepo: RegionRepository,
    private val cheatCommands: CheatCommandMap,
) {
    init {
        registerPlayer()
    }

    public val player: Player by lazy { players.first() }
    public val client: CaptureClient by lazy { player.captureClient }

    public val objTypes: TestTypeMap<ItemServerType> =
        TestTypeMap(RSCMType.OBJ, ServerCacheManager.getItems())

    public val npcTypes: TestTypeMap<NpcServerType> =
        TestTypeMap(RSCMType.NPC, ServerCacheManager.getNpcs())

    public val locTypes: TestTypeMap<ObjectServerType> =
        TestTypeMap(RSCMType.LOC, ServerCacheManager.getObjects())

    private val Player.captureClient
        get() = client as CaptureClient

    public val Player.stats: StatsDelegate
        get() = StatsDelegate(statMap)

    private var playerUuidCounter = 0L

    private val dialogueRecorders = IdentityHashMap<Player, DialogueRecorder>()

    public fun advance(ticks: Int = 1) {
        repeat(ticks) {
            clearCaptureClients()
            gameCycle.tick()
            recordDialogues()
            flushCaptureClients()
        }
    }

    public fun advanceUntil(
        predicate: () -> Boolean,
        timeoutTicks: Int = 1000,
        timeoutMessage: () -> String? = { null },
    ) {
        for (i in 0 until timeoutTicks) {
            if (predicate()) {
                return
            }
            advance()
        }
        val message = timeoutMessage() ?: "Could not advance after $timeoutTicks ticks!"
        throw IllegalStateException(message)
    }

    @OptIn(InternalApi::class)
    public fun registerPlayer(
        coords: CoordGrid = CoordGrid(0, 50, 50, 0, 0),
        player: Player = Player(),
        slot: Int = players.nextFreeSlot() ?: error("No available slot."),
        client: Client<Any, Any> = CaptureClient(),
        uuid: Long? = null,
    ): Player {
        val resolvedUuid = uuid ?: playerUuidCounter++
        player.coords = coords
        player.slotId = slot
        player.client = client
        player.uuid = resolvedUuid
        player.accountId = resolvedUuid.toInt()
        player.characterId = resolvedUuid.toInt()
        player.accountHash = resolvedUuid
        player.userId = resolvedUuid
        player.userHash = resolvedUuid
        player.runEnergy = Int.MAX_VALUE
        player.assignUid()
        players[slot] = player
        eventBus.publish(SessionStateEvent.Initialize(player))
        if (player.invMap.isEmpty()) {
            invMapInit.init(player)
        }
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))
        return player
    }

    @OptIn(InternalApi::class)
    public fun unregisterPlayer(player: Player) {
        require(player.slotId != PathingEntity.INVALID_SLOT) {
            "Invalid `slotId` for player: $player"
        }
        val slot = player.slotId
        player.slotId = -1
        player.clearUid()
        player.destroy()
        players.remove(slot)
    }

    /** Sets a varp (`"varp.x"`) or varbit (`"varbit.x"`). */
    public fun Player.setVarp(varp: String, value: Int) {
        VarPlayerIntMapSetter.set(this, varp, value)
    }

    /** Sets a varbit (`"varbit.x"`) or varp (`"varp.x"`). */
    public fun Player.setVarBit(varbit: String, value: Int) {
        VarPlayerIntMapSetter.set(this, varbit, value)
    }

    public fun Player.setCurrentLevel(stat: String, level: Int) {
        stats.setCurrentLevel(stat, level)
    }

    public fun Player.setCurrentLevel(stat: StatType, level: Int) {
        stats.setCurrentLevel(stat.internalName, level)
    }

    public fun Player.setBaseLevel(stat: String, level: Int) {
        stats.setBaseLevel(stat, level)
    }

    public fun Player.setBaseLevel(stat: StatType, level: Int) {
        stats.setBaseLevel(stat.internalName, level)
    }

    public fun Player.setMoveSpeed(speed: MoveSpeed) {
        this.varMoveSpeed = speed
    }

    public fun Player.enableRun() {
        setMoveSpeed(MoveSpeed.Run)
    }

    public fun Player.enableWalk() {
        setMoveSpeed(MoveSpeed.Walk)
    }

    /**
     * Instantly sets the [Player]'s coordinates to [dest] without performing any of the updates or
     * side effects that normal teleport functions handle (such as updating the collision flag map).
     *
     * This function is intended for test setup only. It exists because using [teleport] or
     * [telejump] in tests would require calling [advance] to fully process the teleport logic and
     * any side effects, which can be unnecessary or cumbersome during test initialization.
     *
     * To simulate actual gameplay behavior during tests, use [teleport] or [telejump], as they
     * accurately reflect in-game mechanics and processing.
     */
    public fun Player.placeAt(dest: CoordGrid) {
        allocZoneCollision(dest)
        coords = dest
    }

    public fun Player.teleport(dest: CoordGrid) {
        allocZoneCollision(dest)
        PathingEntityCommon.teleport(this, collision, dest)
    }

    public fun Player.telejump(dest: CoordGrid) {
        allocZoneCollision(dest)
        PathingEntityCommon.telejump(this, collision, dest)
    }

    public fun Player.clearPendingAction() {
        clearPendingAction(eventBus)
    }

    public fun Player.clearInv(inv: Inventory = this.inv) {
        inv.fillNulls()
    }

    public fun Player.fillInv(with: InvObj = InvObj("obj.beer"), inv: Inventory = this.inv) {
        repeat(inv.size) { inv[it] = InvObj(with) }
    }

    public fun Player.count(obj: String, inv: Inventory = this.inv): Int = inv.count(obj)

    public fun Player.count(obj: ItemServerType, inv: Inventory = this.inv): Int =
        inv.count(obj.internalName)

    public fun Player.clearAllInvs() {
        invMap.values.forEach(Inventory::fillNulls)
    }

    /**
     * Executes the `::[command]` cheat for this player, as if typed in chat, with the given [args].
     *
     * Fails if no plugin script under test has registered [command].
     */
    public fun Player.cheat(command: String, vararg args: String) {
        val executed = cheatCommands.execute(this, command, args.toList())
        Assertions.assertTrue(executed) { "Cheat command not registered: `$command`" }
    }

    public fun Player.opLoc1(loc: BoundLocInfo, controlKey: Boolean = false) {
        opLoc(loc, controlKey, op = 1)
    }

    public fun Player.opLoc2(loc: BoundLocInfo, controlKey: Boolean = false) {
        opLoc(loc, controlKey, op = 2)
    }

    public fun Player.opLoc3(loc: BoundLocInfo, controlKey: Boolean = false) {
        opLoc(loc, controlKey, op = 3)
    }

    public fun Player.opLoc4(loc: BoundLocInfo, controlKey: Boolean = false) {
        opLoc(loc, controlKey, op = 4)
    }

    public fun Player.opLoc5(loc: BoundLocInfo, controlKey: Boolean = false) {
        opLoc(loc, controlKey, op = 5)
    }

    public fun Player.opNpc1(npc: Npc, controlKey: Boolean = false) {
        opNpc(npc, controlKey, op = 1)
    }

    public fun Player.opNpc2(npc: Npc, controlKey: Boolean = false) {
        opNpc(npc, controlKey, op = 2)
    }

    public fun Player.opNpc3(npc: Npc, controlKey: Boolean = false) {
        opNpc(npc, controlKey, op = 3)
    }

    public fun Player.opNpc4(npc: Npc, controlKey: Boolean = false) {
        opNpc(npc, controlKey, op = 4)
    }

    public fun Player.opNpc5(npc: Npc, controlKey: Boolean = false) {
        opNpc(npc, controlKey, op = 5)
    }

    /** Opens [interf] (`"interface.x"`) as the main modal. */
    public fun Player.ifOpenMain(interf: String) {
        ifOpenMain(interf, eventBus)
    }

    /** Opens [interf] (`"interface.x"`) as an overlay on [target] (`"component.x:y"`). */
    public fun Player.ifOpenOverlay(interf: String, target: String) {
        ifOpenSub(interf, target, IfSubType.Overlay, eventBus)
    }

    public fun Player.ifClose() {
        ifClose(eventBus)
    }

    /** Clicks [component] (`"component.x:y"`), as the client's `IF_BUTTON` packets do. */
    public fun Player.ifButton(
        component: String,
        comsub: Int? = null,
        op: IfButtonOp = IfButtonOp.Op1,
        obj: Int? = null,
    ) {
        val combinedId = CombinedId(component.asRSCM(RSCMType.COMPONENT))
        val message = If3Button(combinedId, comsub ?: -1, obj = obj ?: -1, op = op.slot)
        captureClient.queue(ifButtonHandler, message)
    }

    /** Drags from [fromComponent] slot [fromComsub] to [intoComponent] slot [intoComsub]. */
    public fun Player.ifButtonD(
        fromComponent: String,
        fromComsub: Int,
        fromObj: ItemServerType?,
        intoComponent: String,
        intoComsub: Int,
        intoObj: ItemServerType?,
    ) {
        val fromCombinedId = CombinedId(fromComponent.asRSCM(RSCMType.COMPONENT))
        val intoCombinedId = CombinedId(intoComponent.asRSCM(RSCMType.COMPONENT))
        val message =
            IfButtonD(
                fromCombinedId,
                fromComsub,
                fromObj?.id ?: -1,
                intoCombinedId,
                intoComsub,
                intoObj?.id ?: -1,
            )
        captureClient.queue(ifButtonDHandler, message)
    }

    public fun Player.ifButtonD(
        fromComponent: String,
        fromComsub: Int,
        intoComsub: Int,
        fromObj: ItemServerType? = null,
        intoObj: ItemServerType? = null,
    ) {
        ifButtonD(
            fromComponent = fromComponent,
            fromComsub = fromComsub,
            fromObj = fromObj,
            intoComponent = fromComponent,
            intoComsub = intoComsub,
            intoObj = intoObj,
        )
    }

    /**
     * Sends `if_runscript` on [component] (`"component.x:y"`) with [args] encoded as the client
     * does: `Int`, `String`, `IntArray` or `Array<String>`. The server decodes them with the types
     * registered for [component] (`onIfScriptTrigger`), as it does for a real client.
     */
    public fun Player.ifScriptTrigger(
        component: String,
        vararg args: Any,
        comsub: Int = -1,
        crc: Int = 0,
    ) {
        val buffer = Unpooled.buffer()
        for (arg in args) {
            when (arg) {
                is Int -> buffer.pVarInt2s(arg)
                is String -> buffer.pjstr(arg)
                is IntArray -> {
                    buffer.pSmart1or2(arg.size)
                    arg.forEach { buffer.pVarInt2s(it) }
                }
                is Array<*> -> {
                    buffer.pSmart1or2(arg.size)
                    arg.forEach { buffer.pjstr(it as String) }
                }
                else -> error("Unsupported if_runscript arg: $arg")
            }
        }
        val combinedId = CombinedId(component.asRSCM(RSCMType.COMPONENT))
        val message = IfScriptTrigger(combinedId, comsub, -1, crc, buffer)
        captureClient.queue(ifScriptTriggerHandler, message)
    }

    public fun Player.resumeCountDialog(count: Int) {
        val message = ResumePCountDialog(count)
        captureClient.queue(resumePCountDialog, message)
    }

    public fun Player.moveGameClick(dest: CoordGrid, keyCombination: Int = 0) {
        allocZoneCollision(dest)
        val message = MoveGameClick(dest.x, dest.z, keyCombination)
        captureClient.queue(gameClickHandler, message)
    }

    /**
     * The chatbox dialogue this player is looking at (an npc or player line, a message box, an obj
     * box or a choice menu), or `null` if no chatbox modal is open. See [TestDialogue].
     */
    public fun Player.dialogue(): TestDialogue? {
        val interfaceId = ui.getModalOrNull(CHAT_MODAL)?.packed ?: return null
        val recorder = dialogueRecorder()
        recorder.record(captureClient.outgoingMessages)
        return recorder.read(interfaceId)
    }

    /**
     * Clicks "Click here to continue" on the open dialogue, as the client's `RESUME_PAUSEBUTTON`
     * packet does. Like every input helper it is applied on the next [advance].
     */
    public fun Player.resumePauseButton() {
        val dialogue = requireDialogue()
        val layout =
            DialogueLayout.of(dialogue.interf)
                ?: Assertions.fail("No continue button known for ${dialogue.interf}.")
        if (layout.isChoice) {
            Assertions.fail<Unit>("A choice menu is open; use chooseOption. ($dialogue)")
        }
        queueResumePauseButton(layout.continueComponent, layout.continueSub)
    }

    /** Picks choice [option] (1 is the first) of the open choice menu, on the next [advance]. */
    public fun Player.chooseOption(option: Int) {
        val dialogue = requireDialogue()
        val layout = DialogueLayout.of(dialogue.interf)
        if (layout == null || !layout.isChoice) {
            Assertions.fail<Unit>("No choice menu is open. ($dialogue)")
            return
        }
        if (option !in 1..dialogue.options.size) {
            Assertions.fail<Unit>("No option $option. (options=${dialogue.options})")
        }
        queueResumePauseButton(layout.continueComponent, option)
    }

    /** Picks the option of the open choice menu whose text is [option], on the next [advance]. */
    public fun Player.chooseOption(option: String) {
        val options = requireDialogue().options
        val index = options.indexOf(option)
        if (index < 0) {
            Assertions.fail<Unit>("Option not found. (search=$option) | (options=$options)")
        }
        chooseOption(index + 1)
    }

    /**
     * Asserts that [player]'s open dialogue shows exactly [text] (line breaks read as spaces) and,
     * if given, that [speaker] says it.
     */
    public fun assertDialogue(text: String, speaker: String? = null, player: Player = this.player) {
        val dialogue = player.requireDialogue()
        Assertions.assertEquals(text, dialogue.text) { "Unexpected dialogue text. ($dialogue)" }
        if (speaker != null) {
            Assertions.assertEquals(speaker, dialogue.speaker) { "Unexpected speaker. ($dialogue)" }
        }
    }

    /** Asserts that [player] has a choice menu open with exactly [options], in order. */
    public fun assertDialogueOptions(vararg options: String, player: Player = this.player) {
        val dialogue = player.requireDialogue()
        Assertions.assertEquals(options.toList(), dialogue.options) {
            "Unexpected options. ($dialogue)"
        }
    }

    /** Asserts that [player] has no chatbox dialogue open. */
    public fun assertNoDialogue(player: Player = this.player) {
        val dialogue = player.dialogue()
        Assertions.assertNull(dialogue) { "Dialogue is open: $dialogue" }
    }

    /**
     * Buys [count] (1, 5, 10 or 50) of [obj] (`"obj.x"`) from the open shop, as the client's
     * `Buy-n` op on the shop's item does. Applied on the next [advance].
     */
    public fun Player.shopBuy(obj: String, count: Int = 1) {
        val shop = openedShop ?: Assertions.fail("No shop is open.")
        val type = objType(obj)
        val slot = shop.inv.objs.indexOfFirst { it?.id == type.id }
        if (slot < 0) {
            Assertions.fail<Unit>("The shop does not stock $obj.")
        }
        ifButton("component.shopmain:items", comsub = slot + 1, op = shopOp(count), obj = type.id)
    }

    /**
     * Sells [count] (1, 5, 10 or 50) of [obj] (`"obj.x"`) from the inventory to the open shop, as
     * the client's `Sell n` op does. Applied on the next [advance].
     */
    public fun Player.shopSell(obj: String, count: Int = 1) {
        if (openedShop == null) {
            Assertions.fail<Unit>("No shop is open.")
        }
        val type = objType(obj)
        val slot = inv.objs.indexOfFirst { it?.id == type.id }
        if (slot < 0) {
            Assertions.fail<Unit>("The inventory holds no $obj.")
        }
        ifButton("component.shopside:items", comsub = slot, op = shopOp(count), obj = type.id)
    }

    private fun shopOp(count: Int): IfButtonOp =
        when (count) {
            1 -> IfButtonOp.Op2
            5 -> IfButtonOp.Op3
            10 -> IfButtonOp.Op4
            50 -> IfButtonOp.Op5
            else -> Assertions.fail("Shops buy and sell 1, 5, 10 or 50 at a time, not $count.")
        }

    private fun Player.requireDialogue(): TestDialogue =
        dialogue() ?: Assertions.fail("No dialogue is open. (modals=${ui.modals.backing.values})")

    private fun Player.dialogueRecorder(): DialogueRecorder =
        dialogueRecorders.getOrPut(this) { DialogueRecorder() }

    private fun Player.queueResumePauseButton(component: String, sub: Int) {
        val combinedId = CombinedId(component.asRSCM(RSCMType.COMPONENT))
        captureClient.queue(resumePauseButtonHandler, ResumePauseButton(combinedId, sub))
    }

    private fun recordDialogues() {
        for (player in players) {
            player.dialogueRecorder().record(player.captureClient.outgoingMessages)
        }
    }

    public fun Player.withProtectedAccess(action: suspend ProtectedAccess.() -> Unit) {
        protectedAccess.launch(this) { action() }
    }

    public fun Npc.telejump(dest: CoordGrid) {
        allocZoneCollision(dest)
        PathingEntityCommon.telejump(this, collision, dest)
    }

    public fun Npc.opPlayer2(target: Player) {
        opPlayer2(target, aiPlayerInteractions)
    }

    public fun Npc.apPlayer2(target: Player) {
        apPlayer2(target, aiPlayerInteractions)
    }

    public fun Npc.queueHit(source: Player, delay: Int, type: HitType, damage: Int) {
        queueHit(source, delay, type, damage, npcHitModifier)
    }

    public fun Inventory.count(obj: ItemServerType): Int = count(obj.internalName)

    public fun allocZoneCollision(coord: CoordGrid) {
        collision.allocateIfAbsent(coord.x, coord.z, coord.level)
    }

    /** The cache obj type [internal] (`"obj.x"`). */
    public fun objType(internal: String): ItemServerType = objTypes[internal]

    /** The cache npc type [internal] (`"npc.x"`). */
    public fun npcType(internal: String): NpcServerType = npcTypes[internal]

    /** The cache loc type [internal] (`"loc.x"`). */
    public fun locType(internal: String): ObjectServerType = locTypes[internal]

    /** The cache inv type [internal] (`"inv.x"`). */
    public fun invType(internal: String): InventoryServerType =
        ServerCacheManager.getInventory(internal.asRSCM(RSCMType.INV))
            ?: error("Inv type not found: $internal")

    public fun spawnNpc(coords: CoordGrid, type: NpcServerType, init: Npc.() -> Unit = {}): Npc {
        val npc = Npc(type, coords).apply(init)
        val add = npcRegistry.add(npc)
        check(add.isSuccess()) { "Could not add npc: result=$add, npc=$npc" }
        return npc
    }

    /** Spawns the cache npc [type] (`"npc.x"`) at [coords]. */
    public fun spawnNpc(coords: CoordGrid, type: String, init: Npc.() -> Unit = {}): Npc =
        spawnNpc(coords, npcType(type), init)

    /** Spawns a controller of [type] (`"controller.x"`) at [coords]. */
    public fun spawnController(
        coords: CoordGrid,
        type: String,
        duration: Int = Int.MAX_VALUE,
        init: Controller.() -> Unit = {},
    ): Controller {
        val controller = Controller(type, coords).apply { duration(duration) }.apply(init)
        val add = conRegistry.add(controller)
        check(add.isSuccess()) { "Could not add controller: result=$add, controller=$controller" }
        return controller
    }

    /**
     * Places a map loc of [type] at [coords], with its collision, as if it had been decoded from
     * the map.
     *
     * Packet handlers look loc types up in `ServerCacheManager`, so a loc that a test interacts with
     * (`opLoc1`, ...) must be a real cache type. Standalone types from `locTypeFactory` only work
     * for code that receives the type directly.
     */
    public fun placeMapLoc(
        coords: CoordGrid,
        type: ObjectServerType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): BoundLocInfo {
        val entity = LocEntity(type.id, shape.id, angle.id)
        val locInfo = LocInfo(LocLayerConstants.of(shape.id), coords, entity)
        val boundLoc = BoundLocInfo(locInfo, type)
        collision.addLoc(locInfo, type)

        val zoneKey = ZoneKey.from(coords)
        val zoneGrid = ZoneGrid.from(coords)
        val locZoneKey = LocZoneKey(zoneGrid, locInfo.layer)
        locZoneStorage.mapLocs[zoneKey, locZoneKey] = entity

        TestCacheTypes.objects.registerIfSynthetic(type.id, type)

        return boundLoc
    }

    /** Places the cache loc [type] (`"loc.x"`) at [coords]. See [placeMapLoc]. */
    public fun placeMapLoc(
        coords: CoordGrid,
        type: String,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): BoundLocInfo = placeMapLoc(coords, locType(type), shape, angle)

    public fun locDel(bound: BoundLocInfo) {
        val locInfo = LocInfo(bound.layer, bound.coords, bound.entity)
        locDel(locInfo)
    }

    public fun locDel(loc: LocInfo) {
        locRegistry.del(loc)
    }

    public fun findLocs(coords: CoordGrid): Sequence<LocInfo> =
        locRegistry.findAll(ZoneKey.from(coords))

    public fun findLoc(coords: CoordGrid, type: String): LocInfo? =
        findLocs(coords).firstOrNull { it.id == type.asRSCM(RSCMType.LOC) }

    public fun locExists(loc: BoundLocInfo): Boolean = locRegistry.isValid(loc.coords, loc.id)

    public fun locExists(coords: CoordGrid, type: String): Boolean =
        locRegistry.isValid(coords, type.asRSCM(RSCMType.LOC))

    public fun findLocTypes(predicate: (ObjectServerType) -> Boolean): Sequence<ObjectServerType> =
        locTypes.values.asSequence().filter(predicate)

    /** The first cache loc type in content group [content] (`"content.x"`). */
    public fun findLocType(
        content: String,
        predicate: (ObjectServerType) -> Boolean = { true },
    ): ObjectServerType {
        val contentId = content.asRSCM(RSCMType.CONTENT)
        return findLocTypes { it.contentGroup == contentId && predicate(it) }.first()
    }

    public fun findObjType(predicate: (ItemServerType) -> Boolean): Sequence<ItemServerType> =
        objTypes.values.asSequence().filter(predicate)

    public fun firstObjType(predicate: (ItemServerType) -> Boolean): ItemServerType {
        val filtered = findObjType(predicate)
        return filtered.firstOrNull()
            ?: throw NoSuchElementException("No ItemServerType found with given predicate.")
    }

    public fun createRegionOrNull(template: RegionTemplate): Region? {
        return regionRepo.add(template)
    }

    public fun createRegion(template: RegionTemplate): Region {
        return createRegionOrNull(template) ?: error("Could not create region.")
    }

    public fun removeInactiveRegions() {
        regionRegistry.removeInactiveSmallRegions()
        regionRegistry.removeInactiveLargeRegions()
    }

    /**
     * Registers the cache map's locs (walls, doors, tables...) of the map square that holds
     * [coords], on every level, in this test's loc registry. A test world's loc registry starts
     * empty, though its collision already includes these locs.
     *
     * Call it before building an instance that copies this map square: the copy takes its loc
     * collision from the loc registry, so without the map's locs the copy has no walls.
     */
    public fun loadMapLocs(coords: CoordGrid) {
        val zones = MapSquareGrid.LENGTH / ZoneGrid.LENGTH
        for (level in 0 until CoordGrid.LEVEL_COUNT) {
            for (zx in 0 until zones) {
                for (zz in 0 until zones) {
                    val lx = zx * ZoneGrid.LENGTH
                    val lz = zz * ZoneGrid.LENGTH
                    val zone = CoordGrid(level, coords.mx, coords.mz, lx, lz)
                    val key = ZoneKey.from(zone)
                    val mapLocs = MapSingletons.locZones.mapLocs[key] ?: continue
                    locZoneStorage.mapLocs.getOrPut(key).putAll(mapLocs)
                }
            }
        }
    }

    public fun CaptureClient.clear() {
        clearOutgoing()
    }

    public fun assertTrue(condition: Boolean) {
        contract { returns() implies condition }
        Assertions.assertTrue(condition)
    }

    public fun assertTrue(condition: Boolean, message: () -> String) {
        contract { returns() implies condition }
        Assertions.assertTrue(condition, message)
    }

    public fun assertFalse(condition: Boolean) {
        contract { returns() implies !condition }
        Assertions.assertFalse(condition)
    }

    public fun assertNull(actual: Any?) {
        contract { returns() implies (actual == null) }
        Assertions.assertNull(actual)
    }

    public fun assertNotNull(actual: Any?) {
        contract { returns() implies (actual != null) }
        Assertions.assertNotNull(actual)
    }

    public fun assertEquals(expected: Any?, actual: Any?) {
        Assertions.assertEquals(expected, actual)
    }

    public fun assertNotEquals(expected: Any?, actual: Any?) {
        Assertions.assertNotEquals(expected, actual)
    }

    public fun assertContains(inv: Inventory, obj: String) {
        Assertions.assertTrue(obj in inv) { "Obj not found. (obj=$obj) | (inv=$inv)" }
    }

    public fun assertContains(inv: Inventory, obj: ItemServerType) {
        Assertions.assertTrue(obj in inv) { "Obj not found. (obj=$obj) | (inv=$inv)" }
    }

    public fun assertDoesNotContain(inv: Inventory, obj: String) {
        Assertions.assertFalse(obj in inv) { "Obj found. (obj=$obj) | (inv=$inv)" }
    }

    public fun assertDoesNotContain(inv: Inventory, obj: ItemServerType) {
        Assertions.assertFalse(obj in inv) { "Obj found. (obj=$obj) | (inv=$inv)" }
    }

    public fun assertExists(loc: BoundLocInfo) {
        Assertions.assertTrue(locExists(loc)) {
            val found = locRegistry.findAll(ZoneKey.from(loc.coords)).toList()
            "Loc not found. (loc=$loc) | (found=$found)"
        }
    }

    public fun assertExists(coords: CoordGrid, type: String) {
        Assertions.assertTrue(locExists(coords, type)) {
            val found = locRegistry.findAll(ZoneKey.from(coords)).toList()
            "Loc not found. (coords=$coords, type=$type) | (found=$found)"
        }
    }

    public fun assertDoesNotExist(loc: BoundLocInfo) {
        Assertions.assertFalse(locExists(loc)) { "Loc found. (loc=$loc)" }
    }

    public fun assertDoesNotExist(coords: CoordGrid, type: String) {
        Assertions.assertFalse(locExists(coords, type)) {
            "Loc found. (coords=$coords) | (type=$type)"
        }
    }

    public fun assertMessageSent(text: String, player: Player = this.player) {
        val messages = player.captureClient.mapOf(MessageGame::message)
        val sent = messages.contains(text)
        Assertions.assertTrue(sent) { "Message not found. (search=$text) | (messages=$messages)" }
    }

    public fun assertMessageNotSent(text: String, player: Player = this.player) {
        val messages = player.captureClient.mapOf(MessageGame::message)
        val sent = messages.contains(text)
        Assertions.assertFalse(sent) { "Message found. (search=$text) | (messages=$messages)" }
    }

    public fun assertMessagesSent(vararg text: String, player: Player = this.player) {
        require(text.isNotEmpty()) { "Must provide at least one `text` argument." }
        val messages = player.captureClient.mapOf(MessageGame::message)
        val notFound = text.filter { search -> messages.none { search == it } }
        Assertions.assertTrue(notFound.isEmpty()) {
            "Messages not found. (notFound=$notFound) | (messages=$messages)"
        }
    }

    public fun assertNoMessageSent(player: Player = this.player) {
        val messages = player.captureClient.mapOf(MessageGame::message)
        Assertions.assertEquals(emptyList<String>(), messages) { "Messages found:" }
    }

    /** Asserts that [interf] (`"interface.x"`) is open as a modal. */
    public fun assertModalOpen(interf: String, player: Player = this.player) {
        Assertions.assertTrue(player.ui.containsModal(interf)) {
            val openedModals = player.ui.modals.values.map(::UserInterface)
            "Modal not opened. (expected=$interf) | (found=$openedModals) | (player=$player)"
        }
    }

    /** Asserts that [interf] (`"interface.x"`) is not open as a modal. */
    public fun assertModalNotOpen(interf: String, player: Player = this.player) {
        Assertions.assertFalse(player.ui.containsModal(interf)) {
            val openedModals = player.ui.modals.values.map(::UserInterface)
            "Modal is opened. (modal=$interf) | (opened=$openedModals) | (player=$player)"
        }
    }

    /** Asserts that [interf] (`"interface.x"`) is open as an overlay. */
    public fun assertOverlayOpen(interf: String, player: Player = this.player) {
        Assertions.assertTrue(player.ui.containsOverlay(interf)) {
            "Overlay not opened. (expected=$interf) | (player=$player)"
        }
    }

    /** Asserts that [interf] (`"interface.x"`) is not open as an overlay. */
    public fun assertOverlayNotOpen(interf: String, player: Player = this.player) {
        Assertions.assertFalse(player.ui.containsOverlay(interf)) {
            "Overlay is opened. (overlay=$interf) | (player=$player)"
        }
    }

    /**
     * This requires a context (`Player` in this case) so that after catching the expected
     * [Throwable] the entity can be registered into the game world once again if they were
     * disconnected.
     */
    public inline fun <reified T : Throwable> Player.assertThrows(block: () -> Unit): T {
        val startClient = client
        val startSlot = slotId
        val startUuid = uuid
        val caught: Throwable? =
            try {
                client = CaptureClient()
                block()
                null
            } catch (t: Throwable) {
                t
            }

        Assertions.assertNotNull(caught) {
            "Expected ${T::class.java.simpleName} to be thrown, but nothing was thrown."
        }

        Assertions.assertInstanceOf(T::class.java, caught) {
            "Expected ${T::class.java.simpleName} to be thrown, " +
                "but ${caught?.javaClass?.simpleName} was thrown."
        }

        // We want to restore the player's capture client before any errors occurred.
        this.client = startClient

        // If player was disconnected due to the error, we should re-register them.
        if (slotId == PathingEntity.INVALID_SLOT && startSlot != PathingEntity.INVALID_SLOT) {
            registerPlayer(coords, this, startSlot, startClient, startUuid)
        }
        return caught as T
    }

    public fun assertDoesNotThrow(msg: String, block: () -> Unit) {
        Assertions.assertDoesNotThrow({ block() }, msg)
    }

    public fun assertDoesNotThrow(block: () -> Unit) {
        Assertions.assertDoesNotThrow { block() }
    }

    private fun Player.opLoc(loc: BoundLocInfo, controlKey: Boolean, op: Int) {
        val message =
            OpLocV2(
                id = loc.id,
                x = loc.coords.x,
                z = loc.coords.z,
                controlKey = controlKey,
                op = op,
                subop = -1,
            )
        captureClient.queue(opLocHandler, message)
    }

    private fun Player.opNpc(npc: Npc, controlKey: Boolean, op: Int) {
        val message = OpNpcV2(index = npc.slotId, controlKey = controlKey, op = op, subop = -1)
        captureClient.queue(opNpcHandler, message)
    }

    private fun clearCaptureClients() {
        for (player in players) {
            val client = player.captureClient
            client.clearOutgoing()
        }
    }

    private fun flushCaptureClients() {
        for (player in players) {
            val client = player.captureClient
            client.clearIncoming()
        }
    }

    /**
     * Builds the injector for one test. See [GameTestState.runGameTest] for what it contains.
     *
     * Every plugin module outside `org.rsmod.content` is installed, plus the content plugin modules
     * that own one of [scripts]. Only [scripts] have their events bound.
     */
    public class Builder(
        private val state: GameTestState,
        private val scripts: Set<KClass<out PluginScript>>,
    ) {
        internal fun build(): GameTestScope =
            buildInjector(emptyList()).getInstance(GameTestScope::class.java)

        internal fun buildInjector(overrides: List<Module>): Injector {
            val injector = state.createTestInjector(scripts, overrides)
            bindScriptEvents(injector)
            return injector
        }

        private fun bindScriptEvents(injector: Injector) {
            val context = injector.getInstance(ScriptContext::class.java)
            for (clazz in scripts) {
                val script = injector.getInstance(clazz.java)
                with(script) { context.startup() }
            }
        }
    }

    /**
     * A read-only view of one cache type table, keyed by id, that can also be indexed by RSCM name:
     * `npcTypes["npc.man"]`.
     */
    public class TestTypeMap<T : Any>(
        private val rscmType: RSCMType,
        private val backing: Map<Int, T>,
    ) : Map<Int, T> by backing {
        public operator fun get(internal: String): T =
            backing[internal.asRSCM(rscmType)] ?: error("Cache type not found: $internal")
    }

    @Suppress("konsist.avoid usage of stdlib Random in properties")
    public class VariableGameRandom {
        internal val impl: InternalRandomImpl = InternalRandomImpl()
        private val sequence = SequenceRandom(size = 128)
        private val default = DefaultGameRandom(seed = 123456)

        /** @see [SequenceRandom.next] */
        public var next: Int by sequence::next

        /** @see [SequenceRandom.then] */
        public var then: Int by sequence::then

        public var nextBoolean: Boolean
            get() = if (sequence.hasNext) sequence.next == 0 else default.randomBoolean()
            set(value) {
                next = if (value) 0 else 1
            }

        internal inner class InternalRandomImpl : GameRandom {
            override fun of(maxExclusive: Int): Int {
                return if (sequence.hasNext) {
                    sequence.of(maxExclusive)
                } else {
                    default.of(maxExclusive)
                }
            }

            override fun of(minInclusive: Int, maxInclusive: Int): Int {
                return if (sequence.hasNext) {
                    sequence.of(minInclusive, maxInclusive)
                } else {
                    default.of(minInclusive, maxInclusive)
                }
            }

            override fun randomDouble(): Double {
                return if (sequence.hasNext) {
                    sequence.randomDouble()
                } else {
                    default.randomDouble()
                }
            }
        }
    }

    /** Reads and writes skill levels by RSCM stat name (`player.stats["stat.attack"] = 99`). */
    public class StatsDelegate(private val backing: PlayerStatMap) {
        @OptIn(InternalApi::class)
        public operator fun get(stat: String): Int =
            backing.getCurrentLevel(stat).toInt() and 0xFF

        public operator fun set(stat: String, value: Int) {
            backing.setBaseLevel(stat, value.toByte())
            backing.setCurrentLevel(stat, value.toByte())
            setFineXp(stat, value)
        }

        public fun setBaseLevel(stat: String, value: Int) {
            backing.setBaseLevel(stat, value.toByte())
            setFineXp(stat, value)
        }

        public fun setCurrentLevel(stat: String, value: Int) {
            backing.setCurrentLevel(stat, value.toByte())
        }

        private fun setFineXp(stat: String, level: Int) {
            if (level <= 0) {
                backing.setFineXP(stat, 0)
            } else {
                val xp = PlayerSkillXPTable.getFineXPFromLevel(level)
                backing.setFineXP(stat, xp)
            }
        }
    }
}
