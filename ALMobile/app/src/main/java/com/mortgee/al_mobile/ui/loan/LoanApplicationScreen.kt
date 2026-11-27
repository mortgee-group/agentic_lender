package com.mortgee.al_mobile.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mortgee.al_mobile.data.model.LoanField

internal const val UNCONFIRMED_SUBMISSION_MESSAGE =
    "We couldn’t confirm submission. Ask your lender to check for an existing application before starting again."

@Composable
fun LoanApplicationScreen(
    state: LoanUiState,
    onEdit: (LoanField, String) -> Unit,
    onDebt: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        LoanPhase.CLOSED -> {
            Box(modifier = modifier)
        }

        LoanPhase.EDITING,
        LoanPhase.SENDING,
        -> {
            val isEditing = state.phase == LoanPhase.EDITING
            val isSending = state.phase == LoanPhase.SENDING
            val errors = state.visibleErrors
            val canSubmit = state.canSubmit

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { if (isEditing) onBack() },
                        enabled = isEditing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Back to account")
                    }

                    Button(
                        onClick = { if (canSubmit) onSubmit() },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Submit application")
                    }
                }

                if (isSending) {
                    CircularProgressIndicator()
                }

                state.notice?.let { noticeText ->
                    Text(
                        text = noticeText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = "Affordability",
                    style = MaterialTheme.typography.titleMedium,
                )

                LoanFinanceFields(
                    draft = state.draft,
                    errors = errors,
                    enabled = isEditing,
                    onEdit = onEdit,
                    onDebt = onDebt,
                )

                Text(
                    text = "Property",
                    style = MaterialTheme.typography.titleMedium,
                )

                LoanPropertyFields(
                    draft = state.draft,
                    errors = errors,
                    enabled = isEditing,
                    onEdit = onEdit,
                )
            }
        }

        LoanPhase.RECEIPT -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Application submitted",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Reference: ${state.receiptId.orEmpty()}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onBack) {
                    Text(text = "Back to account")
                }
            }
        }

        LoanPhase.UNCONFIRMED -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = UNCONFIRMED_SUBMISSION_MESSAGE,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onBack) {
                    Text(text = "Back to account")
                }
            }
        }

        LoanPhase.DENIED -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.notice?.let { noticeText ->
                    Text(
                        text = noticeText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(onClick = onBack) {
                    Text(text = "Back to account")
                }
            }
        }
    }
}
