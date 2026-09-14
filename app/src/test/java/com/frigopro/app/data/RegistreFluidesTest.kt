package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le registre des mouvements de fluide.
 *
 * Le test porte sur trois choses qui se paient devant un contrôle : la **date**
 * qu'une ligne affiche, ce qu'une ligne **ne perd pas** quand un lien est cassé,
 * et le tonnage équivalent CO₂ qui n'est **jamais deviné**.
 */
class RegistreFluidesTest {

    private val zone = ZoneId.of("Europe/Paris")

    private val client = Client(nom = "Boucherie Morel", ville = "Lyon")

    private val machine = Equipement(clientId = client.id, nom = "Chambre froide", fluide = "R-449A")

    private fun intervention(
        id: String,
        date: LocalDate,
        numero: String = "INT-2605-018",
    ) = Intervention(
        id = id,
        date = date,
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        equipementId = machine.id,
        equipementNom = machine.nom,
        numero = numero,
        technicienNom = "Karim B.",
    )

    private fun mouvement(
        interventionId: String,
        fluide: String = "R-449A",
        sens: SensFluide = SensFluide.AJOUT,
        masse: Double = 1.0,
        le: Instant = Instant.parse("2026-05-14T18:00:00Z"),
    ) = MouvementFluide(
        interventionId = interventionId,
        equipementId = machine.id,
        fluide = fluide,
        sens = sens,
        masseKg = masse,
        le = le,
    )

    /**
     * La date du registre est celle du **chantier**, pas celle de la saisie.
     *
     * C'est le point le plus facile à rater : `le` est posé par le dépôt au
     * moment d'écrire, et consigner sa tournée le soir — ou trois semaines plus
     * tard — daterait le mouvement du jour de la saisie. Un contrôle rapproche
     * le registre des fiches d'intervention, et deux dates qui ne se répondent
     * pas font ouvrir le dossier en grand.
     */
    @Test
    fun `la ligne porte la date de l'intervention, pas celle de la saisie`() {
        val tournee = intervention("i1", LocalDate.of(2026, 5, 14))
        val consigneLeLendemain = mouvement("i1", le = Instant.parse("2026-05-15T21:30:00Z"))

        val registre = RegistreFluides.pour(
            annee = 2026,
            mouvements = listOf(consigneLeLendemain),
            interventions = listOf(tournee),
            clients = listOf(client),
            equipements = listOf(machine),
            zone = zone,
        )

        assertEquals(LocalDate.of(2026, 5, 14), registre.lignes.single().date)
    }

    /**
     * Un mouvement orphelin reste au registre.
     *
     * Le cas vient d'une sauvegarde restaurée à moitié : l'intervention manque,
     * le mouvement est là. Le faire disparaître serait effacer des kilos qui ont
     * réellement bougé — une ligne imprécise vaut mieux qu'une ligne absente.
     */
    @Test
    fun `un mouvement sans intervention garde une date et reste au registre`() {
        val orphelin = mouvement("disparue", le = Instant.parse("2026-03-02T09:00:00Z"))

        val registre = RegistreFluides.pour(
            annee = 2026,
            mouvements = listOf(orphelin),
            interventions = emptyList(),
            zone = zone,
        )

        val ligne = registre.lignes.single()
        assertEquals(LocalDate.of(2026, 3, 2), ligne.date)
        assertEquals("", ligne.client)
        assertEquals(1.0, ligne.masseKg, 0.001)
    }

    @Test
    fun `seule l'annee demandee est retenue`() {
        val mouvements = listOf(mouvement("i1"), mouvement("i2"))
        val tournees = listOf(
            intervention("i1", LocalDate.of(2025, 12, 31)),
            intervention("i2", LocalDate.of(2026, 1, 2)),
        )

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        assertEquals(1, registre.lignes.size)
        assertEquals(LocalDate.of(2026, 1, 2), registre.lignes.single().date)
    }

    @Test
    fun `les annees proposees sont celles ou quelque chose a bouge`() {
        val mouvements = listOf(mouvement("i1"), mouvement("i2"), mouvement("i3"))
        val tournees = listOf(
            intervention("i1", LocalDate.of(2024, 6, 1)),
            intervention("i2", LocalDate.of(2026, 1, 2)),
            intervention("i3", LocalDate.of(2026, 5, 14)),
        )

        // La plus récente d'abord — c'est celle qu'un contrôle demande — et 2025
        // n'y est pas : proposer une année vide ferait ouvrir un registre blanc.
        assertEquals(listOf(2026, 2024), RegistreFluides.annees(mouvements, tournees, zone))
    }

