package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ce qui s'est passé sur place est ce qui se facture et ce qui se déclare :
 * un relevé fantôme, un kilogramme de fluide en trop ou une intervention
 * supprimée qui laisse son registre derrière elle sont des défauts qui se
 * paient ailleurs que sur l'écran.
 */
class SuiviRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val dao = FauxSuiviDao(daoInterventions)
    private val stockage = FauxRangementPhotos()
    private val repository = SuiviRepository(dao, stockage)

    @Test
    fun `un releve est horodate a l'enregistrement`() = runTest {
        val enregistre = repository.enregistrerReleve(releve(surchauffe = 11.2))

        assertTrue(enregistre.releveLe.isAfter(Instant.EPOCH))
        assertEquals(11.2, dao.contenuReleves.single().surchauffeK!!, 0.001)
    }

    /**
     * Vider les quatre cases est la façon naturelle de dire « finalement je
     * n'ai rien relevé » : garder une ligne vide ferait apparaître un relevé
     * fantôme dans la tendance de la machine.
     */
    @Test
    fun `un releve vide est efface plutot que conserve`() = runTest {
        val pose = repository.enregistrerReleve(releve(surchauffe = 11.2))

        repository.enregistrerReleve(pose.copy(surchauffeK = null))

        assertTrue(dao.contenuReleves.isEmpty())
    }

    @Test
    fun `la premiere date de releve ne se deplace pas a la correction`() = runTest {
        val pose = repository.enregistrerReleve(releve(surchauffe = 11.2))

        val corrige = repository.enregistrerReleve(pose.copy(surchauffeK = 9.0))

        assertEquals(pose.releveLe, corrige.releveLe)
    }

    // — Fluide —————————————————————————————————————————————————————————————

    /** Le sens porte la direction : une masse signée fausserait les totaux. */
    @Test
    fun `une masse negative est ramenee a sa valeur absolue`() = runTest {
        repository.enregistrerMouvement(mouvement(masse = -0.8))

        assertEquals(0.8, dao.contenuMouvements.single().masseKg, 0.001)
    }

    @Test
    fun `une masse nulle n'est pas un mouvement`() = runTest {
        val rendu = repository.enregistrerMouvement(mouvement(masse = 0.0))

        assertNull(rendu)
        assertTrue(dao.contenuMouvements.isEmpty())
    }

    @Test
    fun `le fluide est mis en forme canonique au registre`() = runTest {
        repository.enregistrerMouvement(mouvement(fluide = " r-452a "))

        assertEquals("R452A", dao.contenuMouvements.single().fluide)
    }

    // — Pièces —————————————————————————————————————————————————————————————

    @Test
    fun `une piece sans designation n'est pas enregistrable`() = runTest {
        val rendu = repository.enregistrerPiece(
            PiecePosee(interventionId = "id-1", designation = "   "),
        )

        assertNull(rendu)
        assertTrue(dao.contenuPieces.isEmpty())
    }

    @Test
    fun `la designation et la reference sont nettoyees`() = runTest {
        repository.enregistrerPiece(
            PiecePosee(
                interventionId = "id-1",
                designation = "  Filtre déshydrateur  ",
                reference = " DML 084 ",
            ),
        )

        val posee = dao.contenuPieces.single()
        assertEquals("Filtre déshydrateur", posee.designation)
        assertEquals("DML 084", posee.reference)
    }

    // — Suppression ————————————————————————————————————————————————————————

    /**
     * Une intervention effacée qui laisserait des kilogrammes au registre
     * serait pire qu'un défaut d'affichage : ce sont des lignes réglementaires,
     * et elles ne doivent désigner que des interventions qui existent.
     */
    @Test
    fun `supprimer une intervention emporte tout ce qu'elle portait`() = runTest {
        daoInterventions.enregistrer(intervention())
        repository.enregistrerReleve(releve(surchauffe = 11.2))
        repository.enregistrerMouvement(mouvement(masse = 0.8))
        repository.enregistrerPiece(PiecePosee(interventionId = "id-1", designation = "Filtre"))
        repository.ajouterCapture("id-1", CategoriePhoto.AVANT, "avant.jpg")

        repository.supprimerIntervention("id-1")

        assertTrue(dao.contenuReleves.isEmpty())
        assertTrue(dao.contenuMouvements.isEmpty())
        assertTrue(dao.contenuPieces.isEmpty())
        assertTrue(dao.contenuPhotos.isEmpty())
        assertTrue(daoInterventions.contenu.isEmpty())
        assertEquals("les fichiers partent avec les lignes", emptyList<String>(), stockage.fichiers)
    }

    @Test
    fun `une photo supprimee emporte son fichier`() = runTest {
        val photo = repository.ajouterCapture("id-1", CategoriePhoto.AVANT, "avant.jpg")!!

        repository.supprimerPhoto(photo)

        assertTrue(dao.contenuPhotos.isEmpty())
        assertEquals(emptyList<String>(), stockage.fichiers)
    }

    /** Une prise de vue abandonnée ne laisse ni ligne ni fichier. */
    @Test
    fun `une capture abandonnee n'ecrit rien`() = runTest {
        stockage.captureAboutit = false

        val rendu = repository.ajouterCapture("id-1", CategoriePhoto.AVANT, "avant.jpg")

        assertNull(rendu)
        assertTrue(dao.contenuPhotos.isEmpty())
    }

    @Test
    fun `les photos d'une intervention sont servies dans l'ordre de prise`() = runTest {
        repository.ajouterCapture("id-1", CategoriePhoto.AVANT, "a.jpg")
        repository.ajouterCapture("id-1", CategoriePhoto.APRES, "b.jpg")
        repository.ajouterCapture("autre", CategoriePhoto.AVANT, "c.jpg")

        val photos = repository.observerPhotos("id-1").first()

        assertEquals(listOf("a.jpg", "b.jpg"), photos.map { it.fichier })
    }

    private fun releve(surchauffe: Double? = null) =
        Releve(interventionId = "id-1", surchauffeK = surchauffe)

    private fun mouvement(masse: Double = 0.8, fluide: String = "R452A") = MouvementFluide(
        interventionId = "id-1",
        fluide = fluide,
        sens = SensFluide.AJOUT,
        masseKg = masse,
    )

    private fun intervention() = Intervention(
        id = "id-1",
        date = LocalDate.of(2026, 5, 14),
        heure = LocalTime.of(8, 0),
        client = "Boucherie Martel",
        ville = "Vitry",
    )
}

