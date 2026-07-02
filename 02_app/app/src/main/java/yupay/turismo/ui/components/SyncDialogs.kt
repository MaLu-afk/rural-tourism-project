package yupay.turismo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import yupay.turismo.utils.UiTranslations

@Composable
fun ConnectionRequiredDialog(
    language: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                UiTranslations.getString(context, "sync_connection_required_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(UiTranslations.getString(context, "sync_connection_required_desc", language))
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(UiTranslations.getString(context, "sync_connection_required_btn", language))
            }
        }
    )
}
