package com.frigopro.app.ui

import com.frigopro.app.data.Chrono
import com.frigopro.app.data.Client
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.MouvementFluide
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.PiecePosee
import com.frigopro.app.data.PointChecklist
import com.frigopro.app.data.Releve
import com.frigopro.app.data.SensFluide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ce que le compte-rendu imprimé dit.
 *
 * Le test porte sur ce que le document **n'invente pas**, parce que c'est là
 * qu'est le risque : un compte-rendu est signé par le client et vaut preuve de
 * passage. Une valeur inventée y engage l'entreprise, une valeur tue la
 * désarme — et aucune des deux ne se voit avant que le document soit parti.
 */
class DocumentRapportTest {

    private val zone = ZoneId.of("Europe/Paris")

    private val jour = LocalDate.of(2026, 5, 14)

    private val entreprise = Parametres(
        entreprise = "FrigoPro",
        entrepriseAdresse = "12 rue des Lilas, Lyon",
        attestation = "ATT-2024-118",
    )

    private val client = Client(
        nom = "Boucherie Morel",
        ville = "Lyon",
        adresse = "8 place du Marché",
        telephone = "0478000000",
    )

    private val intervention = Intervention(
        date = jour,
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        typeLibelle = "Dépannage chambre froide",
        technicienNom = "Karim B.",
        equipementNom = "Chambre froide positive",
        numero = "INT-2605-018",
        notes = "Remplacement du détendeur, tirage au vide et recharge.",
        chrono = Chrono(arriveeLe = Instant.parse("2026-05-14T06:12:00Z"), cumuleS = 6_420),
    )

    private fun etat(
        intervention: Intervention = this.intervention,
        releve: Releve? = null,
        mouvements: List<MouvementFluide> = emptyList(),
        pieces: List<PiecePosee> = emptyList(),
        checklist: List<PointChecklist> = emptyList(),
        equipement: Equipement? = null,
        parametres: Parametres = entreprise,
    ) = EtatIntervention(
        intervention = intervention,
        client = client,
        equipement = equipement,
        releve = releve,
        mouvements = mouvements,
        pieces = pieces,
        checklist = checklist,
        parametres = parametres,
    )

    private fun document(
        etat: EtatIntervention = etat(),
        ecoule: Duration = Duration.ofSeconds(6_420),
    ) = DocumentRapport.de(etat = etat, ecoule = ecoule, aujourdhui = jour, zone = zone)

    private fun DocumentImprime.bloc(intitule: String): BlocImprime? =
        blocs.firstOrNull { it.intitule == intitule }

    @Test
    fun `l'en-tete porte l'entreprise, le client et la reference`() {
        val document = document()

        assertEquals("COMPTE-RENDU", document.titre)
        assertEquals("INT-2605-018", document.numero)
        assertTrue(document.emetteur.first().contains("FrigoPro"))
        assertTrue(document.destinataire.contains("Boucherie Morel"))
        assertTrue(document.destinataire.any { it.contains("place du Marché") })
        // Le nom du fichier est celui que le client reçoit : il doit porter la
        // référence, et rien qu'un système de fichiers puisse refuser.
        assertEquals("INT-2605-018.pdf", document.nomFichier)
    }

    /**
     * Le document se reconnaît à ses blocs, et c'est ce qui aiguille toute la
     * chaîne d'impression : un compte-rendu ne passe pas par le tableau chiffré.
     */
    @Test
    fun `le compte-rendu ne chiffre rien`() {
        val document = document(
            etat(
                pieces = listOf(
                    PiecePosee(
                        interventionId = "i",
                        designation = "Détendeur",
                        quantite = 1.0,
                        prixAchat = 137.77,
                    ),
                ),
            ),
        )

        assertTrue(document.enBlocs)
        assertTrue(document.lignes.isEmpty())
        assertTrue(document.totaux.isEmpty())

        // Le prix d'achat est sur la ligne en base — il sert à la marge — mais il
        // ne sort nulle part sur le document du client. Le test cherche le
        // **symbole** et le montant exact, et non « 137 » : un contrôle qui mord
        // sur un nombre ordinaire finit désactivé, et la propriété part avec lui.
        val imprime = document.blocs.flatMap { it.lignes }
        assertTrue(imprime.none { it.intitule.contains('€') || it.valeur.contains('€') })
        assertTrue(imprime.none { it.valeur.contains("137,77") })
        assertEquals("× 1", imprime.first { it.intitule.startsWith("Détendeur") }.valeur)
    }

