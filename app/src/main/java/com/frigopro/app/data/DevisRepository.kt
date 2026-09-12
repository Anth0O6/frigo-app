package com.frigopro.app.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Les devis.
 *
 * Un devis naît souvent sur place — le compresseur est mort, il faut le
 * remplacer — et c'est pour cela qu'il vit dans l'application de terrain. Le
 * dépôt fait trois choses : il numérote, il horodate, et il empêche de
 * modifier ce qui ne doit plus l'être.
 */
class DevisRepository(private val dao: DevisDao) {

    /** Tous les devis, du plus récent au plus ancien. */
    val devis: Flow<List<Devis>> = dao.observerTous()

    fun observerDuClient(clientId: String): Flow<List<Devis>> = dao.observerDuClient(clientId)

    /** Un devis et ses lignes, réunis pour que les totaux soient calculables. */
    fun observerComplet(id: String): Flow<DevisComplet?> =
        combine(dao.observer(id), dao.observerLignes(id)) { devis, lignes ->
            devis?.let { DevisComplet(it, lignes) }
        }

    /**
     * Les devis avec leur montant, et les compteurs qui s'en déduisent.
     *
     * Le montant ne vit pas sur la ligne du devis — il est la somme de ses
     * lignes, et le recopier serait s'exposer à ce qu'il cesse d'être juste
     * après une modification. Il est donc recalculé, mais par SQL et en une
     * seule requête (voir [DevisDao.observerTotaux]).
     */
    val devisChiffres: Flow<List<DevisChiffre>> =
        combine(dao.observerTous(), dao.observerTotaux()) { devis, totaux ->
            val parDevis = totaux.associate { it.devisId to it.montant }
            devis.map { DevisChiffre(it, (parDevis[it.id] ?: 0.0).auCentime()) }
        }

    /** Le nombre de devis ouverts d'un client, tel que l'affiche sa fiche. */
    fun compterOuverts(clientId: String): Flow<Int> = dao.observerDuClient(clientId).map { liste ->
        liste.count { it.statut == StatutDevis.BROUILLON || it.statut == StatutDevis.ENVOYE }
    }

    /**
     * Crée un devis numéroté pour ce client.
     *
     * @param aujourdhui passé en paramètre plutôt que lu de l'horloge, pour
     *   que la numérotation soit éprouvable.
     */
    suspend fun creer(
        client: Client?,
        equipement: Equipement? = null,
        objet: String = "",
        tauxTva: Double = 20.0,
        /**
         * Le régime de l'entreprise, **recopié** sur le document.
         *
         * Comme le taux : un devis établi en franchise en base ne doit pas se
         * mettre à afficher de la TVA le jour où l'entreprise franchit le seuil.
         * C'est le document d'alors qui fait foi.
         */
        assujettiTva: Boolean = true,
        aujourdhui: LocalDate = LocalDate.now(),
    ): Devis {
        val numeros = dao.tous().map { it.numero }
        val devis = Devis(
            numero = Numerotation.suivant(Numerotation.PREFIXE_DEVIS, aujourdhui, numeros),
            clientId = client?.id,
            clientNom = client?.nom.orEmpty(),
            equipementId = equipement?.id,
            equipementNom = equipement?.nom.orEmpty(),
            objet = objet.trim(),
            statut = StatutDevis.BROUILLON,
            tauxTva = tauxTva,
            assujettiTva = assujettiTva,
            creeLe = aujourdhui,
            // Un mois de validité : la durée usuelle, et celle au-delà de
            // laquelle un prix de pièce n'engage plus personne.
            valableJusquau = aujourdhui.plusMonths(1),
            modifieLe = Instant.now(),
        )
        dao.enregistrer(devis)
        return devis
    }

    suspend fun enregistrer(devis: Devis): Devis {
        val nettoye = devis.copy(objet = devis.objet.trim(), modifieLe = Instant.now())
        dao.enregistrer(nettoye)
        return nettoye
    }

    /** Fait passer le devis à un autre état : envoyé, accepté, refusé. */
    suspend fun changerStatut(devis: Devis, statut: StatutDevis): Devis =
        enregistrer(devis.copy(statut = statut))

    /**
     * Ajoute une ligne à la fin du devis.
     *
     * Une désignation vide est refusée : une ligne de devis sans intitulé est
     * un montant que le client ne peut pas vérifier.
     */
    suspend fun ajouterLigne(
        devisId: String,
        designation: String,
        quantite: Double,
        unite: String,
        prixUnitaire: Double,
    ): LigneDevis? {
        val intitule = designation.trim()
        if (intitule.isEmpty()) return null
        val ligne = LigneDevis(
            devisId = devisId,
            designation = intitule,
            quantite = quantite,
            unite = unite.trim(),
            prixUnitaire = prixUnitaire,
            rang = dao.prochainRang(devisId),
        )
        dao.enregistrerLigne(ligne)
        return ligne
    }

    suspend fun enregistrerLigne(ligne: LigneDevis): LigneDevis? {
        val intitule = ligne.designation.trim()
        if (intitule.isEmpty()) return null
        val nettoyee = ligne.copy(designation = intitule, unite = ligne.unite.trim())
        dao.enregistrerLigne(nettoyee)
        return nettoyee
    }

    suspend fun supprimerLigne(id: String) = dao.effacerLigne(id)

    /**
     * Offre une ligne, ou reprend le geste.
     *
     * La ligne **garde son prix** : c'est le devis qui affichera le montant barré
     * et « offert ». Remettre le prix à zéro aurait été plus court et aurait
     * effacé l'argument de vente — un geste commercial qu'on ne voit pas n'en est
     * pas un. C'est aussi ce qui permet de reprendre le geste sans ressaisir.
     */
    suspend fun offrirLigne(ligne: LigneDevis, offerte: Boolean): LigneDevis {
        val basculee = ligne.copy(offerte = offerte)
        dao.enregistrerLigne(basculee)
        return basculee
    }

