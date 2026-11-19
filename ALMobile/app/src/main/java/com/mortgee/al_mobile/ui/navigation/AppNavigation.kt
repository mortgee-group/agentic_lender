package com.mortgee.al_mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.ui.catalogue.PropertyCatalogueRoute
import com.mortgee.al_mobile.ui.detail.PropertyDetailRoute
import com.mortgee.al_mobile.ui.landing.AuthenticatedLandingRoute
import com.mortgee.al_mobile.ui.register.RegisterMode
import com.mortgee.al_mobile.ui.register.RegisterRoute
import com.mortgee.al_mobile.ui.signin.SignInRoute
import com.mortgee.al_mobile.ui.testfeature.TestFeatureRoute
import kotlinx.coroutines.launch

/**
 * Unauthenticated (Guest) entry destinations managed by [AppNavigation].
 */
enum class GuestDestination {
    /**
     * Standard Sign-In screen.
     */
    SignIn,

    /**
     * Borrower Registration screen in standard account creation mode ([RegisterMode.CreateAccount]).
     */
    Register,

    /**
     * Borrower Registration screen in explicit completion mode ([RegisterMode.CompleteExisting],
     * "Finish registration").
     */
    CompleteRegistration;

    /**
     * Returns the initial [RegisterMode] when this destination enters [RegisterRoute],
     * or `null` when on [SignIn].
     */
    val initialRegisterMode: RegisterMode?
        get() = when (this) {
            SignIn -> null
            Register -> RegisterMode.CreateAccount
            CompleteRegistration -> RegisterMode.CompleteExisting
        }
}

/**
 * State tracking unauthenticated guest navigation between [SignInRoute] and [RegisterRoute].
 *
 * Ensures that:
 * - A Guest can enter standard registration ([RegisterMode.CreateAccount]) or explicit
 *   registration completion ([RegisterMode.CompleteExisting]).
 * - Transitioning to an active authenticated session resets the guest destination to
 *   [GuestDestination.SignIn] and increments [registerEntryGeneration] so system Back
 *   cannot recover a submitted registration form.
 */
data class GuestNavState(
    val destination: GuestDestination = GuestDestination.SignIn,
    val registerEntryGeneration: Int = 0,
) {
    /**
     * Enters standard Borrower registration ([RegisterMode.CreateAccount]) with a fresh form generation.
     * Idempotent if already at [GuestDestination.Register] so rapid duplicate clicks open registration once.
     */
    fun enterRegister(): GuestNavState = if (destination == GuestDestination.Register) {
        this
    } else {
        copy(
            destination = GuestDestination.Register,
            registerEntryGeneration = registerEntryGeneration + 1,
        )
    }

    /**
     * Enters explicit Borrower registration completion ([RegisterMode.CompleteExisting]) with a fresh form generation.
     * Idempotent if already at [GuestDestination.CompleteRegistration] so rapid duplicate clicks open completion once.
     */
    fun enterCompleteRegistration(): GuestNavState = if (destination == GuestDestination.CompleteRegistration) {
        this
    } else {
        copy(
            destination = GuestDestination.CompleteRegistration,
            registerEntryGeneration = registerEntryGeneration + 1,
        )
    }

    /**
     * Returns from registration to [GuestDestination.SignIn] and invalidates the registration form generation.
     * Idempotent if already at [GuestDestination.SignIn].
     */
    fun returnToSignIn(): GuestNavState = if (destination == GuestDestination.SignIn) {
        this
    } else {
        copy(
            destination = GuestDestination.SignIn,
            registerEntryGeneration = registerEntryGeneration + 1,
        )
    }

    /**
     * Resets guest navigation upon session establishment so Back cannot recover the submitted registration form.
     */
    fun onSessionAuthenticated(): GuestNavState = if (destination != GuestDestination.SignIn) {
        copy(
            destination = GuestDestination.SignIn,
            registerEntryGeneration = registerEntryGeneration + 1,
        )
    } else {
        this
    }
}

