package com.frigopro.app.data

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/**
 * Le parc de machines et leurs photos.
 *
 * C'est le seul endroit où la base et les fichiers image avancent ensemble :
 * une photo est une ligne *et* un fichier, et les deux doivent apparaître et
 * disparaître de concert. L'ordre est toujours le même — la base d'abord, le
 * fichier ensuite — parce qu'une ligne sans fichier se voit à l'écran (une
 * vignette vide) alors qu'un fichier sans ligne ne se voit nulle part et
 * grossirait l'archive de sauvegarde en silence.
 */
class EquipementRepository(
    private val dao: EquipementDao,
    private val stockage: RangementPhotos,
) {

    /**
     * Tout le parc, trié par nom, accents repliés — mêmes raisons que le carnet
     * de clients. Le filtrage par client se fait côté appelant : le flux sert à
     * la fois la fiche d'un client et le formulaire d'intervention.
     */
    val equipements: Flow<List<Equipement>> = dao.observerTous().map { liste ->
        val collateur = Collator.getInstance(Locale.FRENCH)
        liste.sortedWith { a, b -> collateur.compare(a.nom, b.nom) }
    }

    /**
     * Le parc rangé par groupe : chaque groupe, et ses unités intérieures.
     *
     * Le **nombre d'unités se déduit** de la table et ne s'y stocke pas. Un
     * compteur `nombreUnites` aurait été plus court, et aurait dérivé à la
     * première unité ajoutée sans passer par l'écran qui l'incrémente — une
     * restauration de sauvegarde, par exemple. Ici, ajouter une unité suffit à
     * changer le compte, partout.
     *
     * Une machine seule est un groupe sans unité : l'écran n'a donc pas deux cas
     * à traiter, et le multi-split n'est que le cas où la liste n'est pas vide.
     */
    val parGroupe: Flow<List<GroupeMachines>> = equipements.map { liste ->
        val unitesParParent = liste.filter { it.estUnite }.groupBy { it.parentId }
        liste.filterNot { it.estUnite }.map { groupe ->
            GroupeMachines(groupe = groupe, unites = unitesParParent[groupe.id].orEmpty())
        }
    }

    /** Les photos d'une machine, dans l'ordre où elles ont été prises. */
    fun observerPhotos(equipementId: String): Flow<List<Photo>> = dao.observerPhotos(equipementId)

    /**
     * Crée la machine ou remplace celle qui porte le même identifiant, et
     * répercute son nom sur les interventions qui la désignent.
     */
    suspend fun enregistrer(equipement: Equipement): Equipement {
        val nettoye = equipement.copy(nom = equipement.nom.trim(), modifieLe = Instant.now())
        dao.renommer(nettoye)
        return nettoye
    }

    /**
     * Renvoie la machine de ce nom chez ce client, en la créant au besoin :
     * c'est ce qui permet d'inscrire une machine inconnue depuis le formulaire,
     * sans interrompre la saisie d'une intervention.
     */
    suspend fun trouverOuCreer(clientId: String, nom: String): Equipement {
        val recherche = nom.trim()
        return dao.trouverParNom(clientId, recherche)
            ?: enregistrer(Equipement(clientId = clientId, nom = recherche))
    }

    /**
     * Retire la machine du parc, avec ses photos. Les interventions qui la
     * désignaient gardent son nom et perdent le lien : une tournée passée dit
     * toujours sur quoi on est intervenu.
     */
    suspend fun supprimer(id: String) {
        // Les photos des unités intérieures avec celles du groupe : le DAO les
        // emporte en base (voir [EquipementDao.supprimer]), et sans cette
        // collecte leurs fichiers resteraient sur le téléphone sans qu'aucun
        // écran ne puisse plus les montrer ni les effacer.
        val photos = dao.photosDe(id) + dao.unitesDe(id).flatMap { dao.photosDe(it.id) }
        dao.supprimer(id)
        photos.forEach { stockage.supprimer(it.fichier) }
    }

    /** Le fichier qu'une application d'appareil photo viendra remplir. */
    fun preparerCapture(): Capture = stockage.preparerCapture()

    /**
     * Enregistre la photo que l'appareil vient de prendre. Renvoie `null` si la
     * prise de vue n'a rien donné, auquel cas rien n'est écrit en base.
     */
    suspend fun ajouterCapture(
        equipementId: String,
        categorie: CategoriePhoto,
        nom: String,
    ): Photo? {
        val rangee = stockage.finaliserCapture(nom) ?: return null
        return enregistrerPhoto(equipementId, categorie, rangee)
    }

    /** Enregistre une photo reprise de la galerie. `null` si elle est illisible. */
    suspend fun ajouterDepuisGalerie(
        equipementId: String,
        categorie: CategoriePhoto,
        source: Uri,
    ): Photo? {
        val rangee = stockage.importer(source) ?: return null
        return enregistrerPhoto(equipementId, categorie, rangee)
    }

    /**
     * Décode une image à la taille demandée. Le dépôt s'en charge plutôt que
     * l'écran : les images sont rangées ici, c'est donc ici qu'on sait les lire.
     */
    suspend fun charger(fichier: String, coteMax: Int): Bitmap? = stockage.charger(fichier, coteMax)

    suspend fun supprimerPhoto(photo: Photo) {
        dao.effacerPhoto(photo.id)
        stockage.supprimer(photo.fichier)
    }

    private suspend fun enregistrerPhoto(
        equipementId: String,
        categorie: CategoriePhoto,
        fichier: String,
    ): Photo {
        val photo = Photo(
            equipementId = equipementId,
            categorie = categorie,
            fichier = fichier,
            priseLe = Instant.now(),
        )
        dao.enregistrerPhoto(photo)
        return photo
    }
}
