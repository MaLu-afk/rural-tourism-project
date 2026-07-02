package yupay.turismo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import yupay.turismo.utils.UiTranslations

@Composable
fun UnsavedChangesDialog(
    language: String,
    onContinueEditing: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiTranslations.getString(context, "exit_unsaved_title", language)) },
        text = { Text(UiTranslations.getString(context, "exit_unsaved_desc", language)) },
        confirmButton = {
            TextButton(onClick = onContinueEditing) {
                Text(UiTranslations.getString(context, "btn_continue", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onExitWithoutSaving) {
                Text(UiTranslations.getString(context, "btn_exit_without_save", language))
            }
        }
    )
}
