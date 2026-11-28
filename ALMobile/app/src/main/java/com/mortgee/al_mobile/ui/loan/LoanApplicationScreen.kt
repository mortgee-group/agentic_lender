package com.mortgee.al_mobile.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.ui.theme.ALMobileTheme

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

            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    LoanBottomActionBar(
                        showSubmit = true,
                        isSending = isSending,
                        canSubmit = canSubmit,
                        canGoBack = isEditing,
                        onSubmit = onSubmit,
                        onBack = onBack,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
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
        }

        LoanPhase.RECEIPT -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    LoanBottomActionBar(
                        showSubmit = false,
                        isSending = false,
                        canSubmit = false,
                        canGoBack = true,
                        onSubmit = onSubmit,
                        onBack = onBack,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
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
                }
            }
        }

        LoanPhase.UNCONFIRMED -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    LoanBottomActionBar(
                        showSubmit = false,
                        isSending = false,
                        canSubmit = false,
                        canGoBack = true,
                        onSubmit = onSubmit,
                        onBack = onBack,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = UNCONFIRMED_SUBMISSION_MESSAGE,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        LoanPhase.DENIED -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    LoanBottomActionBar(
                        showSubmit = false,
                        isSending = false,
                        canSubmit = false,
                        canGoBack = true,
                        onSubmit = onSubmit,
                        onBack = onBack,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
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
                }
            }
        }
    }
}

@Composable
private fun LoanBottomActionBar(
    showSubmit: Boolean,
    isSending: Boolean,
    canSubmit: Boolean,
    canGoBack: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSubmit) {
                    Button(
                        onClick = { if (canSubmit) onSubmit() },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = "Submit application")
                        }
                    }
                    OutlinedButton(
                        onClick = { if (canGoBack) onBack() },
                        enabled = canGoBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Back to account")
                    }
                } else {
                    Button(
                        onClick = { if (canGoBack) onBack() },
                        enabled = canGoBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Back to account")
                    }
                }
            }
        }
    }
}

private val previewValidLoanDraft = LoanDraft(
    values = mapOf(
        LoanField.INCOME to "75000",
        LoanField.DEBT to "10000",
        LoanField.DEPOSIT to "70000",
        LoanField.TERM to "30",
        LoanField.PRICE to "350000",
        LoanField.COUNTY to "co. Kilkenny",
        LoanField.AREA to "115",
        LoanField.BEDS to "3",
        LoanField.BATHS to "2",
        LoanField.YEAR to "2005",
        LoanField.TYPE to "SEMI_DETACHED",
        LoanField.BER to "B2",
    ),
    hasDebt = true,
)

@Preview(showBackground = true)
@Composable
fun LoanApplicationScreenEditingPreview() {
    ALMobileTheme {
        LoanApplicationScreen(
            state = LoanUiState(
                phase = LoanPhase.EDITING,
                draft = previewValidLoanDraft,
            ),
            onEdit = { _, _ -> },
            onDebt = {},
            onSubmit = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanApplicationScreenSendingPreview() {
    ALMobileTheme {
        LoanApplicationScreen(
            state = LoanUiState(
                phase = LoanPhase.SENDING,
                draft = previewValidLoanDraft,
            ),
            onEdit = { _, _ -> },
            onDebt = {},
            onSubmit = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanApplicationScreenReceiptPreview() {
    ALMobileTheme {
        LoanApplicationScreen(
            state = LoanUiState(
                phase = LoanPhase.RECEIPT,
                receiptId = "application-001",
            ),
            onEdit = { _, _ -> },
            onDebt = {},
            onSubmit = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanApplicationScreenUnconfirmedPreview() {
    ALMobileTheme {
        LoanApplicationScreen(
            state = LoanUiState(
                phase = LoanPhase.UNCONFIRMED,
            ),
            onEdit = { _, _ -> },
            onDebt = {},
            onSubmit = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoanApplicationScreenDeniedPreview() {
    ALMobileTheme {
        LoanApplicationScreen(
            state = LoanUiState(
                phase = LoanPhase.DENIED,
                notice = "Borrower access is required.",
            ),
            onEdit = { _, _ -> },
            onDebt = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
