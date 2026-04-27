package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class ScpListingParserTest {
    private val cache = NameCache(mapOf(1000 to "marc", 0 to "root"), mapOf(1000 to "marc", 0 to "root"))

    @Test fun simpleFile() {
        val out = "-rw-r--r-- 1 1000 1000 1234 2026-04-26T19:25:00 hello.txt\n"
        val files = ScpListingParser.parse(out, "/home/marc", cache)
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo("hello.txt")
        assertThat(files[0].path).isEqualTo("/home/marc/hello.txt")
        assertThat(files[0].size).isEqualTo(1234L)
        assertThat(files[0].owner).isEqualTo("marc")
        assertThat(files[0].isDirectory).isFalse()
    }

    @Test fun directoryEntry() {
        val out = "drwxr-xr-x 2 1000 1000 4096 2026-04-26T19:25:00 docs\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].isDirectory).isTrue()
    }

    @Test fun spacesInFilename() {
        val out = "-rw-r--r-- 1 1000 1000 10 2026-04-26T19:25:00 my file.txt\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("my file.txt")
    }

    @Test fun symlinkDropsTarget() {
        val out = "lrwxrwxrwx 1 1000 1000 12 2026-04-26T19:25:00 link -> /etc/hosts\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("link")
    }

    @Test fun arrowInRegularFilenameKept() {
        val out = "-rw-r--r-- 1 1000 1000 5 2026-04-26T19:25:00 a -> b.txt\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("a -> b.txt")
    }

    @Test fun zeroByte() {
        val out = "-rw-r--r-- 1 1000 1000 0 2026-04-26T19:25:00 empty\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].size).isEqualTo(0L)
    }

    @Test fun totalLineSkipped() {
        val out = "total 16\n-rw-r--r-- 1 1000 1000 1 2026-04-26T19:25:00 a\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)).hasSize(1)
    }

    @Test fun dotAndDotDotFiltered() {
        val out = """
            drwxr-xr-x 2 1000 1000 4096 2026-04-26T19:25:00 .
            drwxr-xr-x 3 1000 1000 4096 2026-04-26T19:25:00 ..
            -rw-r--r-- 1 1000 1000 1 2026-04-26T19:25:00 a
        """.trimIndent() + "\n"
        assertThat(ScpListingParser.parse(out, "/x", cache).map { it.name }).containsExactly("a")
    }

    @Test fun uidNotInCache() {
        val out = "-rw-r--r-- 1 9999 9999 1 2026-04-26T19:25:00 a\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].owner).isEqualTo("9999")
    }

    @Test fun emptyInput() {
        assertThat(ScpListingParser.parse("", "/x", cache)).isEmpty()
    }

    @Test fun bsdLsFormatRejected() {
        val out = "-rw-r--r--   1 marc  staff   1234 Apr 26 19:25 hello.txt\n"
        val ex = assertThrows(IOException::class.java) { ScpListingParser.parse(out, "/x", cache) }
        assertThat(ex.message).contains("GNU coreutils")
    }
}
