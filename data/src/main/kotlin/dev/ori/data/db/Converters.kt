package dev.ori.data.db

import androidx.room.TypeConverter
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

class Converters {

    @TypeConverter
    fun fromProtocol(value: Protocol): String = value.name

    @TypeConverter
    fun toProtocol(value: String): Protocol = Protocol.valueOf(value)

    @TypeConverter
    fun fromAuthMethod(value: AuthMethod): String = value.name

    @TypeConverter
    fun toAuthMethod(value: String): AuthMethod = AuthMethod.valueOf(value)

    @TypeConverter
    fun fromSshKeyType(value: SshKeyType?): String? = value?.name

    @TypeConverter
    fun toSshKeyType(value: String?): SshKeyType? = value?.let { SshKeyType.valueOf(it) }

    @TypeConverter
    fun fromTransferDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toTransferDirection(value: String): TransferDirection = TransferDirection.valueOf(value)

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus = TransferStatus.valueOf(value)
}
