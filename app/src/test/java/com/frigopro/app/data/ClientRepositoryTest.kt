package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ClientRepositoryTest {

    private val interventions = FauxInterventionDao()
    private val equipements = FauxEquipementDao(interventions)
    private val devis = FauxDevisDao()
    private val factures = FauxFactureDao()
    private val stockage = FauxRangementPhotos()

    /**
     * Le carnet est branché sur les autres tables, comme le vrai DAO l'est en
     * base : supprimer un client y écrit, et c'est exactement ce qu'on veut
     * vérifier sans SQLite. Les tests qui ne suppriment rien ne s'en aperçoivent
     * pas.
     */
    private val dao = FauxClientDao(interventions, equipements, devis, factures)
    private val repository = ClientRepository(dao, stockage)

    /**
     * L'assertion qui justifie le tri en Kotlin plutôt qu'en SQL : « Élise »
     * doit se ranger entre « Edouard » et « Fabien ». Un `ORDER BY nom COLLATE
     * NOCASE` la rejetterait après « Zoé », les accents passant après Z.
     */
    @Test
    fun `le carnet est trie en francais, accents replies`() = runTest {
        for (nom in listOf("Zoé", "Élise", "Edouard", "Fabien")) {
            repository.enregistrer(Client(id = nom, nom = nom, ville = "Rouen"))
        }

        val ordre = repository.clients.first().map { it.nom }

        assertEquals(listOf("Edouard", "Élise", "Fabien", "Zoé"), ordre)
    }

    @Test
    fun `un client inconnu est inscrit au carnet`() = runTest {
        val client = repository.trouverOuCreer("Boucherie Lemoine", "Rouen")

        assertEquals("Boucherie Lemoine", client.nom)
        assertEquals("Rouen", client.ville)
        assertEquals(listOf("Boucherie Lemoine"), dao.contenu.map { it.nom })
    }

    @Test
    fun `un client deja connu n'est pas duplique, meme a la casse pres`() = runTest {
        val premier = repository.trouverOuCreer("Boucherie Lemoine", "Rouen")

        val second = repository.trouverOuCreer("boucherie lemoine", "Elbeuf")

        assertEquals("le carnet doit rendre le client existant", premier.id, second.id)
        assertEquals("Rouen", second.ville)
        assertEquals(1, dao.contenu.size)
    }

    @Test
    fun `les espaces parasites de saisie sont retires`() = runTest {
        val client = repository.trouverOuCreer("  Fromagerie Hardy ", " Caudebec  ")

        assertEquals("Fromagerie Hardy", client.nom)
        assertEquals("Caudebec", client.ville)
    }

    @Test
    fun `l'adresse et le telephone sont nettoyes a l'enregistrement`() = runTest {
        val client = repository.enregistrer(
            Client(
                nom = "Fromagerie Hardy",
                ville = "Caudebec",
                adresse = "  12 rue des Halles ",
                telephone = " 02 35 00 00 00  ",
            ),
        )

        assertEquals("12 rue des Halles", client.adresse)
        assertEquals("02 35 00 00 00", client.telephone)
    }

    /**
     * Les sites se rangent sous leur donneur d'ordre, et n'encombrent pas le
     * premier rang du carnet.
     *
     * « Carrefour », « Carrefour Part-Dieu » et « Carrefour Vaise » côte à côte
     * feraient trois entrées pour un client, et la recherche d'un nom en
     * ramènerait autant.
     */
    @Test
    fun `les sites se rangent sous leur donneur d'ordre`() = runTest {
        val enseigne = repository.enregistrer(Client(id = "cl-1", nom = "Carrefour", ville = "Lyon"))
        repository.enregistrer(
            Client(id = "cl-2", nom = "Part-Dieu", ville = "Lyon", parentId = enseigne.id),
        )
        repository.enregistrer(
            Client(id = "cl-3", nom = "Vaise", ville = "Lyon", parentId = enseigne.id),
        )
        repository.enregistrer(Client(id = "cl-4", nom = "Boucherie Morel", ville = "Lyon"))

        val groupes = repository.groupes.first()

        assertEquals(
            "seuls les donneurs d'ordre paraissent au premier rang",
            listOf("Boucherie Morel", "Carrefour"),
            groupes.map { it.donneur.nom },
        )
        val carrefour = groupes.single { it.donneur.nom == "Carrefour" }
        assertEquals(2, carrefour.nombreSites)
        assertEquals(listOf("Part-Dieu", "Vaise"), carrefour.sites.map { it.nom })
    }

    /**
     * Un site dont le donneur d'ordre a disparu remonte au premier rang.
     *
     * Mieux vaut une fiche mal rangée qu'une fiche introuvable : c'est la même
     * règle que l'intitulé d'un type supprimé, qui reste sur les tournées
     * passées.
     */
    @Test
    fun `un site orphelin ne disparait pas du carnet`() = runTest {
        repository.enregistrer(
            Client(id = "cl-2", nom = "Part-Dieu", ville = "Lyon", parentId = "cl-disparu"),
        )

        val groupes = repository.groupes.first()

        assertEquals(listOf("Part-Dieu"), groupes.map { it.donneur.nom })
    }

    /**
     * Deux donneurs d'ordre peuvent avoir chacun un site du même nom.
     *
     * Ce n'est pas une confusion, c'est le cas ordinaire : deux syndics ont
     * chacun un immeuble « Les Tilleuls ». Même règle que pour deux unités
     * « Salon » sous deux multi-splits différents.
     */
    @Test
    fun `le doublon d'un site se juge sous son parent`() = runTest {
        repository.enregistrer(Client(id = "cl-1", nom = "Syndic A", ville = "Lyon"))
        repository.enregistrer(Client(id = "cl-2", nom = "Syndic B", ville = "Lyon"))
        repository.enregistrer(
            Client(id = "s-1", nom = "Les Tilleuls", ville = "Lyon", parentId = "cl-1"),
        )

        assertNotNull(repository.trouverSite("les tilleuls", "cl-1"))
        assertNull(
            "le même nom sous un autre donneur d'ordre n'est pas un doublon",
            repository.trouverSite("Les Tilleuls", "cl-2"),
        )
    }

    @Test
    fun `l'horodatage de modification est pose par le depot`() = runTest {
        val avant = Instant.now()

        repository.enregistrer(Client(nom = "Primeur Vasseur", ville = "Duclair"))

        val modifieLe = dao.contenu.single().modifieLe
        assertNotEquals(Instant.EPOCH, modifieLe)
        assertFalse("l'horodatage doit être postérieur à l'appel", modifieLe.isBefore(avant))
    }

    // — La suppression, et ce qu'elle emporte ————————————————————————————

    /**
     * Le cœur de la suppression d'un client : **deux traitements opposés**.
     *
     * Ce qui n'existe que par lui est effacé — son parc —, ce qui raconte ce qui
     * s'est passé est gardé, lien coupé. C'est la règle que le projet applique
     * déjà au type d'intervention et à la machine, et c'est elle qui rend la
     * suppression tenable : effacer les interventions aurait emporté le temps
     * chronométré, les relevés et la signature du client.
     */
    @Test
    fun `supprimer un client efface son parc et detache ses interventions`() = runTest {
        val client = repository.enregistrer(Client(nom = "Boucherie Morel", ville = "Lyon"))
        val machine = Equipement(clientId = client.id, nom = "Chambre froide")
        equipements.enregistrer(machine)
        interventions.enregistrer(
            tournee(clientId = client.id, equipementId = machine.id),
        )

        repository.supprimer(client)

        assertTrue("le carnet ne le porte plus", dao.contenu.none { it.id == client.id })
        assertTrue("son parc part avec lui", equipements.contenu.isEmpty())

        val restee = interventions.contenu.single()
        assertNull("le lien est coupé", restee.clientId)
        assertNull("celui de la machine aussi", restee.equipementId)
        // Et c'est tout l'intérêt du doublon lien / copie : la tournée de mars
        // continue de dire chez qui l'on est allé, et sur quelle machine.
        assertEquals("Boucherie Morel", restee.client)
        assertEquals("Chambre froide", restee.equipementNom)
    }

    /**
     * Une facture ne s'efface jamais, même quand son client disparaît.
     *
     * C'est un document comptable, que l'entreprise doit pouvoir représenter
     * pendant dix ans, et son numéro tient une séquence continue qu'un trou
     * trahirait. Tout ce qu'elle imprime étant déjà recopié sur elle, le lien
     * coupé ne lui retire rien de ce qui est parti chez le client.
     */
    @Test
    fun `devis et factures restent, lien coupe`() = runTest {
        val client = repository.enregistrer(Client(nom = "Boucherie Morel", ville = "Lyon"))
        devis.enregistrer(
            Devis(id = "d1", clientId = client.id, clientNom = client.nom, numero = "DEV-2603-001"),
        )
        factures.enregistrer(
            Facture(
                id = "f1",
                clientId = client.id,
                clientNom = client.nom,
                clientAdresse = "8 place du Marché, Lyon",
                numero = "FAC-2026-0007",
                statut = StatutFacture.EMISE,
            ),
        )

        repository.supprimer(client)

        val devisReste = devis.contenu.single()
        assertNull(devisReste.clientId)
        assertEquals("Boucherie Morel", devisReste.clientNom)

        val factureRestee = factures.contenu.single()
        assertNull(factureRestee.clientId)
        assertEquals("FAC-2026-0007", factureRestee.numero)
        // L'adresse est celle du jour de la facture, recopiée : c'est ce qui est
        // parti chez le client, et le carnet n'a plus rien à en dire.
        assertEquals("8 place du Marché, Lyon", factureRestee.clientAdresse)
    }

    /**
     * Les sites partent avec leur donneur d'ordre, et chacun emporte son parc.
     *
     * Un site est l'adresse d'une enseigne et n'a pas de sens sans elle ; le
     * laisser derrière aurait produit une fiche orpheline pointant vers un
     * parent disparu.
     */
    @Test
    fun `les sites partent avec leur donneur d'ordre`() = runTest {
        val enseigne = repository.enregistrer(Client(nom = "Carrefour", ville = "Lyon"))
        val site = repository.enregistrer(
            Client(nom = "Part-Dieu", ville = "Lyon", parentId = enseigne.id),
        )
        equipements.enregistrer(Equipement(clientId = site.id, nom = "Meuble mural"))
        interventions.enregistrer(tournee(clientId = site.id, id = "i-site"))

        repository.supprimer(enseigne)

        assertTrue("le carnet est vide", dao.contenu.isEmpty())
        assertTrue("le parc du site aussi", equipements.contenu.isEmpty())
        // L'intervention du site survit comme les autres : ce qui s'est passé
        // sur place ne dépend pas de la fiche qui l'a rangée.
        assertNull(interventions.contenu.single().clientId)
    }

    /** En sous-traitance, le donneur d'ordre efface son lien et laisse son nom. */
    @Test
    fun `supprimer un donneur d'ordre laisse le nom sur la facture de sous-traitance`() = runTest {
        val donneur = repository.enregistrer(Client(nom = "Groupe Delta", ville = "Paris"))
        interventions.enregistrer(
            tournee(clientId = "autre", id = "i1").copy(
                clientFactureId = donneur.id,
                clientFactureNom = donneur.nom,
            ),
        )

        repository.supprimer(donneur)

        val restee = interventions.contenu.single()
        assertNull(restee.clientFactureId)
        assertEquals("Groupe Delta", restee.clientFactureNom)
        assertEquals("celui chez qui on est allé ne bouge pas", "autre", restee.clientId)
    }

    /** Les fichiers image du parc partent aussi : SQLite ne les connaît pas. */
    @Test
    fun `les fichiers photo du parc sont effaces`() = runTest {
        val client = repository.enregistrer(Client(nom = "Boucherie Morel", ville = "Lyon"))
        val machine = Equipement(clientId = client.id, nom = "Chambre froide")
        equipements.enregistrer(machine)
        equipements.enregistrerPhoto(
            Photo(equipementId = machine.id, categorie = CategoriePhoto.PLAQUE, fichier = "plaque.jpg"),
        )
        stockage.fichiers += "plaque.jpg"

        repository.supprimer(client)

        // Sans cela, l'image resterait sur le téléphone sans qu'aucun écran ne
        // puisse la montrer ni l'effacer — et repartirait dans chaque archive.
        assertTrue("le fichier est effacé", stockage.fichiers.isEmpty())
        assertTrue(equipements.contenuPhotos.isEmpty())
    }

    private fun tournee(
        clientId: String,
        id: String = "i1",
        equipementId: String? = null,
    ) = Intervention(
        id = id,
        date = LocalDate.of(2026, 3, 12),
        heure = LocalTime.of(8, 0),
        client = "Boucherie Morel",
        ville = "Lyon",
        clientId = clientId,
        equipementId = equipementId,
        equipementNom = if (equipementId != null) "Chambre froide" else "",
    )
}
