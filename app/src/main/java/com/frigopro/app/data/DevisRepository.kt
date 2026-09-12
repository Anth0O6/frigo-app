package com.frigopro.app.data

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
class ParametresRepository(private val dao: ParametresDao) {

    val parametres: Flow<Parametres> = dao.observer().map { it ?: Parametres() }

    suspend fun lire(): Parametres = dao.lire() ?: Parametres()

    suspend fun enregistrer(parametres: Parametres) {
        dao.enregistrer(parametres.copy(id = Parametres.UNIQUE, modifieLe = Instant.now()))
    }

    /** Applique une modification à la ligne courante, sans que l'appelant ait à la lire. */
    suspend fun modifier(transformation: (Parametres) -> Parametres) {
        enregistrer(transformation(lire()))
    }
}
