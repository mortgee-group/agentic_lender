package com.mortgee.al_mobile.ui.signin

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Sign-In Screen Composable.
 *
 * Renders controlled inputs for user email and password using Compose and Material 3 theme conventions.
 * Masks the password by default, disables form editing and submission during a sign-in attempt,
 * and routes both button click and keyboard IME submit actions through [onSubmit].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    formState: SignInFormState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Sign In") },
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
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please enter your credentials to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Email Field
                    OutlinedTextField(
                        value = formState.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email Address") },
                        placeholder = { Text("user@example.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Icon"
                            )
                        },
                        singleLine = true,
                        enabled = !formState.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password Icon"
                            )
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                enabled = !formState.isSubmitting
                            ) {
                                Text(
                                    text = if (isPasswordVisible) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        enabled = !formState.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (formState.canSubmit) {
                                    onSubmit()
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Error message display
                    formState.error?.let { errorResult ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error Icon",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatErrorMessage(errorResult),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Submit Button
                    Button(
                        onClick = onSubmit,
                        enabled = formState.canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (formState.isSubmitting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing in...")
                            }
                        } else {
                            Text("Sign In")
                        }
                    }
                }
            }
        }
    }
}

private fun formatErrorMessage(result: SignInResult): String {
    return when (result) {
        SignInResult.InvalidCredentials -> "Invalid email or password. Please try again."
        SignInResult.DisabledAccount -> "This account has been disabled. Please contact support."
        SignInResult.Connectivity -> "Network connection error. Please check your connection."
        SignInResult.RejectedAuthentication -> "Authentication failed. Please verify your credentials."
        SignInResult.MissingProfile -> "User profile not found."
        SignInResult.UnsupportedRole -> "Your account role is not supported."
        SignInResult.Busy -> "A sign-in operation is already in progress."
        SignInResult.Success -> ""
        SignInResult.GenericFailure -> "An unexpected error occurred. Please try again."
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    ALMobileTheme {
        SignInScreen(
            formState = SignInFormState(
                email = "borrower@example.com",
                password = "password123"
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenSubmittingPreview() {
    ALMobileTheme {
        SignInScreen(
            formState = SignInFormState(
                email = "borrower@example.com",
                password = "password123",
                isSubmitting = true
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenErrorPreview() {
    ALMobileTheme {
        SignInScreen(
            formState = SignInFormState(
                email = "borrower@example.com",
                password = "password123",
                error = SignInResult.InvalidCredentials
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {}
        )
    }
}