    @Test
    fun `un releve non pris ne s'invente pas`() {
        // Une case vide sur un document signé se lit « rien à signaler », ce qui
        // n'est pas « pas mesuré » : le bloc disparaît plutôt que d'aligner des
        // tirets.
        assertNull(document().bloc("Relevés frigorifiques"))

        val partiel = document(etat(releve = Releve(interventionId = "i", bpBar = 2.4)))
        val lignes = partiel.bloc("Relevés frigorifiques")!!.lignes
        assertEquals(1, lignes.size)
        assertEquals("Basse pression", lignes.single().intitule)
        // L'unité est écrite, et la pression dite relative comme au manomètre :
        // l'écart avec l'absolu vaut 1,013 bar, soit près de 7 K en basse pression.
        assertTrue(lignes.single().valeur.contains("bar rel."))
    }

    @Test
    fun `chaque mouvement de fluide est detaille, et les totaux suivent`() {
        val document = document(
            etat(
                mouvements = listOf(
                    MouvementFluide(interventionId = "i", fluide = "R-449A", sens = SensFluide.AJOUT, masseKg = 3.0),
                    MouvementFluide(
                        interventionId = "i",
                        fluide = "R-449A",
                        sens = SensFluide.RECUPERATION,
                        masseKg = 1.0,
                    ),
                ),
            ),
        )

        val lignes = document.bloc("Fluide frigorigène")!!.lignes
        // Deux mouvements puis deux totaux : « 2 kg ajoutés » et « 3 ajoutés,
        // 1 récupéré » ne racontent pas la même intervention, et c'est la
        // seconde qu'un contrôle veut lire.
        assertEquals(4, lignes.size)
        assertTrue(lignes[0].intitule.contains("Ajouté"))
        assertTrue(lignes[1].intitule.contains("Récupéré"))
        assertEquals("Total ajouté", lignes[2].intitule)
        assertEquals("Total récupéré", lignes[3].intitule)

        // Et le renvoi au registre, qui dit où la trace réglementaire est tenue.
        assertTrue(document.mentions.any { it.contains("517/2014") })
    }

    @Test
    fun `un seul mouvement ne se double pas d'un total`() {
        val document = document(
            etat(
                mouvements = listOf(
                    MouvementFluide(interventionId = "i", fluide = "R-134a", sens = SensFluide.AJOUT, masseKg = 2.0),
                ),
            ),
        )

        assertEquals(1, document.bloc("Fluide frigorigène")!!.lignes.size)
    }

    @Test
    fun `un point non fait se voit`() {
        val document = document(
            etat(
                checklist = listOf(
                    PointChecklist(interventionId = "i", libelle = "Contrôle d'étanchéité", fait = true),
                    PointChecklist(interventionId = "i", libelle = "Registre renseigné", fait = false),
                ),
            ),
        )

        val lignes = document.bloc("Points vérifiés")!!.lignes
        assertEquals("Vérifié", lignes[0].valeur)
        // Ni tu, ni arrondi en « conforme » : c'est un relevé de ce qui a été
        // vérifié, pas un certificat.
        assertEquals("Non fait", lignes[1].valeur)
    }

