package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.Parametres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que le devis imprimé dit, et ce qu'il ne doit pas dire.
 *
 * Ces tests portent sur un document qui **part chez un client** : une mention
 * légale oubliée, une TVA affichée par une entreprise en franchise en base, un
 * total qui ne correspond pas aux lignes au-dessus sont des fautes dont
 * l'entreprise répond, et qu'on ne découvre qu'après l'envoi.
 */
class DocumentDevisTest {

    private val mai = LocalDate.of(2026, 5, 14)

    private val entreprise = Parametres(
        technicien = "Karim Benali",
        attestation = "FR-2019-0847",
        entreprise = "Froid Services Rouen",
        entrepriseAdresse = "8 rue des Charrettes, 76000 Rouen",
        entrepriseTelephone = "02 35 00 00 00",
        entrepriseEmail = "contact@froid-services.fr",
        entrepriseSiret = "812 345 678 00019",
        logoFichier = "logo.jpg",
    )

    private val client = Client(
        id = "cl-1",
        nom = "Boucherie Lemoine",
        ville = "Rouen",
        adresse = "12 rue des Carmes",
        telephone = "02 35 11 11 11",
    )

    private fun devis(
        assujetti: Boolean = true,
        tvaOfferte: Boolean = false,
        lignes: List<LigneDevis> = listOf(
            LigneDevis(devisId = "d-1", designation = "Compresseur", quantite = 1.0, prixUnitaire = 1000.0),
        ),
    ) = DevisComplet(
        devis = Devis(
            id = "d-1",
            numero = "DEV-2605-007",
            clientId = "cl-1",
            clientNom = "Boucherie Lemoine",
            equipementNom = "Chambre froide positive",
            objet = "Remplacement compresseur",
            tauxTva = 20.0,
            assujettiTva = assujetti,
            tvaOfferte = tvaOfferte,
            creeLe = mai,
            valableJusquau = mai.plusMonths(1),
        ),
        lignes = lignes,
    )

    @Test
    fun `l'en-tete porte l'entreprise, ses coordonnees et son SIRET`() {
        val document = DocumentDevis.de(devis(), entreprise, client, mai)

        assertEquals("Froid Services Rouen", document.emetteur.first())
        assertTrue(document.emetteur.any { it.contains("8 rue des Charrettes") })
        assertTrue(document.emetteur.any { it.contains("02 35 00 00 00") })
        assertTrue(document.emetteur.any { it.contains("SIRET 812 345 678 00019") })
        assertTrue(
            "l'attestation fluides se porte sur les documents",
            document.emetteur.any { it.contains("FR-2019-0847") },
        )
        assertEquals("logo.jpg", document.logoFichier)
    }

    /**
     * Sans entreprise renseignée, le technicien prend sa place : un devis doit dire
     * de qui il vient, et c'est le cas normal d'un artisan qui n'a pas encore ouvert
     * les Réglages. Le document reste produisible — mieux vaut un devis sans
     * en-tête que pas de devis.
     */
    @Test
    fun `sans entreprise, le technicien tient l'en-tete`() {
        val document = DocumentDevis.de(
            devis(),
            Parametres(technicien = "Karim Benali"),
            client,
            mai,
        )

        assertEquals("Karim Benali", document.emetteur.first())
        assertTrue(
            "et le SIRET manquant est signalé, à nous et non au client",
            document.mentions.any { it.contains("SIRET non renseigné") },
        )
    }

    /** Sans rien du tout, le document existe quand même, plutôt que d'échouer. */
    @Test
    fun `sans entreprise ni technicien, l'en-tete n'est pas vide`() {
        val document = DocumentDevis.de(devis(), Parametres(), client, mai)

        assertTrue(document.emetteur.first().isNotBlank())
    }

    @Test
    fun `le destinataire vient du nom recopie sur le devis`() {
        // Le carnet dit autre chose : c'est le devis qui fait foi, parce qu'un devis
        // de mars doit continuer d'afficher le client tel qu'il s'appelait en mars.
        val document = DocumentDevis.de(
            devis(),
            entreprise,
            client.copy(nom = "Boucherie Lemoine & Fils"),
            mai,
        )

        assertEquals("Boucherie Lemoine", document.destinataire.first())
        assertTrue("mais l'adresse du carnet, qui n'est pas sur le devis", document.destinataire.any { it == "12 rue des Carmes" })
    }

    /**
     * La TVA figure au taux du devis, puis la remise en dessous quand elle est
     * offerte. L'escamoter en affichant un TTC égal au HT donnerait un document
     * faux : la taxe reste due, et c'est une remise commerciale qui la prend en
     * charge.
     */
    @Test
    fun `la TVA offerte apparait en remise, pas en taux a zero`() {
        val document = DocumentDevis.de(devis(tvaOfferte = true), entreprise, client, mai)

        val intitules = document.totaux.map { it.intitule }
        assertTrue("la TVA est toujours due et affichée", intitules.any { it.startsWith("TVA 20") })
        assertTrue(intitules.any { it.contains("Remise commerciale") })
        assertEquals(
            "et le client paie le hors taxes",
            "1 000,00 €",
            sansEspacesFines(document.totaux.last().valeur),
        )
        assertTrue("le total à payer est mis en avant", document.totaux.last().forte)
    }

