package com.frigopro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.theme.LocalStatuts

/**
 * La couleur d'une intervention, et le mot qui va avec.
 *
 * Le modèle garde l'urgence et l'avancement **séparés** — une urgence reste une
 * urgence une fois terminée, ce qui permet d'en compter trois dans la semaine
 * écoulée. L'écran, lui, n'a qu'une pastille à peindre, et c'est ici que les
 * deux dimensions se réduisent à une.
 *
 * La règle tient en une phrase : ce qui est fait est vert, ce qui est urgent et
 * pas encore fait est rouge, le reste suit son avancement.
 */
@Composable
fun couleurStatut(intervention: Intervention): Color {
    val statuts = LocalStatuts.current
    return when {
        intervention.statut.close -> statuts.termine
        intervention.urgente -> statuts.urgence
        intervention.statut == StatutIntervention.EN_COURS -> statuts.urgence
        intervention.statut == StatutIntervention.A_VALIDER -> statuts.aValider
        else -> statuts.planifie
    }
}

/**
 * Ce qu'annonce la pastille : « Urgence » l'emporte sur l'avancement tant que
 * le travail n'est pas fait, parce que c'est ce mot-là qui fait changer l'ordre
 * de la tournée.
 */
fun libelleStatut(intervention: Intervention): String = when {
    intervention.statut.close -> StatutIntervention.TERMINEE.libelle
    intervention.urgente -> "Urgence"
    else -> intervention.statut.libelle
}
