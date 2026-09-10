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
    val types: List<TypeInterventionSauvegarde> = emptyList(),
    val clients: List<ClientSauvegarde> = emptyList(),
    val equipements: List<EquipementSauvegarde> = emptyList(),
    val photos: List<PhotoSauvegarde> = emptyList(),
    val interventions: List<InterventionSauvegarde> = emptyList(),
)

@Serializable
data class TypeInterventionSauvegarde(
    val id: String,
    val libelle: String,
    val modifieLe: Long = 0L,
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
data class EquipementSauvegarde(
    val id: String,
    val clientId: String,
    val nom: String,
    val modifieLe: Long = 0L,
)

/**
 * Une photo, décrite ici, rangée dans `photos/` de l'archive : le JSON dit à
 * quelle machine et à quelle catégorie appartient chaque image, [fichier] fait
 * le lien entre les deux.
 */
@Serializable
data class PhotoSauvegarde(
    val id: String,
    val equipementId: String,
    val categorie: String,
    val fichier: String,
    val priseLe: Long = 0L,
)

@Serializable
data class InterventionSauvegarde(
    val id: String,
    val date: String,
    val heure: String,
    val client: String,
    val ville: String,
    val statut: String,
    val typeId: String? = null,
    val typeLibelle: String = "",
    /**
     * Format 1 : le type était une valeur fixe de l'application
     * (`FUITE_FLUIDE`, `ENTRETIEN`…). Conservé en lecture seule pour qu'une
     * sauvegarde faite avant ce changement reste restaurable.
     */
    val typePanne: String? = null,
    val clientId: String? = null,
    val equipementId: String? = null,
    val equipementNom: String = "",
    val notes: String = "",
    val modifieLe: Long = 0L,
)

/**
 * Version courante du format de fichier.
 *
 * Le format 3 ajoute le parc de machines et leurs photos, et sort du seul
 * fichier texte : la sauvegarde est désormais une archive (voir
 * [ArchiveSauvegarde]) dont ce JSON n'est qu'une entrée. Un fichier `.json`
 * exporté par une version antérieure reste restaurable tel quel.
 */
const val FORMAT_COURANT: Int = 3

/**
 * `prettyPrint` parce qu'une sauvegarde doit pouvoir se relire à l'œil, et
 * `ignoreUnknownKeys` pour qu'un champ ajouté plus tard ne rende pas le fichier
 * illisible par une version qui l'ignore.
 */
internal val JSON_SAUVEGARDE: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    // Sans cela, chaque champ facultatif non renseigné écrirait une ligne
    // `null` : un fichier que l'on veut pouvoir relire à l'œil n'y gagne rien.
    explicitNulls = false
}

/**
 * Intitulés des types figés de l'époque du format 1, pour relire ces fichiers.
 * Les mêmes que ceux posés par `MIGRATION_4_5` : une sauvegarde d'alors et une
 * base d'alors doivent donner le même résultat.
 */
private val LIBELLES_HISTORIQUES = mapOf(
    "FUITE_FLUIDE" to "Fuite de fluide",
    "COMPRESSEUR" to "Compresseur",
    "REGULATION" to "Régulation",
    "GIVRAGE" to "Givrage",
    "ENTRETIEN" to "Entretien préventif",
)

/**
 * Formats du fichier, dupliqués à dessein de ceux de [Convertisseurs] : le
 * stockage de la base peut changer sans que le fichier en souffre.
 */
private val FORMAT_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val FORMAT_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun TypeIntervention.versSauvegarde(): TypeInterventionSauvegarde = TypeInterventionSauvegarde(
    id = id,
    libelle = libelle,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun Client.versSauvegarde(): ClientSauvegarde = ClientSauvegarde(
    id = id,
    nom = nom,
    ville = ville,
    adresse = adresse,
    telephone = telephone,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun Equipement.versSauvegarde(): EquipementSauvegarde = EquipementSauvegarde(
    id = id,
    clientId = clientId,
    nom = nom,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun Photo.versSauvegarde(): PhotoSauvegarde = PhotoSauvegarde(
    id = id,
    equipementId = equipementId,
    categorie = categorie.name,
    fichier = fichier,
    priseLe = priseLe.toEpochMilli(),
)

internal fun Intervention.versSauvegarde(): InterventionSauvegarde = InterventionSauvegarde(
    id = id,
    date = date.format(FORMAT_DATE),
    heure = heure.format(FORMAT_HEURE),
    client = client,
    ville = ville,
    statut = statut.name,
    typeId = typeId,
    typeLibelle = typeLibelle,
    clientId = clientId,
    equipementId = equipementId,
    equipementNom = equipementNom,
    notes = notes,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun TypeInterventionSauvegarde.versType(): TypeIntervention = TypeIntervention(
    id = id,
    libelle = libelle,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun ClientSauvegarde.versClient(): Client = Client(
    id = id,
    nom = nom,
    ville = ville,
    adresse = adresse,
    telephone = telephone,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun EquipementSauvegarde.versEquipement(): Equipement = Equipement(
    id = id,
    clientId = clientId,
    nom = nom,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

/**
 * `null` pour une photo qui ne se relit pas : une catégorie inconnue, comme un
 * statut inconnu, est une valeur fixe de l'application et non du texte libre —
 * et un nom de fichier qui n'en est pas un (un chemin, un `..`) trahit une
 * archive bricolée. Dans les deux cas le fichier entier sera refusé, ce qui
 * vaut mieux qu'une photo rangée hors de son dossier.
 */
internal fun PhotoSauvegarde.versPhoto(): Photo? {
    val rangement = CategoriePhoto.entries.firstOrNull { it.name == categorie } ?: return null
    val nom = StockagePhotos.nomSur(fichier) ?: return null
    return Photo(
        id = id,
        equipementId = equipementId,
        categorie = rangement,
        fichier = nom,
        priseLe = Instant.ofEpochMilli(priseLe),
    )
}

/**
 * `null` quand une valeur du fichier ne se relit pas — un statut inconnu, une
 * date mal formée. Plutôt que de deviner, la restauration refusera le fichier
 * en entier : à moitié restaurée, une tournée ne vaut rien.
 *
 * L'intitulé du type, lui, ne peut pas rendre un fichier illisible : il est
 * libre, et un fichier du format 1 n'en portait pas — on le déduit alors de
 * l'ancienne valeur fixe.
 */
internal fun InterventionSauvegarde.versIntervention(): Intervention? {
    val avancement = StatutIntervention.entries.firstOrNull { it.name == statut } ?: return null
    val jour = runCatching { LocalDate.parse(date, FORMAT_DATE) }.getOrNull() ?: return null
    val moment = runCatching { LocalTime.parse(heure, FORMAT_HEURE) }.getOrNull() ?: return null

    return Intervention(
        id = id,
        date = jour,
        heure = moment,
        client = client,
        ville = ville,
        typeId = typeId,
        typeLibelle = typeLibelle.ifBlank { intituleHistorique() },
        clientId = clientId,
        equipementId = equipementId,
        equipementNom = equipementNom,
        statut = avancement,
        notes = notes,
        modifieLe = Instant.ofEpochMilli(modifieLe),
    )
}

/**
 * Intitulé d'une intervention venue d'un fichier du format 1. Une valeur
 * inconnue est recopiée telle quelle plutôt que perdue, et l'absence de type
 * donne une chaîne vide — ce qui est désormais permis.
 */
private fun InterventionSauvegarde.intituleHistorique(): String {
    val ancien = typePanne ?: return ""
    return LIBELLES_HISTORIQUES[ancien] ?: ancien
}
