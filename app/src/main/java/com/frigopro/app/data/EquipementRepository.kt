package com.frigopro.app.data

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale
import java.util.UUID

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
     * Ajoute une unité intérieure à un groupe.
     *
     * Distincte de [trouverOuCreer], qui cherche par nom chez un client : deux
     * unités peuvent légitimement s'appeler « Salon » chez le même client si elles
     * pendent de deux groupes différents, et réutiliser la recherche par nom
     * rattacherait la seconde au premier groupe. Le doublon qui compte ici est
     * celui au sein du groupe, et c'est l'écran qui le refuse — là où il peut le
     * dire.
     *
     * L'unité **hérite du client** de son groupe : elle ne peut pas appartenir à
     * quelqu'un d'autre, et le lui demander serait une question sans réponse
     * possible.
     */
    suspend fun ajouterUnite(groupe: Equipement, nom: String): Equipement =
        enregistrer(Equipement(clientId = groupe.clientId, parentId = groupe.id, nom = nom))

    /** Les unités d'un groupe, pour qui n'observe pas tout le parc. */
    suspend fun unitesDe(groupeId: String): List<Equipement> = dao.unitesDe(groupeId)

    /**
     * Recopie une machine sous un nouveau nom, **avec ses unités intérieures**.
     *
     * C'est le geste qui rend un inventaire de plusieurs centaines d'équipements
     * tenable : un linéaire est fait de meubles identiques, et saisir douze fois
     * la même marque, le même modèle, le même fluide et la même charge est le
     * genre de travail qui ne se fait pas — on en saisit trois, et le parc est
     * faux pour toujours.
     *
     * **Ce qui est recopié est une caractéristique ; ce qui ne l'est pas est un
     * acte.** C'est toute la règle, et elle se lit ligne par ligne :
     *
     * - La marque, le modèle, le fluide et la charge **suivent** : ce sont les
     *   propriétés du matériel, identiques d'un meuble à l'autre du linéaire.
     * - La **mise en service** suit aussi : un linéaire est posé le même jour, et
     *   c'est un fait d'installation, pas un relevé.
     * - Le **numéro de série** ne suit pas : il est unique par définition, et le
     *   recopier aurait rempli le parc de doublons qui désignent une seule
     *   machine — avec, au bout, une pièce commandée pour le mauvais meuble.
     * - Le **dernier contrôle d'étanchéité** ne suit pas : c'est un acte
     *   réglementaire fait sur *une* machine, et le dater sur la copie
     *   reviendrait à attester un contrôle qui n'a pas eu lieu. La conséquence
     *   est exactement celle que l'application refuse partout ailleurs : une
     *   échéance repoussée à tort, c'est-à-dire une obligation manquée.
     * - Les **photos** ne suivent pas : la plaque du meuble 1 n'est pas celle du
     *   meuble 2, et la copier mettrait en image un numéro de série faux. Même
     *   raison que le numéro, en pire — une photo fait foi.
     * - Le **plan de maintenance** ne suit pas non plus, et c'est le seul point
     *   où le choix est discutable : les douze meubles suivront les mêmes gammes.
     *   Mais `MaintenanceViewModel.onAffecterAuParc` rattache **tout le parc**
     *   d'un geste, ce qui est la bonne réponse sur un site de plusieurs
     *   centaines — là où une recopie machine par machine aurait laissé
     *   l'affectation du dernier meuble dépendre de l'ordre des saisies.
     *
     * Les unités intérieures gardent leur nom — « Salon », « Chambre » se
     * répètent d'un appartement à l'autre, et c'est le cas ordinaire d'un
     * immeuble — et leur plaque, suivant la même règle que leur groupe.
     */
    suspend fun dupliquer(source: Equipement, nom: String): Equipement {
        val copie = enregistrer(
            caracteristiques(source).copy(
                id = UUID.randomUUID().toString(),
                clientId = source.clientId,
                parentId = source.parentId,
                nom = nom,
            ),
        )
        // Une unité ne porte pas d'unité : la boucle ne descend donc jamais plus
        // d'un rang, et c'est la hiérarchie à un seul niveau qui le garantit.
        if (!source.estUnite) {
            dao.unitesDe(source.id).forEach { unite ->
                enregistrer(
                    caracteristiques(unite).copy(
                        id = UUID.randomUUID().toString(),
                        clientId = copie.clientId,
                        parentId = copie.id,
                        nom = unite.nom,
                    ),
                )
            }
        }
        return copie
    }

    /** Ce qu'une copie hérite : le matériel, et rien de ce qui a été fait. */
    private fun caracteristiques(source: Equipement): Equipement = source.copy(
        numeroSerie = "",
        dernierControleLe = null,
    )

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
