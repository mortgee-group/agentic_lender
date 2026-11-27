package com.mortgee.al_mobile.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.ui.form.PropertyFormValidator
import com.mortgee.al_mobile.ui.theme.ALMobileTheme

@Composable
fun LoanPropertyFields(
    draft: LoanDraft,
    errors: Map<LoanField, String>,
    enabled: Boolean,
    onEdit: (LoanField, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoanTextInput(
            f = LoanField.COUNTY,
            label = "County",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Text,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.AREA,
            label = "Floor area (m²)",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Decimal,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.BEDS,
            label = "Bedrooms",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Number,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.BATHS,
            label = "Bathrooms",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Number,
            onEdit = onEdit,
        )

        LoanTextInput(
            f = LoanField.YEAR,
            label = "Year built",
            draft = draft,
            errors = errors,
            enabled = enabled,
            keyboard = KeyboardType.Number,
            onEdit = onEdit,
        )

        LoanCodePicker(
            f = LoanField.TYPE,
            label = "Property type",
            options = PropertyFormValidator.VALID_PROPERTY_TYPES,
            draft = draft,
            errors = errors,
            enabled = enabled,
            onEdit = onEdit,
        )

        LoanCodePicker(
            f = LoanField.BER,
            label = "BER rating",
            options = PropertyFormValidator.VALID_BER_RATINGS,
            draft = draft,
            errors = errors,
            enabled = enabled,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun LoanCodePicker(
    f: LoanField,
    label: String,
    options: Set<String>,
    draft: LoanDraft,
    errors: Map<LoanField, String>,
    enabled: Boolean,
    onEdit: (LoanField, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentValue = draft[f]
    val fieldError = errors[f]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (enabled) expanded = !expanded },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(f.path),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = label)
                    Text(text = currentValue)
                }
            }

            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(text = code) },
                        onClick = {
                            if (enabled) {
                                expanded = false
                                onEdit(f, code)
                            }
                        },
                        enabled = enabled,
                    )
                }
            }
        }

        fieldError?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoanPropertyFieldsFilledPreview() {
    ALMobileTheme {
        LoanPropertyFields(
            draft = LoanDraft(
                values = mapOf(
                    LoanField.COUNTY to "Kilkenny",
                    LoanField.AREA to "115",
                    LoanField.BEDS to "3",
                    LoanField.BATHS to "2",
                    LoanField.YEAR to "2005",
                    LoanField.TYPE to "SEMI_DETACHED",
                    LoanField.BER to "B2",
                ),
            ),
            errors = emptyMap(),
            enabled = true,
            onEdit = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanPropertyFieldsErrorsPreview() {
    ALMobileTheme {
        LoanPropertyFields(
            draft = LoanDraft(),
            errors = mapOf(
                LoanField.COUNTY to "Enter the county.",
                LoanField.TYPE to "Property type is required",
                LoanField.BER to "BER rating is required",
            ),
            enabled = true,
            onEdit = { _, _ -> },
        )
    }
}
