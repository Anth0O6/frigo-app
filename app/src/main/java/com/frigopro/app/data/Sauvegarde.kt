package com.frigopro.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Format du fichier de sauvegarde.
 *
 * Il est **volontairement distinct des entités Room**. Le schéma de la base
 * suit les besoins de l'application et change à chaque migration ; un fichier
 * de sauvegarde, lui, doit rester lisible par les versions suivantes. Les deux
 * évolueront donc séparément, et [FORMAT_COURANT] se numérote à part de la
 * version de la base.
 *
 * Les champs qui peuvent manquer portent une valeur par défaut : un fichier
 * écrit par une version antérieure reste ainsi lisible sans cas particulier.
 */
@Serializable
data class Sauvegarde(
    val format: Int,
    val exporteeLe: String,
    val clients: List<ClientSauvegarde> = emptyList(),
    val interventions: List<InterventionSauvegarde> = emptyList(),
)

@Serializable
data class ClientSauvegarde(
    val id: String,
    val nom: String,
    val ville: String,
    val adresse: String = "",
    val telephone: String = "",
    val modifieLe: Long = 0L,
)

@Serializable
data class InterventionSauvegarde(
    val id: String,
    val date: String,
    val heure: String,
    val client: String,
    val ville: String,
    val typePanne: String,
    val statut: String,
    val clientId: String? = null,
    val notes: String = "",
    val modifieLe: Long = 0L,
)

/** Version courante du format de fichier. */
const val FORMAT_COURANT: Int = 1

/**
 * `prettyPrint` parce qu'une sauvegarde doit pouvoir se relire à l'œil, et
 * `ignoreUnknownKeys` pour qu'un champ ajouté plus tard ne rende pas le fichier
 * illisible par une version qui l'ignore.
 */
internal val JSON_SAUVEGARDE: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Formats du fichier, dupliqués à dessein de ceux de [Convertisseurs] : le
 * stockage de la base peut changer sans que le fichier en souffre.
 */
private val FORMAT_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val FORMAT_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun Client.versSauvegarde(): ClientSauvegarde = ClientSauvegarde(
    id = id,
    nom = nom,
    ville = ville,
    adresse = adresse,
    telephone = telephone,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun Intervention.versSauvegarde(): InterventionSauvegarde = InterventionSauvegarde(
    id = id,
    date = date.format(FORMAT_DATE),
    heure = heure.format(FORMAT_HEURE),
    client = client,
    ville = ville,
    typePanne = typePanne.name,
    statut = statut.name,
    clientId = clientId,
    notes = notes,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun ClientSauvegarde.versClient(): Client = Client(
    id = id,
    nom = nom,
    ville = ville,
    adresse = adresse,
    telephone = telephone,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

/**
 * `null` quand une valeur du fichier ne se relit pas — un type de panne ou un
 * statut inconnu, une date mal formée. Plutôt que de deviner, la restauration
 * refusera le fichier en entier : à moitié restaurée, une tournée ne vaut rien.
 */
internal fun InterventionSauvegarde.versIntervention(): Intervention? {
    val panne = TypePanne.entries.firstOrNull { it.name == typePanne } ?: return null
    val avancement = StatutIntervention.entries.firstOrNull { it.name == statut } ?: return null
    val jour = runCatching { LocalDate.parse(date, FORMAT_DATE) }.getOrNull() ?: return null
    val moment = runCatching { LocalTime.parse(heure, FORMAT_HEURE) }.getOrNull() ?: return null

    return Intervention(
        id = id,
        date = jour,
        heure = moment,
        client = client,
        ville = ville,
        typePanne = panne,
        clientId = clientId,
        statut = avancement,
        notes = notes,
        modifieLe = Instant.ofEpochMilli(modifieLe),
    )
}
