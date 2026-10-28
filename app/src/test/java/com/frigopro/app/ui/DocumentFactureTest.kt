package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.LigneFacture
import com.frigopro.app.data.NatureFacture
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
        assertFalse("rien n'y est dit hors taxes", document.horsTaxes)
        assertFalse(document.totaux.any { it.intitule.contains("HT") })
        assertTrue("assujettie, elle l'est", DocumentFacture.de(facture(), entreprise, client, LE_12_MARS).horsTaxes)
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

    /**
     * Un SIRET manquant se dit à qui émet, **jamais au client** : la mention
     * « (SIRET non renseigné dans les Réglages) » partait sur le PDF. C'est la
     * confirmation d'émission qui le rappelle désormais, par [manquesEmetteur].
     */
    @Test
    fun `un SIRET manquant est signale a qui emet, pas au client`() {
        val sansSiret = entreprise.copy(entrepriseSiret = "")
        val facture = DocumentFacture.de(facture(), sansSiret, client, LE_12_MARS)
        val avoir = DocumentFacture.de(
            facture().let { it.copy(facture = it.facture.copy(nature = NatureFacture.AVOIR)) },
            sansSiret,
            client,
            LE_12_MARS,
        )

        listOf(facture, avoir).forEach { document ->
            val imprime = document.mentions + document.emetteur + document.dates
            assertFalse(
                "aucune note technique sur le document du client : $imprime",
                imprime.any { it.contains("SIRET") || it.contains("Réglages") },
            )
        }
        assertEquals(listOf("le SIRET"), manquesEmetteur(sansSiret))
        assertTrue("rien ne manque à une entreprise complète", manquesEmetteur(entreprise).isEmpty())
    }

    @Test
    fun `le nom du fichier est celui du numero, assaini`() {
        assertEquals("FAC-2026-0042.pdf", DocumentFacture.de(facture(), entreprise, client, LE_12_MARS).nomFichier)
    }

    // — L'avoir ——————————————————————————————————————————————————————————

    /**
     * Un avoir dit ce qu'il corrige : le numéro **et la date** de la facture
     * d'origine, en tête comme en pied.
     */
    @Test
    fun `l'avoir porte la reference de la facture d'origine`() {
        val document = DocumentFacture.de(avoir(), entreprise, client, LE_20_MARS)

        assertEquals("AVOIR", document.titre)
        assertEquals("AV-2026-0001", document.numero)
        assertTrue(document.dates.contains("Émis le 20/03/2026"))
        assertTrue(document.dates.contains("Sur la facture FAC-2026-0042 du 12/03/2026"))
        assertTrue(
            document.mentions.any { it.contains("FAC-2026-0042") && it.contains("12/03/2026") },
        )
        assertTrue("le destinataire est celui de la facture", document.destinataire.contains("Boucherie Morel"))
    }

    /**
     * Rien de ce qui décrit un paiement attendu : sur un avoir c'est l'entreprise
     * qui doit, et des pénalités imprimées laisseraient croire qu'on demande au
     * client de régler un crédit.
     */
    @Test
    fun `l'avoir ne porte ni echeance ni penalites`() {
        val document = DocumentFacture.de(avoir(), entreprise, client, LE_20_MARS)
        val tout = (document.dates + document.mentions).joinToString(" ")

        assertFalse(tout.contains("échéance", ignoreCase = true))
        assertFalse(tout.contains("pénalités"))
        assertFalse(tout.contains("D. 441-5"))
        assertFalse(tout.contains(Nombres.enEuros(Facture.INDEMNITE_RECOUVREMENT)))
        assertFalse(tout.contains("escompte"))
    }

    /** Positif, sous un titre qui dit ce que c'est : c'est le titre qui porte le sens. */
    @Test
    fun `le total de l'avoir est positif et se nomme`() {
        val document = DocumentFacture.de(avoir(), entreprise, client, LE_20_MARS)

        assertEquals("TOTAL DE L'AVOIR", document.totaux.last().intitule)
        assertEquals(Nombres.enEuros(720.0), document.totaux.last().valeur)
        assertFalse(document.totaux.any { it.intitule.contains("À PAYER") })
    }

    /** La franchise en base suit l'avoir : c'est le régime de sa facture. */
    @Test
    fun `l'avoir d'une facture en franchise garde la mention`() {
        val document = DocumentFacture.de(avoir(assujettiTva = false), entreprise, client, LE_20_MARS)

        assertTrue(document.mentions.contains(Parametres.MENTION_FRANCHISE))
    }

    @Test
    fun `le fichier d'un avoir porte son propre nom`() {
        assertEquals("AV-2026-0001.pdf", DocumentFacture.de(avoir(), entreprise, client, LE_20_MARS).nomFichier)
        assertEquals(
            "un brouillon d'avoir ne s'appelle jamais « facture »",
            "avoir.pdf",
            DocumentFacture.de(avoir(numero = ""), entreprise, client, LE_20_MARS).nomFichier,
        )
    }

    private fun avoir(numero: String = "AV-2026-0001", assujettiTva: Boolean = true): FactureComplete {
        val avoir = Facture(
            id = "av-1",
            numero = numero,
            nature = NatureFacture.AVOIR,
            factureOrigineId = "fac-1",
            factureOrigineNumero = "FAC-2026-0042",
            factureOrigineEmiseLe = LE_12_MARS,
            clientNom = "Boucherie Morel",
            clientAdresse = "8 place du Marché, Lyon",
            objet = "Fuite de fluide — INT-2603-001",
            statut = if (numero.isBlank()) StatutFacture.BROUILLON else StatutFacture.EMISE,
            tauxTva = 20.0,
            assujettiTva = assujettiTva,
            emiseLe = if (numero.isBlank()) null else LE_20_MARS,
        )
        return FactureComplete(avoir, lignes(avoir.id))
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
        val LE_20_MARS: LocalDate = LocalDate.of(2026, 3, 20)
    }
}
