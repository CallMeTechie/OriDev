package dev.ori.data.mapper

import dev.ori.data.entity.ServerProfileEntity
import dev.ori.domain.model.ServerProfile

fun ServerProfileEntity.toDomain(): ServerProfile =
    ServerProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = protocol,
        username = username,
        authMethod = authMethod,
        credentialRef = credentialRef,
        sshKeyType = sshKeyType,
        startupCommand = startupCommand,
        projectDirectory = projectDirectory,
        claudeCodeModel = claudeCodeModel,
        claudeMdPath = claudeMdPath,
        isFavorite = isFavorite,
        lastConnected = lastConnected,
        createdAt = createdAt,
        sortOrder = sortOrder,
        require2fa = require2fa,
    )

fun ServerProfile.toEntity(): ServerProfileEntity =
    ServerProfileEntity(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = protocol,
        username = username,
        authMethod = authMethod,
        credentialRef = credentialRef,
        sshKeyType = sshKeyType,
        startupCommand = startupCommand,
        projectDirectory = projectDirectory,
        claudeCodeModel = claudeCodeModel,
        claudeMdPath = claudeMdPath,
        isFavorite = isFavorite,
        lastConnected = lastConnected,
        createdAt = createdAt,
        sortOrder = sortOrder,
        require2fa = require2fa,
    )
