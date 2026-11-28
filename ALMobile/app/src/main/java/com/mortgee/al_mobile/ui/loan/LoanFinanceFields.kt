package com.mortgee.al_mobile.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.ui.theme.ALMobileTheme

@Composable
fun LoanFinanceFields(
    draft: LoanDraft,
    errors: Map<LoanField, String>,
    enabled: Boolean,
    onEdit: (LoanField, String) -> Unit,
    onDebt: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoanTextInput(
            f = LoanField.INCOME,
            label = "Household annual gross income (€)",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Decimal,
            onEdit = onEdit,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Do you have existing debt liabilities?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = draft.hasDebt == true,
                    onClick = { if (enabled) onDebt(true) },
                    label = { Text("Yes") },
                    enabled = enabled,
                )
                FilterChip(
                    selected = draft.hasDebt == false,
                    onClick = { if (enabled) onDebt(false) },
                    label = { Text("No") },
                    enabled = enabled,
                )
            }
            if (draft.hasDebt != true) {
                errors[LoanField.DEBT]?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (draft.hasDebt == true) {
            LoanTextInput(
                f = LoanField.DEBT,
                label = "Existing debt liabilities (€)",
                draft = draft,
                errors = errors,
                enabled = enabled,
                keyboard = KeyboardType.Decimal,
                onEdit = onEdit,
            )
        }

        LoanTextInput(
            f = LoanField.DEPOSIT,
            label = "Deposit (€)",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Decimal,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.TERM,
            label = "Requested mortgage term (years)",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Number,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.PRICE,
            label = "Seller asking price (€)",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Decimal,
            onEdit = onEdit,
        )
    }
}

@Composable
internal fun LoanTextInput(
    f: LoanField,
    label: String,
    draft: LoanDraft,
    errors: Map<LoanField, String>,
    enabled: Boolean,
    keyboard: KeyboardType,
    onEdit: (LoanField, String) -> Unit,
) {
    val fieldError = errors[f]
    OutlinedTextField(
        value = draft[f],
        onValueChange = { if (enabled) onEdit(f, it) },
        label = { Text(label) },
        singleLine = true,
        isError = fieldError != null,
        supportingText = fieldError?.let { errorMessage ->
            {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(f.path),
    )
}

@Preview(showBackground = true)
@Composable
fun LoanFinanceFieldsWithDebtPreview() {
    ALMobileTheme {
        LoanFinanceFields(
            draft = LoanDraft(
                values = mapOf(
                    LoanField.INCOME to "75000",
                    LoanField.DEBT to "10000",
                    LoanField.DEPOSIT to "70000",
                    LoanField.TERM to "30",
                    LoanField.PRICE to "350000",
                ),
                hasDebt = true,
            ),
            errors = emptyMap(),
            enabled = true,
            onEdit = { _, _ -> },
            onDebt = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanFinanceFieldsErrorsPreview() {
    ALMobileTheme {
        LoanFinanceFields(
            draft = LoanDraft(),
            errors = mapOf(
                LoanField.INCOME to "Enter a non-negative amount in euros.",
                LoanField.DEBT to "Choose Yes or No.",
                LoanField.DEPOSIT to "Deposit must be less than the seller asking price.",
            ),
            enabled = true,
            onEdit = { _, _ -> },
            onDebt = {},
        )
    }
}
