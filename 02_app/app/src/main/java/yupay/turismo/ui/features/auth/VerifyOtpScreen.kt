package yupay.turismo.ui.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.AuthEvent
import yupay.turismo.utils.UiTranslations

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyOtpScreen(
    viewModel: MainViewModel,
    email: String,
    flow: String, // "register" or "reset"
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val otpLength = 8
    
    // Almacenamos TextFieldValue para controlar la posición del cursor
    val otpValues = remember { 
        mutableStateListOf(*Array(otpLength) { 
            TextFieldValue(text = " ", selection = TextRange(1)) 
        }) 
    }
    val focusRequesters = remember { List(otpLength) { FocusRequester() } }

    fun handlePaste() {
        val pastedText = clipboardManager.getText()?.text ?: ""
        val numericText = pastedText.filter { it.isDigit() }.take(otpLength)
        numericText.forEachIndexed { index, char ->
            otpValues[index] = TextFieldValue(text = char.toString(), selection = TextRange(1))
        }
        if (numericText.isNotEmpty()) {
            val nextFocus = numericText.length.coerceAtMost(otpLength - 1)
            focusRequesters[nextFocus].requestFocus()
        }
    }

    LaunchedEffect(authState.event) {
        if (authState.event == AuthEvent.LoggedIn || authState.event == AuthEvent.CodeValid) {
            onSuccess()
            viewModel.consumeAuthEvent()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(UiTranslations.getString(context, "otp_title", language), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 8.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                UiTranslations.getString(context, "otp_subtitle", language, otpLength),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                email,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                for (i in 0 until otpLength) {
                    OtpBox(
                        value = otpValues[i],
                        onValueChange = { newValue ->
                            val currentVal = otpValues[i].text
                            val text = newValue.text
                            
                            // 1. Detección de Borrado (Retroceso)
                            if (text.length < currentVal.length || text.isEmpty()) {
                                if (currentVal != " ") {
                                    // Tenía un número, lo ponemos en blanco
                                    otpValues[i] = TextFieldValue(" ", selection = TextRange(1))
                                } else if (i > 0) {
                                    // Ya estaba en blanco, borramos el anterior y retrocedemos
                                    otpValues[i - 1] = TextFieldValue(" ", selection = TextRange(1))
                                    focusRequesters[i - 1].requestFocus()
                                }
                                return@OtpBox
                            }

                            // 2. Extraer solo dígitos nuevos
                            val digits = text.filter { it.isDigit() }
                            
                            if (digits.isNotEmpty()) {
                                if (digits.length == 1) {
                                    // Entrada simple de un dígito (nuevo o reemplazo total)
                                    otpValues[i] = TextFieldValue(digits, selection = TextRange(1))
                                    if (i < otpLength - 1) focusRequesters[i + 1].requestFocus()
                                } else {
                                    // Reemplazo (ej: "12" donde "1" era el viejo) o Pegado
                                    // Determinamos cuál es el nuevo caracter
                                    val newDigit = if (text.startsWith(currentVal) && currentVal != " ") {
                                        text.substring(currentVal.length).filter { it.isDigit() }.lastOrNull()?.toString()
                                    } else {
                                        digits.last().toString()
                                    }

                                    if (newDigit != null) {
                                        otpValues[i] = TextFieldValue(newDigit, selection = TextRange(1))
                                        if (i < otpLength - 1) focusRequesters[i + 1].requestFocus()
                                    }
                                }
                            } else {
                                // Si no es dígito, restaurar espacio semilla
                                otpValues[i] = TextFieldValue(" ", selection = TextRange(1))
                            }
                        },
                        focusRequester = focusRequesters[i],
                        isError = authState.error != null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = { handlePaste() }) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(UiTranslations.getString(context, "otp_paste_clipboard", language))
            }

            if (authState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = authState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val code = otpValues.joinToString("") { if (it.text == " ") "" else it.text }
                    if (flow == "register") {
                        viewModel.verifySignupCode(email, code)
                    } else {
                        viewModel.verifyResetCode(email, code)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = otpValues.all { it.text != " " } && !authState.loading
            ) {
                if (authState.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(UiTranslations.getString(context, "otp_btn_verify", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    if (flow == "register") viewModel.resendVerification(email)
                    else viewModel.forgotPassword(email)
                },
                enabled = !authState.loading
            ) {
                Text(UiTranslations.getString(context, "otp_resend", language))
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequesters[0].requestFocus()
    }
}

@Composable
fun OtpBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    isError: Boolean
) {
    // Usamos Surface para el contenedor visual y BasicTextField para evitar scrolls/paddings internos
    Surface(
        modifier = Modifier
            .width(42.dp)
            .height(56.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isError) MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (value.text == " ") Color.Transparent else MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent)
            )
        }
    }
}
