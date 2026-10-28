package com.apex.axpense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

private const val MIN_PASSWORD_LENGTH = 6

@Composable
fun LoginScreen() {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

    fun submit() {
        val cleanEmail = email.trim()
        error = null
        info = null
        when {
            cleanEmail.isEmpty() || password.isEmpty() -> error = "Email and password cannot be empty"
            isRegister && password.length < MIN_PASSWORD_LENGTH ->
                error = "Password must be at least $MIN_PASSWORD_LENGTH characters"
            else -> {
                isLoading = true
                val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password)
                else auth.signInWithEmailAndPassword(cleanEmail, password)
                task.addOnCompleteListener { result ->
                    isLoading = false
                    // On success the ViewModel's auth listener moves the app past this screen.
                    if (!result.isSuccessful) error = authErrorMessage(result.exception, isRegister)
                }
            }
        }
    }

    fun sendPasswordReset() {
        val cleanEmail = email.trim()
        error = null
        info = null
        if (cleanEmail.isEmpty()) {
            error = "Enter your email above, then tap Forgot password"
            return
        }
        isLoading = true
        auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { result ->
            isLoading = false
            if (result.isSuccessful) {
                // Don't reveal whether the address has an account.
                info = "If an account exists for $cleanEmail, a password reset link is on its way."
            } else {
                error = authErrorMessage(result.exception, false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (isRegister) "Create Account" else "Axpense Login",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            supportingText = if (isRegister) {
                { Text("At least $MIN_PASSWORD_LENGTH characters") }
            } else null,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isLoading) submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
        info?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text(if (isRegister) "Create Account" else "Login")
        }

        if (!isRegister) {
            TextButton(onClick = ::sendPasswordReset, enabled = !isLoading) {
                Text("Forgot password?")
            }
        }

        TextButton(
            onClick = {
                isRegister = !isRegister
                error = null
                info = null
            },
            enabled = !isLoading
        ) {
            Text(if (isRegister) "Already have an account? Log in" else "No account? Register here")
        }
    }
}

private fun authErrorMessage(e: Exception?, registering: Boolean): String = when (e) {
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later."
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try logging in."
        "ERROR_WEAK_PASSWORD" -> "That password is too weak. Use at least $MIN_PASSWORD_LENGTH characters."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Wrong email or password."
        else -> e.message ?: "Something went wrong. Please try again."
    }
    else -> e?.message ?: if (registering) "Registration failed" else "Login failed"
}
