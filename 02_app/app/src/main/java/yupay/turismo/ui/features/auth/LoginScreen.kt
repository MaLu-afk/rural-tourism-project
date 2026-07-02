package yupay.turismo.ui.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.VisualTransformation
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import yupay.turismo.ui.AuthEvent
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.LoadingOverlay
import yupay.turismo.ui.navigation.Routes
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onLinkOffline: () -> Unit,
    onSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(authState.event) {
        if (authState.event == AuthEvent.LoggedIn) {
            onSuccess()
            viewModel.consumeAuthEvent()
        }
    }

    // Cuerpo del formulario reutilizado en vertical y horizontal. Cierra sobre el estado local, así no
    // hay que pasar nada por parámetros. `compact` comprime espaciados y alturas para el modo horizontal.
    val formContent: @Composable (Boolean) -> Unit = { compact ->
        val btnHeight = if (compact) 48.dp else 56.dp

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(UiTranslations.getString(context, "auth_email_label", language)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            isError = authState.error != null
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(UiTranslations.getString(context, "auth_password_label", language)) },
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
            ) {
                Text(UiTranslations.getString(context, "login_forgot_password", language))
            }
        }

        if (authState.error != null) {
            Text(
                text = authState.error ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().height(btnHeight),
            shape = RoundedCornerShape(28.dp),
            enabled = email.isNotBlank() && password.isNotBlank() && !authState.loading
        ) {
            if (authState.loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text(UiTranslations.getString(context, "login_btn_enter", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

        Text(UiTranslations.getString(context, "auth_divider_or", language), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

        OutlinedButton(
            onClick = {
                viewModel.beginGoogleAuth() // muestra la pantalla de carga de inmediato
                scope.launch {
                    try {
                        when (val result = GoogleAuthHelper.getIdToken(context)) {
                            is GoogleAuthHelper.Result.Success -> viewModel.signInWithGoogle(result.idToken)
                            is GoogleAuthHelper.Result.Error -> viewModel.setAuthError(result.message)
                            GoogleAuthHelper.Result.Cancelled -> viewModel.cancelGoogleAuth()
                        }
                    } catch (e: CancellationException) {
                        viewModel.cancelGoogleAuth() // p.ej. salió de la pantalla: apaga la carga
                        throw e
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(btnHeight),
            shape = RoundedCornerShape(28.dp),
            enabled = !authState.loading && !authState.googleLoading
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(UiTranslations.getString(context, "auth_continue_google", language), fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 20.dp else 40.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

        Button(
            onClick = onLinkOffline,
            modifier = Modifier.fillMaxWidth().height(btnHeight),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(UiTranslations.getString(context, "login_link_offline", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(UiTranslations.getString(context, "login_title", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { padding ->
        if (isLandscape) {
            // Horizontal: panel izquierdo con título/subtítulo y panel derecho con el formulario.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        UiTranslations.getString(context, "login_title", language),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        UiTranslations.getString(context, "login_subtitle", language),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    formContent(true)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    UiTranslations.getString(context, "login_subtitle", language),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                formContent(false)
            }
        }
    }

        if (authState.googleLoading) {
            LoadingOverlay(message = UiTranslations.getString(context, "auth_google_signing_in", language))
        }
    }
}
