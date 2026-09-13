package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.LigneFacture
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.StatutFacture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que la facture imprimée dit.
 *
 * Le test porte sur les **mentions**, parce que c'est là que la facture se
 * distingue du devis et que l'erreur coûte cher : sur un devis une mention
 * oubliée est un manque de soin, sur une facture c'est un manquement dont
 * l'entreprise répond — et qui se découvre quand le client refuse de payer les
 * pénalités qu'on n'a pas annoncées.
 */
class DocumentFactureTest {

    private val entreprise = Parametres(
        entreprise = "FrigoPro",
        entrepriseAdresse = "12 rue des Lilas, Lyon",
        entrepriseTelephone = "0472000000",
        entrepriseSiret = "80012345600017",
        attestation = "ATT-2024-118",
    )

    private val client = Client(
        nom = "Boucherie Morel",
        ville = "Lyon",
        adresse = "8 place du Marché",
        telephone = "0478000000",
    )

    @Test
    fun `l'en-tete dit de qui vient la facture et pour qui elle est`() {
        val document = DocumentFacture.de(facture(), entreprise, client, LE_12_MARS)

        assertEquals("FACTURE", document.titre)
        assertEquals("FAC-2026-0042", document.numero)
        assertTrue(document.emetteur.contains("FrigoPro"))
        assertTrue(document.emetteur.contains("SIRET 80012345600017"))
        assertTrue("l'attestation fluides prouve le droit d'intervenir",
            document.emetteur.contains("Attestation fluides ATT-2024-118"))
        assertTrue(document.destinataire.contains("Boucherie Morel"))
    }

    /**
     * L'adresse imprimée est celle **recopiée sur la facture**, pas celle du
     * carnet : qu'un client déménage ne doit pas réécrire une facture de mars.
     */
    @Test
    fun `l'adresse imprimee est celle du jour de la facture`() {
        val demenage = client.copy(adresse = "3 avenue Neuve", ville = "Villeurbanne")

        val document = DocumentFacture.de(facture(), entreprise, demenage, LE_12_MARS)

        assertTrue(document.destinataire.contains("8 place du Marché, Lyon"))
        assertFalse(document.destinataire.any { it.contains("Villeurbanne") })
    }

    @Test
    fun `les dates disent l'emission et l'echeance`() {
        val document = DocumentFacture.de(facture(), entreprise, client, LE_12_MARS)

        assertTrue(document.dates.contains("Émise le 12/03/2026"))
        assertTrue(document.dates.contains("Échéance le 11/04/2026"))
    }

    @Test
    fun `une facture payee le dit`() {
        val payee = facture(
            statut = StatutFacture.PAYEE,
            payeeLe = LocalDate.of(2026, 3, 20),
        )

        val document = DocumentFacture.de(payee, entreprise, client, LE_12_MARS)

        assertTrue(document.dates.contains("Payée le 20/03/2026"))
    }

    /**
     * Les trois mentions obligatoires. Les pénalités sont dues de plein droit
     * dès le lendemain de l'échéance — mais encore faut-il que la facture les
     * ait annoncées.
     */
    @Test
    fun `les mentions obligatoires sont portees`() {
        val document = DocumentFacture.de(facture(), entreprise, client, LE_12_MARS)
        val pied = document.mentions.joinToString(" ")

        assertTrue("l'échéance", pied.contains("Paiement à échéance du 11/04/2026"))
        assertTrue("les pénalités de retard", pied.contains("pénalités"))
        assertTrue(
            "l'indemnité forfaitaire",
            pied.contains(Nombres.enEuros(Facture.INDEMNITE_RECOUVREMENT)),
        )
        assertTrue("son fondement", pied.contains("D. 441-5"))
        assertTrue("l'escompte", pied.contains("escompte"))
    }

