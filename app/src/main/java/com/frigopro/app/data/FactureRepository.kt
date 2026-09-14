package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Les factures.
 *
 * Le dépôt fait quatre choses, et trois d'entre elles n'existent que parce
 * qu'une facture n'est pas un devis :
 *
 * 1. **Il numérote à l'émission, jamais avant.** C'est ce qui rend la séquence
 *    continue sans registre séparé : un brouillon abandonné n'a consommé aucun
 *    rang. Voir [Numerotation.suivantAnnuel].
 * 2. **Il fige.** Passé l'émission, les lignes ne bougent plus — ni ajout, ni
 *    modification, ni suppression. Une facture émise fait foi, et la retoucher
 *    n'est pas une correction.
 * 3. **Il refuse de supprimer ce qui porte un numéro.** Une facture qu'on
 *    regrette s'annule en gardant son numéro ; l'effacer creuserait un trou dans
 *    la séquence, et c'est exactement ce qu'un contrôle cherche.
 * 4. Il horodate, comme tous les dépôts du projet.
 */
class FactureRepository(private val dao: FactureDao) {

    /** Toutes les factures, de la plus récente à la plus ancienne. */
    val factures: Flow<List<Facture>> = dao.observerToutes()

    /**
     * Les factures avec leur montant.
     *
     * Même motif que pour les devis : le montant est la somme des lignes, et le
     * recopier sur la facture serait s'exposer à ce qu'il cesse d'être juste.
     * Un `GROUP BY` les donne toutes d'un coup (voir [FactureDao.observerTotaux]).
     */
    val facturesChiffrees: Flow<List<FactureChiffree>> =
        combine(dao.observerToutes(), dao.observerTotaux()) { factures, totaux ->
            val parId = totaux.associate { it.factureId to it.montant }
            factures.map { FactureChiffree(it, (parId[it.id] ?: 0.0).auCentime()) }
        }

    /**
     * Ce que chaque devis facturé est devenu, par identifiant de devis.
     *
     * L'onglet des devis s'en sert pour **ranger plus bas** ceux qui ont déjà
     * produit une facture : un devis facturé est une affaire close, et le laisser
     * en tête de liste noie ceux qui attendent encore une réponse — c'est la
     * seule question à laquelle cet écran doit répondre d'un coup d'œil.
     */
    val devisFactures: Flow<Map<String, DevisFacture>> =
        dao.observerDevisFactures().map { liens -> liens.associateBy { it.devisId } }

    /** Une facture et ses lignes, réunies pour que les totaux soient calculables. */
    fun observerComplete(id: String): Flow<FactureComplete?> =
        combine(dao.observer(id), dao.observerLignes(id)) { facture, lignes ->
            facture?.let { FactureComplete(it, lignes) }
        }

    fun observerDuClient(clientId: String): Flow<List<Facture>> = dao.observerDuClient(clientId)

    /** La facture déjà établie pour cette intervention, s'il y en a une. */
    fun observerDeLIntervention(interventionId: String): Flow<Facture?> =
        dao.observerDeLIntervention(interventionId)

    /**
     * Ce qui attend un règlement en retard, et qu'il est temps de relancer.
     *
     * Rien n'est stocké : « en retard » se déduit de l'échéance et du jour, et un
     * booléen en base serait faux le lendemain — exactement comme les échéances
     * F-Gas de l'accueil.
     */
    fun aRelancer(aujourdhui: LocalDate): Flow<List<FactureChiffree>> =
        facturesChiffrees.map { liste ->
            liste.filter { it.facture.aRelancer(aujourdhui) }
                .sortedBy { it.facture.echeanceLe }
        }

    /** Tout ce qui est en retard, relancé ou non : le compteur de l'accueil. */
    fun enRetard(aujourdhui: LocalDate): Flow<List<FactureChiffree>> =
        facturesChiffrees.map { liste ->
            liste.filter { it.facture.enRetard(aujourdhui) }
                .sortedBy { it.facture.echeanceLe }
        }

    /**
     * La facture d'une intervention terminée.
     *
     * Rend celle qui existe déjà plutôt que d'en créer une seconde : facturer
     * deux fois la même intervention est l'erreur que ce garde-fou évite, et elle
     * ne se voit qu'au moment où le client la reçoit.
     */
    suspend fun creerDepuisIntervention(
        intervention: Intervention,
        pieces: List<PiecePosee>,
        mouvements: List<MouvementFluide>,
        parametres: Parametres,
        client: Client? = null,
    ): Facture {
        dao.pourIntervention(intervention.id)?.let { return it }

        val facture = Facture(
            clientId = intervention.clientId,
            clientNom = intervention.client,
            clientAdresse = client?.adresseComplete.orEmpty(),
            interventionId = intervention.id,
            equipementNom = intervention.equipementNom,
            objet = objetDe(intervention),
            tauxTva = parametres.tauxTva,
            assujettiTva = parametres.assujettiTva,
            tauxPenalites = parametres.tauxPenalitesRetard,
        )
        val lignes = LignesFacture.depuisIntervention(
            factureId = facture.id,
            intervention = intervention,
            pieces = pieces,
            mouvements = mouvements,
            tauxHoraire = parametres.tauxHoraire,
        )
        val horodatee = facture.copy(modifieLe = Instant.now())
        dao.creer(horodatee, lignes)
        return horodatee
    }

