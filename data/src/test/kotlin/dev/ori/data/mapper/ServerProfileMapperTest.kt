package dev.ori.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.domain.model.ServerProfile
import org.junit.jupiter.api.Test

class ServerProfileMapperTest {

    @Test
    fun entityToDomain_mapsAllFields() {
        val entity = ServerProfileEntity(
            id = 1,
            name = "test",
            host = "192.168.1.1",
            port = 22,
            protocol = Protocol.SSH,
            username = "admin",
            authMethod = AuthMethod.SSH_KEY,
            credentialRef = "key_1",
            sshKeyType = SshKeyType.ED25519,
            startupCommand = "cd /app",
            projectDirectory = "/app",
            claudeCodeModel = "opus",
            claudeMdPath = "/app/CLAUDE.md",
            isFavorite = true,
            lastConnected = 1000L,
            createdAt = 500L,
            sortOrder = 3,
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1)
        assertThat(domain.name).isEqualTo("test")
        assertThat(domain.host).isEqualTo("192.168.1.1")
        assertThat(domain.protocol).isEqualTo(Protocol.SSH)
        assertThat(domain.authMethod).isEqualTo(AuthMethod.SSH_KEY)
        assertThat(domain.sshKeyType).isEqualTo(SshKeyType.ED25519)
        assertThat(domain.isFavorite).isTrue()
        assertThat(domain.claudeCodeModel).isEqualTo("opus")
    }

    @Test
    fun domainToEntity_mapsAllFields() {
        val domain = ServerProfile(
            id = 2,
            name = "prod",
            host = "prod.example.com",
            port = 2222,
            protocol = Protocol.SFTP,
            username = "deploy",
            authMethod = AuthMethod.PASSWORD,
            credentialRef = "pw_2",
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(2)
        assertThat(entity.name).isEqualTo("prod")
        assertThat(entity.port).isEqualTo(2222)
        assertThat(entity.protocol).isEqualTo(Protocol.SFTP)
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = ServerProfileEntity(
            id = 5,
            name = "roundtrip",
            host = "rt.local",
            port = 22,
            protocol = Protocol.SCP,
            username = "user",
            authMethod = AuthMethod.KEY_AGENT,
            credentialRef = "agent_5",
            sshKeyType = null,
            startupCommand = null,
            projectDirectory = null,
            claudeCodeModel = null,
            claudeMdPath = null,
            isFavorite = false,
            lastConnected = null,
            createdAt = 999L,
            sortOrder = 0,
        )

        val result = original.toDomain().toEntity()

        assertThat(result).isEqualTo(original)
    }
}
