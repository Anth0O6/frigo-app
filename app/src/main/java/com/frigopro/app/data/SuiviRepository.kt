package com.frigopro.app.data

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Ce qui s'est passé pendant une intervention : relevés, fluide, pièces,
 * photos avant/après.
 *
 * Comme [EquipementRepository], c'est un endroit où la base et les fichiers
 * avancent ensemble, et l'ordre y est le même pour la même raison : la ligne
 * d'abord, le fichier ensuite.
 *
 * Chaque écriture est horodatée ici et non par le DAO — c'est la règle du
 * projet, et c'est ce qui permet à la restauration d'une sauvegarde d'écrire
 * par les DAO sans écraser le `modifieLe` que le fichier transporte.
 */
class SuiviRepository(
    private val dao: SuiviDao,
    private val stockage: RangementPhotos,
) {

    // — Relevés ————————————————————————————————————————————————————————————

    fun observerReleves(interventionId: String): Flow<List<Releve>> =
        dao.observerReleves(interventionId)

    fun observerRelevesMachine(equipementId: String): Flow<List<Releve>> =
        dao.observerRelevesMachine(equipementId)

    /**
     * Enregistre un relevé, ou l'efface s'il ne porte plus aucune valeur.
     *
     * Vider les quatre cases est la façon naturelle de dire « finalement je
     * n'ai rien relevé » ; conserver une ligne vide ferait apparaître un
     * relevé fantôme dans la tendance de la machine.
     */
    suspend fun enregistrerReleve(releve: Releve): Releve {
        if (releve.vide) {
            dao.effacerReleve(releve.id)
            return releve
        }
        val horodate = releve.copy(
            releveLe = if (releve.releveLe == Instant.EPOCH) Instant.now() else releve.releveLe,
            modifieLe = Instant.now(),
        )
        dao.enregistrerReleve(horodate)
        return horodate
    }

    suspend fun supprimerReleve(id: String) = dao.effacerReleve(id)

    // — Fluide —————————————————————————————————————————————————————————————

    fun observerMouvements(interventionId: String): Flow<List<MouvementFluide>> =
        dao.observerMouvements(interventionId)

    /** Le registre entier, ce qui s'exporte depuis les Réglages. */
    fun observerRegistre(): Flow<List<MouvementFluide>> = dao.observerRegistre()

    /**
     * Consigne un mouvement de fluide.
     *
     * La masse est ramenée à une valeur positive : le sens porte la direction,
     * et une masse négative additionnée au registre donnerait un total faux.
     * Une masse nulle n'est pas consignée — ce n'est pas un mouvement.
     */
    suspend fun enregistrerMouvement(mouvement: MouvementFluide): MouvementFluide? {
        val masse = kotlin.math.abs(mouvement.masseKg)
        if (masse == 0.0) return null
        val horodate = mouvement.copy(
            fluide = Fluides.normaliser(mouvement.fluide),
            masseKg = masse,
            le = if (mouvement.le == Instant.EPOCH) Instant.now() else mouvement.le,
            modifieLe = Instant.now(),
        )
        dao.enregistrerMouvement(horodate)
        return horodate
    }

    suspend fun supprimerMouvement(id: String) = dao.effacerMouvement(id)

    // — Pièces —————————————————————————————————————————————————————————————

    fun observerPieces(interventionId: String): Flow<List<PiecePosee>> =
        dao.observerPieces(interventionId)

    /** Une pièce sans désignation n'est pas enregistrable : rien ne l'identifierait. */
    suspend fun enregistrerPiece(piece: PiecePosee): PiecePosee? {
        val designation = piece.designation.trim()
        if (designation.isEmpty()) return null
        val nettoyee = piece.copy(
            designation = designation,
            reference = piece.reference.trim(),
            modifieLe = Instant.now(),
        )
        dao.enregistrerPiece(nettoyee)
        return nettoyee
    }

    suspend fun supprimerPiece(id: String) = dao.effacerPiece(id)

    // — Photos avant / après ———————————————————————————————————————————————

    fun observerPhotos(interventionId: String): Flow<List<Photo>> =
        dao.observerPhotos(interventionId)

    fun preparerCapture(): Capture = stockage.preparerCapture()

    suspend fun ajouterCapture(
        interventionId: String,
        categorie: CategoriePhoto,
        nom: String,
    ): Photo? {
        val rangee = stockage.finaliserCapture(nom) ?: return null
        return enregistrerPhoto(interventionId, categorie, rangee)
    }

    suspend fun ajouterDepuisGalerie(
        interventionId: String,
        categorie: CategoriePhoto,
        source: Uri,
    ): Photo? {
        val rangee = stockage.importer(source) ?: return null
        return enregistrerPhoto(interventionId, categorie, rangee)
    }

    suspend fun charger(fichier: String, coteMax: Int): Bitmap? = stockage.charger(fichier, coteMax)

    /**
     * Range une image fabriquée par l'application — la signature du client.
     *
     * Aucune ligne en base ici : la signature n'est pas une photo, elle est un
     * champ de l'intervention, et c'est le dépôt des interventions qui
     * l'attachera une fois le fichier écrit.
     */
    suspend fun rangerImage(image: Bitmap): String? = stockage.enregistrerImage(image)

    suspend fun supprimerPhoto(photo: Photo) {
        dao.effacerPhoto(photo.id)
        stockage.supprimer(photo.fichier)
    }

    // — Suppression d'une intervention —————————————————————————————————————

    /**
     * Supprime l'intervention et tout ce qu'elle portait, fichiers compris.
     *
     * Les photos sont relevées **avant** la transaction : après, leurs lignes
     * n'existent plus et plus rien ne dirait quels fichiers effacer.
     */
    suspend fun supprimerIntervention(id: String) {
        val photos = dao.photosDe(id)
        dao.supprimerIntervention(id)
        photos.forEach { stockage.supprimer(it.fichier) }
    }

    private suspend fun enregistrerPhoto(
        interventionId: String,
        categorie: CategoriePhoto,
        fichier: String,
    ): Photo {
        val photo = Photo(
            interventionId = interventionId,
            categorie = categorie,
            fichier = fichier,
            priseLe = Instant.now(),
        )
        dao.enregistrerPhoto(photo)
        return photo
    }
}
