package com.mortgee.al_mobile.ui.landing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoanLandingActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun syntheticSession(role: BackendRole): SessionState.Authenticated =
        SessionState.Authenticated(
            key = SessionKey("a", 1L),
            uid = "a",
            email = "a@example.test",
            role = role,
        )

    @Test
    fun borrowerActionVisibilityAndCallbackAcrossRolesAndStates() {
        var startLoanCount = 0
        var signOutCount = 0
        var authenticatedState by mutableStateOf<SessionState.Authenticated?>(
            syntheticSession(BackendRole.BORROWER)
        )
        var provideStartCallback by mutableStateOf(true)

        composeRule.setContent {
            AuthenticatedLandingScreen(
                authenticatedState = authenticatedState,
                onSignOut = { signOutCount++ },
                onStartLoanApplication = if (provideStartCallback) {
                    { startLoanCount++ }
                } else {
                    null
                },
            )
        }

        // 1. Borrower with callback sees "Apply for a mortgage"; one tap -> callback count 1
        composeRule.onNodeWithText("Apply for a mortgage").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Apply for a mortgage").performClick()
        assertEquals(1, startLoanCount)
        assertEquals(0, signOutCount)

        // 2. Borrower without callback -> no button
        composeRule.runOnIdle {
            provideStartCallback = false
        }
        assertEquals(0, composeRule.onAllNodesWithText("Apply for a mortgage").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Session Established").assertIsDisplayed()

        // 3. Broker with callback -> no button
        composeRule.runOnIdle {
            provideStartCallback = true
            authenticatedState = syntheticSession(BackendRole.BROKER)
        }
        assertEquals(0, composeRule.onAllNodesWithText("Apply for a mortgage").fetchSemanticsNodes().size)
        assertEquals(1, startLoanCount)

        // 4. Underwriter with callback -> no button
        composeRule.runOnIdle {
            authenticatedState = syntheticSession(BackendRole.UNDERWRITER)
        }
        assertEquals(0, composeRule.onAllNodesWithText("Apply for a mortgage").fetchSemanticsNodes().size)
        assertEquals(1, startLoanCount)

        // 5. Null state with callback -> no button
        composeRule.runOnIdle {
            authenticatedState = null
        }
        assertEquals(0, composeRule.onAllNodesWithText("Apply for a mortgage").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Session Established").assertIsDisplayed()
        assertEquals(1, startLoanCount)
    }
}
