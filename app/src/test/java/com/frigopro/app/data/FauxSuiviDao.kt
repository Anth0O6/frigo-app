package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Relevés, fluide, pièces et photos d'intervention en mémoire.
 *
 * Comme [FauxEquipementDao], il reçoit le faux DAO d'interventions : le vrai
 * efface une intervention **avec** tout ce qu'elle portait, et c'est
 * exactement cette solidarité qu'on veut pouvoir vérifier sans SQLite.
 */
class FauxSuiviDao(
    private val interventions: FauxInterventionDao = FauxInterventionDao(),
) : SuiviDao() {

    private val mesures = MutableStateFlow<List<Releve>>(emptyList())

    private val fluide = MutableStateFlow<List<MouvementFluide>>(emptyList())

    private val piecesPosees = MutableStateFlow<List<PiecePosee>>(emptyList())

    private val images = MutableStateFlow<List<Photo>>(emptyList())

    /** Contenus courants, pour les assertions. */
    val contenuReleves: List<Releve> get() = mesures.value

    val contenuMouvements: List<MouvementFluide> get() = fluide.value

    val contenuPieces: List<PiecePosee> get() = piecesPosees.value

    val contenuPhotos: List<Photo> get() = images.value

    // — Relevés ————————————————————————————————————————————————————————————

    override fun observerReleves(interventionId: String): Flow<List<Releve>> =
        mesures.map { liste -> liste.filter { it.interventionId == interventionId }.sortedBy { it.releveLe } }

    override fun observerRelevesMachine(equipementId: String): Flow<List<Releve>> =
        mesures.map { liste -> liste.filter { it.equipementId == equipementId }.sortedBy { it.releveLe } }

    override suspend fun tousLesReleves(): List<Releve> = mesures.value

    override suspend fun enregistrerReleve(releve: Releve) {
        mesures.update { liste -> liste.filterNot { it.id == releve.id } + releve }
    }

    override suspend fun enregistrerReleves(releves: List<Releve>) {
        val identifiants = releves.map { it.id }.toSet()
        mesures.update { liste -> liste.filterNot { it.id in identifiants } + releves }
    }

    override suspend fun effacerReleve(id: String) {
        mesures.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerRelevesDe(interventionId: String) {
        mesures.update { liste -> liste.filterNot { it.interventionId == interventionId } }
    }

    // — Fluide —————————————————————————————————————————————————————————————

    override fun observerMouvements(interventionId: String): Flow<List<MouvementFluide>> =
        fluide.map { liste -> liste.filter { it.interventionId == interventionId }.sortedBy { it.le } }

    override fun observerRegistre(): Flow<List<MouvementFluide>> =
        fluide.map { liste -> liste.sortedByDescending { it.le } }

    override suspend fun tousLesMouvements(): List<MouvementFluide> = fluide.value

    override suspend fun enregistrerMouvement(mouvement: MouvementFluide) {
        fluide.update { liste -> liste.filterNot { it.id == mouvement.id } + mouvement }
    }

    override suspend fun enregistrerMouvements(mouvements: List<MouvementFluide>) {
        val identifiants = mouvements.map { it.id }.toSet()
        fluide.update { liste -> liste.filterNot { it.id in identifiants } + mouvements }
    }

    override suspend fun effacerMouvement(id: String) {
        fluide.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerMouvementsDe(interventionId: String) {
        fluide.update { liste -> liste.filterNot { it.interventionId == interventionId } }
    }

    // — Pièces —————————————————————————————————————————————————————————————

    override fun observerPieces(interventionId: String): Flow<List<PiecePosee>> =
        piecesPosees.map { liste -> liste.filter { it.interventionId == interventionId } }

    override suspend fun toutesLesPieces(): List<PiecePosee> = piecesPosees.value

    override suspend fun enregistrerPiece(piece: PiecePosee) {
        piecesPosees.update { liste -> liste.filterNot { it.id == piece.id } + piece }
    }

    override suspend fun enregistrerPieces(pieces: List<PiecePosee>) {
        val identifiants = pieces.map { it.id }.toSet()
        piecesPosees.update { liste -> liste.filterNot { it.id in identifiants } + pieces }
    }

    override suspend fun effacerPiece(id: String) {
        piecesPosees.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerPiecesDe(interventionId: String) {
        piecesPosees.update { liste -> liste.filterNot { it.interventionId == interventionId } }
    }

    // — Photos —————————————————————————————————————————————————————————————

    override fun observerPhotos(interventionId: String): Flow<List<Photo>> =
        images.map { liste -> liste.filter { it.interventionId == interventionId }.sortedBy { it.priseLe } }

    override suspend fun photosDe(interventionId: String): List<Photo> =
        images.value.filter { it.interventionId == interventionId }

    override suspend fun enregistrerPhoto(photo: Photo) {
        images.update { liste -> liste.filterNot { it.id == photo.id } + photo }
    }

    override suspend fun effacerPhoto(id: String) {
        images.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerPhotosDe(interventionId: String) {
        images.update { liste -> liste.filterNot { it.interventionId == interventionId } }
    }

    private val points = MutableStateFlow<List<PointChecklist>>(emptyList())

    val contenuChecklist: List<PointChecklist> get() = points.value

    override fun observerChecklist(interventionId: String): Flow<List<PointChecklist>> =
        points.map { liste -> liste.filter { it.interventionId == interventionId }.sortedBy { it.rang } }

    override suspend fun compterChecklist(interventionId: String): Int =
        points.value.count { it.interventionId == interventionId }

    override suspend fun tousLesPoints(): List<PointChecklist> = points.value

    override suspend fun enregistrerPoint(point: PointChecklist) {
        points.update { liste -> liste.filterNot { it.id == point.id } + point }
    }

    override suspend fun enregistrerPoints(nouveaux: List<PointChecklist>) {
        val identifiants = nouveaux.map { it.id }.toSet()
        points.update { liste -> liste.filterNot { it.id in identifiants } + nouveaux }
    }

    override suspend fun toutCocher(interventionId: String) {
        points.update { liste ->
            liste.map { if (it.interventionId == interventionId) it.copy(fait = true) else it }
        }
    }

    override suspend fun effacerChecklistDe(interventionId: String) {
        points.update { liste -> liste.filterNot { it.interventionId == interventionId } }
    }

    override suspend fun effacerIntervention(id: String) {
        interventions.supprimer(id)
    }
}

/** Devis et lignes en mémoire. */
class FauxDevisDao : DevisDao() {

    private val documents = MutableStateFlow<List<Devis>>(emptyList())

    private val lignes = MutableStateFlow<List<LigneDevis>>(emptyList())

    val contenu: List<Devis> get() = documents.value

    val contenuLignes: List<LigneDevis> get() = lignes.value

    override fun observerTous(): Flow<List<Devis>> =
        documents.map { liste -> liste.sortedByDescending { it.creeLe } }

    override fun observerDuClient(clientId: String): Flow<List<Devis>> =
        documents.map { liste -> liste.filter { it.clientId == clientId } }

    override fun observer(id: String): Flow<Devis?> =
        documents.map { liste -> liste.firstOrNull { it.id == id } }

    override suspend fun tous(): List<Devis> = documents.value

    override suspend fun enregistrer(devis: Devis) {
        documents.update { liste -> liste.filterNot { it.id == devis.id } + devis }
    }

    override suspend fun enregistrerTous(devis: List<Devis>) {
        val identifiants = devis.map { it.id }.toSet()
        documents.update { liste -> liste.filterNot { it.id in identifiants } + devis }
    }

    override fun observerLignes(devisId: String): Flow<List<LigneDevis>> =
        lignes.map { liste -> liste.filter { it.devisId == devisId }.sortedBy { it.rang } }

    override suspend fun toutesLesLignes(): List<LigneDevis> = lignes.value

    /**
     * Le `GROUP BY` du vrai DAO : un devis sans ligne n'apparaît pas, et une ligne
     * offerte compte pour zéro. Le `CASE WHEN offerte = 0` est recopié ici plutôt
     * que délégué à `LigneDevis.montant` : c'est le contrat SQL qu'on reproduit, et
     * un faux qui emprunterait la règle au domaine ne vérifierait plus que les deux
     * disent la même chose.
     */
    override fun observerTotaux(): Flow<List<TotalDevis>> = lignes.map { liste ->
        liste.groupBy { it.devisId }
            .map { (devisId, lignesDuDevis) ->
                TotalDevis(
                    devisId,
                    lignesDuDevis.sumOf { if (it.offerte) 0.0 else it.quantite * it.prixUnitaire },
                )
            }
    }

    override suspend fun prochainRang(devisId: String): Int =
        (lignes.value.filter { it.devisId == devisId }.maxOfOrNull { it.rang } ?: -1) + 1

    override suspend fun enregistrerLigne(ligne: LigneDevis) {
        lignes.update { liste -> liste.filterNot { it.id == ligne.id } + ligne }
    }

    override suspend fun enregistrerLignes(nouvelles: List<LigneDevis>) {
        val identifiants = nouvelles.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + nouvelles }
    }

    override suspend fun effacerLigne(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerLignesDe(devisId: String) {
        lignes.update { liste -> liste.filterNot { it.devisId == devisId } }
    }

    override suspend fun effacer(id: String) {
        documents.update { liste -> liste.filterNot { it.id == id } }
    }

    // — Le déplacement facturé —

    private val deplacements = MutableStateFlow<List<Trajet>>(emptyList())

    val contenuTrajets: List<Trajet> get() = deplacements.value

    override fun observerTrajet(devisId: String): Flow<Trajet?> =
        deplacements.map { liste -> liste.firstOrNull { it.devisId == devisId } }

    override suspend fun tousLesTrajets(): List<Trajet> = deplacements.value

    override suspend fun enregistrerTrajet(trajet: Trajet) {
        deplacements.update { liste -> liste.filterNot { it.id == trajet.id } + trajet }
    }

    override suspend fun enregistrerTrajets(trajets: List<Trajet>) {
        val identifiants = trajets.map { it.id }.toSet()
        deplacements.update { liste -> liste.filterNot { it.id in identifiants } + trajets }
    }

    override suspend fun effacerTrajetDe(devisId: String) {
        deplacements.update { liste -> liste.filterNot { it.devisId == devisId } }
    }

    /**
     * Le `WHERE deplacement = 1` du vrai DAO.
     *
     * Recopié plutôt que déduit, pour la même raison que le `CASE WHEN offerte`
     * ci-dessus : c'est le contrat SQL qu'on reproduit, et un faux qui filtrerait
     * autrement ne vérifierait plus que les deux disent la même chose — ici, que
     * recalculer un trajet n'emporte pas les lignes saisies à la main.
     */
    override suspend fun effacerLignesDeplacementDe(devisId: String) {
        lignes.update { liste -> liste.filterNot { it.devisId == devisId && it.deplacement } }
    }
}

/** La ligne unique des réglages, en mémoire. */
class FauxParametresDao : ParametresDao {

    private val ligne = MutableStateFlow<Parametres?>(null)

    val contenu: Parametres? get() = ligne.value

    override fun observer(): Flow<Parametres?> = ligne

    override suspend fun lire(): Parametres? = ligne.value

    override suspend fun enregistrer(parametres: Parametres) {
        ligne.value = parametres
    }
}

/** Techniciens en mémoire, avec la propagation que fait le vrai DAO. */
class FauxTechnicienDao(
    private val interventions: FauxInterventionDao = FauxInterventionDao(),
) : TechnicienDao() {

    private val lignes = MutableStateFlow<List<Technicien>>(emptyList())

    val contenu: List<Technicien> get() = lignes.value

    override fun observerTous(): Flow<List<Technicien>> = lignes

    override suspend fun tous(): List<Technicien> = lignes.value

    override suspend fun trouverParNom(nom: String): Technicien? =
        lignes.value.firstOrNull { it.nom.equals(nom, ignoreCase = true) }

    override suspend fun enregistrer(technicien: Technicien) {
        lignes.update { liste -> liste.filterNot { it.id == technicien.id } + technicien }
    }

    override suspend fun enregistrerTous(techniciens: List<Technicien>) {
        val identifiants = techniciens.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + techniciens }
    }

    override suspend fun propagerNom(id: String, nom: String) {
        interventions.propagerTechnicien(id, nom)
    }

    override suspend fun detacher(id: String) {
        interventions.detacherTechnicien(id)
    }

    override suspend fun effacer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }
}

/** Le catalogue en mémoire. */
class FauxPrestationDao : PrestationDao {

    private val lignes = MutableStateFlow<List<Prestation>>(emptyList())

    val contenu: List<Prestation> get() = lignes.value

    override fun observerToutes(): Flow<List<Prestation>> =
        lignes.map { liste -> liste.sortedBy { it.rang } }

    override suspend fun toutes(): List<Prestation> = lignes.value

    override suspend fun prochainRang(): Int = (lignes.value.maxOfOrNull { it.rang } ?: 0) + 1

    override suspend fun enregistrer(prestation: Prestation) {
        lignes.update { liste -> liste.filterNot { it.id == prestation.id } + prestation }
    }

    override suspend fun enregistrerToutes(prestations: List<Prestation>) {
        val identifiants = prestations.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + prestations }
    }

    override suspend fun effacer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }
}

/** Les vérifications de courbe, en mémoire. */
class FauxVerificationFluideDao : VerificationFluideDao {

    private val lignes = MutableStateFlow<List<VerificationFluide>>(emptyList())

    val contenu: List<VerificationFluide> get() = lignes.value

    override fun observerToutes(): Flow<List<VerificationFluide>> = lignes

    override suspend fun toutes(): List<VerificationFluide> = lignes.value

    override suspend fun enregistrer(verification: VerificationFluide) {
        lignes.update { liste -> liste.filterNot { it.fluide == verification.fluide } + verification }
    }

    override suspend fun enregistrerToutes(verifications: List<VerificationFluide>) {
        val noms = verifications.map { it.fluide }.toSet()
        lignes.update { liste -> liste.filterNot { it.fluide in noms } + verifications }
    }

    override suspend fun effacer(fluide: String) {
        lignes.update { liste -> liste.filterNot { it.fluide == fluide } }
    }
}
