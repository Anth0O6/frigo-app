package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class EquipementRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoMaintenance = FauxMaintenanceDao()
    private val dao = FauxEquipementDao(daoInterventions, daoMaintenance)
    private val stockage = FauxRangementPhotos()
    private val repository = EquipementRepository(dao, stockage)
    private val plan = MaintenanceRepository(daoMaintenance)

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

    /**
     * **Le partage de la suppression, côté maintenance.** Une machine effacée
     * emporte son plan et **garde ses visites**, lien coupé.
     *
     * Les deux moitiés comptent. L'affectation n'existe que par la machine :
     * `PlanMaintenance.echeances` l'écarterait en silence, si bien qu'elle
     * resterait en base et repartirait dans chaque archive sans qu'aucun écran
     * ne puisse la montrer ni l'effacer. La visite, elle, est la preuve qu'une
     * maintenance contractuelle a eu lieu : l'effacer parce que quelqu'un range
     * son inventaire reviendrait à perdre ce qu'un contrôle vient chercher. Elle
     * garde le nom de la machine, recopié sur elle, et se relit donc entière.
     */
    @Test
    fun `supprimer une machine emporte son plan et garde ses visites`() = runTest {
        val machine = repository.enregistrer(
            Equipement(id = "eq-1", clientId = "cli-1", nom = "Centrale négatif"),
        )
        val gamme = GammeMaintenance(id = "ga-1", libelle = "Visite mensuelle")
        plan.enregistrerGamme(gamme)
        plan.affecter(machine.id, gamme.id, LocalDate.of(2026, 1, 15))
        plan.consigner(machine, gamme, faitLe = LocalDate.of(2026, 3, 2))

        repository.supprimer(machine.id)

        assertTrue("le plan est parti", daoMaintenance.affectations.isEmpty())
        val visite = daoMaintenance.releves.single()
        assertNull("le lien est coupé", visite.equipementId)
        assertEquals("le nom reste lisible", "Centrale négatif", visite.equipementNom)
        assertEquals("Visite mensuelle", visite.gammeLibelle)
        assertEquals(LocalDate.of(2026, 3, 2), visite.faitLe)
    }

    /**
     * Les unités intérieures passent par le même chemin que leur groupe.
     *
     * Une unité se rattache à une gamme pour elle-même — on nettoie *ce*
     * filtre —, et la laisser derrière aurait gardé une échéance sur une unité
     * qui n'existe plus. C'est la même raison que pour ses photos, déjà tenue
     * par un test au-dessus.
     */
    @Test
    fun `supprimer un groupe emporte aussi le plan de ses unites`() = runTest {
        val groupe = repository.enregistrer(
            Equipement(id = "gr-1", clientId = "cli-1", nom = "Bi-split Daikin"),
        )
        val unite = repository.ajouterUnite(groupe, "Salon")
        val gamme = GammeMaintenance(id = "ga-1", libelle = "Filtres")
        plan.enregistrerGamme(gamme)
        plan.affecter(groupe.id, gamme.id)
        plan.affecter(unite.id, gamme.id)
        plan.consigner(unite, gamme, faitLe = LocalDate.of(2026, 4, 9))

        repository.supprimer(groupe.id)

        assertTrue("les deux affectations sont parties", daoMaintenance.affectations.isEmpty())
        assertNull(daoMaintenance.releves.single().equipementId)
        assertEquals("Salon", daoMaintenance.releves.single().equipementNom)
    }

    /**
     * Le nom proposé pour une copie, qui est la raison d'être de la
     * duplication : un linéaire est fait de meubles numérotés à la suite, et
     * trois duplications donnent 2, 3 puis 4 — et non trois fois 2, c'est-à-dire
     * trois fois le même doublon à corriger à la main.
     */
    @Test
    fun `le nom d'une copie prend le rang suivant, jusqu'au premier libre`() {
        val pris = mutableSetOf("Vitrine 1")

        repeat(3) { pris += nomDeCopie("Vitrine 1") { candidat -> candidat in pris } }

        assertEquals(setOf("Vitrine 1", "Vitrine 2", "Vitrine 3", "Vitrine 4"), pris)
        assertEquals("Chambre froide 2", nomDeCopie("Chambre froide") { false })
        assertEquals("Vitrine 13", nomDeCopie("Vitrine 12") { false })
        assertEquals("Armoire 10", nomDeCopie("  Armoire 9  ") { false })
    }

    /**
     * **Ce qui est recopié est une caractéristique ; ce qui ne l'est pas est un
     * acte.** C'est toute la règle de la duplication, et les deux moitiés
     * comptent autant.
     *
     * Le numéro de série est unique par définition, et le recopier aurait rempli
     * le parc de doublons désignant une seule machine — avec, au bout, une pièce
     * commandée pour le mauvais meuble. Le dernier contrôle d'étanchéité est un
     * acte réglementaire fait sur *une* machine : le dater sur la copie
     * reviendrait à attester un contrôle qui n'a pas eu lieu, et donc à repousser
     * une échéance, c'est-à-dire à manquer une obligation.
     */
    @Test
    fun `dupliquer recopie le materiel et jamais ce qui a ete fait`() = runTest {
        val source = repository.enregistrer(
            Equipement(
                id = "eq-1",
                clientId = "cli-1",
                nom = "Vitrine 1",
                marque = "Costan",
                modele = "Gazelle",
                numeroSerie = "4821007",
                fluide = "R449A",
                chargeKg = 3.4,
                misEnServiceLe = LocalDate.of(2024, 5, 12),
                dernierControleLe = LocalDate.of(2026, 2, 18),
                zone = "Linéaire surgelés",
            ),
        )

        val copie = repository.dupliquer(source, "Vitrine 2")

        assertNotEquals("la copie est une autre machine", source.id, copie.id)
        assertEquals("Vitrine 2", copie.nom)
        assertEquals("cli-1", copie.clientId)
        assertEquals("Costan", copie.marque)
        assertEquals("Gazelle", copie.modele)
        assertEquals("R449A", copie.fluide)
        assertEquals(3.4, copie.chargeKg!!, 0.0)
        assertEquals(
            "un linéaire est posé le même jour",
            LocalDate.of(2024, 5, 12),
            copie.misEnServiceLe,
        )
        assertEquals("et les douze meubles sont dans le même linéaire", "Linéaire surgelés", copie.zone)
        assertEquals("le numéro de série ne se recopie pas", "", copie.numeroSerie)
        assertNull("ni le contrôle d'étanchéité", copie.dernierControleLe)
        assertEquals(
            "la source n'a pas bougé",
            "4821007",
            dao.contenu.first { it.id == source.id }.numeroSerie,
        )
    }

    /**
     * Un multi-split dupliqué arrive **avec ses unités**, qui gardent leur nom :
     * « Salon » et « Chambre » se répètent d'un appartement à l'autre, et c'est
     * le cas ordinaire d'un immeuble. Elles pendent du nouveau groupe, et non de
     * l'ancien — sans quoi la copie serait un groupe vide et l'original en
     * porterait quatre.
     */
    @Test
    fun `dupliquer un multi-split emporte ses unites sous la copie`() = runTest {
        val groupe = repository.enregistrer(
            Equipement(id = "gr-1", clientId = "cli-1", nom = "Daikin 1", marque = "Daikin"),
        )
        repository.ajouterUnite(groupe, "Salon")
        repository.ajouterUnite(groupe, "Chambre")

        val copie = repository.dupliquer(groupe, "Daikin 2")

        val unitesCopie = dao.unitesDe(copie.id)
        val unitesSource = dao.unitesDe(groupe.id).map { it.id }
        assertEquals(listOf("Chambre", "Salon"), unitesCopie.map { it.nom }.sorted())
        assertEquals("Daikin", copie.marque)
        assertEquals("l'original garde les siennes", 2, unitesSource.size)
        assertTrue(
            "aucune unité n'est restée accrochée aux deux",
            unitesCopie.none { it.id in unitesSource },
        )
    }

    /**
     * Dupliquer une **unité** crée une sœur sous le même groupe, et non un
     * groupe de plus : la hiérarchie n'a qu'un niveau, et la copie d'une unité
     * reste une unité.
     */
    @Test
    fun `dupliquer une unite la laisse sous son groupe`() = runTest {
        val groupe = repository.enregistrer(
            Equipement(id = "gr-1", clientId = "cli-1", nom = "Daikin 1"),
        )
        val unite = repository.ajouterUnite(groupe, "Chambre 1")

        val copie = repository.dupliquer(unite, "Chambre 2")

        assertEquals(groupe.id, copie.parentId)
        assertEquals(3, dao.contenu.size)
        assertEquals(
            listOf("Chambre 1", "Chambre 2"),
            dao.unitesDe(groupe.id).map { it.nom }.sorted(),
        )
    }

    /**
     * Les zones employées se **dérivent du parc**, triées en français et sans les
     * vides.
     *
     * Vide veut dire « non rangée » : la faire figurer comme une zone aurait
     * produit un groupe sans nom en tête de l'écran, et un filtre qui ne filtre
     * rien. Et le tri passe par un `Collator` comme le carnet — « Étuve » n'est
     * pas après « Zone 2 ».
     */
    @Test
    fun `les zones du parc sont derivees, triees et sans les vides`() = runTest {
        listOf("Zone 2", "Étuve", "", "Toiture", "  ", "Toiture", "Atelier")
            .forEachIndexed { rang, zone ->
                repository.enregistrer(
                    Equipement(id = "eq-$rang", clientId = "cli-1", nom = "m$rang", zone = zone),
                )
            }

        val zones = zonesDe(repository.equipements.first())

        assertEquals(listOf("Atelier", "Étuve", "Toiture", "Zone 2"), zones)
    }
}
