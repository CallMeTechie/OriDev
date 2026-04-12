package dev.ori.app.crash

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScrubbingSenderTest {

    @Test
    fun scrub_absolutePath_replacedWithPlaceholder() {
        val input = "at java.io.FileNotFoundException: /sdcard/Documents/secret.txt"

        val result = scrub(input)

        assertThat(result).contains("<path>")
        assertThat(result).doesNotContain("secret.txt")
        assertThat(result).doesNotContain("/sdcard/Documents")
    }

    @Test
    fun scrub_dataPath_replacedWithPlaceholder() {
        val input = "open /data/user/0/dev.ori.app/files/oops.log failed"

        val result = scrub(input)

        assertThat(result).contains("<path>")
        assertThat(result).doesNotContain("oops.log")
    }

    @Test
    fun scrub_fqdn_replacedWithPlaceholder() {
        val input = "ConnectionException: prod-db.customer.internal:22 refused"

        val result = scrub(input)

        assertThat(result).contains("<host>")
        assertThat(result).doesNotContain("customer.internal")
    }

    @Test
    fun scrub_publicFqdn_replacedWithPlaceholder() {
        val input = "Caused by: java.net.UnknownHostException: api.example.com"

        val result = scrub(input)

        assertThat(result).contains("<host>")
        assertThat(result).doesNotContain("example.com")
    }

    @Test
    fun scrub_pathAndHostTogether_bothReplaced() {
        val input = "Failed to upload /storage/emulated/0/notes.md to git.acme.dev"

        val result = scrub(input)

        assertThat(result).contains("<path>")
        assertThat(result).contains("<host>")
        assertThat(result).doesNotContain("notes.md")
        assertThat(result).doesNotContain("acme.dev")
    }

    @Test
    fun scrub_plainStackFrame_unchanged() {
        val input = "at dev.ori.feature.connections.SomeClass.method(SomeClass.kt:42)"

        val result = scrub(input)

        assertThat(result).isEqualTo(input)
    }
}
