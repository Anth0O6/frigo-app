package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture d'une réponse de l'API Routes.
 *
 * C'est la moitié éprouvable du calcul d'itinéraire : le transport demande un
 * réseau, l'analyse demande une chaîne de caractères. Et c'est là que sont les
 * pièges — un entier de 64 bits arrive en texte, une durée porte son unité, un
 * prix se lit en deux morceaux, et un succès peut ne contenir aucune route.
 *
 * Les réponses ci-dessous reproduisent la **forme** documentée de l'API. Aucun
 * appel réel n'a lieu ici, et c'est assumé : ce test dit que l'on sait lire ce
 * que l'API annonce, pas que l'API l'annonce encore.
 */
class AnalyseItineraireGoogleTest {

    @Test
    fun `une route avec son peage se lit entierement`() {
        val reponse = """
            {
              "routes": [
                {
                  "distanceMeters": 24870,
                  "duration": "1320s",
                  "travelAdvisory": {
                    "tollInfo": {
                      "estimatedPrice": [
                        {"currencyCode": "EUR", "units": "8", "nanos": 400000000}
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val resultat = AnalyseItineraireGoogle.lire(200, reponse)

        val trouve = resultat as ResultatItineraire.Trouve
        assertEquals(24.87, trouve.distanceKm, 0.001)
        assertEquals(22, trouve.dureeMinutes)
        assertEquals("8 EUR et 400 millions de nanos font 8,40 EUR", 8.40, trouve.peages, 0.001)
        assertTrue(trouve.peagesConnus)
    }

    /**
     * Le cas qui compte le plus : un `tollInfo` présent mais vide veut dire « j'ai
     * regardé, il n'y a pas de péage ». C'est un zéro **connu**, et le dire inconnu
     * ferait ressaisir un péage qui n'existe pas.
     */
    @Test
    fun `un trajet sans peage se distingue d'un peage inconnu`() {
        val sansPeage = """
            {"routes": [{"distanceMeters": 8200, "duration": "600s",
             "travelAdvisory": {"tollInfo": {"estimatedPrice": []}}}]}
        """.trimIndent()
        val silencieux = """
            {"routes": [{"distanceMeters": 8200, "duration": "600s"}]}
        """.trimIndent()

        val avec = AnalyseItineraireGoogle.lire(200, sansPeage) as ResultatItineraire.Trouve
        val sans = AnalyseItineraireGoogle.lire(200, silencieux) as ResultatItineraire.Trouve

        assertEquals(0.0, avec.peages, 0.001)
        assertTrue("le service s'est prononce : il n'y a pas de peage", avec.peagesConnus)
        assertEquals(0.0, sans.peages, 0.001)
        assertTrue("le service ne s'est pas prononce", !sans.peagesConnus)
    }

    /**
     * Une devise étrangère rend le péage inconnu plutôt que repris tel quel.
     * L'ajouter à un devis en euros y aurait glissé des francs suisses au taux de
     * un pour un — une erreur silencieuse, et qui sous-facture.
     */
    @Test
    fun `un peage dans une autre devise n'est pas repris`() {
        val reponse = """
            {"routes": [{"distanceMeters": 120000, "duration": "5400s",
             "travelAdvisory": {"tollInfo": {"estimatedPrice": [
               {"currencyCode": "CHF", "units": "40", "nanos": 0}]}}}]}
        """.trimIndent()

        val trouve = AnalyseItineraireGoogle.lire(200, reponse) as ResultatItineraire.Trouve

        assertEquals(0.0, trouve.peages, 0.001)
        assertTrue(!trouve.peagesConnus)
    }

    /** Plusieurs barrières sur le trajet : les prix s'additionnent. */
    @Test
    fun `plusieurs peages s'additionnent`() {
        val reponse = """
            {"routes": [{"distanceMeters": 300000, "duration": "10800s",
             "travelAdvisory": {"tollInfo": {"estimatedPrice": [
               {"currencyCode": "EUR", "units": "12", "nanos": 500000000},
               {"currencyCode": "EUR", "units": "7", "nanos": 900000000}]}}}]}
        """.trimIndent()

        val trouve = AnalyseItineraireGoogle.lire(200, reponse) as ResultatItineraire.Trouve

        assertEquals(20.40, trouve.peages, 0.001)
    }

    /**
     * Une liste de routes vide est la façon dont l'API dit qu'elle n'a pas trouvé.
     * La prendre pour un succès aurait facturé un déplacement de zéro kilomètre
     * sans que rien ne le signale.
     */
    @Test
    fun `un 200 sans route est un echec, pas un trajet nul`() {
        val resultat = AnalyseItineraireGoogle.lire(200, """{"routes": []}""")

        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.ADRESSE_INTROUVABLE),
            resultat,
        )
    }

    /**
     * Chaque échec a sa raison, parce que la suite n'est pas la même : une clé se
     * corrige dans les Réglages, un quota s'attend, une adresse se réécrit.
     */
    @Test
    fun `chaque refus donne sa raison`() {
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.CLE_REFUSEE),
            AnalyseItineraireGoogle.lire(403, """{"error": {"status": "PERMISSION_DENIED"}}"""),
        )
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.QUOTA_EPUISE),
            AnalyseItineraireGoogle.lire(429, ""),
        )
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.SERVICE_INDISPONIBLE),
            AnalyseItineraireGoogle.lire(503, ""),
        )
        assertEquals(
            "le statut de l'API est plus precis que le code HTTP",
            ResultatItineraire.Echec(RaisonEchec.QUOTA_EPUISE),
            AnalyseItineraireGoogle.lire(400, """{"error": {"status": "RESOURCE_EXHAUSTED"}}"""),
        )
    }

    @Test
    fun `une reponse illisible ne fait pas tomber l'application`() {
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE),
            AnalyseItineraireGoogle.lire(200, "ceci n'est pas du json"),
        )
        assertEquals(
            ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE),
            AnalyseItineraireGoogle.lire(200, """{"routes": [{"duration": "60s"}]}"""),
        )
    }

    /** La durée arrondit à la minute, plutôt que de tronquer et sous-facturer. */
    @Test
    fun `la duree s'arrondit a la minute`() {
        fun minutes(duree: String): Int {
            val reponse = """{"routes": [{"distanceMeters": 1000, "duration": "$duree"}]}"""
            return (AnalyseItineraireGoogle.lire(200, reponse) as ResultatItineraire.Trouve)
                .dureeMinutes
        }

        assertEquals(22, minutes("1320s"))
        assertEquals(23, minutes("1350s"))
        assertEquals(1, minutes("31s"))
        assertEquals(0, minutes("29s"))
    }

    /**
     * L'adresse part dans du JSON échappé, et pas dans un gabarit de texte : un
     * guillemet dans « 3 rue "Neuve", L'Épicerie » aurait produit une requête
     * invalide, et c'est une adresse que quelqu'un finira par saisir.
     */
    @Test
    fun `l'adresse est echappee dans la requete`() {
        val corps = AnalyseItineraireGoogle.corpsRequete(
            """3 rue "Neuve", L'Épicerie""",
            "  Lyon  ",
        )

        assertTrue("le JSON reste analysable", corps.contains("\\\"Neuve\\\""))
        assertTrue("les espaces de bord sont retires", corps.contains("\"Lyon\""))
        assertTrue("les peages sont demandes", corps.contains("TOLLS"))
    }
}
