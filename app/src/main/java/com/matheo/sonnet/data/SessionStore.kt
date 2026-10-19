package com.matheo.sonnet.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session Jellyfin récupérée automatiquement depuis l'interface web (pas de 2e connexion).
 * Gardée en mémoire seulement : le jeton n'est jamais écrit sur le disque par Sonnet.
 */
data class JellyfinSession(
    val server: String,
    val token: String,
    val userId: String,
)

object SessionStore {
    private val _session = MutableStateFlow<JellyfinSession?>(null)
    val session: StateFlow<JellyfinSession?> = _session.asStateFlow()

    fun update(session: JellyfinSession) {
        if (_session.value != session) _session.value = session
    }

    fun clear() {
        _session.value = null
    }
}

/** Interrupteur lu par le JavaScript injecté (thread JavaBridge) : doit rester thread-safe. */
object NativePlayerGate {
    @Volatile var enabled: Boolean = true
    @Volatile private var bypassNext: Boolean = false

    /** La prochaine lecture lancée depuis le web restera dans le lecteur Jellyfin web. */
    fun bypassOnce() {
        bypassNext = true
    }

    fun shouldIntercept(): Boolean {
        if (bypassNext) {
            bypassNext = false
            return false
        }
        return enabled
    }
}
