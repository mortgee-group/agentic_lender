package com.mortgee.al_mobile.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.ui.theme.ALMobileTheme

/**
 * Borrower Registration Screen Composable.
 *
 * Renders controlled inputs for Borrower display name, email, and password using Compose and
 * Material 3 theme conventions.
 * - Masks the password credential by default using [PasswordVisualTransformation].
 * - Displays safe field-level and form-level error messages without leaking credentials, tokens, or raw exceptions.
 * - Disables editing and prevents duplicate submission while [RegisterFormState.isSubmitting] is true.
 * - Routes both primary button click and keyboard IME Done actions through the same [onSubmit] callback
 *   via a single guarded dispatcher.
 * - Exposes the explicit "Finish registration" ([RegisterMode.CompleteExisting]) action and mode switch,
 *   along with an optional navigation callback to ordinary Sign In.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    formState: RegisterFormState,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectCompletionMode: () -> Unit = {},
    onSelectCreateAccountMode: () -> Unit = {},
    onNavigateToSignIn: (() -> Unit)? = null,
) {
    var isPasswordVisible by remember { mutableStateOf(value = false) }

    val triggerSubmit: () -> Unit = {
        if (!formState.isSubmitting) {
            onSubmit()
        }
    }

    val topBarTitle = if (formState.isCompletionMode) {
        "Finish Registration"
    } else {
        "Register Account"
    }

    val headerTitle = if (formState.isCompletionMode) {
        "Finish Borrower Registration"
    } else {
        "Create Borrower Account"
    }

    val headerSubtitle = if (formState.isCompletionMode) {
        "Enter your existing account credentials and display name to complete your Borrower profile"
    } else {
        "Provide your display name, email, and password to register"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = topBarTitle) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Display Name Field
                    OutlinedTextField(
                        value = formState.displayName,
                        onValueChange = { if (!formState.isSubmitting) onDisplayNameChange(it) },
                        label = { Text("Display Name") },
                        placeholder = { Text("Test Borrower") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Display Name Icon",
                            )
                        },
                        isError = formState.displayNameError != null,
                        supportingText = formState.displayNameError?.let { fieldError ->
                            {
                                Text(
                                    text = formatRegisterErrorMessage(fieldError),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !formState.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Email Field
                    OutlinedTextField(
                        value = formState.email,
                        onValueChange = { if (!formState.isSubmitting) onEmailChange(it) },
                        label = { Text("Email Address") },
                        placeholder = { Text("borrower@example.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Icon",
                            )
                        },
                        isError = formState.emailError != null,
                        supportingText = formState.emailError?.let { fieldError ->
                            {
                                Text(
                                    text = formatRegisterErrorMessage(fieldError),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !formState.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = { if (!formState.isSubmitting) onPasswordChange(it) },
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password Icon",
                            )
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                enabled = !formState.isSubmitting,
                            ) {
                                Text(
                                    text = if (isPasswordVisible) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        visualTransformation = resolvePasswordVisualTransformation(
                            isPasswordVisible = isPasswordVisible,
                        ),
                        isError = formState.passwordError != null,
                        supportingText = formState.passwordError?.let { fieldError ->
                            {
                                Text(
                                    text = formatRegisterErrorMessage(fieldError),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !formState.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { triggerSubmit() },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Non-field (form-level / account-level) error banner
                    formState.formError?.let { errorResult ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error Icon",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatRegisterErrorMessage(errorResult),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }

                                val showRecoveryCompletionAction =
                                    !formState.isCompletionMode && formState.canOfferCompletion
                                val showSignInRecoveryAction =
                                    onNavigateToSignIn != null && (
                                        errorResult == SignInResult.AccountExists ||
                                            errorResult == SignInResult.UnsupportedRole
                                        )

                                if (showRecoveryCompletionAction || showSignInRecoveryAction) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (showSignInRecoveryAction) {
                                            TextButton(
                                                onClick = onNavigateToSignIn,
                                                enabled = !formState.isSubmitting,
                                            ) {
                                                Text("Sign In")
                                            }
                                        }
                                        if (showRecoveryCompletionAction) {
                                            OutlinedButton(
                                                onClick = onSelectCompletionMode,
                                                enabled = !formState.isSubmitting,
                                            ) {
                                                Text("Finish registration")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Primary Submit Button
                    Button(
                        onClick = triggerSubmit,
                        enabled = !formState.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        if (formState.isSubmitting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (formState.isCompletionMode) {
                                        "Finishing registration..."
                                    } else {
                                        "Creating account..."
                                    },
                                )
                            }
                        } else {
                            Text(
                                text = if (formState.isCompletionMode) {
                                    "Finish registration"
                                } else {
                                    "Create Account"
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Explicit Completion Mode Toggle ("Finish registration" vs "Create a new account")
                    if (formState.isCompletionMode) {
                        TextButton(
                            onClick = onSelectCreateAccountMode,
                            enabled = !formState.isSubmitting,
                        ) {
                            Text("Create a new account instead")
                        }
                    } else {
                        TextButton(
                            onClick = onSelectCompletionMode,
                            enabled = !formState.isSubmitting,
                        ) {
                            Text("Finish registration for an existing account")
                        }
                    }

                    // Optional navigation back to ordinary Sign-In
                    if (onNavigateToSignIn != null) {
                        TextButton(
                            onClick = onNavigateToSignIn,
                            enabled = !formState.isSubmitting,
                        ) {
                            Text(ALREADY_HAVE_ACCOUNT_SIGN_IN_LABEL)
                        }
                    }
                }
            }
        }
    }
}

internal const val ALREADY_HAVE_ACCOUNT_SIGN_IN_LABEL = "Already have an account? Sign in"

/**
 * Resolves the [VisualTransformation] for the password input field.
 * Defaults to [PasswordVisualTransformation] to mask credentials unless explicitly toggled visible.
 */
