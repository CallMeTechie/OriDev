package dev.ori.data.di

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshScpClientImpl
import dev.ori.core.network.ssh.SshSftpClientImpl
import javax.inject.Qualifier

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolKey(val value: Protocol)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultSshClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SshClientModule {

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SFTP)
    abstract fun bindSftp(impl: SshSftpClientImpl): SshClient

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SCP)
    abstract fun bindScp(impl: SshScpClientImpl): SshClient

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SSH)
    abstract fun bindSshAsSftp(impl: SshSftpClientImpl): SshClient

    @Binds
    @DefaultSshClient
    abstract fun bindDefault(impl: SshSftpClientImpl): SshClient
}
