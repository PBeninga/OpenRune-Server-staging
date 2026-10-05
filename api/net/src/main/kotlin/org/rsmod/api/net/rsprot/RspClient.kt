package org.rsmod.api.net.rsprot

import java.util.concurrent.TimeUnit
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ExperimentalUnsignedTypes
private typealias Service = NetworkService<Player>

@OptIn(ExperimentalUnsignedTypes::class)
class RspClient(private val session: Session<Player>, private val infos: Infos) :
    Client<NetworkService<Player>, OutgoingGameMessage> {
    private val lingeringClose =
        LingeringClose(
            delayMillis = CLOSE_DELAY_MILLIS,
            schedule = { task, delay ->
                session.ctx.executor().schedule(task, delay, TimeUnit.MILLISECONDS)
            },
            close = { session.requestClose() },
        )

    override fun close() {
        lingeringClose.request()
    }

    override fun write(message: OutgoingGameMessage) {
        session.queue(message)
    }

    override fun read(player: Player) {
        if (lingeringClose.requested) {
            return
        }
        session.processIncomingPackets(player)
    }

    override fun flush() {
        if (lingeringClose.requested) {
            return
        }
        session.flush()
    }

    override fun flushHighPriority() {
        session.discardLowPriorityCategoryPackets()
        session.flush()
    }

    override fun unregister(service: NetworkService<Player>, player: Player) {
        service.infoProtocols.dealloc(infos)
    }

    private companion object {
        private const val CLOSE_DELAY_MILLIS = 2_000L
    }
}
