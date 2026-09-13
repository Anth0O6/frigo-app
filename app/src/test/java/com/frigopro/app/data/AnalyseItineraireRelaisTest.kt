package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture d'une réponse du relais.
 *
 * Beaucoup plus courte que celle qu'il a remplacée, et c'est le signe que le
 * relais fait son travail : les pièges d'un service de cartographie — entiers
 * rendus en texte, durées suffixées, prix en deux morceaux — sont absorbés là-bas
 * et éprouvés là-bas (`relais/worker.test.mjs`). Ce qui reste ici tient en
 * quatre nombres, et l'essentiel des cas porte sur ce qui **ne doit pas** passer
 * pour un trajet.
 */
class AnalyseItineraireRelaisTest {

    @Test
    fun `une reponse complete se lit`() {
        val resultat = AnalyseItineraireRelais.lire(
            200,
            """{"version":1,"distanceKm":24.87,"dureeMinutes":22,
               "peages":0,"peagesConnus":false}""",
        )

        val trouve = resultat as ResultatItineraire.Trouve
        assertEquals(24.87, trouve.distanceKm, 0.001)
        assertEquals(22, trouve.dureeMinutes)
        assertTrue("le relais dit ne pas savoir : il faut le croire", !trouve.peagesConnus)
    }

    /**
     * Le jour où le relais saura chiffrer les péages, l'application saura déjà
     * les lire : le contrat les prévoit, et seule leur source changera.
     */
    @Test
    fun `un peage chiffre est repris et marque connu`() {
        val trouve = AnalyseItineraireRelais.lire(
            200,
            """{"distanceKm":120.0,"dureeMinutes":75,"peages":8.4,"peagesConnus":true}""",
        ) as ResultatItineraire.Trouve

        assertEquals(8.40, trouve.peages, 0.001)
        assertTrue(trouve.peagesConnus)
    }

    /**
     * Un trajet de zéro kilomètre n'est pas un trajet : c'est deux adresses
     * tombées au même endroit. Le facturer serait facturer un déplacement qui
     * n'a pas eu lieu.
     */
    @Test
    fun `un trajet nul est un echec, pas un deplacement gratuit`() {
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.ADRESSE_INTROUVABLE),
            AnalyseItineraireRelais.lire(200, """{"distanceKm":0,"dureeMinutes":0}"""),
        )
    }

    /** Chaque échec nommé par le relais garde son sens jusqu'à l'écran. */
    @Test
    fun `les echecs du relais gardent leur sens`() {
        fun echec(code: Int, corps: String) = AnalyseItineraireRelais.lire(code, corps)

        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.ADRESSE_INTROUVABLE),
            echec(404, """{"erreur":"ADRESSE_INTROUVABLE"}"""),
        )
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.QUOTA_EPUISE),
            echec(429, """{"erreur":"QUOTA_EPUISE"}"""),
        )
        assertEquals(
            "un relais mal configuré n'est pas la faute de l'utilisateur",
            ResultatItineraire.Echec(RaisonEchec.RELAIS_INDISPONIBLE),
            echec(500, """{"erreur":"RELAIS_NON_CONFIGURE"}"""),
        )
        assertEquals(
            "un code d'erreur sans corps reste lisible",
            ResultatItineraire.Echec(RaisonEchec.SERVICE_INDISPONIBLE),
            echec(502, ""),
        )
    }

    @Test
    fun `une reponse illisible ne fait pas tomber l'application`() {
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE),
            AnalyseItineraireRelais.lire(200, "ceci n'est pas du json"),
        )
        assertEquals(
            "un succès sans distance n'est pas un succès",
            ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE),
            AnalyseItineraireRelais.lire(200, """{"version":1}"""),
        )
    }

    /**
     * Le message d'échec ne doit **jamais** renvoyer l'utilisateur vers une
     * configuration : c'est tout l'objet du relais, et le seul message qui parle
     * d'un réglage s'adresse à qui a construit l'application.
     */
    @Test
    fun `aucun message ne demande de creer un compte ou une cle`() {
        val suspects = listOf("clé", "cle ", "compte", "API", "console")
        val fautifs = RaisonEchec.entries.filter { raison ->
            raison != RaisonEchec.RELAIS_INDISPONIBLE &&
                suspects.any { raison.message.contains(it, ignoreCase = true) }
        }

        assertEquals("aucun message ne doit renvoyer à une configuration", emptyList<RaisonEchec>(), fautifs)
    }

    @Test
    fun `les adresses partent echappees`() {
        val corps = AnalyseItineraireRelais.corpsRequete(
            """3 rue "Neuve", L'Épicerie""",
            "  Lyon  ",
        )

        assertTrue("le JSON reste analysable", corps.contains("\\\"Neuve\\\""))
        assertTrue("les espaces de bord sont retirés", corps.contains("\"Lyon\""))
    }
}