/** Les devis : la numérotation, les totaux, et ce qui se fige. */
class DevisRepositoryTest {

    private val dao = FauxDevisDao()
    private val repository = DevisRepository(dao)

    private val mai = LocalDate.of(2026, 5, 14)

    @Test
    fun `un devis cree recoit un numero et une validite`() = runTest {
        val devis = repository.creer(client = null, aujourdhui = mai)

        assertEquals("DEV-2605-001", devis.numero)
        assertEquals(mai, devis.creeLe)
        assertEquals(mai.plusMonths(1), devis.valableJusquau)
        assertEquals(StatutDevis.BROUILLON, devis.statut)
    }

    @Test
    fun `le numero suit celui du mois`() = runTest {
        repository.creer(client = null, aujourdhui = mai)

        val second = repository.creer(client = null, aujourdhui = mai)

        assertEquals("DEV-2605-002", second.numero)
    }

    @Test
    fun `le nom du client est recopie sur le devis`() = runTest {
        val client = Client(id = "cl-1", nom = "Boucherie Martel", ville = "Vitry")

        val devis = repository.creer(client = client, aujourdhui = mai)

        assertEquals("cl-1", devis.clientId)
        assertEquals("Boucherie Martel", devis.clientNom)
    }

    @Test
    fun `une ligne sans designation est refusee`() = runTest {
        val devis = repository.creer(client = null, aujourdhui = mai)

        val ligne = repository.ajouterLigne(devis.id, "  ", 1.0, "", 100.0)

        assertNull(ligne)
        assertTrue(dao.contenuLignes.isEmpty())
    }

    @Test
    fun `les lignes se posent a la suite`() = runTest {
        val devis = repository.creer(client = null, aujourdhui = mai)

        repository.ajouterLigne(devis.id, "Compresseur", 1.0, "", 1240.0)
        repository.ajouterLigne(devis.id, "Main d'œuvre", 6.0, "h", 68.0)

        assertEquals(listOf(0, 1), dao.contenuLignes.sortedBy { it.rang }.map { it.rang })
    }

    /**
     * Les chiffres de la maquette : 1 240,00 + 408,00 + 272,80 = 1 920,80 HT,
     * 384,16 de TVA, 2 304,96 TTC.
     */
    @Test
    fun `les totaux retombent sur l'addition du client`() {
        val devis = DevisComplet(
            devis = Devis(tauxTva = 20.0),
            lignes = listOf(
                LigneDevis(devisId = "d", designation = "Compresseur", quantite = 1.0, prixUnitaire = 1240.0),
                LigneDevis(devisId = "d", designation = "Main d'œuvre", quantite = 6.0, prixUnitaire = 68.0),
                LigneDevis(devisId = "d", designation = "Charge", quantite = 6.2, prixUnitaire = 44.0),
            ),
        )

        assertEquals(1920.80, devis.totalHt, 0.001)
        assertEquals(384.16, devis.tva, 0.001)
        assertEquals(2304.96, devis.totalTtc, 0.001)
    }

    /**
     * Arrondir ligne à ligne puis sommer, et non l'inverse : trois lignes à un
     * tiers d'euro doivent donner ce que le client obtient en additionnant ce
     * qu'il lit, pas un centime de plus.
     */
    @Test
    fun `chaque ligne est arrondie avant d'etre sommee`() {
        val devis = DevisComplet(
            devis = Devis(tauxTva = 0.0),
            lignes = (1..3).map {
                LigneDevis(devisId = "d", designation = "Ligne $it", quantite = 1.0, prixUnitaire = 0.334)
            },
        )

        assertEquals(0.99, devis.totalHt, 0.0001)
    }