    @Test
    fun `les totaux separent ce qui entre et ce qui sort`() {
        val mouvements = listOf(
            mouvement("i1", masse = 3.0),
            mouvement("i1", sens = SensFluide.RECUPERATION, masse = 1.0),
            mouvement("i1", fluide = "R-134a", masse = 0.5),
        )
        val tournees = listOf(intervention("i1", LocalDate.of(2026, 5, 14)))

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        val r449a = registre.totaux.first { it.fluide == "R-449A" }
        assertEquals(3.0, r449a.ajoute, 0.001)
        assertEquals(1.0, r449a.recupere, 0.001)
        assertEquals(2.0, r449a.net, 0.001)
        // Deux fluides, chacun son total : un registre qui les additionnerait
        // ne dirait plus rien d'un quota.
        assertEquals(2, registre.totaux.size)
    }

    /**
     * Un démantèlement sort plus de fluide qu'il n'en entre, et le net est alors
     * négatif. Le ramener à zéro aurait effacé ce qu'un contrôle cherche à voir.
     */
    @Test
    fun `le net peut etre negatif`() {
        val mouvements = listOf(
            mouvement("i1", sens = SensFluide.RECUPERATION, masse = 4.0),
            mouvement("i1", masse = 1.0),
        )
        val tournees = listOf(intervention("i1", LocalDate.of(2026, 5, 14)))

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        assertEquals(-3.0, registre.totaux.single().net, 0.001)
    }

    /**
     * L'équivalent CO₂ porte sur ce qui a été **ajouté**, et n'est jamais
     * deviné : c'est un chiffre qui sert à se situer sous un seuil
     * réglementaire, et un zéro inventé y placerait l'entreprise à tort.
     */
    @Test
    fun `l'equivalent CO2 se calcule sur l'ajoute et jamais sur l'inconnu`() {
        val mouvements = listOf(
            mouvement("i1", masse = 2.0),
            mouvement("i1", fluide = "R-inconnu", masse = 5.0),
        )
        val tournees = listOf(intervention("i1", LocalDate.of(2026, 5, 14)))

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        val connu = registre.totaux.first { it.fluide == "R-449A" }
        val attendu = Fluides.tonnesEquivalentCo2("R-449A", 2.0)
        assertEquals(attendu!!, connu.tonnesEqCo2Ajoutees!!, 0.001)

        val inconnu = registre.totaux.first { it.fluide != "R-449A" }
        assertNull(inconnu.gwp)
        assertNull(inconnu.tonnesEqCo2Ajoutees)
    }

    @Test
    fun `un fluide seulement recupere n'a pas d'equivalent CO2 ajoute`() {
        val mouvements = listOf(mouvement("i1", sens = SensFluide.RECUPERATION, masse = 2.0))
        val tournees = listOf(intervention("i1", LocalDate.of(2026, 5, 14)))

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        // Rien n'a été mis en circulation : compter le récupéré reviendrait à
        // compter deux fois le même kilo.
        assertNull(registre.totaux.single().tonnesEqCo2Ajoutees)
    }

    @Test
    fun `les lignes sont groupees par fluide et chronologiques dedans`() {
        val mouvements = listOf(
            mouvement("i2", fluide = "R-134a"),
            mouvement("i1", fluide = "R-449A"),
            mouvement("i2", fluide = "R-449A"),
            mouvement("i1", fluide = "R-134a"),
        )
        val tournees = listOf(
            intervention("i1", LocalDate.of(2026, 1, 5), numero = "INT-2601-001"),
            intervention("i2", LocalDate.of(2026, 9, 9), numero = "INT-2609-004"),
        )

        val registre = RegistreFluides.pour(2026, mouvements, tournees, zone = zone)

        // « R-134a » s'affiche « R-134A » : le suffixe d'isomère ne se met en
        // minuscule que sur les 6xx et les 1xxx (voir `Fluides.afficher`).
        assertEquals(
            listOf("R-134A", "R-134A", "R-449A", "R-449A"),
            registre.lignes.map { it.fluide },
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 9, 9),
            ),
            registre.lignes.map { it.date },
        )
    }

    @Test
    fun `une annee sans mouvement donne un registre vide mais reel`() {
        val registre = RegistreFluides.pour(2026, emptyList(), emptyList(), zone = zone)

        assertTrue(registre.vide)
        assertEquals(2026, registre.annee)
        assertTrue(registre.totaux.isEmpty())
    }
}
