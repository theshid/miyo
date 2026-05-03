package ani.saikou.presentation.screens.login

import androidx.lifecycle.ViewModel
import ani.saikou.domain.usecase.auth.GetAnilistAuthUrlUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Login is a one-button screen — there's no async work to track and no
 * server round-trip from the VM's side; the OAuth handshake completes
 * out of process via Custom Tabs → LoginCallbackActivity. The VM exists
 * to keep the auth-URL resolution behind the use-case / repository chain
 * so the screen never builds the URL itself.
 */
class LoginViewModel(
    getAuthUrl: GetAnilistAuthUrlUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState(authUrl = getAuthUrl()))
    val uiState: StateFlow<LoginUiState> = _uiState
}

data class LoginUiState(
    val authUrl: String = "",
)
