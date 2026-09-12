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
    val releves: List<ReleveSauvegarde> = emptyList(),
    val mouvementsFluide: List<MouvementFluideSauvegarde> = emptyList(),
    val pieces: List<PiecePoseeSauvegarde> = emptyList(),
    val devis: List<DevisSauvegarde> = emptyList(),
    val lignesDevis: List<LigneDevisSauvegarde> = emptyList(),
    val parametres: ParametresSauvegarde? = null,
    val techniciens: List<TechnicienSauvegarde> = emptyList(),
    val checklists: List<PointChecklistSauvegarde> = emptyList(),
    val prestations: List<PrestationSauvegarde> = emptyList(),
    val verificationsFluide: List<VerificationFluideSauvegarde> = emptyList(),
)

/**
 * Un fluide dont la courbe de saturation a été contrôlée.
 *
 * Sauvegardé comme le reste : quelqu'un qui a comparé douze fluides à sa
 * réglette et perd ce travail en changeant de téléphone ne le refera pas, et la
 * réglette l'avertira indéfiniment sans qu'il y prête plus attention — ce qui est
 * le pire résultat possible pour un avertissement.
 */
@Serializable
data class VerificationFluideSauvegarde(
    val fluide: String,
    val verifieLe: Long = 0L,
    val par: String = "",
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
    /** Le groupe d'une unité intérieure. Absent d'un fichier d'avant le format 6. */
    val parentId: String? = null,
    val marque: String = "",
    val modele: String = "",
    val numeroSerie: String = "",
    val fluide: String = "",
    val chargeKg: Double? = null,
    val misEnServiceLe: String? = null,
    val dernierControleLe: String? = null,
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
    /** Nullable depuis le format 4 : une photo peut appartenir à une intervention. */
    val equipementId: String? = null,
    val interventionId: String? = null,
    val categorie: String,
    val fichier: String,
    val legende: String = "",
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
    val urgente: Boolean = false,
    val dureeMin: Int = DUREE_PAR_DEFAUT_MIN,
    val technicienId: String? = null,
    val technicienNom: String = "",
    val arriveeLe: Long? = null,
    val demarreLe: Long? = null,
    val cumuleS: Long = 0L,
    val numero: String = "",
    val signatureFichier: String? = null,
    val signeeLe: Long? = null,
    val modifieLe: Long = 0L,
)

/** Un relevé frigorifique. Les quatre grandeurs peuvent manquer. */
@Serializable
data class ReleveSauvegarde(
    val id: String,
    val interventionId: String,
    val equipementId: String? = null,
    val bpBar: Double? = null,
    val hpBar: Double? = null,
    val surchauffeK: Double? = null,
    val sousRefroidissementK: Double? = null,
    val releveLe: Long = 0L,
    val modifieLe: Long = 0L,
)

/**
 * Un mouvement de fluide.
 *
 * C'est la ligne la plus précieuse du fichier : le registre des fluides est
 * une obligation réglementaire, et il ne se reconstitue pas de mémoire.
 */
@Serializable
data class MouvementFluideSauvegarde(
    val id: String,
    val interventionId: String,
    val equipementId: String? = null,
    val fluide: String,
    val sens: String,
    val masseKg: Double,
    val le: Long = 0L,
    val modifieLe: Long = 0L,
)

@Serializable
data class PiecePoseeSauvegarde(
    val id: String,
    val interventionId: String,
    val designation: String,
    val reference: String = "",
    val quantite: Double = 1.0,
    val prixUnitaire: Double? = null,
    val modifieLe: Long = 0L,
)

@Serializable
data class DevisSauvegarde(
    val id: String,
    val numero: String = "",
    val clientId: String? = null,
    val clientNom: String = "",
    val equipementId: String? = null,
    val equipementNom: String = "",
    val objet: String = "",
    val statut: String,
    val tauxTva: Double = 20.0,
    val tvaOfferte: Boolean = false,
    /**
     * `true` par défaut : un devis d'avant le format 6 a été établi par une
     * entreprise assujettie, puisque le régime n'existait pas encore et que la
     * TVA s'appliquait toujours.
     */
    val assujettiTva: Boolean = true,
    val creeLe: String? = null,
    val valableJusquau: String? = null,
    val modifieLe: Long = 0L,
)

