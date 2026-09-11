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

class EquipementRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val dao = FauxEquipementDao(daoInterventions)
    private val stockage = FauxRangementPhotos()
    private val repository = EquipementRepository(dao, stockage)

    @Test
    fun `le parc demarre vide`() = runTest {
        assertEquals(emptyList<Equipement>(), repository.equipements.first())
    }

    /** Même raison que pour le carnet : « Étuve » n'est pas après « Vitrine ». */
    @Test
    fun `le parc est trie en francais, accents replies`() = runTest {
        for (nom in listOf("Vitrine", "Étuve", "Armoire", "Chambre froide")) {
            repository.enregistrer(Equipement(id = nom, clientId = "cli-1", nom = nom))
        }

        val ordre = repository.equipements.first().map { it.nom }

        assertEquals(listOf("Armoire", "Chambre froide", "Étuve", "Vitrine"), ordre)
    }

    @Test
    fun `une machine inconnue est creee, une machine connue est rendue telle quelle`() = runTest {
        val premiere = repository.trouverOuCreer("cli-1", "  Vitrine salle 2 ")

        assertEquals("Vitrine salle 2", premiere.nom)

        val seconde = repository.trouverOuCreer("cli-1", "vitrine salle 2")

        assertEquals("une machine ne doit pas se dédoubler à la casse près", premiere.id, seconde.id)
        assertEquals(1, dao.contenu.size)
    }

    /**
     * Deux clients ont souvent la même machine, nommée de la même façon. Les
     * confondre rattacherait les photos de l'un au parc de l'autre.
     */
    @Test
    fun `deux clients peuvent avoir une machine du meme nom`() = runTest {
        val chezLun = repository.trouverOuCreer("cli-1", "Vitrine salle 2")
        val chezLautre = repository.trouverOuCreer("cli-2", "Vitrine salle 2")

        assertTrue(chezLun.id != chezLautre.id)
        assertEquals(2, dao.contenu.size)
    }

    @Test
    fun `un renommage suit les interventions passees`() = runTest {
        val machine = repository.trouverOuCreer("cli-1", "Vitrine")
        daoInterventions.enregistrer(intervention(machine))

        repository.enregistrer(machine.copy(nom = "Vitrine salle 2"))

        val ligne = daoInterventions.contenu.single()
        assertEquals("Vitrine salle 2", ligne.equipementNom)
        assertEquals("le lien ne bouge pas", machine.id, ligne.equipementId)
    }

    /**
     * Le cœur du sujet : supprimer une machine ne doit ni vider une tournée
     * passée, ni laisser des fichiers image derrière elle.
     */
    @Test
    fun `une machine supprimee laisse son nom et emporte ses photos`() = runTest {
        val machine = repository.trouverOuCreer("cli-1", "Vitrine salle 2")
        daoInterventions.enregistrer(intervention(machine))
        repository.ajouterCapture(machine.id, CategoriePhoto.PLAQUE, "plaque.jpg")
        repository.ajouterCapture(machine.id, CategoriePhoto.EMPLACEMENT, "coin.jpg")

        repository.supprimer(machine.id)

        val ligne = daoInterventions.contenu.single()
        assertNull("le lien est coupé", ligne.equipementId)
        assertEquals("le nom reste affiché", "Vitrine salle 2", ligne.equipementNom)
        assertEquals(emptyList<Equipement>(), dao.contenu)
        assertEquals(emptyList<Photo>(), dao.contenuPhotos)
        assertEquals("aucun fichier ne doit survivre", emptyList<String>(), stockage.fichiers)
    }

    @Test
    fun `une capture enregistree devient une photo de la machine`() = runTest {
        val machine = repository.trouverOuCreer("cli-1", "Vitrine")

        val photo = repository.ajouterCapture(machine.id, CategoriePhoto.PLAQUE, "plaque.jpg")

        assertEquals("plaque.jpg", photo?.fichier)
        assertEquals(CategoriePhoto.PLAQUE, photo?.categorie)
        assertEquals(listOf(photo), repository.observerPhotos(machine.id).first())
        assertTrue("l'horodatage est posé par le dépôt", photo!!.priseLe > Instant.EPOCH)
    }

    /** Prise de vue abandonnée : ni ligne en base, ni fichier orphelin. */
    @Test
    fun `une prise de vue sans image n'enregistre rien`() = runTest {
        val machine = repository.trouverOuCreer("cli-1", "Vitrine")
        stockage.captureAboutit = false

        val photo = repository.ajouterCapture(machine.id, CategoriePhoto.PLAQUE, "vide.jpg")

        assertNull(photo)
        assertEquals(emptyList<Photo>(), dao.contenuPhotos)
        assertEquals(emptyList<String>(), stockage.fichiers)
    }

    @Test
    fun `une photo supprimee emporte son fichier`() = runTest {
        val machine = repository.trouverOuCreer("cli-1", "Vitrine")
        val photo = repository.ajouterCapture(machine.id, CategoriePhoto.PLAQUE, "plaque.jpg")!!

        repository.supprimerPhoto(photo)

        assertEquals(emptyList<Photo>(), dao.contenuPhotos)
        assertEquals(emptyList<String>(), stockage.fichiers)
    }

    private fun intervention(machine: Equipement): Intervention = Intervention(
        id = "id-1",
        date = LocalDate.of(2026, 3, 9),
        heure = LocalTime.of(9, 0),
        client = "Boucherie Lemoine",
        ville = "Rouen",
        clientId = machine.clientId,
        equipementId = machine.id,
        equipementNom = machine.nom,
    )
}
