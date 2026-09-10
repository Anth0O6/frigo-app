package com.frigopro.app.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant

/** Ce qu'une sauvegarde contient, pour le dire à l'utilisateur. */
data class Export(
    val contenu: String,
    val clients: Int,
    val interventions: Int,
)

/** Issue d'une restauration. */
sealed interface ResultatRestauration {

    data class Reussie(val clients: Int, val interventions: Int) : ResultatRestauration

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
    private val maintenant: () -> Instant = { Instant.now() },
) {

    suspend fun exporter(): Export {
        val clients = clientDao.tous()
        val interventions = interventionDao.toutes()
        val sauvegarde = Sauvegarde(
            format = FORMAT_COURANT,
            exporteeLe = maintenant().toString(),
            clients = clients.map { it.versSauvegarde() },
            interventions = interventions.map { it.versSauvegarde() },
        )

        return Export(
            contenu = JSON_SAUVEGARDE.encodeToString(sauvegarde),
            clients = clients.size,
            interventions = interventions.size,
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

        // Le carnet d'abord : une intervention ne doit jamais désigner un client
        // que la base ne contient pas encore.
        val clients = sauvegarde.clients.map { it.versClient() }
        clientDao.enregistrerTous(clients)
        interventionDao.enregistrerToutes(interventions.filterNotNull())

        return ResultatRestauration.Reussie(
            clients = clients.size,
            interventions = interventions.size,
        )
    }
}
