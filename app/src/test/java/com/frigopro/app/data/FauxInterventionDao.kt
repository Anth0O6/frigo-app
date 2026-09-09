package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * DAO en mémoire reproduisant le contrat SQL du vrai : filtre par journée,
 * tri par heure, et remplacement de la ligne portant le même identifiant.
 *
 * Permet d'exercer le dépôt et le ViewModel sur la JVM, sans Android ni SQLite.
 */
class FauxInterventionDao : InterventionDao {

    private val lignes = MutableStateFlow<List<Intervention>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<Intervention> get() = lignes.value

    override fun observerJournee(date: LocalDate): Flow<List<Intervention>> =
        lignes.map { liste -> liste.filter { it.date == date }.sortedBy { it.heure } }

    override suspend fun enregistrer(intervention: Intervention) {
        lignes.update { liste -> liste.filterNot { it.id == intervention.id } + intervention }
    }

    override suspend fun supprimer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }
}
