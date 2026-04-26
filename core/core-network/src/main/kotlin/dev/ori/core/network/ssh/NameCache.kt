package dev.ori.core.network.ssh

internal data class NameCache(val uids: Map<Int, String>, val gids: Map<Int, String>) {
    fun resolveUid(uid: Int): String = uids[uid] ?: uid.toString()
    fun resolveGid(gid: Int): String = gids[gid] ?: gid.toString()
    companion object { fun empty() = NameCache(emptyMap(), emptyMap()) }
}
