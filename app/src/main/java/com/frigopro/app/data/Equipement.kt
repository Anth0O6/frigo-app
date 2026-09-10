package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Une machine du parc d'un client : vitrine, chambre froide, groupe de
 * production de froid.
 *
 * La fiche ne porte **qu'un nom d'usage**, et c'est un choix : marque, modèle
 * et numéro de série sont écrits sur la plaque signalétique, et la photographier
 * vaut mieux que les retaper sur un toit. Le nom, lui, est ce qui permet de
 * reconnaître la machine dans une liste avant d'être monté lire la plaque —
 * « vitrine salle 2 » dit en trois mots ce que trois références ne disent pas.
 * La contrepartie est assumée : un numéro de série ne se cherche pas en texte,
 * il se lit sur la photo. Le jour où l'application commandera des pièces, ces
 * champs s'ajouteront par une simple migration.
 *
 * @param clientId client chez qui la machine se trouve. Une machine n'existe
 *   jamais seule : c'est le parc d'un client, et c'est par lui qu'on y arrive.
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(
    tableName = "equipements",
    indices = [Index("clientId")],
)
data class Equipement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val nom: String,
    val modifieLe: Instant = Instant.EPOCH,
)
