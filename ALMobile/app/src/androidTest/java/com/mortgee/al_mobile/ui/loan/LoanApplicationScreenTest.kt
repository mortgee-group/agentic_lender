package com.mortgee.al_mobile.ui.loan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mortgee.al_mobile.data.model.LoanField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals( editsBeforeDisable, editCount)
        assertEquals(choicesBeforeDisable, debtChoiceCount)
        assertEquals(true, draft.hasDebt)
    }
}
