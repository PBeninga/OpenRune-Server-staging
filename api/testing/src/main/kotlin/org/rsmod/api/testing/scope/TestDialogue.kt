package org.rsmod.api.testing.scope

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript

/**
 * The chatbox dialogue a test player is looking at, as [GameTestScope.dialogue] reads it.
 *
 * @property interf the open chatbox modal, for example `"interface.chat_left"` (npc),
 *   `"interface.chat_right"` (player), `"interface.messagebox"` or `"interface.chatmenu"`.
 * @property speaker the name shown above an npc or player line, or `null`.
 * @property text the dialogue text with line breaks (`<br>`) turned into spaces. For a choice menu,
 *   its title.
 * @property options the choices of a choice menu (`"interface.chatmenu"`), first option first.
 */
public data class TestDialogue(
    val interf: String,
    val speaker: String?,
    val text: String,
    val options: List<String>,
)

/**
 * Remembers the chatbox text written to one player, across ticks. The capture client keeps a
 * tick's outgoing messages only until the next tick, but a dialogue stays open for many.
 */
internal class DialogueRecorder {
    private val texts = HashMap<Int, String>()
    private var choiceTitle: String = ""
    private var choices: List<String> = emptyList()

    fun record(messages: List<Any>) {
        for (message in messages) {
            when (message) {
                is IfSetText -> texts[message.combinedId] = message.text
                is RunClientScript ->
                    if (message.id == CHATBOX_MULTI_INIT && message.values.size >= 2) {
                        choiceTitle = message.values[0].toString()
                        choices = message.values[1].toString().split('|')
                    }
            }
        }
    }

    fun read(interfaceId: Int): TestDialogue? {
        val interf = RSCM.getReverseMapping(RSCMType.INTERFACE, interfaceId)
        val layout = DialogueLayout.of(interf) ?: return TestDialogue(interf, null, "", emptyList())
        if (layout.isChoice) {
            return TestDialogue(interf, null, choiceTitle, choices)
        }
        val speaker = layout.speaker?.let { texts[it.asComponent()] }
        val text = layout.text?.let { texts[it.asComponent()] }.orEmpty()
        return TestDialogue(interf, speaker, text.flattenLines(), emptyList())
    }

    private fun String.asComponent(): Int = asRSCM(RSCMType.COMPONENT)

    private fun String.flattenLines(): String =
        replace("<br>", " ").replace(Regex(" +"), " ").trim()

    private companion object {
        /** `chatbox_multi_init`, which fills a `chatmenu` with its title and options. */
        const val CHATBOX_MULTI_INIT = 58
    }
}

/**
 * How a chatbox dialogue interface is laid out: where its speaker and text are, and which
 * component (and comsub) continues it.
 */
internal data class DialogueLayout(
    val speaker: String?,
    val text: String?,
    val continueComponent: String,
    val continueSub: Int,
    val isChoice: Boolean = false,
) {
    companion object {
        private val layouts: Map<String, DialogueLayout> =
            mapOf(
                "interface.chat_left" to
                    DialogueLayout(
                        speaker = "component.chat_left:name",
                        text = "component.chat_left:text",
                        continueComponent = "component.chat_left:continue",
                        continueSub = -1,
                    ),
                "interface.chat_right" to
                    DialogueLayout(
                        speaker = "component.chat_right:name",
                        text = "component.chat_right:text",
                        continueComponent = "component.chat_right:continue",
                        continueSub = -1,
                    ),
                "interface.messagebox" to
                    DialogueLayout(
                        speaker = null,
                        text = "component.messagebox:text",
                        continueComponent = "component.messagebox:continue",
                        continueSub = -1,
                    ),
                "interface.objectbox" to
                    DialogueLayout(
                        speaker = null,
                        text = "component.objectbox:text",
                        continueComponent = "component.objectbox:universe",
                        continueSub = 0,
                    ),
                "interface.objectbox_double" to
                    DialogueLayout(
                        speaker = null,
                        text = "component.objectbox_double:text",
                        continueComponent = "component.objectbox_double:pausebutton",
                        continueSub = -1,
                    ),
                "interface.chatmenu" to
                    DialogueLayout(
                        speaker = null,
                        text = null,
                        continueComponent = "component.chatmenu:options",
                        continueSub = -1,
                        isChoice = true,
                    ),
            )

        fun of(interf: String): DialogueLayout? = layouts[interf]
    }
}

/** The chatbox target every dialogue modal opens in. */
internal const val CHAT_MODAL: String = "component.chatbox:chatmodal"
