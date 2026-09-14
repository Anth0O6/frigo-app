package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Carnet en mémoire, reproduisant le contrat SQL du vrai DAO.
 *
 * Il reçoit les autres faux DAO pour la même raison que [FauxEquipementDao] :
 * le vrai écrit dans leurs tables, et **c'est précisément ce qu'on veut pouvoir
 * vérifier sans SQLite** — une suppression de client efface un parc et détache
 * des interventions, des devis et des factures. Laisser ce comportement au
 * hasard ferait passer des tests que la vraie base démentirait.
 *
 * Chacun a une valeur par défaut, si bien que les tests qui ne suppriment
 * aucun client construisent le carnet comme avant.
 */
class FauxClientDao(
    private val interventions: FauxInterventionDao = FauxInterventionDao(),
    private val equipements: FauxEquipementDao = FauxEquipementDao(interventions),
    private val devis: FauxDevisDao = FauxDevisDao(),
    private val factures: FauxFactureDao = FauxFactureDao(),
) : ClientDao() {

    private val lignes = MutableStateFlow<List<Client>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<Client> get() = lignes.value

    override fun observerTous(): Flow<List<Client>> = lignes

    /** `COLLATE NOCASE` du vrai DAO : correspondance exacte à la casse près. */
    override suspend fun trouverParNom(nom: String): Client? =
        lignes.value.firstOrNull { it.nom.equals(nom, ignoreCase = true) }

    /** Le `WHERE parentId = :parentId` du vrai DAO : la recherche est portée. */
    override suspend fun trouverSite(nom: String, parentId: String): Client? =
        lignes.value.firstOrNull {
            it.parentId == parentId && it.nom.equals(nom, ignoreCase = true)
        }

    override suspend fun tous(): List<Client> = lignes.value

    override suspend fun enregistrer(client: Client) {
        lignes.update { liste -> liste.filterNot { it.id == client.id } + client }
    }

    override suspend fun enregistrerTous(clients: List<Client>) {
        val identifiants = clients.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + clients }
    }

    // — La suppression, et ce qu'elle emporte ————————————————————————————

    override suspend fun sitesDe(id: String): List<Client> =
        lignes.value.filter { it.parentId == id }

    override suspend fun photosDuParc(id: String): List<Photo> {
        val parc = equipements.contenu.filter { it.clientId == id }.map { it.id }.toSet()
        return equipements.contenuPhotos.filter { it.equipementId in parc }
    }

    override suspend fun effacerPhotosDuParc(id: String) {
        photosDuParc(id).forEach { equipements.effacerPhoto(it.id) }
    }

    override suspend fun detacherMachinesDuParc(id: String) {
        interventions.detacherEquipements(
            equipements.contenu.filter { it.clientId == id }.map { it.id }.toSet(),
        )
    }

    override suspend fun effacerParc(id: String) {
        equipements.contenu.filter { it.clientId == id }.forEach { equipements.effacer(it.id) }
    }

    override suspend fun detacherInterventions(id: String) {
        interventions.detacherClient(id)
    }

    override suspend fun detacherSousTraitance(id: String) {
        interventions.detacherDonneurDOrdre(id)
    }

    override suspend fun detacherDevis(id: String) {
        devis.detacherClient(id)
    }

    override suspend fun detacherFactures(id: String) {
        factures.detacherClient(id)
    }

    override suspend fun effacer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }
}
