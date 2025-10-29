package mx.edu.utng.mahg.seguridad01.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.edu.utng.mahg.seguridad01.models.AuthState
import mx.edu.utng.mahg.seguridad01.models.User
import mx.edu.utng.mahg.seguridad01.repository.AuthRepository

/**
 * ViewModel para gestionar la autenticación
 *
 * ANALOGÍA DEL VIEWMODEL:
 * Imaginen que están en un restaurante. Ustedes (Activity) no van
 * a la cocina a preparar su comida. Le dicen al mesero (ViewModel)
 * qué quieren, y el mesero se comunica con la cocina (Repository).
 *
 * El ViewModel sobrevive a cambios de configuración (rotación de pantalla)
 * mientras que las Activities se destruyen y recrean.
 */

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    // LiveData para observar cambios en el estado de autenticación
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // LiveData para el usuario actual
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /**
     * Inicializamos el ViewModel verificando si hay sesión activa
     */
    init {
        checkExistingSession()
    }

    /**
     * Verifica si existe una sesión guardada al iniciar la app
     */


    private fun checkExistingSession() {
        viewModelScope.launch {
            if (repository.isLoggedIn()) {
                val user = repository.getCurrentUser()
                if (user != null) {
                    _currentUser.value = user
                    _authState.value = AuthState.Success(user)

                    // Validamos el token con el servidor
                    validateToken()
                }
            }
        }
    }

    /* @param password Contraseña del usuario
    */
    fun login(email: String, password: String) {
        // Cambiamos el estado a Loading
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            // Llamamos al repositorio de forma asíncrona
            val result = repository.login(email, password)

            // Procesamos el resultado
            result.onSuccess{ user ->
                _currentUser.value = user
                _authState.value = AuthState.Success(user)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(
                    exception.message ?: "Error desconocido en el login"
                )
            }
        }
    }

    /**
     * Valida el token actual con el servidor
     * Útil para verificar si la sesión sigue válida
     */
    fun validateToken() {
        viewModelScope.launch {
            val result = repository.validateToken()

            result.onSuccess { isValid ->
                if (!isValid) {
                    // Token inválido o expirado
                    logout()
                }
            }.onFailure {
                // Error al validar, pero mantenemos sesión local
                // El usuario podrá seguir usando la app offline
            }
        }
    }

    /**
     * Cierra la sesión del usuario
     */
    fun logout() {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            val result = repository.logout()

            result.onSuccess {
                _currentUser.value = null
                _authState.value = AuthState.Logout
            }.onFailure { exception ->
                // Aunque falle, forzamos el logout local
                _currentUser.value = null
                _authState.value = AuthState.Logout
            }
        }
    }

    /**
     * Reinicia el estado a Idle
     * Útil después de mostrar un error o éxito
     */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    /**
     * Actualiza la actividad del usuario
     * Llamar cuando el usuario interactúe con la app
     */
    fun updateUserActivity() {
        repository.updateActivity()
    }

    /**
     * Verifica si hay sesión activa
     */
    fun isLoggedIn(): Boolean {
        return repository.isLoggedIn()
    }
}