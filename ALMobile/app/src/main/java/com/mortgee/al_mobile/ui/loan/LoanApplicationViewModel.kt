package com.mortgee.al_mobile.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.repository.LoanApplicationRepository
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoanApplicationViewModel internal constructor(
    private val repo: LoanApplicationRepository,
    private val sessions: StateFlow<SessionState>,
) : ViewModel() {

    @Inject
    constructor(
        repo: LoanApplicationRepository,
        owner: SessionOwner,
    ) : this(repo, owner.sessionState)

    private val _state = MutableStateFlow(LoanUiState())
    val state: StateFlow<LoanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.collect {
                val owner = _state.value.owner
                if (owner != null && !isCurrent(owner)) {
                    _state.value = LoanUiState()
                }
            }
        }
    }

    private fun isCurrent(key: SessionKey?): Boolean {
        val current = sessions.value as? SessionState.Authenticated ?: return false
        return key != null && current.role == BackendRole.BORROWER && current.key == key
    }

    fun open(key: SessionKey) {
        if (!isCurrent(key)) {
            val owner = _state.value.owner
            if (owner != null && !isCurrent(owner)) {
                _state.value = LoanUiState()
            }
            return
        }
        val current = _state.value
        if (current.owner == key && current.phase != LoanPhase.CLOSED) {
            return
        }
        _state.value = LoanUiState(
            owner = key,
            phase = LoanPhase.EDITING,
        )
    }

    fun close(key: SessionKey) {
        val current = _state.value
        if (current.owner != key || current.phase == LoanPhase.SENDING) {
            return
        }
        _state.value = LoanUiState()
    }

    fun edit(key: SessionKey, f: LoanField, text: String) {
        val current = _state.value
        if (!isCurrent(key) || current.owner != key || current.phase != LoanPhase.EDITING) {
            return
        }
        if (f == LoanField.DEBT && current.draft.hasDebt != true) {
            return
        }
        val clearedServerFields = if (f == LoanField.PRICE) {
            current.serverFields - LoanField.PRICE - LoanField.DEPOSIT
        } else {
            current.serverFields - f
        }
        _state.value = current.copy(
            draft = current.draft.edit(f, text),
            touched = current.touched + f,
            serverFields = clearedServerFields,
            serverOther = false,
            notice = null,
        )
    }

    fun chooseDebt(key: SessionKey, yes: Boolean) {
        val current = _state.value
        if (!isCurrent(key) || current.owner != key || current.phase != LoanPhase.EDITING) {
            return
        }
        _state.value = current.copy(
            draft = current.draft.chooseDebt(yes),
            touched = current.touched + LoanField.DEBT,
            serverFields = current.serverFields - LoanField.DEBT,
            serverOther = false,
            notice = null,
        )
    }

    fun submit(key: SessionKey) {
        val s = _state.value
        if (!isCurrent(key) || s.owner != key || !s.canSubmit) {
            return
        }
        val request = prepareLoan(s.draft).value ?: return
        _state.value = s.copy(phase = LoanPhase.SENDING, notice = null)
        viewModelScope.launch {
            val outcome = repo.submit(key, request)
            val latest = _state.value
            if (isCurrent(key) && latest.owner == key && latest.phase == LoanPhase.SENDING) {
                _state.value = latest.afterResult(outcome)
            }
        }
    }
}