    /**
     * Faute de taux convenu, c'est le taux légal qui s'applique de plein droit.
     * Imprimer « 0 % » serait à la fois faux et une renonciation au recours.
     */
    @Test
    fun `sans taux fixe, la facture renvoie au taux legal et jamais a zero`() {
        val document = DocumentFacture.de(facture(tauxPenalites = 0.0), entreprise, client, LE_12_MARS)
        val pied = document.mentions.joinToString(" ")

        assertTrue(pied.contains("Banque centrale européenne"))
        assertTrue(pied.contains("majoré de 10 points"))
        assertFalse("« 0 % » ne doit jamais paraître", pied.contains("0 %"))
    }

    @Test
    fun `un taux fixe est annonce tel quel`() {
        val document = DocumentFacture.de(facture(tauxPenalites = 12.0), entreprise, client, LE_12_MARS)
        val pied = document.mentions.joinToString(" ")

        assertTrue(pied.contains("taux annuel de 12 %"))
        assertFalse(pied.contains("Banque centrale"))
    }

    /** Même règle que sur le devis : c'est le régime de la facture qui tranche. */
    @Test
    fun `la franchise en base porte sa mention et aucune TVA`() {
        val franchise = facture(assujettiTva = false)

        val document = DocumentFacture.de(franchise, entreprise, client, LE_12_MARS)

        assertTrue(document.mentions.contains(Parametres.MENTION_FRANCHISE))
        assertFalse("aucune ligne de TVA", document.totaux.any { it.intitule.startsWith("TVA") })
        assertEquals("TOTAL À PAYER", document.totaux.last().intitule)
        assertEquals(Nombres.enEuros(600.0), document.totaux.last().valeur)
    }

    @Test
    fun `la TVA offerte est une remise, pas un taux a zero`() {
        val offerte = facture(tvaOfferte = true)

        val document = DocumentFacture.de(offerte, entreprise, client, LE_12_MARS)
        val intitules = document.totaux.map { it.intitule }

        assertTrue("la TVA reste due et s'affiche", intitules.contains("TVA 20 %"))
        assertTrue(intitules.any { it.contains("TVA offerte") })
        assertEquals(
            "le client paie le hors taxes",
            Nombres.enEuros(600.0),
            document.totaux.last().valeur,
        )
    }

    @Test
    fun `un SIRET manquant est signale a qui envoie, pas au client`() {
        val document = DocumentFacture.de(
            facture(), entreprise.copy(entrepriseSiret = ""), client, LE_12_MARS,
        )

        assertTrue(document.mentions.any { it.contains("SIRET non renseigné") })
    }

    @Test
    fun `le nom du fichier est celui du numero, assaini`() {
        assertEquals("FAC-2026-0042.pdf", DocumentFacture.de(facture(), entreprise, client, LE_12_MARS).nomFichier)
    }

    private fun facture(
        statut: StatutFacture = StatutFacture.EMISE,
        assujettiTva: Boolean = true,
        tvaOfferte: Boolean = false,
        tauxPenalites: Double = 0.0,
        payeeLe: LocalDate? = null,
    ): FactureComplete {
        val facture = Facture(
            id = "fac-1",
            numero = "FAC-2026-0042",
            clientNom = "Boucherie Morel",
            clientAdresse = "8 place du Marché, Lyon",
            objet = "Fuite de fluide — INT-2603-001",
            statut = statut,
            tauxTva = 20.0,
            tvaOfferte = tvaOfferte,
            assujettiTva = assujettiTva,
            emiseLe = LE_12_MARS,
            echeanceLe = LE_12_MARS.plusDays(30),
            tauxPenalites = tauxPenalites,
            payeeLe = payeeLe,
        )
        return FactureComplete(facture, lignes(facture.id))
    }

    private fun lignes(factureId: String) = listOf(
        LigneFacture(
            factureId = factureId,
            designation = "Main-d'œuvre",
            quantite = 2.0,
            unite = "h",
            prixUnitaire = 300.0,
            rang = 0,
        ),
    )

    private companion object {
        val LE_12_MARS: LocalDate = LocalDate.of(2026, 3, 12)
    }
}
