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
            // Les avoirs émis sont imputés ici, une fois pour toutes les listes :
            // l'onglet, l'accueil et le rappel du matin lisent tous ce flux, et un
            // solde calculé à trois endroits finirait par différer d'un centime.
            Avoirs.imputer(
                factures.map { FactureChiffree(it, (parId[it.id] ?: 0.0).auCentime()) },
            )
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
        dao.observerDevisFactures().map { liens ->
            // Un devis peut avoir produit plusieurs factures, depuis qu'une
            // facture annulée ne bloque plus d'en établir une autre : c'est la
            // vivante qui doit se lire sur la ligne du devis, pas celle qu'on a
            // annulée pour la remplacer.
            liens.groupBy { it.devisId }.mapValues { (_, factures) ->
                factures.firstOrNull { it.statut != StatutFacture.ANNULEE } ?: factures.first()
            }
        }

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
     * F-Gas de l'accueil. Une facture que ses avoirs couvrent n'y est plus, et
     * une facture partiellement couverte y reste pour son reste.
     */
    fun aRelancer(aujourdhui: LocalDate): Flow<List<FactureChiffree>> =
        facturesChiffrees.map { liste ->
            liste.filter { it.aRelancer(aujourdhui) }
                .sortedBy { it.facture.echeanceLe }
        }

    /** Tout ce qui est en retard, relancé ou non : le compteur de l'accueil. */
    fun enRetard(aujourdhui: LocalDate): Flow<List<FactureChiffree>> =
        facturesChiffrees.map { liste ->
            liste.filter { it.enRetard(aujourdhui) }
                .sortedBy { it.facture.echeanceLe }
        }

    /**
     * La facture qui compte encore parmi celles d'un même devis ou d'une même
     * intervention, s'il y en a une.
     *
     * Une facture **éteinte** ne réclame plus rien et n'empêche donc plus d'en
     * établir une autre : annulée, ou entièrement couverte par ses avoirs émis.
     * C'est le chemin normal pour corriger une facture partie fausse — l'annuler
     * ou la créditer, puis refacturer —, et un `LIMIT 1` qui rendait la première
     * venue le fermait : « Facturer » rouvrait pour toujours la facture annulée.
     *
     * Toute autre facture est **vivante** et doit être rouverte plutôt que
     * doublée : un brouillon, une facture émise non couverte — même en partie
     * créditée, c'est son reste qu'on réclame —, une facture payée. Facturer deux
     * fois le même travail est l'erreur que ce garde-fou existe pour éviter.
     *
     * « Couverte » se juge exactement comme sur la liste ([FactureChiffree.couverte]
     * et [Avoirs.imputer]) : une facture dite couverte à l'écran ne doit pas
     * bloquer ici pour un centime d'arrondi.
     */
    private suspend fun vivante(factures: List<Facture>): Facture? =
        factures.firstOrNull { !eteinte(it) }

    private suspend fun eteinte(facture: Facture): Boolean {
        if (facture.statut == StatutFacture.ANNULEE) return true
        if (!facture.numerotee) return false
        val credits = dao.avoirsDe(facture.id).filter { it.deduit }
        if (credits.isEmpty()) return false
        val chiffree = FactureChiffree(
            facture = facture,
            totalHt = FactureComplete(facture, dao.lignesDe(facture.id)).totalHt,
        )
        val avoirs = credits.map { avoir ->
            FactureChiffree(avoir, FactureComplete(avoir, dao.lignesDe(avoir.id)).totalHt)
        }
        return chiffree.copy(
            avoirsHt = avoirs.sumOf { it.totalHt }.auCentime(),
            avoirsTtc = avoirs.sumOf { it.totalTtc }.auCentime(),
        ).couverte
    }

    /**
     * La facture d'une intervention terminée.
     *
     * Rend celle qui existe déjà plutôt que d'en créer une seconde : facturer
     * deux fois la même intervention est l'erreur que ce garde-fou évite, et elle
     * ne se voit qu'au moment où le client la reçoit. Une facture annulée ou
     * entièrement créditée ne compte plus (voir [vivante]) : on refacture alors.
     */
    suspend fun creerDepuisIntervention(
        intervention: Intervention,
        pieces: List<PiecePosee>,
        mouvements: List<MouvementFluide>,
        parametres: Parametres,
        /** La fiche de **celui qui paie** : le donneur d'ordre s'il y en a un. */
        client: Client? = null,
    ): Facture {
        vivante(dao.facturesDeLIntervention(intervention.id))?.let { return it }

        // Le **donneur d'ordre** quand il y en a un : en sous-traitance, on
        // travaille chez Y et c'est X qui reçoit la facture. L'appelant passe
        // donc la fiche de celui qui paie, dont vient l'adresse.
        val facture = Facture(
            clientId = intervention.clientFactureId ?: intervention.clientId,
            clientNom = intervention.clientFacture,
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

    /**
     * La facture d'un devis accepté : ses lignes, à l'identique. Même garde que
     * pour une intervention — la vivante est rouverte, une éteinte ne bloque rien.
     */
    suspend fun creerDepuisDevis(
        devis: DevisComplet,
        parametres: Parametres,
        client: Client? = null,
    ): Facture {
        vivante(dao.facturesDuDevis(devis.devis.id))?.let { return it }

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
     * Le brouillon d'un avoir sur une facture émise.
     *
     * Un avoir ne naît que d'une facture **émise** et non annulée
     * ([Facture.creditable]) : un brouillon se corrige ou s'efface, et rien n'est
     * encore parti chez le client. Il naît **brouillon**, comme une facture, et
     * pour la même raison — le numéro ne se consomme qu'à l'émission, et un
     * brouillon d'avoir abandonné ne laisse aucun trou dans la séquence `AV`.
     *
     * Tout ce qui fait le document est recopié de la facture d'origine : le
     * destinataire et son adresse telle qu'elle y figurait, le régime de TVA —
     * un avoir corrige une facture **dans ses termes**, et le créditer à un
     * autre taux ferait reprendre au client une TVA qu'il n'a pas payée —, et sa
     * référence. `interventionId` et `devisId` ne le sont **pas** : ce sont eux
     * qui empêchent de facturer deux fois une intervention ou un devis, et un
     * avoir qui les porterait passerait pour leur facture. Le lien vers le
     * chantier se fait par la facture d'origine.
     *
     * Rend `null` quand la facture ne se crédite pas, quand les quantités sont
     * impossibles (voir [Avoirs.lignes]), et quand le crédit **dépasserait la
     * facture**. Cette garde compte les avoirs émis et aussi les **brouillons**
     * : sans eux, deux brouillons d'avoir total pourraient attendre côte à côte,
     * et le second ne s'émettrait jamais. L'émission, elle, ne compte que les
     * émis, et c'est elle qui tranche.
     *
     * @param quantites `null` pour l'avoir total ; sinon la quantité à créditer
     *   de chaque ligne, par identifiant de ligne de la facture.
     */
    suspend fun creerAvoir(
        origine: Facture,
        quantites: Map<String, Double>? = null,
    ): Facture? {
        if (!origine.creditable) return null
        val lignesOrigine = dao.lignesDe(origine.id)

        val avoir = Facture(
            nature = NatureFacture.AVOIR,
            factureOrigineId = origine.id,
            factureOrigineNumero = origine.numero,
            factureOrigineEmiseLe = origine.emiseLe,
            clientId = origine.clientId,
            clientNom = origine.clientNom,
            clientAdresse = origine.clientAdresse,
            equipementNom = origine.equipementNom,
            objet = origine.objet,
            tauxTva = origine.tauxTva,
            tvaOfferte = origine.tvaOfferte,
            assujettiTva = origine.assujettiTva,
            // Un avoir ne se paie pas : il n'a ni échéance ni pénalités, et un
            // taux recopié ici ne servirait qu'à laisser croire le contraire.
            tauxPenalites = 0.0,
        )
        val lignes = Avoirs.lignes(avoir.id, lignesOrigine, quantites) ?: return null
        val complet = FactureComplete(avoir, lignes)
        if (complet.totalHt < UN_DEMI_CENTIME) return null

        val autres = dao.avoirsDe(origine.id).filter { it.statut != StatutFacture.ANNULEE }
        if (depasse(origine, lignesOrigine, autres, complet)) return null

        val horodate = avoir.copy(modifieLe = Instant.now())
        dao.creer(horodate, lignes)
        return horodate
    }

    /**
     * Émet la facture : elle prend son numéro, sa date et son échéance.
     *
     * C'est le seul endroit où un numéro est attribué, et il ne l'est **qu'une
     * fois** : réémettre une facture déjà émise la rendrait telle quelle. Sans
     * cela, un second appui sur le bouton lui donnerait un second numéro et
     * laisserait le premier orphelin — le trou qu'on cherche précisément à
     * éviter.
     *
     * Un avoir s'émet par le même chemin, dans **sa** séquence (`AV`), et sans
     * échéance : il ne réclame rien. C'est ici que la garde du dépassement
     * **tranche**, parce que c'est ici que l'avoir devient réel : un avoir qui
     * crédite plus que ce qu'il reste à créditer, ou dont la facture a été
     * annulée entre-temps, ou qui ne crédite rien, est rendu **tel quel**, en
     * brouillon et sans numéro — même forme de refus que la réémission, et aucun
     * rang n'est consommé.
     */
    suspend fun emettre(
        facture: Facture,
        parametres: Parametres,
        aujourdhui: LocalDate = LocalDate.now(),
    ): Facture {
        if (facture.numerotee) return facture
        if (facture.estAvoir && !avoirEmettable(facture)) return facture

        val delai = parametres.delaiPaiementJours.coerceAtLeast(0).toLong()
        val emise = facture.copy(
            numero = Numerotation.suivantAnnuel(
                prefixe = if (facture.estAvoir) {
                    Numerotation.PREFIXE_AVOIR
                } else {
                    Numerotation.PREFIXE_FACTURE
                },
                jour = aujourdhui,
                existants = dao.numeros(),
            ),
            statut = StatutFacture.EMISE,
            emiseLe = aujourdhui,
            echeanceLe = if (facture.estAvoir) null else aujourdhui.plusDays(delai),
            modifieLe = Instant.now(),
        )
        dao.enregistrer(emise)
        return emise
    }

    /**
     * L'avoir peut prendre son numéro : sa facture se crédite encore, il crédite
     * quelque chose, et pas plus que ce que les autres avoirs **émis** ont laissé.
     */
    private suspend fun avoirEmettable(avoir: Facture): Boolean {
        val origine = avoir.factureOrigineId?.let { dao.parId(it) } ?: return false
        if (!origine.creditable) return false
        val lignes = dao.lignesDe(avoir.id)
        val complet = FactureComplete(avoir, lignes)
        // Un avoir dont on a retiré toutes les lignes ne crédite rien : lui
        // donner un numéro consommerait un rang pour un document vide.
        if (complet.totalHt < UN_DEMI_CENTIME) return false
        val emis = dao.avoirsDe(origine.id).filter { it.id != avoir.id && it.deduit }
        return !depasse(origine, dao.lignesDe(origine.id), emis, complet)
    }

    /** Le crédit, ajouté à celui des [autres] avoirs, dépasserait l'[origine]. */
    private suspend fun depasse(
        origine: Facture,
        lignesOrigine: List<LigneFacture>,
        autres: List<Facture>,
        avoir: FactureComplete,
    ): Boolean = Avoirs.depasse(
        factureHt = FactureComplete(origine, lignesOrigine).totalHt,
        dejaCredites = autres
            .filter { it.id != avoir.facture.id }
            .map { FactureComplete(it, dao.lignesDe(it.id)).totalHt },
        avoirHt = avoir.totalHt,
    )

    /** Le client a payé. Sans effet sur un avoir, qui ne se règle pas. */
    suspend fun marquerPayee(facture: Facture, le: LocalDate = LocalDate.now()): Facture {
        if (facture.estAvoir) return facture
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
        if (facture.estAvoir) return facture
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
        if (facture.estAvoir) return facture
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

    /**
     * Ce que la facture annonce.
     *
     * En sous-traitance, **le lieu entre dans l'objet**, et ce n'est pas
     * décoratif : le donneur d'ordre reçoit des factures pour des chantiers
     * qu'il n'a pas vus, et « Fuite de fluide — INT-2605-018 » ne lui dit pas
     * lequel. Hors sous-traitance il n'y a rien à préciser — le destinataire
     * était sur place.
     */
    private fun objetDe(intervention: Intervention): String {
        val type = intervention.typeLibelle.ifBlank { "Intervention" }
        val reference = intervention.numero.ifBlank { null }
        val lieu = if (intervention.sousTraitance) {
            intervention.client.takeIf { it.isNotBlank() }?.let { "chez $it" }
        } else {
            null
        }
        return listOfNotNull(type, lieu, reference).joinToString(" — ")
    }

    private companion object {

        /** Sous ce montant, un avoir ne crédite rien : il n'y a pas un centime. */
        const val UN_DEMI_CENTIME = 0.005
    }
}