internal fun resolvePasswordVisualTransformation(
    isPasswordVisible: Boolean,
): VisualTransformation = if (isPasswordVisible) {
    VisualTransformation.None
} else {
    PasswordVisualTransformation()
}

/**
 * Maps a [SignInResult] outcome to a safe, user-facing registration message free of credentials,
 * tokens, or raw server/SDK exception details.
 */
internal fun formatRegisterErrorMessage(result: SignInResult): String {
    return when (result) {
        SignInResult.InvalidDisplayName -> "Please enter a valid display name."
        SignInResult.InvalidEmail -> "Please enter a valid email address."
        SignInResult.WeakPassword -> "Password does not meet security requirements."
        SignInResult.AccountExists ->
            "An account with this email already exists. You can sign in or finish registration."
        SignInResult.CompletionNeeded ->
            "Your account was created, but profile setup did not finish. Choose Finish registration to complete setup."
        SignInResult.InvalidCredentials -> "Invalid email or password. Please try again."
        SignInResult.DisabledAccount -> "This account has been disabled. Please contact support."
        SignInResult.Connectivity -> "Network connection error. Please check your connection and try again."
        SignInResult.RejectedAuthentication -> "Unable to verify account authentication. Please check your credentials."
        SignInResult.MissingProfile -> "Borrower profile has not been created yet. Choose Finish registration."
        SignInResult.UnsupportedRole ->
            "This account is registered under a different role. Please use Sign In instead."
        SignInResult.Busy -> "Another account operation is already in progress."
        SignInResult.Success -> ""
        SignInResult.GenericFailure -> "An unexpected error occurred. Please try again."
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenCreatePreview() {
    ALMobileTheme {
        RegisterScreen(
            formState = RegisterFormState(
                displayName = "Test Borrower",
                email = "borrower@example.com",
                password = "password123",
                mode = RegisterMode.CreateAccount,
            ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenSubmittingPreview() {
    ALMobileTheme {
        RegisterScreen(
            formState = RegisterFormState(
                displayName = "Test Borrower",
                email = "borrower@example.com",
                password = "password123",
                mode = RegisterMode.CreateAccount,
                isSubmitting = true,
            ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenCompletionNeededPreview() {
    ALMobileTheme {
        RegisterScreen(
            formState = RegisterFormState(
                displayName = "Test Borrower",
                email = "borrower@example.com",
                password = "",
                mode = RegisterMode.CreateAccount,
                error = SignInResult.AccountExists,
            ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onSelectCompletionMode = {},
            onNavigateToSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenFieldErrorsPreview() {
    ALMobileTheme {
        RegisterScreen(
            formState = RegisterFormState().validate(),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
