package com.frigopro.app.data

/**
 * Ce qu'une intervention a coûté, et ce qu'elle rapporte.
 *
 * ## Ce que ce chiffre est, et ce qu'il n'est pas
 *
 * C'est une **marge sur coûts directs** : le temps passé, les pièces posées, le
 * fluide ajouté. Ce n'est pas un résultat d'exploitation, et l'écran doit le
 * dire plutôt que de laisser croire le contraire. N'y entrent pas le véhicule,
 * l'assurance, l'outillage, le local, les heures de bureau ni le temps de
 * trajet — qui ne sont pas rattachables à une intervention sans une clé de
 * répartition que l'application n'a pas à inventer.
 *
 * Le dire franchement vaut mieux que de le taire : un technicien qui lirait
 * « 340 € de marge » en croyant que c'est ce qui lui reste facturerait trop bas
 * l'année suivante. C'est la même retenue que pour l'aide au dépannage et pour
 * les pistes de substitution — l'application dit ce qu'elle sait, et nomme ce
 * qu'elle ignore.
 *
 * ## Pourquoi les prix d'achat sont recopiés sur les lignes
 *
 * [PiecePosee.prixAchat] et [MouvementFluide.prixAchatKg] portent le prix
 * **du jour où l'intervention a eu lieu**, et non celui du magasin aujourd'hui.
 * C'est la même règle que le taux de TVA d'une facture : une intervention de
 * mars doit rester chiffrable en mars, et une hausse du tarif fournisseur en
 * juin ne doit pas réécrire la marge qu'on a réellement faite.
 *
 * @param recetteHt ce que l'intervention a été facturée, hors taxes. `null`
 *   tant qu'aucune facture n'en est sortie : il n'y a alors pas de marge à
 *   calculer, et zéro serait un faux — une intervention non encore facturée
 *   n'est pas une intervention à perte.
 */
data class RentabiliteIntervention(
    val heures: Double,
    val coutHoraire: Double,
    val coutPieces: Double,
    val coutFluide: Double,
    val recetteHt: Double?,
) {

    /** Ce que le temps passé a coûté à l'entreprise. */
    val coutMainDoeuvre: Double get() = (heures * coutHoraire).auCentime()

    /** Le total des coûts directs. */
    val coutDirect: Double
        get() = (coutMainDoeuvre + coutPieces + coutFluide).auCentime()

    /**
     * Le calcul a de quoi dire quelque chose.
     *
     * Sans coût horaire renseigné **et** sans pièce chiffrée, le coût vaudrait
     * zéro et la marge serait exactement la recette : un chiffre juste par
     * accident, que personne ne pourrait distinguer d'un vrai. L'écran doit
     * alors réclamer le coût horaire plutôt qu'afficher un résultat flatteur.
     */
    val chiffrable: Boolean get() = coutDirect > 0.0

    /** Ce qui reste des coûts directs, en euros. */
    val marge: Double? get() = recetteHt?.let { (it - coutDirect).auCentime() }

    /** La part de la recette qui n'est pas un coût direct, en pourcentage. */
    val tauxDeMarque: Double?
        get() {
            val recette = recetteHt?.takeIf { it > 0.0 } ?: return null
            return ((recette - coutDirect) / recette * 100.0).arrondiDixieme()
        }

    /** L'intervention a coûté plus qu'elle n'a rapporté. */
    val aPerte: Boolean get() = marge?.let { it < 0.0 } == true

    companion object {

        /**
         * Le calcul, depuis ce que l'intervention a laissé derrière elle.
         *
         * Le fluide **récupéré ne compte pas** : c'est une reprise, pas un
         * achat, et l'imputer en coût reviendrait à se faire payer deux fois le
         * même kilo. Même règle que pour les lignes de facture, où une
         * récupération ne produit aucune ligne.
         */
        fun de(
            intervention: Intervention,
            pieces: List<PiecePosee>,
            mouvements: List<MouvementFluide>,
            parametres: Parametres,
            recetteHt: Double? = null,
        ): RentabiliteIntervention = RentabiliteIntervention(
            // Le même arrondi que la ligne de facture, et pour la même raison :
            // le coût doit se comparer à une recette calculée sur ce chiffre-là,
            // pas sur la valeur exacte.
            heures = (intervention.chrono.cumuleS / 3600.0).auCentime(),
            coutHoraire = parametres.coutHoraireInterne,
            coutPieces = pieces.sumOf { (it.quantite * it.prixAchat).auCentime() }.auCentime(),
            coutFluide = mouvements
                .filter { it.sens == SensFluide.AJOUT }
                .sumOf { (it.masseKg * it.prixAchatKg).auCentime() }
                .auCentime(),
            recetteHt = recetteHt,
        )
    }
}
