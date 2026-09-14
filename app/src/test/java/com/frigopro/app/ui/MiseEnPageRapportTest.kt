package com.frigopro.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'arithmétique de la page du compte-rendu.
 *
 * Même raison que pour [MiseEnPageDevisTest] : un bloc qui déborde hors de la
 * page, un titre seul en bas d'une feuille, une signature séparée de ce qu'elle
 * signe — ce sont des défauts qui ne se voient qu'en ouvrant le PDF, donc une
 * fois le document imprimé et tendu au client.
 */
class MiseEnPageRapportTest {

    private fun bloc(intitule: String, lignes: Int) = BlocImprime(
        intitule = intitule,
        lignes = (1..lignes).map { LigneBloc("$intitule $it", "valeur $it") },
    )

    /** Toutes les lignes de toutes les pages, dans l'ordre où elles s'impriment. */
    private fun List<PageRapport>.toutesLesLignes(): List<LigneBloc> =
        flatMap { page -> page.blocs.flatMap { it.lignes } }

    @Test
    fun `un compte-rendu court tient sur une page`() {
        val pages = MiseEnPageRapport.paginer(
            blocs = listOf(bloc("Relevés", 4), bloc("Pièces", 3)),
            mentions = listOf("Établi le 14/05/2026."),
        )

        assertEquals(1, pages.size)
        assertEquals(1, pages.single().total)
        assertTrue(pages.single().signature)
    }

    @Test
    fun `un compte-rendu vide produit quand meme une page`() {
        // Même règle que le devis sans ligne : ne rien imprimer laisserait
        // croire à un échec de l'export plutôt qu'à une intervention sans relevé.
        val pages = MiseEnPageRapport.paginer(blocs = emptyList())

        assertEquals(1, pages.size)
        assertTrue(pages.single().blocs.isEmpty())
    }

    @Test
    fun `rien n'est perdu quand les blocs debordent`() {
        val blocs = listOf(bloc("Fluide", 40), bloc("Pièces", 30), bloc("Points", 25))
        val pages = MiseEnPageRapport.paginer(blocs)

        assertTrue("Il faut plusieurs pages pour ce volume", pages.size > 1)
        assertEquals(
            blocs.flatMap { it.lignes },
            pages.toutesLesLignes(),
        )
    }

    @Test
    fun `un bloc coupe reprend son titre`() {
        val pages = MiseEnPageRapport.paginer(listOf(bloc("Fluide", 80)))

        val morceaux = pages.flatMap { page -> page.blocs.filter { it.intitule == "Fluide" } }
        assertTrue("Le bloc doit être coupé", morceaux.size > 1)
        assertFalse("Le premier morceau n'est pas une suite", morceaux.first().suite)
        // Un tableau qui recommence sans titre au milieu d'une page ne se lit
        // pas : même raison que la ligne de titres du devis, répétée à chaque page.
        assertTrue(morceaux.drop(1).all { it.suite })
    }

    @Test
    fun `aucun titre ne reste seul en bas d'une page`() {
        // Beaucoup de petits blocs : c'est le cas qui produit des titres
        // orphelins si l'on pose un titre avant de vérifier qu'une ligne suit.
        val blocs = (1..20).map { bloc("Bloc $it", 5) }
        val pages = MiseEnPageRapport.paginer(blocs)

        assertTrue(pages.all { page -> page.blocs.all { it.lignes.isNotEmpty() } })
    }

    @Test
    fun `la signature ne va que sur la derniere page`() {
        val pages = MiseEnPageRapport.paginer(
            blocs = listOf(bloc("Fluide", 60)),
            mentions = listOf("Établi le 14/05/2026."),
        )

        assertTrue(pages.size > 1)
        assertTrue(pages.last().signature)
        assertTrue(pages.dropLast(1).none { it.signature })
        assertTrue(pages.dropLast(1).all { it.mentions.isEmpty() })
    }

    /**
     * Une page pleine pile ne doit pas couper la signature.
     *
     * C'est le cas qui fait mal : les blocs tombent juste, il reste trois
     * centimètres, et le cadre part à moitié sur la page suivante — on fait
     * alors signer une page blanche.
     */
    @Test
    fun `la signature part sur une page a elle quand elle ne tient pas`() {
        val place = MiseEnPageDevis.hautDuPied - MiseEnPageRapport.hautDesBlocs(premiere = true)
        val lignesQuiRemplissent =
            (place - MiseEnPageRapport.HAUTEUR_TITRE - MiseEnPageRapport.ESPACE_ENTRE_BLOCS) /
                MiseEnPageRapport.HAUTEUR_LIGNE

        val pages = MiseEnPageRapport.paginer(
            blocs = listOf(bloc("Fluide", lignesQuiRemplissent)),
            mentions = listOf("Établi le 14/05/2026."),
        )

        assertEquals(2, pages.size)
        assertTrue(pages.first().blocs.isNotEmpty())
        assertTrue(pages.last().blocs.isEmpty())
        assertTrue(pages.last().signature)
    }

    @Test
    fun `sans signature ni mention, rien ne pousse de page supplementaire`() {
        val pages = MiseEnPageRapport.paginer(
            blocs = listOf(bloc("Relevés", 4)),
            mentions = emptyList(),
            avecSignature = false,
        )

        assertEquals(1, pages.size)
        assertFalse(pages.single().signature)
    }

    @Test
    fun `la numerotation est juste`() {
        val pages = MiseEnPageRapport.paginer(listOf(bloc("Fluide", 120)))

        assertEquals(pages.indices.map { it + 1 }, pages.map { it.numero })
        assertTrue(pages.all { it.total == pages.size })
    }

    // — L'enveloppement du texte libre ——————————————————————————————————

    @Test
    fun `un texte court reste sur une ligne`() {
        assertEquals(
            listOf("Remplacement du détendeur."),
            MiseEnPageRapport.envelopper("Remplacement du détendeur."),
        )
    }

    @Test
    fun `un texte vide ne produit aucune ligne`() {
        // Et non une ligne vide : le bloc « Travaux réalisés » disparaîtrait
        // alors avec un titre et rien dessous.
        assertTrue(MiseEnPageRapport.envelopper("   ").isEmpty())
    }

    @Test
    fun `l'enveloppement coupe sur les mots`() {
        val lignes = MiseEnPageRapport.envelopper("un deux trois quatre cinq", caracteres = 10)

        assertEquals(listOf("un deux", "trois", "quatre", "cinq"), lignes)
        assertTrue(lignes.all { it.length <= 10 })
    }

    @Test
    fun `les retours a la ligne saisis sont respectes`() {
        // Un technicien qui énumère ses travaux en trois points veut trois points.
        val lignes = MiseEnPageRapport.envelopper("Vidange\nTirage au vide\nRecharge")

        assertEquals(listOf("Vidange", "Tirage au vide", "Recharge"), lignes)
    }

    @Test
    fun `un mot plus long que la ligne est coupe plutot que perdu`() {
        // Une référence constructeur : la perdre à moitié vaut mieux que la
        // laisser déborder hors de la page, où elle ne se voit pas du tout.
        val lignes = MiseEnPageRapport.envelopper("NTZ068A4LR1A2500", caracteres = 6)

        assertEquals(listOf("NTZ068", "A4LR1A", "2500"), lignes)
    }
}
