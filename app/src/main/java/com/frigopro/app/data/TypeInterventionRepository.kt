package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** La liste des types d'intervention, telle que le technicien la nomme. */
class TypeInterventionRepository(private val dao: TypeInterventionDao) {

    /**
     * Liste triée alphabétiquement, accents repliés, pour les mêmes raisons que
     * le carnet de clients — et parce qu'un ordre stable laisse la main trouver
     * le bon bouton sans lire.
     */
    val types: Flow<List<TypeIntervention>> = dao.observerTous().map { liste ->
        val collateur = Collator.getInstance(Locale.FRENCH)
        liste.sortedWith { a, b -> collateur.compare(a.libelle, b.libelle) }
    }

    /**
     * Crée le type ou remplace celui qui porte le même identifiant, et
     * répercute son intitulé sur les interventions qui le désignent.
     */
    suspend fun enregistrer(type: TypeIntervention): TypeIntervention {
        val nettoye = type.copy(libelle = type.libelle.trim(), modifieLe = Instant.now())
        dao.renommer(nettoye)
        return nettoye
    }

    /**
     * Renvoie le type portant cet intitulé, en le créant au besoin. C'est ce
     * qui permet d'ajouter un type depuis le formulaire, en pleine saisie.
     */
    suspend fun trouverOuCreer(libelle: String): TypeIntervention {
        val recherche = libelle.trim()
        return dao.trouverParLibelle(recherche) ?: enregistrer(TypeIntervention(libelle = recherche))
    }

    /**
     * Retire le type de la liste. Les interventions qui l'employaient gardent
     * leur intitulé et perdent seulement le lien : rien ne disparaît d'une
     * tournée passée parce qu'on a rangé sa liste.
     */
    suspend fun supprimer(id: String) {
        dao.supprimer(id)
    }
}