    /** La facture d'un devis accepté : ses lignes, à l'identique. */
    suspend fun creerDepuisDevis(
        devis: DevisComplet,
        parametres: Parametres,
        client: Client? = null,
    ): Facture {
        dao.pourDevis(devis.devis.id)?.let { return it }

        val facture = Facture(
            clientId = devis.devis.clientId,
            clientNom = devis.devis.clientNom,
            clientAdresse = client?.adresseComplete.orEmpty(),
            devisId = devis.devis.id,
            equipementNom = devis.devis.equipementNom,
            objet = devis.devis.objet.ifBlank { "Devis ${devis.devis.numero}" },
            // Repris du devis et non des réglages : c'est le régime sous lequel
            // le client a accepté, et il ne doit pas changer entre les deux
            // documents.
            tauxTva = devis.devis.tauxTva,
            tvaOfferte = devis.devis.tvaOfferte,
            assujettiTva = devis.devis.assujettiTva,
            tauxPenalites = parametres.tauxPenalitesRetard,
        )
        val lignes = LignesFacture.depuisDevis(facture.id, devis.lignes)
        val horodatee = facture.copy(modifieLe = Instant.now())
        dao.creer(horodatee, lignes)
        return horodatee
    }

    /**
     * Émet la facture : elle prend son numéro, sa date et son échéance.
     *
     * C'est le seul endroit où un numéro est attribué, et il ne l'est **qu'une
     * fois** : réémettre une facture déjà émise la rendrait telle quelle. Sans
     * cela, un second appui sur le bouton lui donnerait un second numéro et
     * laisserait le premier orphelin — le trou qu'on cherche précisément à
     * éviter.
     */
    suspend fun emettre(
        facture: Facture,
        parametres: Parametres,
        aujourdhui: LocalDate = LocalDate.now(),
    ): Facture {
        if (facture.numerotee) return facture

        val delai = parametres.delaiPaiementJours.coerceAtLeast(0).toLong()
        val emise = facture.copy(
            numero = Numerotation.suivantAnnuel(
                prefixe = Numerotation.PREFIXE_FACTURE,
                jour = aujourdhui,
                existants = dao.numeros(),
            ),
            statut = StatutFacture.EMISE,
            emiseLe = aujourdhui,
            echeanceLe = aujourdhui.plusDays(delai),
            modifieLe = Instant.now(),
        )
        dao.enregistrer(emise)
        return emise
    }

    /** Le client a payé. */
    suspend fun marquerPayee(facture: Facture, le: LocalDate = LocalDate.now()): Facture {
        val payee = facture.copy(
            statut = StatutFacture.PAYEE,
            payeeLe = le,
            modifieLe = Instant.now(),
        )
        dao.enregistrer(payee)
        return payee
    }

    /**
     * Le règlement n'était pas le bon : la facture redevient exigible.
     *
     * Le chemin inverse existe parce qu'on coche vite, et qu'une facture
     * marquée payée par erreur ne se relancerait plus jamais.
     */
    suspend fun marquerImpayee(facture: Facture): Facture {
        val rouverte = facture.copy(
            statut = StatutFacture.EMISE,
            payeeLe = null,
            modifieLe = Instant.now(),
        )
        dao.enregistrer(rouverte)
        return rouverte
    }

    /** Annule la facture, **en gardant son numéro** : voir [StatutFacture.ANNULEE]. */
    suspend fun annuler(facture: Facture): Facture {
        val annulee = facture.copy(statut = StatutFacture.ANNULEE, modifieLe = Instant.now())
        dao.enregistrer(annulee)
        return annulee
    }

    /** Note qu'une relance est partie, pour ne pas la renvoyer le lendemain. */
    suspend fun marquerRelancee(facture: Facture, le: LocalDate = LocalDate.now()): Facture {
        val relancee = facture.copy(relanceeLe = le, modifieLe = Instant.now())
        dao.enregistrer(relancee)
        return relancee
    }

    /** Modifie l'en-tête d'un brouillon. Sans effet sur une facture émise. */
    suspend fun enregistrer(facture: Facture): Facture? {
        if (facture.statut.figee) return null
        val horodatee = facture.copy(modifieLe = Instant.now())
        dao.enregistrer(horodatee)
        return horodatee
    }

    /**
     * Ajoute une ligne à un brouillon.
     *
     * Rend `null` sur une facture figée plutôt que de lever : l'écran n'offre
     * pas le geste, et le dépôt le refuse quand même — les deux gardes valent
     * mieux qu'une, celle-ci étant la seule qui survive à une refonte de l'écran.
     */
    suspend fun ajouterLigne(
        facture: Facture,
        designation: String,
        quantite: Double,
        unite: String,
        prixUnitaire: Double,
    ): LigneFacture? {
        if (facture.statut.figee) return null
        val ligne = LigneFacture(
            factureId = facture.id,
            designation = designation.trim(),
            quantite = quantite,
            unite = unite.trim(),
            prixUnitaire = prixUnitaire,
            rang = dao.prochainRang(facture.id),
        )
        dao.enregistrerLigne(ligne)
        return ligne
    }

    suspend fun enregistrerLigne(facture: Facture, ligne: LigneFacture): LigneFacture? {
        if (facture.statut.figee) return null
        dao.enregistrerLigne(ligne)
        return ligne
    }

    suspend fun supprimerLigne(facture: Facture, id: String): Boolean {
        if (facture.statut.figee) return false
        dao.effacerLigne(id)
        return true
    }

    /**
     * Supprime un brouillon.
     *
     * Rend `false` sur une facture numérotée, et c'est la règle centrale de tout
     * ce fichier : ce qui porte un numéro reste, quitte à être annulé.
     */
    suspend fun supprimer(facture: Facture): Boolean {
        if (facture.numerotee) return false
        dao.supprimerAvecLignes(facture.id)
        return true
    }

    private fun objetDe(intervention: Intervention): String {
        val type = intervention.typeLibelle.ifBlank { "Intervention" }
        val reference = intervention.numero.ifBlank { null }
        return listOfNotNull(type, reference).joinToString(" — ")
    }
}
