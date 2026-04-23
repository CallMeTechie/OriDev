package dev.ori.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ResumeEventsTest {

    @Test
    fun `OpenConnections carries profileId`() {
        val action = ResumeAction.OpenConnections(profileId = 7L)
        assertThat(action.profileId).isEqualTo(7L)
    }

    @Test
    fun `ResumeSnackbar default action is None`() {
        val snack = ResumeSnackbar(message = "oops", actionLabel = null, action = ResumeAction.None)
        assertThat(snack.action).isEqualTo(ResumeAction.None)
    }
}
