package yupay.turismo.ui.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.ui.MainViewModel
import androidx.compose.ui.text.input.VisualTransformation
import yupay.turismo.ui.AuthEvent
import yupay.turismo.ui.features.auth.components.PasswordValidationItem
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    viewModel: MainViewModel,
    source: String = "reset",
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current
    val isAccount = source == "account"
    val screenTitle = if (isAccount) UiTranslations.getString(context, "reset_title_account", language) else UiTranslations.getString(context, "reset_title", language)
    val screenSubtitle = if (isAccount)
        UiTranslations.getString(context, "reset_subtitle_account", language)
    else
        UiTranslations.getString(context, "reset_subtitle", language)
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val hasLength = password.length >= 8
    val hasUpper = password.any { it.isUpperCase() }
    val hasLower = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }
    val matches = password == confirmPassword && confirmPassword.isNotEmpty()

    val isFormValid = hasLength && hasUpper && hasLower && hasDigit && hasSpecial && matches

    LaunchedEffect(authState.event) {
        if (authState.event == AuthEvent.PasswordReset) {
            onSuccess()
            viewModel.consumeAuthEvent()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                screenSubtitle,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(UiTranslations.getString(context, "reset_new_password_label", language)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) UiTranslations.getString(context, "auth_cd_hide_password", language) else UiTranslations.getString(context, "auth_cd_show_password", language)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(UiTranslations.getString(context, "reset_confirm_password_label", language)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) UiTranslations.getString(context, "auth_cd_hide_password", language) else UiTranslations.getString(context, "auth_cd_show_password", language)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_min_length", language), hasLength, password.isNotEmpty())
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_upper", language), hasUpper, password.isNotEmpty())
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_lower", language), hasLower, password.isNotEmpty())
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_digit", language), hasDigit, password.isNotEmpty())
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_special", language), hasSpecial, password.isNotEmpty())
                PasswordValidationItem(UiTranslations.getString(context, "pwd_rule_match", language), matches, confirmPassword.isNotEmpty())
            }

            if (authState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = authState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isAccount) viewModel.changeAccountPassword(password)
                    else viewModel.resetPassword(password)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = isFormValid && !authState.loading
            ) {
                if (authState.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(UiTranslations.getString(context, "reset_btn_update", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
