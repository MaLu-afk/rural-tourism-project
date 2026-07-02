package yupay.turismo.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.utils.UiTranslations
import kotlinx.coroutines.delay

@Composable
fun CountdownConfirmationDialog(
    language: String,
    titleKey: String,
    descKey: String,
    seconds: Int = 10,
    confirmKey: String = "btn_confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var timeLeft by remember { mutableIntStateOf(seconds) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiTranslations.getString(context, titleKey, language)) },
        text = { Text(UiTranslations.getString(context, descKey, language)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = timeLeft == 0
            ) {
                Text(
                    if (timeLeft > 0)
                        "${UiTranslations.getString(context, confirmKey, language)} ($timeLeft)"
                    else
                        UiTranslations.getString(context, confirmKey, language)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(UiTranslations.getString(context, "btn_cancel", language))
            }
        }
    )
}
