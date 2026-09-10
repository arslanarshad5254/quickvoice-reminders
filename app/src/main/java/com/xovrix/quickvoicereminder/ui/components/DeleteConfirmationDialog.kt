package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.xovrix.quickvoicereminder.ui.theme.CoralRed

@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete Reminder?",
    message: String = "Are you sure you want to delete this reminder? This action can be undone for a few seconds.",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