/**
 * Main application navigation component.
 *
 * Gates private route entry on an active [SessionState.Authenticated] session.
 * Keys destination state and private back stack to the active session generation ([SessionKey]).
 * Directs unauthenticated or invalid session states to guest entry ([SignInRoute] or [RegisterRoute]),
 * allowing a Guest to enter Borrower registration or explicit completion ("Finish registration").
 * Once a session is established, transitions through session state to the permitted landing destination
 * and clears guest registration navigation state so Back cannot recover the submitted form.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    sessionOwner: SessionOwner? = null,
    sessionStateOverride: SessionState? = null,
    initialGuestDestination: GuestDestination = GuestDestination.SignIn,
    isTestFeatureEnabled: Boolean = NavigationConfig.ENABLE_TEST_FEATURE,
    onSignOut: (() -> Unit)? = null,
) {
    val collectedState = sessionOwner?.sessionState?.collectAsStateWithLifecycle()?.value
    val currentSessionState = sessionStateOverride ?: collectedState ?: SessionState.SignedOut

    var guestDestination by rememberSaveable(initialGuestDestination) {
        mutableStateOf(initialGuestDestination)
    }
    var registerEntryGeneration by rememberSaveable(initialGuestDestination) {
        mutableIntStateOf(if (initialGuestDestination == GuestDestination.SignIn) 0 else 1)
    }

    val coroutineScope = rememberCoroutineScope()
    val handleSignOut: () -> Unit = {
        val resetState = GuestNavState(
            destination = guestDestination,
            registerEntryGeneration = registerEntryGeneration,
        ).returnToSignIn()
        guestDestination = resetState.destination
        registerEntryGeneration = resetState.registerEntryGeneration

        onSignOut?.invoke()
        sessionOwner?.let { owner ->
            coroutineScope.launch {
                owner.signOut()
            }
        }
    }

    val authenticatedState = currentSessionState as? SessionState.Authenticated
    val initialDestination = authenticatedState?.let {
        NavigationConfig.getDestinationForRole(it.role)
    }

    if (authenticatedState != null && initialDestination != null) {
        if (guestDestination != GuestDestination.SignIn) {
            val resetState = GuestNavState(
                destination = guestDestination,
                registerEntryGeneration = registerEntryGeneration,
            ).onSessionAuthenticated()
            guestDestination = resetState.destination
            registerEntryGeneration = resetState.registerEntryGeneration
        }

        key(authenticatedState.key) {
            var currentDestination by remember(authenticatedState.key) {
                mutableStateOf<NavDestination>(initialDestination)
            }

            when (val destination = currentDestination) {
                is NavDestination.AuthenticatedLanding -> {
                    AuthenticatedLandingRoute(
                        authenticatedState = authenticatedState,
                        onSignOut = handleSignOut,
                        modifier = modifier
                    )
                }
                is NavDestination.PropertyCatalogue -> {
                    PropertyCatalogueRoute(
                        onPropertyClick = { propertyId ->
                            currentDestination = NavDestination.PropertyDetail(propertyId)
                        },
                        isTestFeatureEnabled = isTestFeatureEnabled,
                        onTestFeatureClick = if (isTestFeatureEnabled) {
                            { currentDestination = NavDestination.TestFeature }
                        } else null,
                        onSignOut = handleSignOut
                    )
                }
                is NavDestination.PropertyDetail -> {
                    PropertyDetailRoute(
                        propertyId = destination.propertyId,
                        onBackClick = { currentDestination = initialDestination }
                    )
                }
                is NavDestination.TestFeature -> {
                    if (isTestFeatureEnabled) {
                        TestFeatureRoute(
                            onBackClick = { currentDestination = initialDestination }
                        )
                    } else {
                        currentDestination = initialDestination
                        PropertyCatalogueRoute(
                            onPropertyClick = { propertyId ->
                                currentDestination = NavDestination.PropertyDetail(propertyId)
                            },
                            isTestFeatureEnabled = false,
                            onSignOut = handleSignOut
                        )
                    }
                }
            }
        }
    } else {
        val initialRegisterMode = guestDestination.initialRegisterMode
        if (initialRegisterMode != null) {
            RegisterRoute(
                initialMode = initialRegisterMode,
                entryGeneration = registerEntryGeneration,
                onNavigateToSignIn = {
                    val nextState = GuestNavState(
                        destination = guestDestination,
                        registerEntryGeneration = registerEntryGeneration,
                    ).returnToSignIn()
                    guestDestination = nextState.destination
                    registerEntryGeneration = nextState.registerEntryGeneration
                },
                modifier = modifier,
            )
        } else {
            SignInRoute(
                onNavigateToRegister = {
                    val nextState = GuestNavState(
                        destination = guestDestination,
                        registerEntryGeneration = registerEntryGeneration,
                    ).enterRegister()
                    guestDestination = nextState.destination
                    registerEntryGeneration = nextState.registerEntryGeneration
                },
                onNavigateToFinishRegistration = {
                    val nextState = GuestNavState(
                        destination = guestDestination,
                        registerEntryGeneration = registerEntryGeneration,
                    ).enterCompleteRegistration()
                    guestDestination = nextState.destination
                    registerEntryGeneration = nextState.registerEntryGeneration
                },
                modifier = modifier
            )
        }
    }
}
