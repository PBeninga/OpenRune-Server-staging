package org.rsmod.api.net.rsprot

/**
 * Closes a game connection [delayMillis] after the close is requested instead of at once.
 *
 * The server queues LOGOUT and then closes the socket. On a fast link the FIN can reach the client
 * before its game loop has read LOGOUT, so the client treats it as a lost connection and tries to
 * reconnect. OSRS clients close the socket themselves once they have read LOGOUT; the delayed close
 * is only the fallback for a client that never does.
 */
internal class LingeringClose(
    private val delayMillis: Long,
    private val schedule: (task: Runnable, delayMillis: Long) -> Unit,
    private val close: () -> Unit,
) {
    var requested: Boolean = false
        private set

    fun request() {
        if (requested) {
            return
        }
        requested = true
        schedule(Runnable { close() }, delayMillis)
    }
}
