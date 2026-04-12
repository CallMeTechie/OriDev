package dev.ori.data.mapper

import dev.ori.core.network.proxmox.model.ProxmoxVmDto
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus

fun ProxmoxNodeEntity.toDomain(
    isOnline: Boolean = false,
    nodeName: String? = null,
    cpuUsage: Double? = null,
    memUsed: Long? = null,
    memTotal: Long? = null,
) = ProxmoxNode(
    id = id,
    name = name,
    host = host,
    port = port,
    tokenId = tokenId,
    tokenSecretRef = tokenSecretRef,
    certFingerprint = certFingerprint,
    isOnline = isOnline,
    nodeName = nodeName,
    cpuUsage = cpuUsage,
    memUsedBytes = memUsed,
    memTotalBytes = memTotal,
)

fun ProxmoxNode.toEntity() = ProxmoxNodeEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    tokenId = tokenId,
    tokenSecretRef = tokenSecretRef,
    certFingerprint = certFingerprint,
    lastSyncAt = System.currentTimeMillis(),
)

fun ProxmoxVmDto.toDomain(nodeName: String) = ProxmoxVm(
    vmid = vmid,
    name = name ?: "vm-$vmid",
    nodeName = nodeName,
    status = ProxmoxVmStatus.fromString(status),
    cpuUsage = cpu,
    memUsedBytes = mem,
    memTotalBytes = maxmem,
    uptimeSeconds = uptime,
    isTemplate = template == 1,
)
