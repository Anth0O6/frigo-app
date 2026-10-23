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
     * En sous-traitance, la facture part chez le **donneur d'ordre**.
     *
     * Et elle dit où le travail a eu lieu : X reçoit des factures pour des
     * chantiers qu'il n'a pas vus, et « Fuite de fluide — INT-2605-018 » ne lui
     * dit pas lequel.
     */
    @Test
    fun `la facture d'une sous-traitance part chez le donneur d'ordre`() = runTest {
        val travail = intervention().copy(
            clientId = "cl-site",
            client = "Boucherie Morel",
            clientFactureId = "cl-donneur",
            clientFactureNom = "Froid Services",
        )

        val facture = depot.creerDepuisIntervention(
            intervention = travail,
            pieces = emptyList(),
            mouvements = emptyList(),
            parametres = Parametres(),
            client = Client(
                id = "cl-donneur",
                nom = "Froid Services",
                ville = "Villeurbanne",
                adresse = "8 avenue Roosevelt",
            ),
        )

        assertEquals("cl-donneur", facture.clientId)
        assertEquals("Froid Services", facture.clientNom)
        assertEquals(
            "c'est l'adresse de celui qui paie qui s'imprime",
            "8 avenue Roosevelt, Villeurbanne",
            facture.clientAdresse,
        )
        assertTrue(
            "et l'objet dit chez qui on est allé : ${facture.objet}",
            facture.objet.contains("Boucherie Morel"),
        )
    }

    /** Hors sous-traitance, rien ne change : l'objet ne répète pas le destinataire. */
    @Test
    fun `sans sous-traitance, l'objet ne nomme pas le lieu`() = runTest {
        val travail = intervention().copy(clientId = "cl-1", client = "Boucherie Morel")

        val facture = depot.creerDepuisIntervention(
            intervention = travail,
            pieces = emptyList(),
            mouvements = emptyList(),
            parametres = Parametres(),
            client = Client(id = "cl-1", nom = "Boucherie Morel", ville = "Lyon"),
        )

        assertEquals("cl-1", facture.clientId)
        assertEquals("Boucherie Morel", facture.clientNom)
        assertFalse(
            "le destinataire était sur place : ${facture.objet}",
            facture.objet.contains("chez"),
        )
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

    // — L'avoir ——————————————————————————————————————————————————————————

    /**
     * Un avoir ne corrige que ce qui est parti.
     *
     * Un brouillon se corrige ou s'efface ; une facture annulée ne réclame déjà
     * plus rien ; et un avoir erroné s'annule — il ne se corrige pas par un avoir
     * d'avoir.
     */
    @Test
    fun `un avoir ne nait que d'une facture emise`() = runTest {
        val brouillon = depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages)
        assertNull("un brouillon se corrige : rien n'est parti à créditer", depot.creerAvoir(brouillon))

        val emise = depot.emettre(brouillon, reglages, LE_12_MARS)
        val avoir = depot.creerAvoir(emise)
        assertNotNull(avoir)
        assertEquals(NatureFacture.AVOIR, avoir!!.nature)
        assertEquals("il désigne sa facture", emise.id, avoir.factureOrigineId)
        assertEquals("et en recopie la référence", "FAC-2026-0001", avoir.factureOrigineNumero)
        assertEquals(LE_12_MARS, avoir.factureOrigineEmiseLe)

        val avoirEmis = depot.emettre(avoir, reglages, LE_12_MARS)
        assertNull("pas d'avoir d'avoir", depot.creerAvoir(avoirEmis))

        val autre = depot.emettre(
            depot.creerDepuisIntervention(intervention(id = "int-2"), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        assertNull("une facture annulée ne réclame plus rien", depot.creerAvoir(depot.annuler(autre)))
    }

    /**
     * L'avoir ne reprend ni l'intervention ni le devis : ce sont eux qui
     * empêchent de facturer deux fois, et un avoir qui les porterait passerait
     * pour leur facture.
     */
    @Test
    fun `un avoir ne passe pas pour la facture de l'intervention`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val avoir = depot.creerAvoir(emise)!!

        assertNull(avoir.interventionId)
        assertNull(avoir.devisId)
        assertEquals(emise.id, dao.pourIntervention("int-1")?.id)
    }

    /** Même règle que la facture : le rang ne se consomme qu'à l'émission. */
    @Test
    fun `un brouillon d'avoir ne consomme aucun rang`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val abandonne = depot.creerAvoir(emise)!!

        assertEquals("", abandonne.numero)
        assertTrue("un brouillon d'avoir se jette", depot.supprimer(abandonne))

        val avoir = depot.emettre(depot.creerAvoir(emise)!!, reglages, LE_12_MARS)
        assertEquals(
            "le rang abandonné ne doit pas manquer : il n'a jamais été pris",
            "AV-2026-0001",
            avoir.numero,
        )
    }

    /**
     * Deux séquences dans la même colonne, qui ne se voient pas : un avoir
     * n'interrompt pas la numérotation des factures, et l'inverse.
     */
    @Test
    fun `les avoirs ont leur propre sequence, continue, sans toucher a celle des factures`() =
        runTest {
            val premiere = depot.emettre(
                depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
                reglages,
                LE_12_MARS,
            )
            val avoir1 = depot.emettre(depot.creerAvoir(premiere)!!, reglages, LE_12_MARS)
            val seconde = depot.emettre(
                depot.creerDepuisIntervention(intervention(id = "int-2"), pieces(), emptyList(), reglages),
                reglages,
                LE_12_MARS,
            )
            val avoir2 = depot.emettre(depot.creerAvoir(seconde)!!, reglages, LE_12_MARS)

            assertEquals(listOf("FAC-2026-0001", "FAC-2026-0002"), listOf(premiere.numero, seconde.numero))
            assertEquals(listOf("AV-2026-0001", "AV-2026-0002"), listOf(avoir1.numero, avoir2.numero))
            assertNull("un avoir ne réclame rien : il n'a pas d'échéance", avoir1.echeanceLe)
            assertEquals(StatutFacture.EMISE, avoir1.statut)
        }

    @Test
    fun `un avoir emis ne se supprime pas et ne bouge plus`() = runTest {
        val emise = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val avoir = depot.emettre(depot.creerAvoir(emise)!!, reglages, LE_12_MARS)
        val lignesAvant = dao.lignesDe(avoir.id)

        assertFalse("une pièce comptable ne s'efface pas", depot.supprimer(avoir))
        assertNull(depot.ajouterLigne(avoir, "Ligne ajoutée après coup", 1.0, "", 10.0))
        assertFalse(depot.supprimerLigne(avoir, lignesAvant.first().id))
        assertNull(depot.enregistrer(avoir.copy(objet = "Autre objet")))
        assertEquals("rien n'a bougé", lignesAvant, dao.lignesDe(avoir.id))

        val reemis = depot.emettre(avoir, reglages, LE_12_MARS.plusDays(1))
        assertEquals("réémettre ne donne pas un second numéro", avoir.numero, reemis.numero)
        assertEquals(
            "et un avoir ne se « paie » pas",
            StatutFacture.EMISE,
            depot.marquerPayee(avoir, LE_12_MARS).statut,
        )
        assertEquals("il s'annule, en gardant son numéro", "AV-2026-0001", depot.annuler(avoir).numero)
    }

    /**
     * L'avoir total reprend la facture **à l'identique** — gestes commerciaux,
     * prix d'achat, régime de TVA — et retombe dessus au centime : c'est le
     * même [TotauxDocument] qui les compte.
     */
    @Test
    fun `l'avoir total reprend les lignes a l'identique et retombe au centime`() = runTest {
        val devis = Devis(id = "dev-1", numero = "DEV-2603-004", clientNom = "Boucherie Morel", tauxTva = 20.0)
        val lignesDevis = listOf(
            LigneDevis(devisId = "dev-1", designation = "Compresseur", quantite = 1.0, prixUnitaire = 890.0, prixAchat = 610.0, rang = 0),
            LigneDevis(devisId = "dev-1", designation = "Main-d'œuvre", quantite = 3.5, unite = "h", prixUnitaire = 60.0, rang = 1),
            LigneDevis(devisId = "dev-1", designation = "Mise en service", quantite = 1.0, prixUnitaire = 120.0, offerte = true, rang = 2),
            LigneDevis(devisId = "dev-1", designation = "Déplacement", quantite = 42.0, unite = "km", prixUnitaire = 0.45, rang = 3),
        )
        val facture = depot.emettre(
            depot.creerDepuisDevis(DevisComplet(devis.copy(tvaOfferte = true), lignesDevis), reglages),
            reglages,
            LE_12_MARS,
        )
        val avoir = depot.creerAvoir(facture)!!

        val lignesFacture = dao.lignesDe(facture.id)
        val lignesAvoir = dao.lignesDe(avoir.id)
        assertEquals(
            "les mêmes lignes, dans le même ordre, au même prix",
            lignesFacture.map { listOf(it.designation, it.quantite, it.unite, it.prixUnitaire, it.offerte, it.prixAchat, it.rang) },
            lignesAvoir.map { listOf(it.designation, it.quantite, it.unite, it.prixUnitaire, it.offerte, it.prixAchat, it.rang) },
        )
        assertTrue("des lignes à lui, et non celles de la facture", lignesAvoir.none { it.factureId == facture.id })
        assertTrue("le régime de TVA de la facture traverse", avoir.tvaOfferte)

        val totalFacture = FactureComplete(facture, lignesFacture)
        val totalAvoir = FactureComplete(avoir, lignesAvoir)
        assertEquals(totalFacture.totalHt, totalAvoir.totalHt, 0.0)
        assertEquals(totalFacture.totalTtc, totalAvoir.totalTtc, 0.0)
        assertEquals(totalFacture.gestesCommerciaux, totalAvoir.gestesCommerciaux, 0.0)
    }

    /**
     * Le total des avoirs d'une facture ne la dépasse jamais, et la garde est
     * dans le dépôt : à la création, brouillons comptés, et à l'émission, qui
     * tranche.
     */
    @Test
    fun `la garde refuse de crediter plus que la facture`() = runTest {
        val facture = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val lignes = dao.lignesDe(facture.id)
        val mainDOeuvre = lignes.first { it.designation == "Main-d'œuvre" }
        val detendeur = lignes.first { it.designation.startsWith("Détendeur") }

        assertNull(
            "on ne crédite pas plus d'heures qu'il n'en a été facturé",
            depot.creerAvoir(facture, mapOf(mainDOeuvre.id to 2.0)),
        )
        assertNull("un avoir qui ne crédite rien ne naît pas", depot.creerAvoir(facture, emptyMap()))

        val partiel = depot.creerAvoir(facture, mapOf(detendeur.id to 1.0))!!
        assertNull(
            "le brouillon compte déjà : un avoir total de plus dépasserait",
            depot.creerAvoir(facture),
        )
        depot.emettre(partiel, reglages, LE_12_MARS)

        val reste = depot.creerAvoir(facture, mapOf(mainDOeuvre.id to 1.78))
        assertNotNull("ce qui reste se crédite encore", reste)

        // Le brouillon grossit après coup : c'est l'émission qui tranche, et
        // elle le rend tel quel, sans numéro.
        depot.ajouterLigne(reste!!, "Ligne en trop", 1.0, "", 10.0)
        val refuse = depot.emettre(reste, reglages, LE_12_MARS)
        assertEquals("", refuse.numero)
        assertEquals(StatutFacture.BROUILLON, dao.parId(reste.id)!!.statut)
        assertEquals(
            "et aucun rang n'a été consommé",
            listOf("AV-2026-0001"),
            dao.contenu.filter { it.estAvoir && it.numerotee }.map { it.numero },
        )
    }

    /**
     * Une facture entièrement couverte ne se relance plus ; partiellement, elle
     * se relance pour son reste. Rien n'est stocké : tout se dérive des avoirs
     * émis, comme le retard de l'échéance.
     */
    @Test
    fun `une facture couverte par ses avoirs ne se relance plus`() = runTest {
        val facture = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val tard = facture.echeanceLe!!.plusDays(10)
        assertEquals(1, depot.aRelancer(tard).first().size)

        val brouillon = depot.creerAvoir(facture)!!
        assertEquals("un brouillon d'avoir ne déduit rien", 1, depot.aRelancer(tard).first().size)

        depot.emettre(brouillon, reglages, LE_12_MARS)
        assertEquals(emptyList<FactureChiffree>(), depot.aRelancer(tard).first())
        assertEquals(emptyList<FactureChiffree>(), depot.enRetard(tard).first())

        val chiffree = depot.facturesChiffrees.first().single { it.facture.id == facture.id }
        assertTrue(chiffree.couverte)
        assertEquals(0.0, chiffree.resteTtc, 0.0)
        assertTrue(
            "et l'avoir lui-même n'est jamais à relancer",
            depot.facturesChiffrees.first().filter { it.facture.estAvoir }.none { it.aRelancer(tard) },
        )
    }

    @Test
    fun `une facture partiellement couverte se relance pour son reste`() = runTest {
        val facture = depot.emettre(
            depot.creerDepuisIntervention(intervention(), pieces(), emptyList(), reglages),
            reglages,
            LE_12_MARS,
        )
        val tard = facture.echeanceLe!!.plusDays(31)
        val detendeur = dao.lignesDe(facture.id).first { it.designation.startsWith("Détendeur") }
        depot.emettre(depot.creerAvoir(facture, mapOf(detendeur.id to 1.0))!!, reglages, LE_12_MARS)

        val relancees = depot.aRelancer(tard).first()
        assertEquals(listOf(facture.id), relancees.map { it.facture.id })
        val chiffree = relancees.single()
        // 1,78 h × 60 € + 78,50 € = 185,30 € HT, soit 222,36 € TTC ; l'avoir
        // crédite le détendeur, 78,50 € HT soit 94,20 € TTC.
        assertEquals(222.36, chiffree.totalTtc, 0.001)
        assertEquals(94.20, chiffree.avoirsTtc, 0.001)
        assertEquals("ce qui reste, et non le total", 128.16, chiffree.resteTtc, 0.001)
        assertFalse(chiffree.couverte)

        // Les pénalités courent sur le reste : 128,16 € × 12 % × 31 / 365.
        val dues = PenalitesDues.de(chiffree.facture.copy(tauxPenalites = 12.0), chiffree.resteTtc, tard)!!
        assertEquals(1.31, dues.interets!!, 0.001)
    }

    /**
     * La TVA s'arrondit document par document, et c'est un centime réel : deux
     * lignes de 10,03 € font 24,07 € TTC, deux avoirs d'une ligne chacun 24,08 €.
     * Dans l'autre sens, deux arrondis vers le bas laisseraient une facture
     * entièrement créditée due d'un centime. Ni l'un ni l'autre ne doit
     * se voir.
     */
    @Test
    fun `un centime d'arrondi ne laisse pas une facture due, ni un reste negatif`() {
        fun facture(id: String, ht: Double) =
            FactureChiffree(Facture(id = id, numero = "FAC-2026-00$id", statut = StatutFacture.EMISE), ht)
        fun avoir(id: String, origine: String, ht: Double) = FactureChiffree(
            Facture(
                id = id,
                numero = "AV-2026-00$id",
                statut = StatutFacture.EMISE,
                nature = NatureFacture.AVOIR,
                factureOrigineId = origine,
            ),
            ht,
        )

        val imputees = Avoirs.imputer(
            listOf(
                facture("01", 20.06),
                avoir("11", "01", 10.03),
                avoir("12", "01", 10.03),
                facture("02", 0.04),
                avoir("21", "02", 0.02),
                avoir("22", "02", 0.02),
            ),
        ).associateBy { it.facture.id }

        val haut = imputees.getValue("01")
        assertEquals(24.07, haut.totalTtc, 0.001)
        assertEquals("chaque avoir arrondit sa TVA vers le haut", 24.08, haut.avoirsTtc, 0.001)
        assertTrue(haut.couverte)
        assertEquals("jamais un reste négatif", 0.0, haut.resteTtc, 0.0)

        val bas = imputees.getValue("02")
        assertEquals(0.05, bas.totalTtc, 0.001)
        assertEquals(0.04, bas.avoirsTtc, 0.001)
        assertTrue("créditée au hors taxes, elle ne doit plus un centime", bas.couverte)
        assertEquals(0.0, bas.resteTtc, 0.0)
    }

    /** La garde compte en centimes entiers, et l'égalité passe. */
    @Test
    fun `crediter exactement ce qui reste n'est pas depasser`() {
        assertFalse(Avoirs.depasse(185.30, listOf(78.50), 106.80))
        assertTrue(Avoirs.depasse(185.30, listOf(78.50), 106.81))
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
