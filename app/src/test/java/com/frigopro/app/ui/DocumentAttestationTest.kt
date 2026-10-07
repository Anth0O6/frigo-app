package com.frigopro.app.ui

import com.frigopro.app.data.AffectationGamme
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.GammeMaintenance
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Periodicite
import com.frigopro.app.data.ReleveGamme
import com.frigopro.app.data.SuiviMaintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que l'attestation d'entretien dit, et ce qu'elle refuse de dire.
 *
 * Le document part **chez le client**, ce qui place le test au même rang que
 * ceux du devis et de la facture : une visite comptée en trop, un taux flatteur
 * ou une mention de conformité sont des fautes qu'on découvre après l'envoi, et
 * dont l'entreprise répond.
 */
class DocumentAttestationTest {

    private val jour = LocalDate.of(2026, 12, 31)

    private val entreprise = Parametres(
        entreprise = "FrigoPro",
        entrepriseAdresse = "12 rue des Lilas, Lyon",
    )

    private val mensuelle = GammeMaintenance(
        id = "ga-1",
        libelle = "Visite mensuelle",
        periodicite = Periodicite.MENSUEL,
    )

    private val annuelle = GammeMaintenance(
        id = "ga-2",
        libelle = "Contrôle annuel",
        periodicite = Periodicite.ANNUEL,
    )

    private val vitrine = Equipement(
        id = "eq-1",
        clientId = "cl-1",
        nom = "Vitrine salle 2",
        marque = "Costan",
        modele = "Gazelle",
    )

    private fun visite(
        date: LocalDate,
        equipementId: String = vitrine.id,
        gammeId: String = mensuelle.id,
    ) = ReleveGamme(
        equipementId = equipementId,
        equipementNom = "Vitrine salle 2",
        gammeId = gammeId,
        gammeLibelle = "Visite mensuelle",
        faitLe = date,
    )

    private fun attestation(
        equipements: List<Equipement> = listOf(vitrine),
        gammes: List<GammeMaintenance> = listOf(mensuelle),
        affectations: List<AffectationGamme> = listOf(
            AffectationGamme(
                id = "af-1",
                equipementId = vitrine.id,
                gammeId = mensuelle.id,
                depuisLe = LocalDate.of(2025, 12, 1),
            ),
        ),
        releves: List<ReleveGamme> = emptyList(),
        debut: LocalDate = LocalDate.of(2026, 1, 1),
        fin: LocalDate = LocalDate.of(2026, 12, 31),
    ) = SuiviMaintenance.attestation(
        clientNom = "Boucherie Morel",
        clientAdresse = "4 place du Marché, Lyon",
        clientIds = setOf("cl-1"),
        equipements = equipements,
        gammes = gammes,
        affectations = affectations,
        releves = releves,
        debut = debut,
        fin = fin,
    )

    private fun lignesDe(document: DocumentImprime): List<String> =
        document.blocs.flatMap { bloc -> bloc.lignes.map { "${it.intitule} ${it.valeur}" } }

    /**
     * Le bilan en tête, avec **les deux nombres** : « 12 / 12 attendues » se
     * vérifie ligne à ligne au-dessous, « 100 % » ne se vérifie pas.
     */
    @Test
    fun `le bilan vient avant le detail et annonce les deux nombres`() {
        val visites = (1..12).map { visite(LocalDate.of(2026, it, 10)) }

        val document = DocumentAttestation.de(attestation(releves = visites), entreprise, jour)

        assertEquals("ATTESTATION D'ENTRETIEN", document.titre)
        assertEquals("", document.numero)
        assertTrue(
            "un destinataire, contrairement au registre",
            "Boucherie Morel" in document.destinataire,
        )
        assertTrue(document.enBlocs)
        assertEquals("Bilan de la période", document.blocs.first().intitule)
        val bilan = document.blocs.first().lignes.map { "${it.intitule} ${it.valeur}" }
        assertTrue(bilan.any { it.contains("12 / 12 attendues") })
        assertTrue(bilan.any { it.contains("100 %") })
    }

    /**
     * **Une machine suivie et jamais visitée le dit.** C'est précisément le cas
     * où la tentation serait de taire la ligne : un parc dont il manque une
     * machine se lit « tout a été fait », ce qui est l'inverse de la vérité.
     */
    @Test
    fun `une machine jamais visitee reste au document et le dit`() {
        val document = DocumentAttestation.de(attestation(), entreprise, jour)

        val lignes = lignesDe(document)
        assertTrue(
            "la machine a son bloc",
            document.blocs.any { it.intitule.startsWith("Vitrine salle 2") },
        )
        assertTrue(lignes.any { it.contains("aucune visite consignée") })
        assertTrue(
            "et le document le dit en mentions",
            document.mentions.any { it.contains("Aucune visite n'a été consignée") },
        )
    }