    /**
     * Offre la TVA, ou reprend le geste.
     *
     * Sans effet en franchise en base : il n'y a alors pas de TVA à offrir, et
     * laisser le geste disponible ferait croire à une remise qui ne s'appliquerait
     * à rien. L'écran masque d'ailleurs le bouton, mais le dépôt ne s'en remet pas
     * à l'écran pour garantir une règle de ce genre.
     */
    suspend fun offrirTva(devis: Devis, offerte: Boolean): Devis {
        if (!devis.assujettiTva) return devis
        val basculee = devis.copy(tvaOfferte = offerte, modifieLe = Instant.now())
        dao.enregistrer(basculee)
        return basculee
    }

    // — Le déplacement facturé —

    /** Le trajet d'un devis, s'il en porte un. */
    fun observerTrajet(devisId: String): Flow<Trajet?> = dao.observerTrajet(devisId)

    /**
     * Pose le trajet d'un devis et refait les lignes qu'il facture.
     *
     * Le tarif est passé et non lu ici : il vient des réglages, et le dépôt des
     * devis n'a pas à connaître celui des réglages. C'est aussi ce qui rend la
     * règle éprouvable sans base de réglages.
     *
     * Le trajet est horodaté comme le reste — c'est le rôle du dépôt — mais
     * [Trajet.calculeLe], lui, n'est pas touché : il dit quand le *service* a
     * répondu, pas quand on a coché une case, et les confondre ferait passer un
     * trajet vieux de six mois pour un calcul du jour.
     */
    suspend fun enregistrerDeplacement(trajet: Trajet, tarif: TarifDeplacement): Trajet {
        val horodate = trajet.copy(modifieLe = Instant.now())
        dao.enregistrerDeplacement(horodate, LignesDeplacement.pour(horodate, tarif))
        return horodate
    }

    /** Retire le déplacement d'un devis, et les lignes qu'il avait posées. */
    suspend fun supprimerDeplacement(devisId: String) = dao.supprimerDeplacement(devisId)

    /**
     * Offre le déplacement, ou reprend le geste.
     *
     * Les lignes sont refaites pour que leur `offerte` suive : elles portent le
     * geste, et c'est par elles que le total et le PDF l'apprennent. Les
     * chiffres du trajet restent intacts — même raison que pour une ligne
     * offerte, le montant barré est l'argument de vente.
     */
    suspend fun offrirDeplacement(
        trajet: Trajet,
        tarif: TarifDeplacement,
        offert: Boolean,
    ): Trajet = enregistrerDeplacement(trajet.copy(offert = offert), tarif)

    suspend fun supprimer(id: String) = dao.supprimer(id)
}

/**
 * Les réglages.
 *
 * La lecture ne rend jamais `null` : la migration a inséré la ligne unique, et
 * si elle venait à manquer — base créée de zéro sur une version future qui
 * l'oublierait — les valeurs par défaut de [Parametres] font office. Un écran
 * n'a donc jamais à traiter le cas « pas encore de réglages ».
 */
class ParametresRepository(
    private val dao: ParametresDao,
    /**
     * Où vit le logo. Le dépôt est le seul endroit où les réglages et un fichier
     * avancent ensemble, comme [EquipementRepository] pour les machines ; passer par
     * l'interface et non par `StockagePhotos` permet d'éprouver cette coordination
     * sans Android.
     */
    private val stockage: RangementPhotos,
) {

    val parametres: Flow<Parametres> = dao.observer().map { it ?: Parametres() }

    suspend fun lire(): Parametres = dao.lire() ?: Parametres()

    suspend fun enregistrer(parametres: Parametres) {
        dao.enregistrer(parametres.copy(id = Parametres.UNIQUE, modifieLe = Instant.now()))
    }

    /** Applique une modification à la ligne courante, sans que l'appelant ait à la lire. */
    suspend fun modifier(transformation: (Parametres) -> Parametres) {
        enregistrer(transformation(lire()))
    }

    /**
     * Pose le logo de l'entreprise, et efface celui qu'il remplace.
     *
     * Les réglages et un fichier avancent ici ensemble, comme la base et les images
     * dans [EquipementRepository], et dans le même ordre que partout : **le
     * fichier d'abord, la ligne ensuite**, puis l'ancien fichier. L'ordre inverse —
     * effacer avant d'avoir réussi à écrire — laisserait un logo manquant si
     * l'import échouait, et l'échec est précisément le cas où l'on veut que rien ne
     * change.
     *
     * Le logo est rangé avec les photos et non ailleurs : c'est une image, il est
     * réduit comme les autres, et il part dans l'archive de sauvegarde par le même
     * chemin — un technicien qui restaure sur un téléphone neuf et retrouve ses
     * clients mais plus son logo conclurait que la restauration a échoué.
     */
    suspend fun poserLogo(source: Uri): String? {
        val nouveau = stockage.importer(source) ?: return null
        val ancien = lire().logoFichier
        modifier { it.copy(logoFichier = nouveau) }
        if (ancien != null && ancien != nouveau) stockage.supprimer(ancien)
        return nouveau
    }

    /** Décode une image du stockage : le logo, pour son aperçu dans les Réglages. */
    suspend fun charger(nom: String, coteMax: Int) = stockage.charger(nom, coteMax)

    /** Retire le logo, et son fichier avec : rien ne le référencera plus. */
    suspend fun retirerLogo() {
        val ancien = lire().logoFichier ?: return
        modifier { it.copy(logoFichier = null) }
        stockage.supprimer(ancien)
    }
}