    @Test
    fun `un devis accepte ou refuse est fige`() {
        assertTrue(StatutDevis.ACCEPTE.figé)
        assertTrue(StatutDevis.REFUSE.figé)
        assertTrue(!StatutDevis.BROUILLON.figé)
        assertTrue(!StatutDevis.ENVOYE.figé)
    }

    /**
     * Les compteurs et la liste ont besoin du total de chaque devis, qui est la
     * somme de ses lignes. Le recopier sur la ligne du devis serait s'exposer à
     * ce qu'il cesse d'être juste après une modification ; il est donc recalculé,
     * mais en une seule requête.
     */
    @Test
    fun `chaque devis porte le total de ses lignes`() = runTest {
        val premier = repository.creer(client = null, aujourdhui = mai)
        repository.ajouterLigne(premier.id, "Compresseur", 1.0, "pièce", 1240.0)
        repository.ajouterLigne(premier.id, "Main d'œuvre", 6.0, "h", 68.0)
        val second = repository.creer(client = null, aujourdhui = mai)
        repository.ajouterLigne(second.id, "Déplacement", 1.0, "forfait", 45.0)
        repository.creer(client = null, aujourdhui = mai)

        val chiffres = repository.devisChiffres.first().associateBy { it.devis.id }

        assertEquals(3, chiffres.size)
        assertEquals(1240.0 + 6 * 68.0, chiffres.getValue(premier.id).totalHt, 0.001)
        assertEquals(45.0, chiffres.getValue(second.id).totalHt, 0.001)
        assertEquals(
            "un devis sans ligne vaut zéro, il ne disparaît pas de la liste",
            3,
            chiffres.values.size,
        )
        assertTrue(
            "et son total est nul, pas absent",
            chiffres.values.any { it.totalHt == 0.0 },
        )
    }

    @Test
    fun `le montant TTC suit le taux porte par le devis`() = runTest {
        val devis = repository.creer(client = null, aujourdhui = mai, tauxTva = 10.0)
        repository.ajouterLigne(devis.id, "Main d'œuvre", 2.0, "h", 50.0)

        val chiffre = repository.devisChiffres.first().single()

        assertEquals(100.0, chiffre.totalHt, 0.001)
        assertEquals("le taux vient du devis, pas d'un réglage global", 110.0, chiffre.totalTtc, 0.001)
    }

    /**
     * « En attente » est ce qui attend une réponse. Un devis refusé n'attend
     * plus rien, et le compter relancerait un client qui a déjà dit non.
     */
    @Test
    fun `seuls un brouillon et un devis envoye sont en attente`() = runTest {
        val brouillon = repository.creer(client = null, aujourdhui = mai)
        val envoye = repository.creer(client = null, aujourdhui = mai)
        val accepte = repository.creer(client = null, aujourdhui = mai)
        val refuse = repository.creer(client = null, aujourdhui = mai)
        repository.changerStatut(envoye, StatutDevis.ENVOYE)
        repository.changerStatut(accepte, StatutDevis.ACCEPTE)
        repository.changerStatut(refuse, StatutDevis.REFUSE)

        val enAttente = repository.devisChiffres.first().filter { it.enAttente }.map { it.devis.id }

        assertEquals(setOf(brouillon.id, envoye.id), enAttente.toSet())
    }

    @Test
    fun `supprimer un devis emporte ses lignes`() = runTest {
        val devis = repository.creer(client = null, aujourdhui = mai)
        repository.ajouterLigne(devis.id, "Compresseur", 1.0, "", 1240.0)

        repository.supprimer(devis.id)

        assertTrue(dao.contenu.isEmpty())
        assertTrue(dao.contenuLignes.isEmpty())
    }
}

/** Les réglages, et ce qu'une lecture rend quand la ligne unique manque. */
class ParametresRepositoryTest {

    private val dao = FauxParametresDao()
    private val repository = ParametresRepository(dao)

    @Test
    fun `une base sans ligne rend les valeurs par defaut`() = runTest {
        val lus = repository.lire()

        assertTrue("sombre par défaut", lus.themeSombre)
        assertEquals(20.0, lus.tauxTva, 0.001)
    }

    @Test
    fun `une modification ecrit la ligne unique`() = runTest {
        repository.modifier { it.copy(tauxHoraire = 68.0, technicien = "Anthony Ouvrard") }

        val lus = repository.lire()
        assertEquals(Parametres.UNIQUE, dao.contenu!!.id)
        assertEquals(68.0, lus.tauxHoraire, 0.001)
    }

    @Test
    fun `les initiales se deduisent du nom`() {
        assertEquals("AO", Parametres(technicien = "Anthony Ouvrard").initiales)
        assertEquals("AO", Parametres(technicien = "anthony ouvrard").initiales)
        assertEquals("JP", Parametres(technicien = "Jean-Pierre").initiales)
        assertEquals("", Parametres().initiales)
    }
}