@Serializable
data class LigneDevisSauvegarde(
    val id: String,
    val devisId: String,
    val designation: String,
    val quantite: Double = 1.0,
    val unite: String = "",
    val prixUnitaire: Double = 0.0,
    val offerte: Boolean = false,
    val rang: Int = 0,
)

/**
 * Les réglages.
 *
 * Sauvegardés comme le reste : un technicien qui restaure sur un téléphone
 * neuf et retrouve ses clients mais pas son taux horaire ni son attestation
 * considérera, à juste titre, que la restauration a échoué.
 */
@Serializable
data class ParametresSauvegarde(
    val technicien: String = "",
    val attestation: String = "",
    val themeSombre: Boolean = true,
    val modeGants: Boolean = false,
    val chronoAuto: Boolean = false,
    val tauxHoraire: Double = 0.0,
    val tauxTva: Double = 20.0,
    val assujettiTva: Boolean = true,
    val entreprise: String = "",
    val entrepriseAdresse: String = "",
    val entrepriseTelephone: String = "",
    val entrepriseEmail: String = "",
    val entrepriseSiret: String = "",
    /**
     * Le nom du fichier du logo. L'image elle-même part dans `photos/` de
     * l'archive, comme les signatures : l'oublier rendrait les devis restaurés
     * sans en-tête.
     */
    val logoFichier: String? = null,
    val modifieLe: Long = 0L,
)

@Serializable
data class TechnicienSauvegarde(
    val id: String,
    val nom: String,
    val modifieLe: Long = 0L,
)

@Serializable
data class PointChecklistSauvegarde(
    val id: String,
    val interventionId: String,
    val libelle: String,
    val fait: Boolean = false,
    val rang: Int = 0,
    val modifieLe: Long = 0L,
)

/**
 * Une ligne du catalogue.
 *
 * Le prix part dans la sauvegarde comme le reste : c'est un tarif d'entreprise,
 * et le retrouver après un changement de téléphone évite d'avoir à retaper
 * vingt et une lignes avant de pouvoir chiffrer quoi que ce soit.
 */
@Serializable
data class PrestationSauvegarde(
    val id: String,
    val designation: String,
    val categorie: String,
    val prixUnitaire: Double = 0.0,
    val unite: String = "",
    val parUnite: Boolean = false,
    val rang: Int = 0,
    val modifieLe: Long = 0L,
)

/**
 * Version courante du format de fichier.
 *
 * Le format 5 ajoute les techniciens, la checklist des interventions et le
 * catalogue de prestations.
 *
 * Le format 4 avait ajouté ce qui s'est passé sur place — temps chronométré,
 * relevés, mouvements de fluide, pièces posées, photos avant/après — ainsi que
 * les devis et les réglages.
 *
 * Le format 3 avait ajouté le parc de machines et leurs photos, et fait sortir
 * la sauvegarde du seul fichier texte : c'est désormais une archive (voir
 * [ArchiveSauvegarde]) dont ce JSON n'est qu'une entrée. Un fichier `.json`
 * exporté par une version antérieure reste restaurable tel quel.
 */
const val FORMAT_COURANT: Int = 6

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
 * Les statuts retirés, et ce qu'ils sont devenus.
 *
 * `A_FAIRE` est devenu `PLANIFIEE` au format 5 : le mot a changé parce que
 * l'écran a changé, pas l'état. Toute sauvegarde écrite avant porte encore
 * l'ancien nom, et sans cette correspondance elle serait **refusée en entier** —
 * un statut inconnu fait rejeter le fichier, à dessein. Autrement dit : sans ces
 * deux lignes, la mise à jour rendrait illisibles toutes les sauvegardes déjà
 * faites, ce qui est exactement ce que la sauvegarde est censée empêcher.
 *
 * La même correspondance vit en SQL dans `MIGRATION_7_8`, et pour la même
 * raison que pour `typePanne` : un fichier d'alors et une base d'alors doivent
 * donner le même résultat.
 */