    /**
     * « Pas de contrat » et « contrat non honoré » ne s'écrivent pas de la même
     * façon, et c'est la distinction qui compte sur un document remis au client.
     */
    @Test
    fun `un parc sans gamme ne dit pas la meme chose qu'un parc non visite`() {
        val sansPlan = attestation(gammes = emptyList(), affectations = emptyList())

        val document = DocumentAttestation.de(sansPlan, entreprise, jour)

        assertTrue(sansPlan.vide)
        assertTrue(
            document.mentions.any { it.contains("Aucune gamme de maintenance n'est rattachée") },
        )
        assertFalse(
            document.mentions.any { it.contains("Aucune visite n'a été consignée") },
        )
        assertTrue(
            document.blocs.first().lignes.any { it.intitule.contains("ne suit de gamme") },
        )
    }

    /**
     * **Il ne dit pas « conforme ».** Il rapporte des dates, et l'application
     * n'a pas qualité à délivrer un certificat — même règle que la checklist du
     * compte-rendu. Il ne se signe pas non plus : une année de visites dont le
     * client n'a vu que quelques-unes n'est pas quelque chose qu'il peut attester.
     */
    @Test
    fun `il ne certifie rien et ne se signe pas`() {
        val document = DocumentAttestation.de(
            attestation(releves = listOf(visite(LocalDate.of(2026, 3, 4)))),
            entreprise,
            jour,
        )

        val tout = (document.mentions + lignesDe(document) + document.objet).joinToString(" ")
        assertTrue(
            "le document dit lui-même ce qu'il n'est pas",
            document.mentions.any { it.contains("ne vaut pas certificat de conformité") },
        )
        assertFalse("aucune affirmation de conformité", tout.contains("est conforme"))
        assertFalse("ni de certification", tout.contains("certifie"))
        // Pas de cadre de signature : c'est ce qui distingue l'attestation du
        // compte-rendu, et `enBlocs` ne doit pas le devoir à la signature.
        assertEquals("", document.mentionSignature)
        assertNull(document.signatureFichier)
    }

    /**
     * Les dates sont **toutes** là : un client qui conteste pointe une date, et
     * « 12 visites » ne lui répondrait pas. Douze dates ne tiennent pas sur une
     * ligne, elles sont donc enveloppées — et aucune ne doit se perdre au
     * passage.
     */
    @Test
    fun `toutes les dates des visites paraissent, enveloppees si besoin`() {
        val visites = (1..12).map { visite(LocalDate.of(2026, it, 10)) }

        val document = DocumentAttestation.de(attestation(releves = visites), entreprise, jour)

        val tout = lignesDe(document).joinToString(" ")
        (1..12).forEach { mois ->
            val attendue = "%02d/%02d/26".format(10, mois)
            assertTrue("la visite de $attendue est au document", attendue in tout)
        }
    }

    /**
     * Une visite **hors période** ne compte pas, et c'est ce qui fait de
     * l'attestation un document daté plutôt qu'un cumul.
     */
    @Test
    fun `une visite hors periode ne compte pas`() {
        val releves = listOf(
            visite(LocalDate.of(2025, 12, 20)),
            visite(LocalDate.of(2026, 1, 15)),
            visite(LocalDate.of(2027, 1, 2)),
        )

        val resultat = attestation(releves = releves)

        assertEquals(1, resultat.faites)
        assertEquals(listOf(LocalDate.of(2026, 1, 15)), resultat.lignes.single().visites)
    }

    /**
     * **Le dénominateur part du départ du plan de chaque machine.** Une armoire
     * rattachée en octobre ne doit pas douze visites sur l'année : elle en doit
     * deux, et compter douze aurait affiché un manquement sur un contrat
     * respecté.
     */
    @Test
    fun `une machine entree au plan en cours d'annee ne doit que sa part`() {
        val tardive = attestation(
            affectations = listOf(
                AffectationGamme(
                    id = "af-1",
                    equipementId = vitrine.id,
                    gammeId = mensuelle.id,
                    depuisLe = LocalDate.of(2026, 10, 1),
                ),
            ),
            releves = listOf(
                visite(LocalDate.of(2026, 11, 3)),
                visite(LocalDate.of(2026, 12, 2)),
            ),
        )

        assertEquals(2, tardive.attendues)
        assertEquals(2, tardive.faites)
        assertEquals(1.0, tardive.realisation.taux!!, 1e-9)
    }

