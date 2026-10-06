package org.rsmod.content.leagues.demonicpacts

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactActivation
import org.rsmod.content.leagues.demonicpacts.state.PactState
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * Opens and closes the Demonic Pacts tree (`interface.talent_tree`), the Leagues VI interface in
 * the cache.
 *
 * The tree opens as an overlay in the toplevel floater slot, where its init script makes it a
 * draggable floater (`toplevel_floater_drag_init(3, ...)`). `[clientscript,talent_tree_init]` then
 * draws it from the cache (`dbtable.talent_tree`, `enum_5942`) and the player's vars
 * (`combat_mastery_perm_*`, `talent_points_earned`, `talent_points_spent`,
 * `talent_resets_available`), and redraws it whenever the owned nodes or earned points change.
 * The pending selection is kept by the client; only the commit and the reset reach the server
 * ([PactCommitArgs], [PactResetArgs]). The client only sends them while their components have
 * the `ScriptTrigger` event.
 */
class DemonicPactsTree
@Inject
constructor(private val eventBus: EventBus, private val activation: PactActivation) {
    /**
     * Opens the tree, or tells [player] pacts aren't available to them and returns false when
     * their pacts aren't active ([PactActivation]).
     */
    fun open(player: Player): Boolean {
        if (!activation.isActive(player)) {
            player.mes(DemonicPacts.NOT_ACTIVE)
            return false
        }
        VarPlayerIntMapSetter.set(player, LEAGUE_TYPE, LEAGUES_VI)
        player.ifOpenOverlay(TREE, eventBus)
        player.ifSetEvents(NODES, 0 until NODE_COUNT, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3)
        player.ifSetEvents(PANEL, 0 until PANEL_SLOTS, IfEvent.Op1)
        player.ifSetEvents(CONFIRM_PANEL, 0 until PANEL_SLOTS, IfEvent.Op1)
        player.ifSetEvents(FRAME, CLOSE_BUTTON..CLOSE_BUTTON, IfEvent.Op1)
        player.ifSetEvents(COMMIT, -1..-1, IfEvent.ScriptTrigger)
        player.ifSetEvents(RESET, -1..-1, IfEvent.ScriptTrigger)
        player.runClientScript(TALENT_TREE_INIT, CENTRE_ON_ROOT)
        return true
    }

    fun close(player: Player) {
        player.ifCloseOverlay(TREE, eventBus)
    }

    companion object {
        const val TREE = "interface.talent_tree"
        const val FRAME = "component.talent_tree:frame"
        const val NODES = "component.talent_tree:tree_node_draw_layer"
        const val PANEL = "component.talent_tree:perm_floating_panel_content"
        const val CONFIRM_PANEL = "component.talent_tree:floating_panel_content"
        const val COMMIT = "component.talent_tree:data_layer"
        const val RESET = PANEL

        const val LEAGUE_TYPE = PactState.LEAGUE_TYPE
        const val LEAGUES_VI = PactState.LEAGUES_VI

        const val NODE_COUNT = 132
        const val CLOSE_BUTTON = 11

        /**
         * `[clientscript,talent_tree_init]`. Not a gameval: `clientscript.rscm` still gives 7892 to
         * an older proc, so a custom name would collide with it.
         */
        const val TALENT_TREE_INIT = 7892

        private const val PANEL_SLOTS = 16
        private const val CENTRE_ON_ROOT = 1
    }
}