private val STATUTS_HISTORIQUES = mapOf(
    "A_FAIRE" to StatutIntervention.PLANIFIEE,
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
    parentId = parentId,
    marque = marque,
    modele = modele,
    numeroSerie = numeroSerie,
    fluide = fluide,
    chargeKg = chargeKg,
    misEnServiceLe = misEnServiceLe?.format(FORMAT_DATE),
    dernierControleLe = dernierControleLe?.format(FORMAT_DATE),
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun Photo.versSauvegarde(): PhotoSauvegarde = PhotoSauvegarde(
    id = id,
    equipementId = equipementId,
    interventionId = interventionId,
    categorie = categorie.name,
    fichier = fichier,
    legende = legende,
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
    urgente = urgente,
    dureeMin = dureeMin,
    technicienId = technicienId,
    technicienNom = technicienNom,
    arriveeLe = chrono.arriveeLe?.toEpochMilli(),
    demarreLe = chrono.demarreLe?.toEpochMilli(),
    cumuleS = chrono.cumuleS,
    numero = numero,
    signatureFichier = signatureFichier,
    signeeLe = signeeLe?.toEpochMilli(),
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
    parentId = parentId,
    marque = marque,
    modele = modele,
    numeroSerie = numeroSerie,
    fluide = fluide,
    chargeKg = chargeKg,
    misEnServiceLe = misEnServiceLe?.let { jourOuNull(it) },
    dernierControleLe = dernierControleLe?.let { jourOuNull(it) },
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
        interventionId = interventionId,
        categorie = rangement,
        fichier = nom,
        legende = legende,
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
    val avancement = StatutIntervention.entries.firstOrNull { it.name == statut }
        ?: STATUTS_HISTORIQUES[statut]
        ?: return null
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
        urgente = urgente,
        dureeMin = dureeMin,
        technicienId = technicienId,
        technicienNom = technicienNom,
        chrono = Chrono(
            arriveeLe = arriveeLe?.let(Instant::ofEpochMilli),
            demarreLe = demarreLe?.let(Instant::ofEpochMilli),
            cumuleS = cumuleS,
        ),
        numero = numero,
        signatureFichier = signatureFichier?.let { StockagePhotos.nomSur(it) },
        signeeLe = signeeLe?.let(Instant::ofEpochMilli),
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

// — Les tables arrivées avec le format 4 ————————————————————————————————————

internal fun Releve.versSauvegarde(): ReleveSauvegarde = ReleveSauvegarde(
    id = id,
    interventionId = interventionId,
    equipementId = equipementId,
    bpBar = bpBar,
    hpBar = hpBar,
    surchauffeK = surchauffeK,
    sousRefroidissementK = sousRefroidissementK,
    releveLe = releveLe.toEpochMilli(),
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun ReleveSauvegarde.versReleve(): Releve = Releve(
    id = id,
    interventionId = interventionId,
    equipementId = equipementId,
    bpBar = bpBar,
    hpBar = hpBar,
    surchauffeK = surchauffeK,
    sousRefroidissementK = sousRefroidissementK,
    releveLe = Instant.ofEpochMilli(releveLe),
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun MouvementFluide.versSauvegarde(): MouvementFluideSauvegarde = MouvementFluideSauvegarde(
    id = id,
    interventionId = interventionId,
    equipementId = equipementId,
    fluide = fluide,
    sens = sens.name,
    masseKg = masseKg,
    le = le.toEpochMilli(),
    modifieLe = modifieLe.toEpochMilli(),
)

/**
 * `null` pour un sens inconnu : comme un statut, c'est une valeur fixe de
 * l'application et non du texte libre, et le fichier entier sera refusé. Sur
 * une ligne de registre, deviner serait particulièrement malvenu — confondre
 * un ajout et une récupération inverse le bilan d'une installation.
 */
internal fun MouvementFluideSauvegarde.versMouvement(): MouvementFluide? {
    val direction = SensFluide.entries.firstOrNull { it.name == sens } ?: return null
    return MouvementFluide(
        id = id,
        interventionId = interventionId,
        equipementId = equipementId,
        fluide = fluide,
        sens = direction,
        masseKg = masseKg,
        le = Instant.ofEpochMilli(le),
        modifieLe = Instant.ofEpochMilli(modifieLe),
    )
}

internal fun PiecePosee.versSauvegarde(): PiecePoseeSauvegarde = PiecePoseeSauvegarde(
    id = id,
    interventionId = interventionId,
    designation = designation,
    reference = reference,
    quantite = quantite,
    prixUnitaire = prixUnitaire,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun PiecePoseeSauvegarde.versPiece(): PiecePosee = PiecePosee(
    id = id,
    interventionId = interventionId,
    designation = designation,
    reference = reference,
    quantite = quantite,
    prixUnitaire = prixUnitaire,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun Devis.versSauvegarde(): DevisSauvegarde = DevisSauvegarde(
    id = id,
    numero = numero,
    clientId = clientId,
    clientNom = clientNom,
    equipementId = equipementId,
    equipementNom = equipementNom,
    objet = objet,
    statut = statut.name,
    tauxTva = tauxTva,
    tvaOfferte = tvaOfferte,
    assujettiTva = assujettiTva,
    creeLe = creeLe?.format(FORMAT_DATE),
    valableJusquau = valableJusquau?.format(FORMAT_DATE),
    modifieLe = modifieLe.toEpochMilli(),
)

/** `null` pour un statut inconnu, même raison que partout ailleurs. */
internal fun DevisSauvegarde.versDevis(): Devis? {
    val etat = StatutDevis.entries.firstOrNull { it.name == statut } ?: return null
    return Devis(
        id = id,
        numero = numero,
        clientId = clientId,
        clientNom = clientNom,
        equipementId = equipementId,
        equipementNom = equipementNom,
        objet = objet,
        statut = etat,
        tauxTva = tauxTva,
        tvaOfferte = tvaOfferte,
        assujettiTva = assujettiTva,
        creeLe = creeLe?.let { jourOuNull(it) },
        valableJusquau = valableJusquau?.let { jourOuNull(it) },
        modifieLe = Instant.ofEpochMilli(modifieLe),
    )
}

internal fun LigneDevis.versSauvegarde(): LigneDevisSauvegarde = LigneDevisSauvegarde(
    id = id,
    devisId = devisId,
    designation = designation,
    quantite = quantite,
    unite = unite,
    prixUnitaire = prixUnitaire,
    offerte = offerte,
    rang = rang,
)

internal fun LigneDevisSauvegarde.versLigne(): LigneDevis = LigneDevis(
    id = id,
    devisId = devisId,
    designation = designation,
    quantite = quantite,
    unite = unite,
    prixUnitaire = prixUnitaire,
    offerte = offerte,
    rang = rang,
)

internal fun Parametres.versSauvegarde(): ParametresSauvegarde = ParametresSauvegarde(
    technicien = technicien,
    attestation = attestation,
    themeSombre = themeSombre,
    modeGants = modeGants,
    chronoAuto = chronoAuto,
    tauxHoraire = tauxHoraire,
    tauxTva = tauxTva,
    assujettiTva = assujettiTva,
    entreprise = entreprise,
    entrepriseAdresse = entrepriseAdresse,
    entrepriseTelephone = entrepriseTelephone,
    entrepriseEmail = entrepriseEmail,
    entrepriseSiret = entrepriseSiret,
    logoFichier = logoFichier,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun ParametresSauvegarde.versParametres(): Parametres = Parametres(
    technicien = technicien,
    attestation = attestation,
    themeSombre = themeSombre,
    modeGants = modeGants,
    chronoAuto = chronoAuto,
    tauxHoraire = tauxHoraire,
    tauxTva = tauxTva,
    assujettiTva = assujettiTva,
    entreprise = entreprise,
    entrepriseAdresse = entrepriseAdresse,
    entrepriseTelephone = entrepriseTelephone,
    entrepriseEmail = entrepriseEmail,
    entrepriseSiret = entrepriseSiret,
    // Le nom vient de l'extérieur : même rempart que pour une photo. Une archive
    // nommant le logo `../databases/frigopro.db` ferait écrire hors du dossier.
    logoFichier = logoFichier?.let { StockagePhotos.nomSur(it) },
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

/**
 * Une date du fichier, ou `null` si elle est mal formée.
 *
 * Contrairement à la date d'une intervention, ces dates-là sont accessoires —
 * une mise en service inconnue n'empêche pas de restaurer une machine — et
 * une valeur illisible se perd plutôt que de faire refuser toute l'archive.
 */
private fun jourOuNull(valeur: String): LocalDate? =
    runCatching { LocalDate.parse(valeur, FORMAT_DATE) }.getOrNull()


// — Les tables arrivées avec le format 5 ————————————————————————————————————

internal fun Technicien.versSauvegarde(): TechnicienSauvegarde = TechnicienSauvegarde(
    id = id,
    nom = nom,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun TechnicienSauvegarde.versTechnicien(): Technicien = Technicien(
    id = id,
    nom = nom,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun PointChecklist.versSauvegarde(): PointChecklistSauvegarde = PointChecklistSauvegarde(
    id = id,
    interventionId = interventionId,
    libelle = libelle,
    fait = fait,
    rang = rang,
    modifieLe = modifieLe.toEpochMilli(),
)

internal fun PointChecklistSauvegarde.versPoint(): PointChecklist = PointChecklist(
    id = id,
    interventionId = interventionId,
    libelle = libelle,
    fait = fait,
    rang = rang,
    modifieLe = Instant.ofEpochMilli(modifieLe),
)

internal fun Prestation.versSauvegarde(): PrestationSauvegarde = PrestationSauvegarde(
    id = id,
    designation = designation,
    categorie = categorie.name,
    prixUnitaire = prixUnitaire,
    unite = unite,
    parUnite = parUnite,
    rang = rang,
    modifieLe = modifieLe.toEpochMilli(),
)

/**
 * `null` pour une famille inconnue : comme un statut, c'est une valeur fixe de
 * l'application et non du texte libre, et le fichier entier sera refusé.
 */
internal fun PrestationSauvegarde.versPrestation(): Prestation? {
    val famille = CategoriePrestation.entries.firstOrNull { it.name == categorie } ?: return null
    return Prestation(
        id = id,
        designation = designation,
        categorie = famille,
        prixUnitaire = prixUnitaire,
        unite = unite,
        parUnite = parUnite,
        rang = rang,
        modifieLe = Instant.ofEpochMilli(modifieLe),
    )
}

internal fun VerificationFluide.versSauvegarde(): VerificationFluideSauvegarde =
    VerificationFluideSauvegarde(
        fluide = fluide,
        verifieLe = verifieLe.toEpochMilli(),
        par = par,
    )

/**
 * Un nom de fluide est du texte libre — il peut désigner un fluide que cette
 * version ne connaît pas encore — et ne peut donc pas rendre un fichier
 * illisible. Il est seulement normalisé, pour que « r410a » et « R-410A »
 * désignent bien la même courbe.
 */
internal fun VerificationFluideSauvegarde.versVerification(): VerificationFluide =
    VerificationFluide(
        fluide = Fluides.normaliser(fluide),
        verifieLe = Instant.ofEpochMilli(verifieLe),
        par = par,
    )
