package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un client du carnet, réutilisable d'une intervention à l'autre.
 *
 * [adresse] et [telephone] peuvent rester vides : le carnet se remplit tout
 * seul à partir des interventions, où seuls le nom et la ville sont demandés.
 * Une chaîne vide signifie donc « pas encore renseigné », et c'est le cas
 * normal — non un défaut de saisie.
 *
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nom: String,
    val ville: String,
    val adresse: String = "",
    val telephone: String = "",
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Un numéro à appeler. */
    val appelable: Boolean get() = telephone.isNotBlank()

    /**
     * Une destination à ouvrir dans une application de cartographie. La ville
     * seule ne suffit pas : elle mènerait au centre-ville, pas chez le client.
     */
    val localisable: Boolean get() = adresse.isNotBlank()

    /** Adresse complète, telle qu'on la dicterait à quelqu'un. */
    val adresseComplete: String get() = listOf(adresse, ville).filter { it.isNotBlank() }.joinToString(", ")
}
