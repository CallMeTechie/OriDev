package dev.ori.core.common.model

enum class AuthMethod(val displayName: String) {
    PASSWORD("Password"),
    SSH_KEY("SSH Key"),
    KEY_AGENT("Key Agent"),
}
