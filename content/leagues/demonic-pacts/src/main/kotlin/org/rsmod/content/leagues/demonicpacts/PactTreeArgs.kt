package org.rsmod.content.leagues.demonicpacts

import org.rsmod.api.player.ui.IfScriptArgs

/**
 * The tree's "Yes, Apply Changes" (`[proc,script9383]`): the selected nodes' indices in `enum_5942`
 * order, the same index as the node's bit in `combat_mastery_perm_{i / 32}`.
 */
class PactCommitArgs(val nodes: IntArray) : IfScriptArgs

/** The "Reset Pacts" button (`[clientscript,script7898]`), sent with no arguments. */
class PactResetArgs : IfScriptArgs
