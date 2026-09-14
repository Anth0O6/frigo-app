package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Le contrat SQL des factures, reproduit en mémoire.
 *
 * Comme les autres faux DAO du projet, il **reproduit** le SQL au lieu de
 * l'emprunter au domaine : le `CASE WHEN offerte = 0` d'[observerTotaux] est
 * recopié à la main plutôt que délégué à `LigneFacture.montant`, faute de quoi
 * le test ne vérifierait plus que la requête et le modèle disent la même chose.
 */
class FauxFactureDao : FactureDao() {

    private val documents = MutableStateFlow<List<Facture>>(emptyList())

    private val lignes = MutableStateFlow<List<LigneFacture>>(emptyList())

    val contenu: List<Facture> get() = documents.value

    val contenuLignes: List<LigneFacture> get() = lignes.value

    override fun observerToutes(): Flow<List<Facture>> =
        documents.map { liste -> liste.sortedByDescending { it.emiseLe } }

    override fun observerDuClient(clientId: String): Flow<List<Facture>> =
        documents.map { liste -> liste.filter { it.clientId == clientId } }

    override fun observer(id: String): Flow<Facture?> =
        documents.map { liste -> liste.firstOrNull { it.id == id } }

    override fun observerDeLIntervention(interventionId: String): Flow<Facture?> =
        documents.map { liste -> liste.firstOrNull { it.interventionId == interventionId } }

    override suspend fun pourDevis(devisId: String): Facture? =
        documents.value.firstOrNull { it.devisId == devisId }

    override suspend fun pourIntervention(interventionId: String): Facture? =
        documents.value.firstOrNull { it.interventionId == interventionId }

    override fun observerDevisFactures(): Flow<List<DevisFacture>> = documents.map { liste ->
        liste.mapNotNull { facture ->
            facture.devisId?.let { DevisFacture(it, facture.numero, facture.statut) }
        }
    }

    override suspend fun toutes(): List<Facture> = documents.value

    override suspend fun numeros(): List<String> =
        documents.value.map { it.numero }.filter { it.isNotBlank() }

    override suspend fun enregistrer(facture: Facture) {
        documents.update { liste -> liste.filterNot { it.id == facture.id } + facture }
    }

    override suspend fun enregistrerToutes(factures: List<Facture>) {
        val identifiants = factures.map { it.id }.toSet()
        documents.update { liste -> liste.filterNot { it.id in identifiants } + factures }
    }

    override fun observerLignes(factureId: String): Flow<List<LigneFacture>> =
        lignes.map { liste -> liste.filter { it.factureId == factureId }.sortedBy { it.rang } }

    override suspend fun lignesDe(factureId: String): List<LigneFacture> =
        lignes.value.filter { it.factureId == factureId }.sortedBy { it.rang }

    override suspend fun toutesLesLignes(): List<LigneFacture> = lignes.value

    override fun observerTotaux(): Flow<List<TotalFacture>> = lignes.map { liste ->
        liste.groupBy { it.factureId }
            .map { (factureId, lignesDeLaFacture) ->
                TotalFacture(
                    factureId,
                    lignesDeLaFacture.sumOf {
                        if (it.offerte) 0.0 else it.quantite * it.prixUnitaire
                    },
                )
            }
    }

    override suspend fun prochainRang(factureId: String): Int =
        (lignes.value.filter { it.factureId == factureId }.maxOfOrNull { it.rang } ?: -1) + 1

    override suspend fun enregistrerLigne(ligne: LigneFacture) {
        lignes.update { liste -> liste.filterNot { it.id == ligne.id } + ligne }
    }

    override suspend fun enregistrerLignes(nouvelles: List<LigneFacture>) {
        val identifiants = nouvelles.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + nouvelles }
    }

    override suspend fun effacerLigne(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerLignesDe(factureId: String) {
        lignes.update { liste -> liste.filterNot { it.factureId == factureId } }
    }

    override suspend fun effacer(id: String) {
        documents.update { liste -> liste.filterNot { it.id == id } }
    }
}
