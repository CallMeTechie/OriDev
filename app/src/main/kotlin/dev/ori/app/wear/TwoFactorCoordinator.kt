package dev.ori.app.wear

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates 2FA request/response correlation between the phone connection flow
 * and the watch's approve/deny response. The connection flow publishes a request
 * and awaits a CompletableDeferred<Boolean>; the MessageListenerService completes
 * it when the watch responds.
 */
object TwoFactorCoordinator {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun registerRequest(requestId: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pending[requestId] = deferred
        return deferred
    }

    fun completeRequest(requestId: String, approved: Boolean) {
        pending.remove(requestId)?.complete(approved)
    }

    fun cancelRequest(requestId: String) {
        pending.remove(requestId)?.complete(false)
    }
}