    /**
     * Rien n'était attendu : le taux **n'existe pas**, il ne vaut pas zéro. Même
     * règle que l'équivalent CO₂ d'un fluide hors catalogue et que la marge sans
     * prix d'achat — un chiffre inventé part chez le client.
     */
    @Test
    fun `un taux sans rien d'attendu n'est pas chiffre`() {
        val neuve = attestation(
            affectations = listOf(
                AffectationGamme(
                    id = "af-1",
                    equipementId = vitrine.id,
                    gammeId = mensuelle.id,
                    depuisLe = LocalDate.of(2026, 12, 20),
                ),
            ),
            fin = LocalDate.of(2026, 12, 31),
        )

        assertEquals(0, neuve.attendues)
        assertNull(neuve.realisation.taux)
        val document = DocumentAttestation.de(neuve, entreprise, jour)
        assertTrue(
            document.blocs.first().lignes.any { it.valeur.contains("aucune visite attendue") },
        )
    }

    /**
     * Les gammes d'une machine se suivent **de la plus fréquente à la plus
     * rare**, et les machines par leur nom : c'est l'ordre dans lequel un gérant
     * parcourt le document, en cherchant la sienne.
     */
    @Test
    fun `les lignes sont rangees par machine puis par cadence`() {
        val chambre = Equipement(id = "eq-2", clientId = "cl-1", nom = "Chambre froide")
        val resultat = attestation(
            equipements = listOf(vitrine, chambre),
            gammes = listOf(annuelle, mensuelle),
            affectations = listOf(
                AffectationGamme("af-1", vitrine.id, annuelle.id, LocalDate.of(2025, 1, 1)),
                AffectationGamme("af-2", vitrine.id, mensuelle.id, LocalDate.of(2025, 1, 1)),
                AffectationGamme("af-3", chambre.id, mensuelle.id, LocalDate.of(2025, 1, 1)),
            ),
        )

        assertEquals(
            listOf(
                "Chambre froide" to "Visite mensuelle",
                "Vitrine salle 2" to "Visite mensuelle",
                "Vitrine salle 2" to "Contrôle annuel",
            ),
            resultat.lignes.map { it.machineNom to it.gammeLibelle },
        )
        assertEquals(listOf("Chambre froide", "Vitrine salle 2"), resultat.machines)
    }

    /**
     * Le parc d'un **autre** client n'y entre pas. L'évidence mérite un test :
     * une attestation qui annonce les machines du voisin est une fuite de
     * données chez un client.
     */
    @Test
    fun `le parc d'un autre client n'entre pas dans l'attestation`() {
        val ailleurs = Equipement(id = "eq-9", clientId = "cl-2", nom = "Rooftop du voisin")
        val resultat = attestation(
            equipements = listOf(vitrine, ailleurs),
            affectations = listOf(
                AffectationGamme("af-1", vitrine.id, mensuelle.id, LocalDate.of(2026, 1, 1)),
                AffectationGamme("af-2", ailleurs.id, mensuelle.id, LocalDate.of(2026, 1, 1)),
            ),
        )

        assertEquals(listOf("Vitrine salle 2"), resultat.machines)
    }

    /**
     * Le nom du fichier porte le client et l'année : « attestation-2026.pdf »
     * dans un dossier de téléchargements ne dirait pas de qui elle est, et c'est
     * là qu'on la retrouve six mois plus tard.
     */
    @Test
    fun `le nom du fichier porte le client et l'annee`() {
        val document = DocumentAttestation.de(attestation(), entreprise, jour)

        assertEquals("attestation-entretien-boucherie-morel-2026.pdf", document.nomFichier)
    }

    /**
     * Les années proposées sont **dérivées des visites**, l'année courante
     * toujours comprise : proposer trois années dont deux sont vides ferait
     * ouvrir deux documents blancs, et ne pas proposer l'année en cours
     * interdirait l'attestation qu'on remet avec la facture de décembre.
     */
    @Test
    fun `les annees proposees viennent des visites, et l'annee courante toujours`() {
        val annees = SuiviMaintenance.anneesDe(
            clientIds = setOf("cl-1"),
            equipements = listOf(vitrine),
            releves = listOf(
                visite(LocalDate.of(2024, 5, 2)),
                visite(LocalDate.of(2026, 5, 2)),
                visite(LocalDate.of(2026, 7, 2)),
                visite(LocalDate.of(2023, 1, 2), equipementId = "eq-9"),
            ),
            aujourdhui = LocalDate.of(2027, 2, 1),
        )

        assertEquals(listOf(2027, 2026, 2024), annees)
    }
}