    @Test
    fun `les travaux sont enveloppes sur plusieurs lignes`() {
        val long = "Remplacement du détendeur thermostatique, " .repeat(6)
        val document = document(etat(intervention = intervention.copy(notes = long)))

        val lignes = document.bloc("Travaux réalisés")!!.lignes
        assertTrue(lignes.size > 1)
        // Du texte libre : pas de colonne de droite, sans quoi il s'imprimerait
        // sur un tiers de page.
        assertTrue(lignes.all { it.valeur.isBlank() })
        assertTrue(lignes.all { it.intitule.length <= MiseEnPageRapport.CARACTERES_PAR_LIGNE })
    }

    /**
     * L'heure d'arrivée est celle du chronomètre quand il a tourné : c'est ce
     * qui s'est passé, et non ce qui était prévu.
     */
    @Test
    fun `l'arrivee vient du chrono, et le creneau prend le relais`() {
        assertTrue(document().dates.any { it == "Arrivée 08:12" })

        val sansChrono = document(etat(intervention = intervention.copy(chrono = Chrono())))
        assertTrue(sansChrono.dates.any { it == "Arrivée 08:00" })
    }

    @Test
    fun `le compte-rendu s'exporte avant la cloture, et le dit`() {
        // Un technicien fait signer sur place, avant d'avoir clos : exiger une
        // référence aurait interdit le seul usage qui demande ce document.
        val brouillon = document(etat(intervention = intervention.copy(numero = "")))

        assertEquals("compte-rendu.pdf", brouillon.nomFichier)
        assertTrue(brouillon.mentions.any { it.contains("Référence attribuée à la clôture") })
    }

    /**
     * En sous-traitance, le compte-rendu va à celui chez qui on est allé.
     *
     * Et non au donneur d'ordre : c'est la personne qui a vu les travaux qui
     * signe, et elle n'est pas celle qui paie.
     */
    @Test
    fun `la sous-traitance est dite sans changer de destinataire`() {
        val document = document(
            etat(
                intervention = intervention.copy(
                    clientFactureId = "autre",
                    clientFactureNom = "Groupe Delta",
                ),
            ),
        )

        assertTrue(document.destinataire.contains("Boucherie Morel"))
        val lignes = document.bloc("L'intervention")!!.lignes
        assertEquals("Groupe Delta", lignes.first { it.intitule == "Pour le compte de" }.valeur)
    }

    @Test
    fun `l'attestation manquante est signalee des qu'on touche au fluide`() {
        val sansAttestation = entreprise.copy(attestation = "")
        val mouvement = MouvementFluide(
            interventionId = "i",
            fluide = "R-449A",
            sens = SensFluide.AJOUT,
            masseKg = 1.0,
        )

        val avecFluide = document(etat(mouvements = listOf(mouvement), parametres = sansAttestation))
        assertTrue(avecFluide.mentions.any { it.contains("Attestation de capacité non renseignée") })

        // Sans mouvement de fluide, l'attestation n'est pas en cause : un
        // avertissement qui se déclenche toujours est un avertissement qu'on
        // cesse de lire.
        val sansFluide = document(etat(parametres = sansAttestation))
        assertFalse(sansFluide.mentions.any { it.contains("Attestation") })
    }

    @Test
    fun `la machine et son fluide se lisent ensemble`() {
        val document = document(
            etat(
                equipement = Equipement(clientId = client.id, nom = "Chambre froide", fluide = "R-449A"),
            ),
        )

        assertEquals("Chambre froide positive — R-449A", document.machine)
    }

    @Test
    fun `la signature du client part avec le document`() {
        val signe = document(
            etat(intervention = intervention.copy(signatureFichier = "signature-1.png")),
        )

        assertEquals("signature-1.png", signe.signatureFichier)
        // Ce qu'on signe porte sur les travaux, pas sur un montant : un
        // compte-rendu n'engage aucun paiement.
        assertTrue(signe.mentionSignature.contains("travaux décrits"))
        assertFalse(signe.mentionSignature.contains("accord"))
    }
}
