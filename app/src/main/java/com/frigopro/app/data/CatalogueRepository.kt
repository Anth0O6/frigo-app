package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/**
 * Les techniciens de l'entreprise.
 *
 * Tri français par `Collator`, comme le carnet de clients et pour la même
 * raison : `COLLATE NOCASE` ne replie pas les accents et rejetterait « Élise »
 * après « Zoé ».
 */
class TechnicienRepository(private val dao: TechnicienDao) {

    val techniciens: Flow<List<Technicien>> = dao.observerTous().map { liste ->
        val collateur = Collator.getInstance(Locale.FRENCH)
        liste.sortedWith { a, b -> collateur.compare(a.nom, b.nom) }
    }

    /**
     * Crée le technicien ou remplace celui qui porte le même identifiant, et
     * répercute son nom sur les interventions qui le désignent.
     */
    suspend fun enregistrer(technicien: Technicien): Technicien {
        val nettoye = technicien.copy(nom = technicien.nom.trim(), modifieLe = Instant.now())
        dao.renommer(nettoye)
        return nettoye
    }

    /** Renvoie le technicien de ce nom, en le créant au besoin. */
    suspend fun trouverOuCreer(nom: String): Technicien {
        val recherche = nom.trim()
        return dao.trouverParNom(recherche) ?: enregistrer(Technicien(nom = recherche))
    }

    suspend fun supprimer(id: String) = dao.supprimer(id)
}

/**
 * Le catalogue de prestations.
 *
 * Il est livré avec ses intitulés et sans ses prix (voir [Prestation]) : le
 * dépôt ne fait donc rien de particulier au premier lancement, la migration
 * ayant déjà posé les lignes. Ce qu'il garantit, c'est qu'un prix négatif
 * n'entre pas — une remise se saisit en réduisant la quantité ou le prix, pas
 * en inversant le signe, sinon un total devient faux sans qu'on le voie.
 */
class PrestationRepository(private val dao: PrestationDao) {

    val prestations: Flow<List<Prestation>> = dao.observerToutes()

    /** Le catalogue groupé par famille, dans l'ordre du terrain. */
    val parCategorie: Flow<Map<CategoriePrestation, List<Prestation>>> =
        dao.observerToutes().map { liste -> liste.groupBy { it.categorie } }

    suspend fun enregistrer(prestation: Prestation): Prestation? {
        val designation = prestation.designation.trim()
        if (designation.isEmpty()) return null
        val nettoyee = prestation.copy(
            designation = designation,
            unite = prestation.unite.trim(),
            prixUnitaire = prestation.prixUnitaire.coerceAtLeast(0.0),
            rang = if (prestation.rang == 0) dao.prochainRang() else prestation.rang,
            modifieLe = Instant.now(),
        )
        dao.enregistrer(nettoyee)
        return nettoyee
    }

    /** Supprimer une prestation emporte ses paliers : ils n'ont plus d'objet. */
    suspend fun supprimer(id: String) {
        dao.effacerPaliersDe(id)
        dao.effacer(id)
    }

    // — La dégressivité ——————————————————————————————————————————————————

    /** Les paliers de chaque prestation, par identifiant de prestation. */
    val paliers: Flow<Map<String, List<PalierPrestation>>> =
        dao.observerPaliers().map { liste -> liste.groupBy { it.prestationId } }

    /** Le tarif complet d'une prestation : son prix de base, et sa dégression. */
    fun tarif(prestation: Prestation, paliers: List<PalierPrestation>): TarifDegressif =
        TarifDegressif(base = prestation.prixUnitaire, paliers = paliers)

    /**
     * Pose le prix d'un rang d'unité.
     *
     * Le rang 1 n'est pas un palier — c'est le prix de la prestation, qui se
     * règle là où il se réglait déjà. Un appel avec `aPartirDe = 1` ne fait donc
     * rien plutôt que de créer un doublon qui divergerait au premier changement
     * de tarif.
     */
    suspend fun definirPalier(prestationId: String, aPartirDe: Int, prixUnitaire: Double): PalierPrestation? {
        if (aPartirDe < 2 || prixUnitaire < 0.0) return null
        val existant = dao.tousLesPaliers()
            .firstOrNull { it.prestationId == prestationId && it.aPartirDe == aPartirDe }
        val palier = (existant ?: PalierPrestation(prestationId = prestationId, aPartirDe = aPartirDe))
            .copy(prixUnitaire = prixUnitaire, modifieLe = Instant.now())
        dao.enregistrerPalier(palier)
        return palier
    }

    suspend fun supprimerPalier(id: String) = dao.effacerPalier(id)
}
