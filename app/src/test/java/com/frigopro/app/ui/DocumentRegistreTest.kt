package com.frigopro.app.ui

import com.frigopro.app.data.LigneRegistre
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Registre
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.TotalFluide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ce que le registre imprimé dit.
 *
 * Le test porte sur ce qui se voit devant un inspecteur : le bilan avant le
 * détail, l'équivalent CO₂ jamais deviné, et un registre qui **existe** même
 * quand l'année a été calme — « rien à montrer » et « rien ne s'est passé » ne
 * valent pas la même chose, et seul le second est une réponse.
 */
class DocumentRegistreTest {

    private val jour = LocalDate.of(2026, 12, 31)

    private val entreprise = Parametres(
        entreprise = "FrigoPro",
        entrepriseAdresse = "12 rue des Lilas, Lyon",
        attestation = "ATT-2024-118",
    )

    private fun ligne(
        fluide: String = "R-449A",
        sens: SensFluide = SensFluide.AJOUT,
        masse: Double = 3.0,
        date: LocalDate = LocalDate.of(2026, 5, 14),
    ) = LigneRegistre(
        date = date,
        numero = "INT-2605-018",
        client = "Boucherie Morel",
        machine = "Chambre froide",
        fluide = fluide,
        sens = sens,
        masseKg = masse,
        technicien = "Karim B.",
    )

    private fun registre(
        lignes: List<LigneRegistre> = listOf(ligne()),
        totaux: List<TotalFluide> = listOf(TotalFluide("R-449A", ajoute = 3.0, recupere = 1.0)),
    ) = Registre(annee = 2026, lignes = lignes, totaux = totaux)

    private fun document(registre: Registre = registre()) =
        DocumentRegistre.de(registre = registre, parametres = entreprise, aujourdhui = jour)

    @Test
    fun `l'en-tete porte l'entreprise et l'annee, et personne d'autre`() {
        val document = document()

        assertEquals("REGISTRE FLUIDES", document.titre)
        // Un registre n'est pas une pièce numérotée : c'est l'année qui le
        // désigne, et elle est dans les dates.
        assertEquals("", document.numero)
        assertTrue(document.dates.contains("Année 2026"))
        assertTrue(document.emetteur.first().contains("FrigoPro"))
        // Il est tenu par l'entreprise pour elle-même : lui inventer un
        // destinataire aurait laissé croire qu'il s'envoie à quelqu'un.
        assertTrue(document.destinataire.isEmpty())
        // Et le fichier porte l'année : « 2026.pdf » dans un dossier de
        // téléchargements ne dirait rien.
        assertEquals("registre-fluides-2026.pdf", document.nomFichier)
    }

    @Test
    fun `le registre passe par les blocs et ne chiffre rien`() {
        val document = document()

        assertTrue(document.enBlocs)
        assertTrue(document.lignes.isEmpty())
        assertTrue(document.totaux.isEmpty())
        // Et il ne se signe pas : ce n'est pas un engagement pris devant
        // quelqu'un, c'est une tenue de comptes.
        assertEquals("", document.mentionSignature)
    }

    @Test
    fun `le bilan vient avant le detail`() {
        val document = document()

        // C'est le chiffre qu'on demande en premier ; le faire chercher au bout
        // de quatre pages de mouvements aurait inversé l'ordre de la
        // conversation.
        assertTrue(document.blocs.first().intitule.startsWith("Bilan"))
        assertEquals("R-449A", document.blocs[1].intitule)
    }

    @Test
    fun `le bilan dit ce qui entre et ce qui sort`() {
        val lignes = document().blocs.first().lignes

        assertEquals("R-449A", lignes.first().intitule)
        assertEquals("+3,00 / −1,00 kg", lignes.first().valeur)
    }

    @Test
    fun `l'equivalent CO2 n'est pas invente`() {
        val inconnu = registre(
            lignes = listOf(ligne(fluide = "R-inconnu")),
            totaux = listOf(TotalFluide("R-inconnu", ajoute = 5.0, recupere = 0.0)),
        )

        val lignes = document(inconnu).blocs.first().lignes

        // Ni zéro, ni un tonnage approché : le chiffre sert à se situer sous un
        // seuil réglementaire, et un zéro inventé y placerait l'entreprise à tort.
        assertEquals("GWP inconnu", lignes[1].valeur)
        assertFalse(lignes[1].intitule.contains("GWP "))
    }

    @Test
    fun `le sens est ecrit en toutes lettres`() {
        val recupere = registre(lignes = listOf(ligne(sens = SensFluide.RECUPERATION, masse = 1.5)))

        val ligne = document(recupere).blocs[1].lignes.single()

        // Un « − » devant un nombre se lit « moins », pas « récupéré ».
        assertEquals("1,50 kg récupéré", ligne.valeur)
        // Et la ligne dit d'où vient le kilo : date, intervention, client, machine.
        assertEquals("14/05 · INT-2605-018 · Boucherie Morel · Chambre froide", ligne.intitule)
    }

    @Test
    fun `une annee sans mouvement produit quand meme un registre`() {
        val vide = registre(lignes = emptyList(), totaux = emptyList())

        val document = document(vide)

        // Le document existe, passe par les blocs, et le dit en clair.
        assertTrue(document.enBlocs)
        assertEquals(1, document.blocs.size)
        assertTrue(document.blocs.single().lignes.single().intitule.contains("Aucun mouvement"))
        assertTrue(document.mentions.any { it.contains("Aucun mouvement") })
    }

    @Test
    fun `le renvoi reglementaire est toujours la`() {
        assertTrue(document().mentions.any { it.contains("517/2014") })
    }

    @Test
    fun `l'attestation manquante est signalee`() {
        val sans = DocumentRegistre.de(
            registre = registre(),
            parametres = entreprise.copy(attestation = ""),
            aujourdhui = jour,
        )

        // C'est la première chose qu'un contrôle rapproche du registre, et
        // l'export est le dernier moment pour s'apercevoir qu'elle manque.
        assertTrue(sans.mentions.any { it.contains("Attestation de capacité non renseignée") })
        assertFalse(document().mentions.any { it.contains("Attestation") })
    }
}
