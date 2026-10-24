package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.ui.theme.CoralRed

@Composable
fun DeleteConfirmationDialog(
    title: String? = null,
    message: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title ?: stringResource(R.string.delete_reminder_title)) },
        text = { Text(text = message ?: stringResource(R.string.delete_reminder_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
            ) {
                Text(stringResource(R.string.action_delete), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
