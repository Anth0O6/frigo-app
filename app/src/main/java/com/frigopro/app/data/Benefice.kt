package com.frigopro.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Ce qui reste dans la poche : la marge du matériel et la main-d'œuvre, sur les
 * factures.
 *
 * ## Ce qui compte, et ce qui ne compte pas
 *
 * - Une ligne **de matériel** — elle porte un prix d'achat — rapporte sa
 *   **marge**, vente moins achat. Offerte, elle rapporte **moins son coût** :
 *   le matériel a été payé, et le donner au client ne le rend pas gratuit.
 * - Toute autre ligne — main-d'œuvre, forfait, prestation, et le **déplacement**,
 *   qu'une ligne de facture ne distingue pas — rapporte **son montant** : il n'y a
 *   pas d'achat en face. Offerte, elle ne rapporte rien.
 * - La **TVA offerte** se retire : c'est une remise que l'entreprise paie de sa
 *   poche, la taxe restant due.
 * - Un **avoir émis** retire son montant HT, à sa date, et rien ne lui est
 *   rendu en coût : on ne sait pas si le matériel crédité est revenu au camion,
 *   et supposer qu'il l'est gonflerait le chiffre — le bénéfice annoncé est le
 *   plus prudent des deux.
 * - Tout est **hors taxes** : la TVA collectée n'est pas à l'entreprise.
 *
 * Ce n'est **pas un résultat d'exploitation**, et l'écran le dit : ni le
 * véhicule, ni l'assurance, ni les charges sociales n'y sont — même limite que
 * [RentabiliteIntervention], et pour la même raison. Le coût horaire interne
 * n'est pas retiré non plus : une ligne de facture ne dit pas combien d'heures
 * elle porte, et la main-d'œuvre d'un artisan est précisément ce qu'il se paie.
 *
 * Rien n'est stocké : le chiffre se recompose à chaque lecture, comme le retard
 * d'une facture, et un total en base serait faux à la première facture émise.
 */
object CalculBenefice {

    /** Le bénéfice de chaque période, d'aujourd'hui à « depuis le début ». */
    fun parPeriode(
        factures: List<Facture>,
        lignes: List<LigneFacture>,
        base: BaseBenefice,
        aujourdhui: LocalDate,
    ): Map<PeriodeBenefice, Benefice> {
        val parFacture = lignes.groupBy { it.factureId }
        val comptees = factures.mapNotNull { f -> base.dateDe(f)?.let { f to it } }
        return PeriodeBenefice.entries.associateWith { periode ->
            comptees
                .filter { (_, le) -> periode.contient(le, aujourdhui) }
                .fold(Benefice()) { cumul, (f, _) -> cumul + apport(f, parFacture[f.id].orEmpty()) }
        }
    }

    /** Ce qu'une facture — ou un avoir — apporte au bénéfice. */
    fun apport(facture: Facture, lignes: List<LigneFacture>): Benefice {
        if (facture.estAvoir) {
            val credit = lignes.sumOf { it.montant.auCentime() }.auCentime()
            return Benefice(avoirs = credit, documents = 1)
        }
        var materiel = 0.0
        var mainOeuvre = 0.0
        lignes.forEach { l ->
            if (l.prixAchat > 0.0) {
                val vente = if (l.offerte) 0.0 else l.prixUnitaire
                materiel += ((vente - l.prixAchat) * l.quantite).auCentime()
            } else {
                mainOeuvre += l.montant.auCentime()
            }
        }
        val remise = TotauxDocument(lignes, facture.tauxTva, facture.assujettiTva, facture.tvaOfferte).remiseTva
        return Benefice(
            margeMateriel = materiel.auCentime(),
            mainOeuvre = mainOeuvre.auCentime(),
            tvaOfferte = remise,
            documents = 1,
        )
    }
}

/**
 * Le bénéfice d'une période, et ce qui le compose — la somme seule ne se
 * vérifie pas, et on la discute toujours ligne à ligne.
 */
data class Benefice(
    val margeMateriel: Double = 0.0,
    val mainOeuvre: Double = 0.0,
    val tvaOfferte: Double = 0.0,
    val avoirs: Double = 0.0,
    /** Factures et avoirs comptés : zéro dit « rien sur la période », pas « rien gagné ». */
    val documents: Int = 0,
) {
    val total: Double get() = (margeMateriel + mainOeuvre - tvaOfferte - avoirs).auCentime()

    operator fun plus(autre: Benefice) = Benefice(
        margeMateriel = (margeMateriel + autre.margeMateriel).auCentime(),
        mainOeuvre = (mainOeuvre + autre.mainOeuvre).auCentime(),
        tvaOfferte = (tvaOfferte + autre.tvaOfferte).auCentime(),
        avoirs = (avoirs + autre.avoirs).auCentime(),
        documents = documents + autre.documents,
    )
}

/**
 * Quand une facture compte : **émise**, à sa date d'émission — ce qu'on a
 * gagné —, ou **payée**, à sa date de règlement — ce qui est arrivé sur le
 * compte. Les deux répondent à deux questions, et c'est pourquoi l'écran laisse
 * choisir. Un brouillon ne compte jamais, une facture annulée non plus ; un avoir
 * émis compte à sa date d'émission dans les deux cas, faute de date de
 * remboursement.
 */
enum class BaseBenefice(val libelle: String) {
    FACTURE("Facturé"),
    ENCAISSE("Encaissé"),
    ;

    fun dateDe(facture: Facture): LocalDate? {
        if (facture.statut == StatutFacture.BROUILLON || facture.statut == StatutFacture.ANNULEE) return null
        if (facture.estAvoir) return facture.emiseLe
        return when (this) {
            FACTURE -> facture.emiseLe
            ENCAISSE -> facture.payeeLe.takeIf { facture.statut == StatutFacture.PAYEE }
        }
    }
}

/**
 * Les périodes, toutes **en cours** : la semaine part du lundi, le mois et
 * l'année de leur premier jour. Une période à venir n'a pas de sens pour ce qui
 * est déjà dans la poche, et la date du jour est donnée par l'appelant plutôt que
 * lue ici, pour que le calcul s'éprouve sans attendre minuit.
 */
enum class PeriodeBenefice(val libelle: String) {
    JOUR("Aujourd'hui"),
    SEMAINE("Cette semaine"),
    MOIS("Ce mois"),
    ANNEE("Cette année"),
    TOUT("Depuis le début"),
    ;

    fun contient(date: LocalDate, aujourdhui: LocalDate): Boolean {
        if (date.isAfter(aujourdhui)) return this == TOUT
        val debut = when (this) {
            JOUR -> aujourdhui
            SEMAINE -> aujourdhui.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MOIS -> aujourdhui.withDayOfMonth(1)
            ANNEE -> aujourdhui.withDayOfYear(1)
            TOUT -> return true
        }
        return !date.isBefore(debut)
    }
}
