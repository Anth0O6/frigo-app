package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * L'arithmétique du plan de maintenance.
 *
 * Elle s'éprouve sans base ni téléphone, comme les courbes de saturation et
 * l'arithmétique de la réduction de photo : rien ici ne touche à Room, et c'est
 * ce qui permet de tenir les cas de bord des dates — ceux qui ne se voient qu'en
 * février, ou au bout de trois ans.
 */
class MaintenanceTest {

    /**
     * **Le test qui justifie tout le fichier.** Une visite trimestrielle faite le
     * 31 janvier tombe le 30 avril : `plusMonths` ramène au dernier jour valide du
     * mois. La compter en quatre-vingt-dix jours l'aurait fait tomber le 1ᵉʳ mai.
     */
    @Test
    fun `un trimestre depuis le 31 janvier se ramene au 30 avril`() {
        val echeance = Periodicite.TRIMESTRIEL.prochaine(LocalDate.of(2026, 1, 31))

        assertEquals(LocalDate.of(2026, 4, 30), echeance)
    }

    @Test
    fun `un mois depuis le 31 janvier se ramene a la fin de fevrier`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            Periodicite.MENSUEL.prochaine(LocalDate.of(2026, 1, 31)),
        )
    }

    /** Le 31 janvier reste le 31 janvier d'une année sur l'autre. */
    @Test
    fun `une annee depuis le 31 janvier garde le jour`() {
        assertEquals(
            LocalDate.of(2027, 1, 31),
            Periodicite.ANNUEL.prochaine(LocalDate.of(2026, 1, 31)),
        )
    }

    /** Les deux cadences courtes avancent en jours, et franchissent les mois. */
    @Test
    fun `les cadences courtes franchissent les mois et les annees`() {
        assertEquals(
            LocalDate.of(2026, 3, 1),
            Periodicite.JOURNALIER.prochaine(LocalDate.of(2026, 2, 28)),
        )
        assertEquals(
            LocalDate.of(2027, 1, 4),
            Periodicite.HEBDOMADAIRE.prochaine(LocalDate.of(2026, 12, 28)),
        )
    }

    /**
     * **Aucune dérive.** Quatre trimestres depuis le 1ᵉʳ janvier retombent
     * exactement sur le 1ᵉʳ janvier suivant, soit trois cent soixante-cinq jours.
     * Quatre paquets de quatre-vingt-dix jours en auraient fait trois cent
     * soixante : cinq jours d'avance par an, quinze au bout de trois ans — et sur
     * un contrat qui se contrôle, cette avance se voit.
     */
    @Test
    fun `quatre trimestres retombent sur le jour, sans deriver`() {
        val depart = LocalDate.of(2026, 1, 1)
        var courante = depart
        repeat(4) { courante = Periodicite.TRIMESTRIEL.prochaine(courante) }

        assertEquals(LocalDate.of(2027, 1, 1), courante)
    }

    /** Chaque périodicité porte un pas, et un seul. */
    @Test
    fun `chaque periodicite avance vraiment`() {
        val jour = LocalDate.of(2026, 6, 15)
        Periodicite.entries.forEach { periodicite ->
            assertTrue(
                "${periodicite.name} n'avance pas",
                periodicite.prochaine(jour).isAfter(jour),
            )
            assertTrue(periodicite.libelle.isNotBlank())
        }
    }

    @Test
    fun `l'echeance suit la derniere visite quand il y en a une`() {
        val echeance = Maintenance.echeance(
            derniereVisite = LocalDate.of(2026, 5, 10),
            depuisLe = LocalDate.of(2020, 1, 1),
            periodicite = Periodicite.MENSUEL,
        )

        assertEquals(LocalDate.of(2026, 6, 10), echeance)
    }

    /**
     * Sans visite, le plan part de son affectation et non de la mise en service.
     * C'est ce qui rend l'écran utilisable le jour où on le met en route : une
     * machine de 2015 rattachée aujourd'hui à une visite mensuelle doit une visite
     * dans un mois, et non cent trente depuis dix ans.
     */
    @Test
    fun `sans visite l'echeance part de l'affectation`() {
        val echeance = Maintenance.echeance(
            derniereVisite = null,
            depuisLe = LocalDate.of(2026, 6, 1),
            periodicite = Periodicite.MENSUEL,
        )

        assertEquals(LocalDate.of(2026, 7, 1), echeance)
    }

    /**
     * Reculer le départ à la main affiche un vrai retard, et c'est une
     * information : le plan dit qu'une visite était due, et elle ne l'a pas été.
     */
    @Test
    fun `un depart recule affiche le retard qu'il implique`() {
        val echeance = Maintenance.echeance(
            derniereVisite = null,
            depuisLe = LocalDate.of(2026, 1, 1),
            periodicite = Periodicite.MENSUEL,
        )
        val aujourdhui = LocalDate.of(2026, 6, 1)

        assertEquals(LocalDate.of(2026, 2, 1), echeance)
        assertEquals(StatutEcheance.EN_RETARD, Maintenance.statut(echeance, Periodicite.MENSUEL, aujourdhui))
        assertEquals(120L, Maintenance.joursDeRetard(echeance, aujourdhui))
    }

    /**
     * La ronde du matin n'a aucun préavis : demain n'est pas à faire aujourd'hui,
     * sinon la liste du jour porterait toujours le lendemain.
     */
    @Test
    fun `une ronde journaliere ne s'annonce pas la veille`() {
        val aujourdhui = LocalDate.of(2026, 6, 1)

        assertEquals(
            StatutEcheance.A_VENIR,
            Maintenance.statut(LocalDate.of(2026, 6, 2), Periodicite.JOURNALIER, aujourdhui),
        )
        assertEquals(
            StatutEcheance.A_FAIRE,
            Maintenance.statut(LocalDate.of(2026, 6, 1), Periodicite.JOURNALIER, aujourdhui),
        )
        assertEquals(
            StatutEcheance.EN_RETARD,
            Maintenance.statut(LocalDate.of(2026, 5, 31), Periodicite.JOURNALIER, aujourdhui),
        )
    }

    /**
     * Une visite annuelle s'annonce un mois avant, parce qu'elle se prépare — il
     * faut une pièce, un créneau, parfois un arrêt de production. Trois jours de
     * préavis n'auraient servi à personne.
     */
    @Test
    fun `une visite annuelle s'annonce un mois avant`() {
        val aujourdhui = LocalDate.of(2026, 6, 1)

        assertEquals(
            StatutEcheance.A_FAIRE,
            Maintenance.statut(LocalDate.of(2026, 6, 21), Periodicite.ANNUEL, aujourdhui),
        )
        assertEquals(
            StatutEcheance.A_VENIR,
            Maintenance.statut(LocalDate.of(2026, 7, 11), Periodicite.ANNUEL, aujourdhui),
        )
    }

    /** Le préavis croît avec la période : on ne prévient pas d'une ronde un mois avant. */
    @Test
    fun `le preavis croit avec la periode`() {
        val preavis = Periodicite.entries.map { it.preavisJours }

        assertEquals(preavis.sorted(), preavis)
        assertEquals(0L, Periodicite.JOURNALIER.preavisJours)
    }

    @Test
    fun `aucun retard quand l'echeance n'est pas passee`() {
        val aujourdhui = LocalDate.of(2026, 6, 1)

        assertEquals(0L, Maintenance.joursDeRetard(aujourdhui, aujourdhui))
        assertEquals(0L, Maintenance.joursDeRetard(LocalDate.of(2026, 7, 1), aujourdhui))
    }

    @Test
    fun `douze visites mensuelles sont attendues sur l'annee`() {
        val attendues = Maintenance.occurrencesAttendues(
            depuisLe = LocalDate.of(2026, 1, 15),
            debut = LocalDate.of(2026, 1, 1),
            fin = LocalDate.of(2027, 1, 15),
            periodicite = Periodicite.MENSUEL,
        )

        assertEquals(12, attendues)
    }

    @Test
    fun `quatre visites trimestrielles sont attendues sur l'annee`() {
        assertEquals(
            4,
            Maintenance.occurrencesAttendues(
                depuisLe = LocalDate.of(2026, 1, 1),
                debut = LocalDate.of(2026, 1, 1),
                fin = LocalDate.of(2027, 1, 1),
                periodicite = Periodicite.TRIMESTRIEL,
            ),
        )
        assertEquals(
            1,
            Maintenance.occurrencesAttendues(
                depuisLe = LocalDate.of(2026, 1, 1),
                debut = LocalDate.of(2026, 1, 1),
                fin = LocalDate.of(2027, 1, 1),
                periodicite = Periodicite.ANNUEL,
            ),
        )
    }

    /** La fenêtre écarte ce qui la précède : un taux porte sur une période. */
    @Test
    fun `la fenetre ecarte les occurrences qui la precedent`() {
        val attendues = Maintenance.occurrencesAttendues(
            depuisLe = LocalDate.of(2026, 1, 1),
            debut = LocalDate.of(2026, 7, 1),
            fin = LocalDate.of(2026, 12, 31),
            periodicite = Periodicite.MENSUEL,
        )

        assertEquals(6, attendues)
    }

    @Test
    fun `une fenetre inversee n'attend rien`() {
        assertEquals(
            0,
            Maintenance.occurrencesAttendues(
                depuisLe = LocalDate.of(2026, 1, 1),
                debut = LocalDate.of(2026, 6, 1),
                fin = LocalDate.of(2026, 5, 1),
                periodicite = Periodicite.MENSUEL,
            ),
        )
    }

    /**
     * Une ronde journalière sur dix ans fait des milliers de pas, et le garde-fou
     * doit la laisser passer : il n'est là que pour une périodicité qui
     * n'avancerait pas, ce qui ne doit pas arriver.
     */
    @Test
    fun `une ronde journaliere sur un an se compte sans butoir`() {
        val attendues = Maintenance.occurrencesAttendues(
            depuisLe = LocalDate.of(2026, 1, 1),
            debut = LocalDate.of(2026, 1, 1),
            fin = LocalDate.of(2026, 12, 31),
            periodicite = Periodicite.JOURNALIER,
        )

        // Du 2 janvier au 31 décembre inclus : 364 jours.
        assertEquals(364, attendues)
        assertTrue(attendues < Maintenance.PAS_MAXIMUM)
    }

    /**
     * Rien d'attendu ne vaut **pas** zéro pour cent : une gamme affectée la
     * semaine dernière n'a rien à montrer, et « 0 % » la ferait passer pour
     * négligée. Même règle que l'équivalent CO₂ d'un fluide hors catalogue.
     */
    @Test
    fun `rien d'attendu ne donne aucun taux, et non zero`() {
        assertNull(RealisationGamme(attendues = 0, faites = 0).taux)
        assertNull(RealisationGamme(attendues = 0, faites = 3).taux)
    }

    @Test
    fun `le taux compte les faites sur les attendues`() {
        val realisation = RealisationGamme(attendues = 26, faites = 23)

        assertEquals(23.0 / 26.0, requireNotNull(realisation.taux), 0.0001)
    }

    /**
     * On peut faire plus de visites que le plan n'en demande — une machine qui
     * inquiète se regarde plus souvent. Le taux brut le dit, le plafonné sert à
     * l'affichage : « 130 % de réalisation » ne veut rien dire sur un contrat.
     */
    @Test
    fun `plus de visites que prevu plafonne a cent pour cent`() {
        val realisation = RealisationGamme(attendues = 10, faites = 13)

        assertEquals(1.3, requireNotNull(realisation.taux), 0.0001)
        assertEquals(1.0, requireNotNull(realisation.tauxPlafonne), 0.0001)
    }
}
