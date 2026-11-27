package com.mortgee.al_mobile.ui.loan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.repository.LoanSubmitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoanApplicationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debtVisibility() {
        var draft by mutableStateOf(LoanDraft())
        var errors by mutableStateOf(mapOf(LoanField.DEBT to "Choose Yes or No."))
        var enabled by mutableStateOf(true)
        var editCount = 0
        var debtChoiceCount = 0

        composeRule.setContent {
            LoanFinanceFields(
                draft = draft,
                errors = errors,
                enabled = enabled,
                onEdit = { field, text ->
                    editCount++
                    draft = draft.edit(field, text)
                },
                onDebt = { yes ->
                    debtChoiceCount++
                    draft = draft.chooseDebt(yes)
                    errors = errors - LoanField.DEBT
                },
            )
        }

        // Unanswered debt choice: neither chip selected, debt input absent, debt choice error shown
        assertNull(draft.hasDebt)
        composeRule.onNodeWithText("Do you have existing debt liabilities?").assertIsDisplayed()
        composeRule.onNodeWithText("Choose Yes or No.").assertIsDisplayed()
        composeRule.onNodeWithText("Yes").assertIsNotSelected()
        composeRule.onNodeWithText("No").assertIsNotSelected()
        assertEquals(0, composeRule.onAllNodesWithTag(LoanField.DEBT.path).fetchSemanticsNodes().size)

        // Tap Yes -> debt field appears; enter "10000"
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithText("Yes").assertIsSelected()
        composeRule.onNodeWithText("No").assertIsNotSelected()
        composeRule.onNodeWithTag(LoanField.DEBT.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.DEBT.path).performTextInput("10000")
        assertEquals("10000", draft[LoanField.DEBT])

        // All five financial values are independently addressable
        composeRule.onNodeWithTag(LoanField.INCOME.path).performTextInput("75000")
        composeRule.onNodeWithTag(LoanField.DEPOSIT.path).performTextInput("70000")
        composeRule.onNodeWithTag(LoanField.TERM.path).performTextInput("30")
        composeRule.onNodeWithTag(LoanField.PRICE.path).performTextInput("350000")

        assertEquals("75000", draft[LoanField.INCOME])
        assertEquals("10000", draft[LoanField.DEBT])
        assertEquals("70000", draft[LoanField.DEPOSIT])
        assertEquals("30", draft[LoanField.TERM])
        assertEquals("350000", draft[LoanField.PRICE])

        // Tap No -> debt input disappears and stored debt text is cleared
        composeRule.onNodeWithText("No").performClick()
        composeRule.onNodeWithText("No").assertIsSelected()
        composeRule.onNodeWithText("Yes").assertIsNotSelected()
        assertEquals(0, composeRule.onAllNodesWithTag(LoanField.DEBT.path).fetchSemanticsNodes().size)
        assertEquals("", draft[LoanField.DEBT])

        // Tap Yes again -> debt field reappears empty while other 4 financial fields stay intact
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithTag(LoanField.DEBT.path).assertIsDisplayed()
        assertEquals("", draft[LoanField.DEBT])
        composeRule.onNodeWithTag(LoanField.INCOME.path).assertTextContains("75000")
        composeRule.onNodeWithTag(LoanField.DEPOSIT.path).assertTextContains("70000")
        composeRule.onNodeWithTag(LoanField.TERM.path).assertTextContains("30")
        composeRule.onNodeWithTag(LoanField.PRICE.path).assertTextContains("350000")

        // When enabled=false, controls are disabled and dispatch no changes
        val editsBeforeDisable = editCount
        val choicesBeforeDisable = debtChoiceCount
        composeRule.runOnIdle {
            enabled = false
        }

        composeRule.onNodeWithText("Yes").assertIsNotEnabled()
        composeRule.onNodeWithText("No").assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.INCOME.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.DEBT.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.DEPOSIT.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.TERM.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.PRICE.path).assertIsNotEnabled()

        composeRule.onNodeWithText("No").performClick()
        assertEquals(editsBeforeDisable, editCount)
        assertEquals(choicesBeforeDisable, debtChoiceCount)
        assertEquals(true, draft.hasDebt)
    }

    @Test
    fun propertyInputs() {
        var draft by mutableStateOf(LoanDraft())
        var errors by mutableStateOf(
            mapOf(
                LoanField.TYPE to "Property type is required",
                LoanField.BER to "BER rating is required",
            )
        )
        var enabled by mutableStateOf(true)
        val callbackEvents = mutableListOf<Pair<LoanField, String>>()

        composeRule.setContent {
            LoanPropertyFields(
                draft = draft,
                errors = errors,
                enabled = enabled,
                onEdit = { field, text ->
                    callbackEvents.add(field to text)
                    draft = draft.edit(field, text)
                    errors = errors - field
                },
            )
        }

        // Start with blank TYPE and BER; all seven property controls and selector errors are present
        assertEquals("", draft[LoanField.TYPE])
        assertEquals("", draft[LoanField.BER])
        composeRule.onNodeWithTag(LoanField.COUNTY.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.AREA.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.BEDS.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.BATHS.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.YEAR.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.TYPE.path).assertIsDisplayed()
        composeRule.onNodeWithTag(LoanField.BER.path).assertIsDisplayed()
        composeRule.onNodeWithText("Property type is required").assertIsDisplayed()
        composeRule.onNodeWithText("BER rating is required").assertIsDisplayed()

        // Choose TOWN_HOUSE from Property type dropdown -> callback emits "TOWN_HOUSE" and closes menu
        composeRule.onNodeWithTag(LoanField.TYPE.path).performClick()
        composeRule.onNodeWithText("TOWN_HOUSE").performClick()
        assertEquals("TOWN_HOUSE", draft[LoanField.TYPE])
        composeRule.onNodeWithTag(LoanField.TYPE.path).assertTextContains("TOWN_HOUSE")
        assertEquals(0, composeRule.onAllNodesWithText("APARTMENT").fetchSemanticsNodes().size)

        // Choose EXEMPT from BER rating dropdown -> callback emits "EXEMPT" and closes menu
        composeRule.onNodeWithTag(LoanField.BER.path).performClick()
        composeRule.onNodeWithText("EXEMPT").performClick()
        assertEquals("EXEMPT", draft[LoanField.BER])
        composeRule.onNodeWithTag(LoanField.BER.path).assertTextContains("EXEMPT")
        assertEquals(0, composeRule.onAllNodesWithText("A1").fetchSemanticsNodes().size)

        // Enter county, floor area, bedrooms "0", bathrooms "0", and year "2005"
        composeRule.onNodeWithTag(LoanField.COUNTY.path).performTextInput("Kilkenny")
        composeRule.onNodeWithTag(LoanField.AREA.path).performTextInput("115")
        composeRule.onNodeWithTag(LoanField.BEDS.path).performTextInput("0")
        composeRule.onNodeWithTag(LoanField.BATHS.path).performTextInput("0")
        composeRule.onNodeWithTag(LoanField.YEAR.path).performTextInput("2005")

        assertEquals("Kilkenny", draft[LoanField.COUNTY])
        assertEquals("115", draft[LoanField.AREA])
        assertEquals("0", draft[LoanField.BEDS])
        assertEquals("0", draft[LoanField.BATHS])
        assertEquals("2005", draft[LoanField.YEAR])
        assertEquals("TOWN_HOUSE", draft[LoanField.TYPE])
        assertEquals("EXEMPT", draft[LoanField.BER])

        // Disable controls and verify no selection or edit is dispatched
        val countBeforeDisable = callbackEvents.size
        composeRule.runOnIdle {
            enabled = false
        }

        composeRule.onNodeWithTag(LoanField.COUNTY.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.AREA.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.BEDS.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.BATHS.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.YEAR.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.TYPE.path).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.BER.path).assertIsNotEnabled()

        composeRule.onNodeWithTag(LoanField.TYPE.path).performClick()
        composeRule.onNodeWithTag(LoanField.BER.path).performClick()
        assertEquals(countBeforeDisable, callbackEvents.size)
    }

    @Test
    fun submissionViews() {
        val validDraft = LoanDraft(
            values = mapOf(
                LoanField.INCOME to "75000",
                LoanField.DEBT to "10000",
                LoanField.DEPOSIT to "70000",
                LoanField.TERM to "30",
                LoanField.PRICE to "350000",
                LoanField.COUNTY to "Kilkenny",
                LoanField.AREA to "115",
                LoanField.BEDS to "3",
                LoanField.BATHS to "2",
                LoanField.YEAR to "2005",
                LoanField.TYPE to "SEMI_DETACHED",
                LoanField.BER to "B2",
            ),
            hasDebt = true,
        )

        var state by mutableStateOf(
            LoanUiState(
                phase = LoanPhase.EDITING,
                draft = LoanDraft(),
                touched = setOf(LoanField.INCOME, LoanField.COUNTY),
                notice = "No broker is available. Try again later.",
            )
        )
        var editCount = 0
        var debtCount = 0
        var submitCount = 0
        var backCount = 0

        composeRule.setContent {
            LoanApplicationScreen(
                state = state,
                onEdit = { _, _ -> editCount++ },
                onDebt = { _ -> debtCount++ },
                onSubmit = { submitCount++ },
                onBack = { backCount++ },
            )
        }

        // 1. EDITING (incomplete): headings, notice, visibleErrors in both sections, Submit disabled, zero callbacks on compose
        assertFalse(state.canSubmit)
        composeRule.onNodeWithText("No broker is available. Try again later.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Submit application").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Back to account").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Affordability").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enter a non-negative amount in euros.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Property").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enter the county.").performScrollTo().assertIsDisplayed()
        assertEquals(0, submitCount)
        assertEquals(0, backCount)
        assertEquals(0, editCount)
        assertEquals(0, debtCount)

        // 2. EDITING (valid draft): Submit enabled; only explicit button click invokes onSubmit
        composeRule.runOnIdle {
            state = LoanUiState(
                phase = LoanPhase.EDITING,
                draft = validDraft,
            )
        }
        assertTrue(state.canSubmit)
        assertEquals(0, submitCount)
        composeRule.onNodeWithText("Submit application").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Submit application").performClick()
        assertEquals(1, submitCount)

        composeRule.onNodeWithText("Back to account").performScrollTo().performClick()
        assertEquals(1, backCount)

        // 3. SENDING: progress indicator shown; edits, Submit, and Back are disabled and emit zero callbacks
        composeRule.runOnIdle {
            state = state.copy(phase = LoanPhase.SENDING)
        }
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Submit application").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Back to account").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.INCOME.path).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(LoanField.COUNTY.path).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithText("Submit application").performScrollTo().performClick()
        composeRule.onNodeWithText("Back to account").performScrollTo().performClick()
        assertEquals(1, submitCount)
        assertEquals(1, backCount)

        // 4. RECEIPT: displays "Application submitted", "Reference: application-001", and "Back to account"; no submit control
        composeRule.runOnIdle {
            state = state.afterResult(LoanSubmitResult.Created("application-001"))
        }
        composeRule.onNodeWithText("Application submitted").assertIsDisplayed()
        composeRule.onNodeWithText("Reference: application-001").assertIsDisplayed()
        composeRule.onNodeWithText("Back to account").assertIsDisplayed().assertIsEnabled()
        assertEquals(0, composeRule.onAllNodesWithText("Submit application").fetchSemanticsNodes().size)
        assertEquals(1, submitCount)

        composeRule.onNodeWithText("Back to account").performClick()
        assertEquals(2, backCount)
        assertEquals(1, submitCount)

        // 5. UNCONFIRMED: displays uncertain write guidance and "Back to account" with zero submit callbacks
        composeRule.runOnIdle {
            state = state.afterResult(LoanSubmitResult.Unconfirmed)
        }
        composeRule.onNodeWithText(
            "We couldn’t confirm submission. Ask your lender to check for an existing application before starting again."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Back to account").assertIsDisplayed().assertIsEnabled()
        assertEquals(0, composeRule.onAllNodesWithText("Submit application").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Back to account").performClick()
        assertEquals(3, backCount)
        assertEquals(1, submitCount)

        // 6. DENIED: displays state.notice and "Back to account" with no submit control
        composeRule.runOnIdle {
            state = state.afterResult(LoanSubmitResult.Forbidden)
        }
        composeRule.onNodeWithText("Borrower access is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to account").assertIsDisplayed().assertIsEnabled()
        assertEquals(0, composeRule.onAllNodesWithText("Submit application").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Back to account").performClick()
        assertEquals(4, backCount)
        assertEquals(1, submitCount)
        assertEquals(0, editCount)
        assertEquals(0, debtCount)
    }
}
