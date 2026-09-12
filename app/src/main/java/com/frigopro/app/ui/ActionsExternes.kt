package com.frigopro.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/**
 * Ouvre le composeur téléphonique, numéro déjà saisi.
 *
 * `ACTION_DIAL` plutôt que `ACTION_CALL` : l'appel reste déclenché par
 * l'utilisateur, et l'application n'a donc pas besoin de la permission
 * d'appeler — une permission que personne n'aime accorder.
 *
 * Le numéro est encodé : un numéro se note volontiers « 02 35 00 00 00 », et
 * une espace n'a rien à faire dans une URI.
 */
internal fun Context.appeler(telephone: String) {
    demarrer(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(telephone)}".toUri()))
}

/**
 * Ouvre l'adresse dans l'application de cartographie du téléphone.
 *
 * Le schéma `geo:` laisse l'utilisateur choisir son application plutôt que d'en
 * imposer une.
 */
internal fun Context.ouvrirItineraire(adresse: String) {
    demarrer(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(adresse)}".toUri()))
}

/**
 * Propose d'envoyer un document : courriel, messagerie, ou simple
 * enregistrement.
 *
 * Un sélecteur et non une application désignée : un devis part tantôt par
 * courriel à un syndic, tantôt par message à un restaurateur qui ne lit pas ses
 * mails, et choisir à sa place pour lui aurait obligé à ressortir le fichier d'un
 * gestionnaire de fichiers. L'objet et le corps sont pré-remplis — ce qu'une
 * messagerie ignore et qu'un client de courriel reprend.
 *
 * `FLAG_GRANT_READ_URI_PERMISSION` est ce qui rend l'URI lisible par
 * l'application choisie, et le temps de cette intention seulement : notre dossier
 * ne devient pas public pour autant.
 */
internal fun Context.envoyerDocument(
    document: Uri,
    objet: String,
    corps: String,
    type: String = "application/pdf",
) {
    val intention = Intent(Intent.ACTION_SEND).apply {
        setType(type)
        putExtra(Intent.EXTRA_STREAM, document)
        putExtra(Intent.EXTRA_SUBJECT, objet)
        putExtra(Intent.EXTRA_TEXT, corps)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    demarrer(Intent.createChooser(intention, objet))
}

/**
 * Un téléphone sans composeur ni application de cartographie est le seul cas
 * d'échec possible, et il n'y a alors rien d'utile à proposer : mieux vaut ne
 * rien faire que planter en pleine tournée.
 */
private fun Context.demarrer(intention: Intent) {
    try {
        startActivity(intention)
    } catch (_: ActivityNotFoundException) {
        // Sans application pour la recevoir, l'intention n'a pas de repli.
    }
}
