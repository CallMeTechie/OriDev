package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.LocalFileFilter
import net.schmizz.sshj.xfer.LocalSourceFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class SafSourceFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
    private val length: Long,
    private val name: String,
    private val permissions: Int = DEFAULT_PERMS,
) : LocalSourceFile {
    override fun getName() = name
    override fun getLength() = length
    override fun getInputStream(): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("Cannot open input stream for $uri")
    override fun getPermissions() = permissions
    override fun isFile() = true
    override fun isDirectory() = false
    override fun getChildren(filter: LocalFileFilter?): Iterable<LocalSourceFile> = emptyList()
    override fun providesAtimeMtime() = false
    override fun getLastAccessTime() = 0L
    override fun getLastModifiedTime() = 0L
    companion object { private const val DEFAULT_PERMS = 0b110_100_100 }
}

internal class SafDestFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
    private val name: String,
) : LocalDestFile {
    // `name` is exposed only for diagnostics — `LocalDestFile` itself has no `getName()`,
    // so this is a plain Kotlin property, not an override.
    fun displayName(): String = name

    override fun getLength() = 0L
    override fun getOutputStream(append: Boolean): OutputStream {
        require(!append) { "SafDestFile does not support resumable transfers (SCP cannot resume)" }
        return resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open output stream for $uri")
    }
    override fun getOutputStream(): OutputStream = getOutputStream(false)

    // SAF Uris always point at a single file; no child resolution needed. Return `this`
    // so SSHJ's `SCPDownloadClient` walks the recursion through us as if we were the
    // target file directly.
    override fun getChild(name: String): LocalDestFile = this
    override fun getTargetFile(filename: String): LocalDestFile = this
    override fun getTargetDirectory(dirname: String): LocalDestFile = this
    override fun setPermissions(perms: Int) = Unit
    override fun setLastAccessedTime(t: Long) = Unit
    override fun setLastModifiedTime(t: Long) = Unit
}
