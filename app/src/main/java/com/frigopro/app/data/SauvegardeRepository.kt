package com.frigopro.app.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant

/**
 * Ce qu'une sauvegarde contient, pour le dire à l'utilisateur.
 *
 * @param contenu le JSON, qui deviendra l'entrée `sauvegarde.json` de l'archive.
 * @param fichiersPhotos les noms des images à y joindre. Le dépôt ne connaît
 *   que des noms : les fichiers sont l'affaire de [StockagePhotos], et c'est ce
 *   qui permet d'éprouver l'export sans Android.
 */
data class Export(
    val contenu: String,
    val fichiersPhotos: List<String>,
    val types: Int,
    val clients: Int,
    val equipements: Int,
    val interventions: Int,
    val releves: Int = 0,
    val mouvementsFluide: Int = 0,
    val devis: Int = 0,
)

/** Issue d'une restauration. */
sealed interface ResultatRestauration {

    data class Reussie(
        val types: Int,
        val clients: Int,
        val equipements: Int,
        val photos: Int,
        val interventions: Int,
        val releves: Int = 0,
        val mouvementsFluide: Int = 0,
        val devis: Int = 0,
    ) : ResultatRestauration

    /** Fichier écrit par une version plus récente de l'application. */
    data class TropRecente(val format: Int) : ResultatRestauration

    /** Ce n'est pas une sauvegarde FrigoPro, ou elle est abîmée. */
    data object Illisible : ResultatRestauration
}

/**
 * Exporte et restaure l'intégralité des données locales.
 *
 * Les écritures passent par les DAO et non par les dépôts : ceux-ci horodatent
 * chaque écriture, ce qui effacerait le `modifieLe` que le fichier transporte —
 * or c'est précisément ce qui départagera un jour deux versions d'une même
 * ligne.
 *
 * La restauration **fusionne** au lieu de remplacer : chaque ligne écrase celle
 * qui porte le même identifiant et laisse les autres en place. Rien n'est donc
 * jamais supprimé par une restauration. Les identifiants étant des UUID, deux
 * lignes réellement distinctes ne peuvent pas se confondre.
 */
class SauvegardeRepository(
    private val interventionDao: InterventionDao,
    private val clientDao: ClientDao,
    private val typeDao: TypeInterventionDao,
    private val equipementDao: EquipementDao,
    private val suiviDao: SuiviDao,
    private val devisDao: DevisDao,
    private val parametresDao: ParametresDao,
    private val maintenant: () -> Instant = { Instant.now() },
) {

    suspend fun exporter(): Export {
        val types = typeDao.tous()
        val clients = clientDao.tous()
        val equipements = equipementDao.tous()
        val photos = equipementDao.toutesLesPhotos()
        val interventions = interventionDao.toutes()
        val releves = suiviDao.tousLesReleves()
        val mouvements = suiviDao.tousLesMouvements()
        val pieces = suiviDao.toutesLesPieces()
        val devis = devisDao.tous()
        val lignesDevis = devisDao.toutesLesLignes()
        val parametres = parametresDao.lire()
        val sauvegarde = Sauvegarde(
            format = FORMAT_COURANT,
            exporteeLe = maintenant().toString(),
            types = types.map { it.versSauvegarde() },
            clients = clients.map { it.versSauvegarde() },
            equipements = equipements.map { it.versSauvegarde() },
            photos = photos.map { it.versSauvegarde() },
            interventions = interventions.map { it.versSauvegarde() },
            releves = releves.map { it.versSauvegarde() },
            mouvementsFluide = mouvements.map { it.versSauvegarde() },
            pieces = pieces.map { it.versSauvegarde() },
            devis = devis.map { it.versSauvegarde() },
            lignesDevis = lignesDevis.map { it.versSauvegarde() },
            parametres = parametres?.versSauvegarde(),
        )

        // Les signatures sont des images comme les autres, rangées au même
        // endroit : les oublier ici rendrait des comptes-rendus non signés à
        // la restauration, ce qui vide le document de sa valeur.
        val signatures = interventions.mapNotNull { it.signatureFichier }

        return Export(
            contenu = JSON_SAUVEGARDE.encodeToString(sauvegarde),
            fichiersPhotos = photos.map { it.fichier } + signatures,
            types = types.size,
            clients = clients.size,
            equipements = equipements.size,
            interventions = interventions.size,
            releves = releves.size,
            mouvementsFluide = mouvements.size,
            devis = devis.size,
        )
    }

    suspend fun restaurer(contenu: String): ResultatRestauration {
        val sauvegarde = try {
            JSON_SAUVEGARDE.decodeFromString<Sauvegarde>(contenu)
        } catch (_: SerializationException) {
            return ResultatRestauration.Illisible
        } catch (_: IllegalArgumentException) {
            return ResultatRestauration.Illisible
        }

        if (sauvegarde.format > FORMAT_COURANT) {
            return ResultatRestauration.TropRecente(sauvegarde.format)
        }

        val interventions = sauvegarde.interventions.map { it.versIntervention() }
        if (interventions.any { it == null }) return ResultatRestauration.Illisible
        val photos = sauvegarde.photos.map { it.versPhoto() }
        if (photos.any { it == null }) return ResultatRestauration.Illisible
        val mouvements = sauvegarde.mouvementsFluide.map { it.versMouvement() }
        if (mouvements.any { it == null }) return ResultatRestauration.Illisible
        val devis = sauvegarde.devis.map { it.versDevis() }
        if (devis.any { it == null }) return ResultatRestauration.Illisible

        // Les types, le carnet puis le parc d'abord : une intervention ne doit
        // jamais désigner une ligne que la base ne contient pas encore.
        val types = sauvegarde.types.map { it.versType() }
        val clients = sauvegarde.clients.map { it.versClient() }
        val equipements = sauvegarde.equipements.map { it.versEquipement() }
        typeDao.enregistrerTous(types)
        clientDao.enregistrerTous(clients)
        equipementDao.enregistrerTous(equipements)
        equipementDao.enregistrerPhotos(photos.filterNotNull())
        interventionDao.enregistrerToutes(interventions.filterNotNull())

        // Puis ce qui s'accroche aux interventions, une fois celles-ci posées.
        val releves = sauvegarde.releves.map { it.versReleve() }
        val pieces = sauvegarde.pieces.map { it.versPiece() }
        val lignes = sauvegarde.lignesDevis.map { it.versLigne() }
        suiviDao.enregistrerReleves(releves)
        suiviDao.enregistrerMouvements(mouvements.filterNotNull())
        suiviDao.enregistrerPieces(pieces)
        devisDao.enregistrerTous(devis.filterNotNull())
        devisDao.enregistrerLignes(lignes)
        sauvegarde.parametres?.let { parametresDao.enregistrer(it.versParametres()) }

        return ResultatRestauration.Reussie(
            types = types.size,
            clients = clients.size,
            equipements = equipements.size,
            photos = photos.size,
            interventions = interventions.size,
            releves = releves.size,
            mouvementsFluide = mouvements.size,
            devis = devis.size,
        )
    }
}
