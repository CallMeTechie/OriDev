package dev.ori.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.ori.core.network.proxmox.model.ProxmoxVmDto
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVmStatus
import org.junit.jupiter.api.Test

class ProxmoxMapperTest {

    @Test
    fun vmDto_withNullName_coercesToVmPrefix() {
        val dto = ProxmoxVmDto(vmid = 101, name = null, status = "running")
        val vm = dto.toDomain("pve")
        assertThat(vm.name).isEqualTo("vm-101")
        assertThat(vm.nodeName).isEqualTo("pve")
    }

    @Test
    fun vmDto_withStatus_mapsToEnum() {
        assertThat(ProxmoxVmDto(vmid = 1, status = "running").toDomain("pve").status)
            .isEqualTo(ProxmoxVmStatus.RUNNING)
        assertThat(ProxmoxVmDto(vmid = 1, status = "stopped").toDomain("pve").status)
            .isEqualTo(ProxmoxVmStatus.STOPPED)
        assertThat(ProxmoxVmDto(vmid = 1, status = "paused").toDomain("pve").status)
            .isEqualTo(ProxmoxVmStatus.PAUSED)
        assertThat(ProxmoxVmDto(vmid = 1, status = "weird").toDomain("pve").status)
            .isEqualTo(ProxmoxVmStatus.UNKNOWN)
    }

    @Test
    fun node_roundTrip_preservesFields() {
        val node = ProxmoxNode(
            id = 42L,
            name = "lab",
            host = "10.0.0.5",
            port = 8006,
            tokenId = "root@pam!api",
            tokenSecretRef = "proxmox_token_42",
            certFingerprint = "AA:BB:CC",
        )
        val entity: ProxmoxNodeEntity = node.toEntity()
        val roundTripped = entity.toDomain()
        assertThat(roundTripped.id).isEqualTo(node.id)
        assertThat(roundTripped.name).isEqualTo(node.name)
        assertThat(roundTripped.host).isEqualTo(node.host)
        assertThat(roundTripped.port).isEqualTo(node.port)
        assertThat(roundTripped.tokenId).isEqualTo(node.tokenId)
        assertThat(roundTripped.tokenSecretRef).isEqualTo(node.tokenSecretRef)
        assertThat(roundTripped.certFingerprint).isEqualTo(node.certFingerprint)
    }

    @Test
    fun vmDto_templateFlag_mapsToIsTemplate() {
        val template = ProxmoxVmDto(vmid = 9000, name = "ubuntu-tmpl", status = "stopped", template = 1)
        val regular = ProxmoxVmDto(vmid = 100, name = "vm", status = "running", template = 0)
        val none = ProxmoxVmDto(vmid = 101, name = "vm", status = "running", template = null)
        assertThat(template.toDomain("pve").isTemplate).isTrue()
        assertThat(regular.toDomain("pve").isTemplate).isFalse()
        assertThat(none.toDomain("pve").isTemplate).isFalse()
    }
}
