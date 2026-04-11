package dev.ori.core.common.model

enum class Protocol(val displayName: String, val defaultPort: Int) {
    SSH("SSH", 22),
    SFTP("SFTP", 22),
    SCP("SCP", 22),
    FTP("FTP", 21),
    FTPS("FTPS", 990),
    PROXMOX("Proxmox API", 8006),
    ;

    val isSshBased: Boolean get() = this in setOf(SSH, SFTP, SCP)
    val requiresEncryption: Boolean get() = this != FTP
}