    /**
     * En franchise en base, la mention de l'article 293 B est obligatoire, et son
     * absence est un manquement. Elle vient de la constante partagée, pour que le
     * PDF et l'écran ne puissent pas la formuler différemment.
     */
    @Test
    fun `en franchise en base, pas de TVA mais la mention obligatoire`() {
        val document = DocumentDevis.de(devis(assujetti = false), entreprise, client, mai)

        assertFalse(
            "aucune ligne de TVA",
            document.totaux.any { it.intitule.contains("TVA", ignoreCase = true) },
        )
        assertTrue(document.mentions.contains(Parametres.MENTION_FRANCHISE))
    }

    /** Une entreprise assujettie ne porte pas la mention : l'y mettre serait faux. */
    @Test
    fun `une entreprise assujettie ne porte pas la mention de franchise`() {
        val document = DocumentDevis.de(devis(), entreprise, client, mai)

        assertFalse(document.mentions.contains(Parametres.MENTION_FRANCHISE))
    }

    /**
     * Une ligne offerte imprime son prix d'origine et le mot « offert » : le client
     * doit lire ce qu'on lui a donné, sinon le geste n'est pas un argument de vente
     * mais une ligne à zéro inexpliquée.
     */
    @Test
    fun `une ligne offerte imprime son prix et le geste`() {
        val document = DocumentDevis.de(
            devis(
                lignes = listOf(
                    LigneDevis(devisId = "d-1", designation = "Compresseur", quantite = 1.0, prixUnitaire = 1000.0),
                    LigneDevis(
                        devisId = "d-1",
                        designation = "Déplacement",
                        quantite = 1.0,
                        unite = "forfait",
                        prixUnitaire = 45.0,
                        offerte = true,
                    ),
                ),
            ),
            entreprise,
            client,
            mai,
        )

        val offerte = document.lignes.last()
        assertTrue(offerte.offerte)
        assertTrue("son prix est lisible", offerte.montant.contains("45"))
        assertTrue(offerte.montant.contains("offert"))
        assertEquals(
            "et elle ne compte pas dans le total",
            "1 000,00 €",
            sansEspacesFines(document.totaux.first().valeur),
        )
    }

    @Test
    fun `l'unite accompagne la quantite`() {
        val document = DocumentDevis.de(
            devis(
                lignes = listOf(
                    LigneDevis(devisId = "d-1", designation = "Charge", quantite = 6.2, unite = "kg", prixUnitaire = 44.0),
                    LigneDevis(devisId = "d-1", designation = "Pièce", quantite = 2.0, prixUnitaire = 10.0),
                ),
            ),
            entreprise,
            client,
            mai,
        )

        assertEquals("6,2 kg", document.lignes.first().quantite)
        assertEquals("sans unité, pas d'espace en trop", "2", document.lignes.last().quantite)
    }

    @Test
    fun `les dates d'etablissement et de validite sont imprimees`() {
        val document = DocumentDevis.de(devis(), entreprise, client, mai)

        assertTrue(document.dates.any { it.contains("14/05/2026") })
        assertTrue(document.dates.any { it.contains("14/06/2026") })
    }

    /**
     * Le nom du fichier est celui que le client verra en pièce jointe : il doit
     * porter le numéro du devis, et ne contenir que ce qu'un système de fichiers
     * accepte — un numéro exotique ne doit pas produire un nom illégal.
     */
    @Test
    fun `le nom du fichier porte le numero, assaini`() {
        assertEquals("DEV-2605-007.pdf", DocumentDevis.de(devis(), entreprise, client, mai).nomFichier)

        val sansNumero = devis().let { it.copy(devis = it.devis.copy(numero = "")) }
        assertEquals("devis.pdf", DocumentDevis.de(sansNumero, entreprise, client, mai).nomFichier)

        val douteux = devis().let { it.copy(devis = it.devis.copy(numero = "../bases/frigopro")) }
        val nom = DocumentDevis.de(douteux, entreprise, client, mai).nomFichier
        assertFalse("aucun séparateur de chemin", nom.contains('/'))
        assertEquals("..-bases-frigopro.pdf", nom)
    }

    @Test
    fun `l'objet et la machine sont repris`() {
        val document = DocumentDevis.de(devis(), entreprise, client, mai)

        assertEquals("Remplacement compresseur", document.objet)
        assertEquals("Chambre froide positive", document.machine)
    }

    /**
     * Les espaces de groupement ramenées à une espace ordinaire.
     *
     * `Locale.FRANCE` produit tantôt une espace insécable, tantôt une insécable
     * fine, selon la version du JDK : un test qui comparerait l'une ou l'autre
     * passerait ici et échouerait sur le runner de la CI.
     */
    private fun sansEspacesFines(montant: String): String =
        montant.replace('\u00A0', ' ').replace('\u202F', ' ')

    /** Le devis gratuit et le cadre d'accord : c'est ce qui en fait un document à renvoyer. */
    @Test
    fun `le document invite a l'accord`() {
        val document = DocumentDevis.de(devis(), entreprise, client, mai)

        assertTrue(document.mentions.any { it.contains("Bon pour accord") })
    }
}
