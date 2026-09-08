package com.frigopro.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/**
 * Source de données de la v0 : tout est en mémoire, rien n'est persisté.
 *
 * L'interface est volontairement la même que celle qu'aurait un dépôt adossé à
 * Room ou à une API : le jour où la persistance arrive, seule l'implémentation
 * change, pas le ViewModel ni l'UI.
 *
 * La liste exposée est toujours triée par heure de passage, l'ordre de la
 * tournée étant la seule lecture utile pour un technicien.
 */
class InterventionRepository {

    private val _interventions = MutableStateFlow(donneesFactices())

    val interventions: StateFlow<List<Intervention>> = _interventions.asStateFlow()

    /** Crée une intervention et lui attribue un identifiant libre. */
    fun ajouterIntervention(
        heure: LocalTime,
        client: String,
        ville: String,
        typePanne: TypePanne,
    ) {
        _interventions.update { courantes ->
            val nouvelle = Intervention(
                id = (courantes.maxOfOrNull { it.id } ?: 0L) + 1L,
                heure = heure,
                client = client,
                ville = ville,
                typePanne = typePanne,
            )
            (courantes + nouvelle.nettoyee()).triees()
        }
    }

    /**
     * Remplace l'intervention portant le même identifiant.
     *
     * Sans correspondance, la liste est laissée telle quelle : une édition ne
     * doit jamais faire réapparaître une ligne supprimée entre-temps.
     */
    fun modifierIntervention(intervention: Intervention) {
        _interventions.update { courantes ->
            courantes
                .map { if (it.id == intervention.id) intervention.nettoyee() else it }
                .triees()
        }
    }

    /** Retire l'intervention de la tournée. Sans effet si elle n'existe plus. */
    fun supprimerIntervention(id: Long) {
        _interventions.update { courantes -> courantes.filterNot { it.id == id } }
    }

    private companion object {

        fun donneesFactices(): List<Intervention> = listOf(
            Intervention(
                id = 1L,
                heure = LocalTime.of(8, 30),
                client = "Boucherie Lemoine",
                ville = "Rouen",
                typePanne = TypePanne.FUITE_FLUIDE,
            ),
            Intervention(
                id = 2L,
                heure = LocalTime.of(10, 0),
                client = "Supérette Val-Fleuri",
                ville = "Sotteville-lès-Rouen",
                typePanne = TypePanne.COMPRESSEUR,
            ),
            Intervention(
                id = 3L,
                heure = LocalTime.of(11, 15),
                client = "Restaurant Le Comptoir",
                ville = "Elbeuf",
                typePanne = TypePanne.REGULATION,
            ),
            Intervention(
                id = 4L,
                heure = LocalTime.of(14, 0),
                client = "Traiteur Delaunay",
                ville = "Barentin",
                typePanne = TypePanne.GIVRAGE,
            ),
            Intervention(
                id = 5L,
                heure = LocalTime.of(16, 30),
                client = "Pharmacie du Centre",
                ville = "Yvetot",
                typePanne = TypePanne.ENTRETIEN,
            ),
        )
    }
}

private fun List<Intervention>.triees(): List<Intervention> = sortedBy { it.heure }

/** Les libellés saisis au clavier arrivent souvent avec des espaces parasites. */
private fun Intervention.nettoyee(): Intervention = copy(
    client = client.trim(),
    ville = ville.trim(),
)
