package com.frigopro.app.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.LocalDate

/** Calendrier Material 3, partagé par la barre de navigation et le formulaire. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurDate(
    date: LocalDate,
    onDateChoisie: (LocalDate) -> Unit,
    onFermer: () -> Unit,
) {
    val etat = rememberDatePickerState(initialSelectedDateMillis = date.versMillisUtc())

    DatePickerDialog(
        onDismissRequest = onFermer,
        confirmButton = {
            TextButton(
                onClick = { etat.selectedDateMillis?.let { onDateChoisie(it.versLocalDate()) } },
                enabled = etat.selectedDateMillis != null,
            ) {
                Text(text = "Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    ) {
        DatePicker(state = etat)
    }
}
