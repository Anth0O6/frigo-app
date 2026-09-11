package com.frigopro.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * La sauvegarde est le seul filet contre un téléphone perdu : ce qui en sort
 * doit y rentrer à l'identique, et un fichier douteux ne doit rien abîmer.
 */
class SauvegardeRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoClients = FauxClientDao()
    private val daoTypes = FauxTypeInterventionDao(daoInterventions)
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val daoSuivi = FauxSuiviDao(daoInterventions)
    private val daoDevis = FauxDevisDao()
    private val daoParametres = FauxParametresDao()
    private val daoTechniciens = FauxTechnicienDao(daoInterventions)
    private val daoPrestations = FauxPrestationDao()
    private val repository =
        SauvegardeRepository(
            daoInterventions,
            daoClients,
            daoTypes,
            daoEquipements,
            daoSuivi,
            daoDevis,
            daoParametres,
            daoTechniciens,
            daoPrestations,
        )

    @Test
    fun `ce qui est exporte revient identique`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        daoTypes.enregistrer(TYPE)
        daoEquipements.enregistrer(EQUIPEMENT)
        daoEquipements.enregistrerPhoto(PHOTO)

        val export = repository.exporter()
        val resultat = vierge().restaurer(export.contenu)

        assertEquals(
            ResultatRestauration.Reussie(
                types = 1,
                clients = 1,
                equipements = 1,
                photos = 1,
                interventions = 1,
            ),
            resultat,
        )
    }

    /**
     * L'horodatage ne doit pas être réécrit à la restauration : c'est lui qui
     * départagera un jour deux versions concurrentes d'une même ligne.
     */
    @Test
    fun `la restauration conserve les valeurs, horodatage compris`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu

        val autreInterventions = FauxInterventionDao()
        val autreClients = FauxClientDao()
        val autreTypes = FauxTypeInterventionDao(autreInterventions)
        SauvegardeRepository(
            autreInterventions,
            autreClients,
            autreTypes,
            FauxEquipementDao(autreInterventions),
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(autreInterventions),
            FauxPrestationDao(),
        ).restaurer(contenu)

        assertEquals(CLIENT, autreClients.contenu.single())
        assertEquals(INTERVENTION, autreInterventions.contenu.single())
    }

    /**
     * Le parc et les photos suivent la même règle : ce qui sort doit rentrer à
     * l'identique, `priseLe` comprise, puisque c'est elle qui donne l'ordre
     * d'affichage.
     */
    @Test
    fun `le parc et ses photos reviennent identiques`() = runTest {
        daoEquipements.enregistrer(EQUIPEMENT)
        daoEquipements.enregistrerPhoto(PHOTO)
        val contenu = repository.exporter().contenu

        val autreInterventions = FauxInterventionDao()
        val autreEquipements = FauxEquipementDao(autreInterventions)
        SauvegardeRepository(
            autreInterventions,
            FauxClientDao(),
            FauxTypeInterventionDao(autreInterventions),
            autreEquipements,
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(autreInterventions),
            FauxPrestationDao(),
        ).restaurer(contenu)

        assertEquals(EQUIPEMENT, autreEquipements.contenu.single())
        assertEquals(PHOTO, autreEquipements.contenuPhotos.single())
    }

    /**
     * L'export nomme les fichiers à joindre à l'archive, et seulement ceux que
     * la base connaît : une image orpheline n'a pas à grossir la sauvegarde.
     */
    @Test
    fun `l'export nomme les fichiers des photos`() = runTest {
        daoEquipements.enregistrer(EQUIPEMENT)
        daoEquipements.enregistrerPhoto(PHOTO)
        daoEquipements.enregistrerPhoto(PHOTO.copy(id = "ph-2", fichier = "coin.jpg"))

        val export = repository.exporter()

        assertEquals(listOf("plaque.jpg", "coin.jpg"), export.fichiersPhotos)
    }

    /**
     * La catégorie est une valeur fixe de l'application, comme le statut : une
     * valeur inconnue fait refuser le fichier entier.
     */
    @Test
    fun `une categorie de photo inconnue fait refuser tout le fichier`() = runTest {
        daoEquipements.enregistrer(EQUIPEMENT)
        daoEquipements.enregistrerPhoto(PHOTO)
        val contenu = repository.exporter().contenu.replace("PLAQUE", "AUTRE_CHOSE")

        val interventions = FauxInterventionDao()
        val equipements = FauxEquipementDao(interventions)
        val resultat = SauvegardeRepository(
            interventions,
            FauxClientDao(),
            FauxTypeInterventionDao(interventions),
            equipements,
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(interventions),
            FauxPrestationDao(),
        ).restaurer(contenu)

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue("rien ne doit être écrit avant la vérification", equipements.contenu.isEmpty())
    }

    /**
     * Une archive bricolée pourrait nommer une photo `../databases/frigopro.db`
     * pour faire écrire ailleurs que dans le dossier des images. Un nom qui n'en
     * est pas un fait refuser le fichier, avant toute écriture.
     */
    @Test
    fun `un nom de fichier qui n'en est pas un fait refuser le fichier`() = runTest {
        val contenu = """
            {
              "format": 3,
              "exporteeLe": "2026-09-10T10:00:00Z",
              "photos": [
                {
                  "id": "ph-1", "equipementId": "eq-1", "categorie": "PLAQUE",
                  "fichier": "../databases/frigopro.db", "priseLe": 0
                }
              ]
            }
        """.trimIndent()

        val resultat = repository.restaurer(contenu)

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue(daoEquipements.contenuPhotos.isEmpty())
    }

    /**
     * Une sauvegarde faite avant le parc reste restaurable : elle n'en parle
     * pas, et l'absence de machines n'est pas une anomalie.
     */
    @Test
    fun `une sauvegarde du format 2 se restaure sans parc`() = runTest {
        val contenu = """
            {
              "format": 2,
              "exporteeLe": "2026-09-09T08:00:00Z",
              "interventions": [
                {
                  "id": "id-1", "date": "2026-09-10", "heure": "08:30",
                  "client": "Boucherie Lemoine", "ville": "Rouen",
                  "typeLibelle": "Fuite de fluide", "statut": "A_FAIRE"
                }
              ]
            }
        """.trimIndent()

        val resultat = repository.restaurer(contenu)

        assertEquals(
            ResultatRestauration.Reussie(
                types = 0,
                clients = 0,
                equipements = 0,
                photos = 0,
                interventions = 1,
            ),
            resultat,
        )
        val restauree = daoInterventions.contenu.single()
        assertNull("le parc n'existait pas", restauree.equipementId)
        assertEquals("", restauree.equipementNom)
    }

    /** Restaurer deux fois la même sauvegarde ne doit pas tout dédoubler. */
    @Test
    fun `restaurer deux fois ne cree pas de doublon`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu

        repository.restaurer(contenu)
        repository.restaurer(contenu)

        assertEquals(1, daoClients.contenu.size)
        assertEquals(1, daoInterventions.contenu.size)
    }

    /** Une restauration fusionne : elle n'efface pas ce qu'elle ne contient pas. */
    @Test
    fun `la restauration n'efface pas les lignes absentes du fichier`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu
        val ailleurs = INTERVENTION.copy(id = "id-2", client = "Saisie depuis")
        daoInterventions.enregistrer(ailleurs)

        repository.restaurer(contenu)

        assertEquals(setOf("id-1", "id-2"), daoInterventions.contenu.map { it.id }.toSet())
    }

    @Test
    fun `un fichier qui n'est pas du JSON est refuse`() = runTest {
        val resultat = repository.restaurer("ceci n'est pas une sauvegarde")

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue(daoInterventions.contenu.isEmpty())
    }

    @Test
    fun `un fichier ecrit par une version plus recente est refuse`() = runTest {
        val contenu = """{"format":${FORMAT_COURANT + 1},"exporteeLe":"2026-09-10T10:00:00Z"}"""

        val resultat = repository.restaurer(contenu)

        assertEquals(ResultatRestauration.TropRecente(FORMAT_COURANT + 1), resultat)
    }

    /**
     * Un statut inconnu fait rejeter le fichier **en entier** : une tournée
     * restaurée à moitié serait pire qu'une restauration refusée.
     */
    @Test
    fun `une valeur inconnue fait refuser tout le fichier`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu.replace("EN_COURS", "STATUT_INVENTE")

        val viergeInterventions = FauxInterventionDao()
        val viergeClients = FauxClientDao()
        val viergeTypes = FauxTypeInterventionDao(viergeInterventions)
        val viergeEquipements = FauxEquipementDao(viergeInterventions)
        val resultat = SauvegardeRepository(
            viergeInterventions,
            viergeClients,
            viergeTypes,
            viergeEquipements,
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(viergeInterventions),
            FauxPrestationDao(),
        ).restaurer(contenu)

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue("rien ne doit être écrit avant la vérification", viergeClients.contenu.isEmpty())
        assertTrue(viergeInterventions.contenu.isEmpty())
        assertTrue(viergeTypes.contenu.isEmpty())
    }

    /**
     * L'intitulé du type est libre : une valeur qu'aucune liste ne contient ne
     * rend pas le fichier illisible, contrairement au statut.
     */
    @Test
    fun `un intitule de type inconnu passe tel quel`() = runTest {
        daoInterventions.enregistrer(INTERVENTION.copy(typeId = null, typeLibelle = "Carotte"))

        val resultat = vierge().restaurer(repository.exporter().contenu)

        assertEquals(
            ResultatRestauration.Reussie(
                types = 0,
                clients = 0,
                equipements = 0,
                photos = 0,
                interventions = 1,
            ),
            resultat,
        )
    }

    @Test
    fun `la liste des types fait partie de la sauvegarde`() = runTest {
        daoTypes.enregistrer(TYPE)
        daoTypes.enregistrer(TYPE.copy(id = "t2", libelle = "Entretien annuel"))

        val export = repository.exporter()
        val autreInterventions = FauxInterventionDao()
        val autreTypes = FauxTypeInterventionDao(autreInterventions)
        SauvegardeRepository(
            autreInterventions,
            FauxClientDao(),
            autreTypes,
            FauxEquipementDao(autreInterventions),
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(autreInterventions),
            FauxPrestationDao(),
        ).restaurer(export.contenu)

        assertEquals(2, export.types)
        assertEquals(setOf("Fuite de fluide", "Entretien annuel"), autreTypes.contenu.map { it.libelle }.toSet())
    }

    /**
     * Le plus important des cas de compatibilité : une sauvegarde faite avant
     * que les types deviennent modifiables doit rester restaurable, et retrouver
     * l'intitulé français que l'application affichait alors.
     */
    @Test
    fun `une sauvegarde du format 1 se restaure avec l'intitule d'alors`() = runTest {
        val contenu = """
            {
              "format": 1,
              "exporteeLe": "2026-09-09T08:00:00Z",
              "interventions": [
                {
                  "id": "id-1", "date": "2026-09-10", "heure": "08:30",
                  "client": "Boucherie Lemoine", "ville": "Rouen",
                  "typePanne": "ENTRETIEN", "statut": "A_FAIRE"
                }
              ]
            }
        """.trimIndent()

        val resultat = repository.restaurer(contenu)

        assertEquals(
            ResultatRestauration.Reussie(
                types = 0,
                clients = 0,
                equipements = 0,
                photos = 0,
                interventions = 1,
            ),
            resultat,
        )
        val restauree = daoInterventions.contenu.single()
        assertEquals("Entretien préventif", restauree.typeLibelle)
        assertNull("le format 1 ne connaissait pas de liste", restauree.typeId)
    }

    /** Une valeur fixe que l'application n'a jamais connue vaut mieux qu'un vide. */
    @Test
    fun `un type inconnu du format 1 est recopie tel quel`() = runTest {
        val contenu = """
            {
              "format": 1,
              "exporteeLe": "2026-09-09T08:00:00Z",
              "interventions": [
                {
                  "id": "id-1", "date": "2026-09-10", "heure": "08:30",
                  "client": "Boucherie Lemoine", "ville": "Rouen",
                  "typePanne": "AUTRE_CHOSE", "statut": "A_FAIRE"
                }
              ]
            }
        """.trimIndent()

        repository.restaurer(contenu)

        assertEquals("AUTRE_CHOSE", daoInterventions.contenu.single().typeLibelle)
    }

    @Test
    fun `l'export annonce ce qu'il contient`() = runTest {
        daoTypes.enregistrer(TYPE)
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        daoInterventions.enregistrer(INTERVENTION.copy(id = "id-2"))

        val export = repository.exporter()

        assertEquals(1, export.types)
        assertEquals(1, export.clients)
        assertEquals(0, export.equipements)
        assertEquals(2, export.interventions)
    }

    /** Un dépôt dont toutes les tables sont vides, pour restaurer à froid. */
    private fun vierge(): SauvegardeRepository {
        val interventions = FauxInterventionDao()
        return SauvegardeRepository(
            interventions,
            FauxClientDao(),
            FauxTypeInterventionDao(interventions),
            FauxEquipementDao(interventions),
            FauxSuiviDao(),
            FauxDevisDao(),
            FauxParametresDao(),
            FauxTechnicienDao(interventions),
            FauxPrestationDao(),
        )
    }

    private companion object {

        val CLIENT = Client(
            id = "cl-1",
            nom = "Boucherie Lemoine",
            ville = "Rouen",
            adresse = "12 rue des Carmes",
            telephone = "02 35 00 00 00",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )

        val TYPE = TypeIntervention(
            id = "t1",
            libelle = "Fuite de fluide",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )

        val EQUIPEMENT = Equipement(
            id = "eq-1",
            clientId = "cl-1",
            nom = "Vitrine salle 2",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )

        val PHOTO = Photo(
            id = "ph-1",
            equipementId = "eq-1",
            categorie = CategoriePhoto.PLAQUE,
            fichier = "plaque.jpg",
            priseLe = Instant.ofEpochMilli(1_757_500_000_000),
        )

        val INTERVENTION = Intervention(
            id = "id-1",
            date = LocalDate.of(2026, 9, 10),
            heure = LocalTime.of(8, 30),
            client = "Boucherie Lemoine",
            ville = "Rouen",
            typeId = "t1",
            typeLibelle = "Fuite de fluide",
            clientId = "cl-1",
            equipementId = "eq-1",
            equipementNom = "Vitrine salle 2",
            statut = StatutIntervention.EN_COURS,
            notes = "Fuite au détendeur.",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )
    }
}
