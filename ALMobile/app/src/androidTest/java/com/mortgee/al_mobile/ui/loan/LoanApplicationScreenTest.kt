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
import androidx.compose.ui.test.onAllNodesWithText
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
}
