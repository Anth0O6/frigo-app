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
 */
class InterventionRepository {

    private val _interventions = MutableStateFlow(donneesFactices())

    val interventions: StateFlow<List<Intervention>> = _interventions.asStateFlow()

    /**
     * Ajoute une intervention à la tournée du jour.
     *
     * Faute de formulaire de saisie en v0, la nouvelle ligne est piochée dans un
     * jeu d'exemples et planifiée une heure après la dernière intervention.
     */
    fun ajouterIntervention() {
        _interventions.update { courantes ->
            val modele = MODELES_AJOUT[courantes.size % MODELES_AJOUT.size]
            val derniereHeure = courantes.maxOfOrNull { it.heure } ?: LocalTime.of(8, 0)
            val nouvelle = modele.copy(
                id = (courantes.maxOfOrNull { it.id } ?: 0L) + 1L,
                heure = derniereHeure.plusHours(1),
            )
            (courantes + nouvelle).sortedBy { it.heure }
        }
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

        /** Exemples réutilisés par [ajouterIntervention]. */
        val MODELES_AJOUT = listOf(
            Intervention(0L, LocalTime.MIDNIGHT, "Cave coopérative Saint-Ouen", "Duclair", TypePanne.REGULATION),
            Intervention(0L, LocalTime.MIDNIGHT, "Fromagerie Hardy", "Caudebec", TypePanne.FUITE_FLUIDE),
            Intervention(0L, LocalTime.MIDNIGHT, "Brasserie du Port", "Le Havre", TypePanne.COMPRESSEUR),
            Intervention(0L, LocalTime.MIDNIGHT, "Primeur Vasseur", "Bois-Guillaume", TypePanne.GIVRAGE),
            Intervention(0L, LocalTime.MIDNIGHT, "Clinique des Ormes", "Mont-Saint-Aignan", TypePanne.ENTRETIEN),
        )
    }
}
