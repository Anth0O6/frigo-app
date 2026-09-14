package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Les factures.
 *
 * Les cas retenus sont ceux où une erreur ne se voit qu'une fois le document
 * chez le client, ou pire, devant un contrôle : un trou dans la numérotation,
 * une facture modifiée après émission, une intervention facturée deux fois.
 */
class FactureRepositoryTest {

    private val dao = FauxFactureDao()
    private val depot = FactureRepository(dao)

    private val reglages = Parametres(
        tauxHoraire = 60.0,
        tauxTva = 20.0,
        delaiPaiementJours = 30,
    )

    // — La numérotation ————————————————————————————————————————————————

    /**
     * Le cœur du dispositif : un brouillon ne consomme aucun rang. Sans cela,
     * jeter un brouillon laisserait un trou, et une numérotation de facture n'en
     * tolère pas.
     */
    @Test
    fun `un brouillon n'a pas de numero, et n'en consomme aucun`() = runTest {
        val brouillon = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)

        assertEquals("", brouillon.numero)
        assertFalse(brouillon.numerotee)
        assertEquals(StatutFacture.BROUILLON, brouillon.statut)

        assertTrue("un brouillon se jette", depot.supprimer(brouillon))

        val suivante = depot.creerDepuisIntervention(
            intervention(id = "int-2"), pieces(), emptyList(), reglages,
        )
        val emise = depot.emettre(suivante, reglages, LE_12_MARS)
        assertEquals(
            "le rang abandonné ne doit pas manquer : il n'a jamais été pris",
            "FAC-2026-0001",
            emise.numero,
        )
    }

    @Test
    fun `les numeros se suivent dans l'annee`() = runTest {
        val numeros = (1..3).map { rang ->
            val brouillon = depot.creerDepuisIntervention(
                intervention(id = "int-$rang"), pieces(), emptyList(), reglages,
            )
            depot.emettre(brouillon, reglages, LE_12_MARS).numero
        }

        assertEquals(listOf("FAC-2026-0001", "FAC-2026-0002", "FAC-2026-0003"), numeros)
    }

    /**
     * Un second appui sur « Émettre » ne doit pas donner un second numéro : le
     * premier resterait orphelin, et c'est le trou qu'on cherche à éviter.
     */
    @Test
    fun `emettre deux fois n'attribue qu'un numero`() = runTest {
        val brouillon = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)
        val emise = depot.emettre(brouillon, reglages, LE_12_MARS)
        val reemise = depot.emettre(emise, reglages, LE_12_MARS.plusDays(1))

        assertEquals(emise.numero, reemise.numero)
        assertEquals("et la date d'émission ne bouge pas non plus", LE_12_MARS, reemise.emiseLe)
    }

    /**
     * Une facture émise ne se supprime pas : elle s'annule **en gardant son
     * numéro**. L'effacer creuserait le trou.
     */
    @Test
    fun `une facture numerotee ne se supprime pas, elle s'annule`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )

        assertFalse("une pièce comptable ne s'efface pas", depot.supprimer(emise))
        assertEquals(1, dao.contenu.size)

        val annulee = depot.annuler(emise)
        assertEquals(StatutFacture.ANNULEE, annulee.statut)
        assertEquals("le numéro reste consommé", "FAC-2026-0001", annulee.numero)
    }

    // — Ce qui fige ————————————————————————————————————————————————————

    @Test
    fun `les lignes d'une facture emise ne bougent plus`() = runTest {
        val brouillon = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)
        assertNotNull(
            "sur un brouillon, ajouter une ligne fonctionne",
            depot.ajouterLigne(brouillon, "Déplacement", 1.0, "", 45.0),
        )

        val emise = depot.emettre(brouillon, reglages, LE_12_MARS)
        val avant = dao.contenuLignes.size

        assertNull(depot.ajouterLigne(emise, "Ligne ajoutée après coup", 1.0, "", 100.0))
        assertFalse(depot.supprimerLigne(emise, dao.contenuLignes.first().id))
        assertNull(depot.enregistrer(emise.copy(objet = "Autre objet")))

        assertEquals("rien n'a été écrit", avant, dao.contenuLignes.size)
        assertEquals("l'objet non plus", "Fuite de fluide — INT-2603-001", dao.contenu.single().objet)
    }

    // — L'échéance et les relances ————————————————————————————————————

    @Test
    fun `l'echeance decoule du delai, et ne bouge plus ensuite`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )

        assertEquals(LE_12_MARS.plusDays(30), emise.echeanceLe)

        // Changer le délai des réglages ne doit pas déplacer l'échéance d'une
        // facture déjà partie : elle est imprimée dessus.
        val apres = depot.marquerRelancee(emise, LE_12_MARS.plusDays(45))
        assertEquals(LE_12_MARS.plusDays(30), apres.echeanceLe)
    }

    @Test
    fun `une facture n'est en retard qu'apres son echeance`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val echeance = emise.echeanceLe!!

        assertFalse("le jour de l'échéance, rien n'est dû en retard", emise.enRetard(echeance))
        assertTrue(emise.enRetard(echeance.plusDays(1)))
        assertEquals(1L, emise.joursDeRetard(echeance.plusDays(1)))
        assertNull(emise.joursDeRetard(echeance))
    }

    @Test
    fun `une facture payee ne se relance plus`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val tard = emise.echeanceLe!!.plusDays(10)

        assertTrue(emise.aRelancer(tard))

        val payee = depot.marquerPayee(emise, tard)
        assertFalse(payee.enRetard(tard))
        assertFalse(payee.aRelancer(tard))
        assertEquals(emptyList<FactureChiffree>(), depot.aRelancer(tard).first())
    }

    /**
     * On coche vite. Une facture marquée payée par erreur ne doit pas devenir
     * invisible pour toujours.
     */
    @Test
    fun `une facture marquee payee par erreur redevient exigible`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val tard = emise.echeanceLe!!.plusDays(10)

        val rouverte = depot.marquerImpayee(depot.marquerPayee(emise, tard))

        assertEquals(StatutFacture.EMISE, rouverte.statut)
        assertNull(rouverte.payeeLe)
        assertTrue(rouverte.enRetard(tard))
    }

    @Test
    fun `une relance recente ne se repete pas le lendemain`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val tard = emise.echeanceLe!!.plusDays(10)
        val relancee = depot.marquerRelancee(emise, tard)

        assertFalse("relancer tous les jours fâche un client qui a un virement en route",
            relancee.aRelancer(tard.plusDays(1)))
        assertTrue("mais au bout d'une semaine, si", relancee.aRelancer(tard.plusDays(7)))
    }

    // — Ce qu'une intervention devient ————————————————————————————————

    @Test
    fun `une intervention devient temps, pieces et fluide`() = runTest {
        val facture = depot.creerDepuisIntervention(
            intervention = intervention(),
            pieces = pieces(),
            mouvements = listOf(
                MouvementFluide(interventionId = "int-1", fluide = "R449A", sens = SensFluide.AJOUT, masseKg = 1.5),
                MouvementFluide(interventionId = "int-1", fluide = "R-449A", sens = SensFluide.AJOUT, masseKg = 0.8),
                MouvementFluide(interventionId = "int-1", fluide = "R449A", sens = SensFluide.RECUPERATION, masseKg = 3.0),
            ),
            parametres = reglages,
        )

        val lignes = dao.contenuLignes.sortedBy { it.rang }
        assertEquals(
            listOf("Main-d'œuvre", "Détendeur (DET-42)", "Fluide R-449A"),
            lignes.map { it.designation },
        )
        // 1 h 47 chronométrées : la quantité s'imprime à deux décimales et doit
        // valoir ce que la ligne annonce.
        assertEquals(1.78, lignes[0].quantite, 0.001)
        assertEquals(60.0, lignes[0].prixUnitaire, 0.001)
        assertEquals(
            "les deux compléments de charge font une ligne, et les 3 kg repris n'en font aucune",
            2.3,
            lignes[2].quantite,
            0.001,
        )
        assertEquals("kg", lignes[2].unite)
    }

    /**
     * Facturer deux fois la même intervention est l'erreur qui ne se voit qu'au
     * moment où le client reçoit la seconde.
     */
    @Test
    fun `une intervention ne se facture qu'une fois`() = runTest {
        val premiere = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)
        val seconde = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)

        assertEquals(premiere.id, seconde.id)
        assertEquals(1, dao.contenu.size)
    }

    @Test
    fun `une intervention sans temps ni piece ne fabrique aucune ligne`() = runTest {
        depot.creerDepuisIntervention(
            intervention(secondes = 0L), emptyList(), emptyList(), reglages,
        )

        assertTrue(dao.contenuLignes.isEmpty())
    }

    // — Ce qu'un devis devient ————————————————————————————————————————

    /**
     * Le client a accepté des montants. La facture doit retomber dessus au
     * centime, sinon c'est une facture qu'on discute.
     */
    @Test
    fun `un devis accepte devient une facture au meme montant`() = runTest {
        val devis = Devis(
            id = "dev-1",
            numero = "DEV-2603-004",
            clientNom = "Boucherie Morel",
            objet = "Remplacement compresseur",
            tauxTva = 20.0,
        )
        val lignes = listOf(
            LigneDevis(devisId = "dev-1", designation = "Compresseur", quantite = 1.0, prixUnitaire = 890.0, rang = 0),
            LigneDevis(devisId = "dev-1", designation = "Main-d'œuvre", quantite = 3.5, unite = "h", prixUnitaire = 60.0, rang = 1),
            LigneDevis(devisId = "dev-1", designation = "Mise en service", quantite = 1.0, prixUnitaire = 120.0, offerte = true, rang = 2),
            LigneDevis(devisId = "dev-1", designation = "Déplacement", quantite = 42.0, unite = "km", prixUnitaire = 0.45, deplacement = true, rang = 3),
        )
        val complet = DevisComplet(devis, lignes)

        val facture = depot.creerDepuisDevis(complet, reglages)
        val posees = dao.contenuLignes.sortedBy { it.rang }

        assertEquals("dev-1", facture.devisId)
        assertEquals(complet.lignes.size, posees.size)
        assertEquals(
            FactureComplete(facture, posees).totalTtc,
            complet.totalTtc,
            0.001,
        )
        assertTrue("le geste commercial traverse", posees[2].offerte)
        assertEquals("l'ordre de rédaction est conservé", "Déplacement", posees[3].designation)
    }

    @Test
    fun `un devis ne se facture qu'une fois`() = runTest {
        val complet = DevisComplet(Devis(id = "dev-1", clientNom = "Morel"), emptyList())

        val premiere = depot.creerDepuisDevis(complet, reglages)
        val seconde = depot.creerDepuisDevis(complet, reglages)

        assertEquals(premiere.id, seconde.id)
        assertEquals(1, dao.contenu.size)
    }

    /** Le régime vient du devis, pas des réglages du jour : c'est celui accepté. */
    @Test
    fun `le regime de TVA du devis traverse jusqu'a la facture`() = runTest {
        val devis = Devis(id = "dev-1", tauxTva = 10.0, assujettiTva = false, tvaOfferte = true)

        val facture = depot.creerDepuisDevis(DevisComplet(devis, emptyList()), reglages)

        assertEquals(10.0, facture.tauxTva, 0.001)
        assertFalse(facture.assujettiTva)
        assertTrue(facture.tvaOfferte)
    }

    /**
     * Ce qu'un retard fait courir, et ce qu'il ne fait pas.
     *
     * Le montant grandit chaque jour : il ne peut donc pas être stocké, et c'est
     * la même règle que « en retard » ou que les échéances F-Gas.
     */
    @Test
    fun `les penalites se calculent sur le ttc et le nombre de jours`() {
        val facture = Facture(
            statut = StatutFacture.EMISE,
            echeanceLe = LocalDate.of(2026, 3, 1),
            tauxPenalites = 12.0,
        )

        val dues = PenalitesDues.de(facture, totalTtc = 1200.0, aujourdhui = LocalDate.of(2026, 4, 1))!!

        assertEquals(31L, dues.joursDeRetard)
        // 1 200 € × 12 % × 31 / 365 = 12,23 €.
        assertEquals(12.23, dues.interets!!, 0.005)
        assertEquals(Facture.INDEMNITE_RECOUVREMENT, dues.indemnite, 0.001)
        assertEquals(52.23, dues.total!!, 0.005)
    }

    /**
     * Sans taux convenu, les intérêts ne sont pas chiffrés — et l'indemnité
     * reste due.
     *
     * Le taux légal s'applique alors de plein droit, mais c'est celui de la BCE
     * majoré de dix points : il varie dans le temps et n'est pas dans le
     * téléphone. Inventer le chiffre reviendrait à le réclamer à un vrai client.
     */
    @Test
    fun `sans taux convenu, les interets ne sont pas devines`() {
        val facture = Facture(
            statut = StatutFacture.EMISE,
            echeanceLe = LocalDate.of(2026, 3, 1),
        )

        val dues = PenalitesDues.de(facture, totalTtc = 1200.0, aujourdhui = LocalDate.of(2026, 4, 1))!!

        assertNull("aucun taux, aucun intérêt chiffré", dues.tauxAnnuel)
        assertNull(dues.interets)
        assertNull("ni de total, donc", dues.total)
        assertEquals(
            "mais l'indemnité forfaitaire, elle, est fixée par la loi",
            40.0,
            dues.indemnite,
            0.001,
        )
    }

    /** Une facture payée, ou pas encore échue, ne fait courir aucune pénalité. */
    @Test
    fun `rien ne court sur une facture qui n'est pas en retard`() {
        val payee = Facture(
            statut = StatutFacture.PAYEE,
            echeanceLe = LocalDate.of(2026, 3, 1),
            tauxPenalites = 12.0,
        )
        val aEcheoir = Facture(
            statut = StatutFacture.EMISE,
            echeanceLe = LocalDate.of(2026, 4, 30),
            tauxPenalites = 12.0,
        )
        val avril = LocalDate.of(2026, 4, 1)

        assertNull(PenalitesDues.de(payee, 1200.0, avril))
        assertNull(PenalitesDues.de(aEcheoir, 1200.0, avril))
    }

    private fun intervention(id: String = "int-1", secondes: Long = 6420L) = Intervention(
        id = id,
        date = LocalDate.of(2026, 3, 12),
        heure = LocalTime.of(9, 0),
        client = "Boucherie Morel",
        ville = "Lyon",
        typeLibelle = "Fuite de fluide",
        numero = "INT-2603-001",
        statut = StatutIntervention.TERMINEE,
        chrono = Chrono(arriveeLe = Instant.parse("2026-03-12T08:00:00Z"), cumuleS = secondes),
    )

    private fun pieces() = listOf(
        PiecePosee(
            interventionId = "int-1",
            designation = "Détendeur",
            reference = "DET-42",
            quantite = 1.0,
            prixUnitaire = 78.5,
        ),
    )

    private companion object {
        val LE_12_MARS: LocalDate = LocalDate.of(2026, 3, 12)
    }
}
