package dev.ori.core.common.model

enum class SshKeyType(val algorithmName: String, val displayName: String) {
    ED25519("ssh-ed25519", "Ed25519"),
    RSA("ssh-rsa", "RSA"),
}
