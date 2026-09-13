package com.frigopro.app.ui

import com.frigopro.app.data.Parametres
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Un document tel qu'il s'imprime : tout le texte, et rien d'autre.
 *
 * Séparé du dessin pour la raison qui vaut partout dans ce projet — ce qui se
 * vérifie sans téléphone doit l'être — et ici la raison est plus forte
 * qu'ailleurs : **ces documents partent chez un client**. Une mention légale
 * oubliée, une TVA affichée par une entreprise qui n'y est pas assujettie, un
 * total qui ne correspond pas aux lignes au-dessus : ce sont des fautes qu'on
 * découvre après l'envoi, et dont l'entreprise répond.
 *
 * La classe a d'abord décrit le seul devis. Elle en décrit deux depuis que la
 * facture existe, et c'est ce qui a valu de la renommer : la mise en page, la
 * pagination et le dessin sont exactement les mêmes, seuls le titre, les dates
 * et les mentions changent. Deux chaînes d'impression auraient divergé au
 * premier ajustement de maquette.
 *
 * @param nomSansNumero ce qui nomme le fichier tant que le document n'a pas de
 *   numéro. Un devis en brouillon en est là ; une facture, jamais — elle n'est
 *   exportable qu'une fois émise.
 */
data class DocumentImprime(
    val titre: String,
    val numero: String,
    /** L'émetteur : l'entreprise, ou à défaut le technicien. */
    val emetteur: List<String>,
    /** Le destinataire : le client, son adresse si on l'a. */
    val destinataire: List<String>,
    val dates: List<String>,
    val objet: String,
    /** La machine concernée, quand le document en désigne une. */
    val machine: String,
    val lignes: List<LigneImprimee>,
    val totaux: List<LigneTotalImprimee>,
    val mentions: List<String>,
    /** Le fichier du logo, `null` si l'entreprise n'en a pas posé. */
    val logoFichier: String?,
    val nomSansNumero: String = "document",
) {

    /** Le nom du fichier PDF, tel que le client le recevra. */
    val nomFichier: String
        get() = (numero.ifBlank { nomSansNumero } + ".pdf").replace(Regex("[^A-Za-z0-9._-]"), "-")
}

internal val FORMAT_DATE_DOCUMENT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/**
 * L'émetteur d'un document, commun au devis et à la facture.
 *
 * L'entreprise si elle est renseignée, sinon le technicien : un document doit
 * dire de qui il vient, et une entreprise non saisie ne doit pas produire un
 * en-tête vide — c'est le cas normal d'un artisan qui n'a pas encore ouvert les
 * Réglages.
 */
internal fun emetteurDe(parametres: Parametres, defaut: String): List<String> = buildList {
    add(parametres.entreprise.ifBlank { parametres.technicien }.ifBlank { defaut })
    if (parametres.entrepriseAdresse.isNotBlank()) add(parametres.entrepriseAdresse)
    val contact = listOf(parametres.entrepriseTelephone, parametres.entrepriseEmail)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    if (contact.isNotBlank()) add(contact)
    if (parametres.entrepriseSiret.isNotBlank()) {
        add("SIRET ${parametres.entrepriseSiret}")
    }
    if (parametres.attestation.isNotBlank()) {
        // L'attestation de capacité se porte sur les documents : c'est ce qui
        // prouve le droit de manipuler des fluides frigorigènes, et un client
        // professionnel la demande.
        add("Attestation fluides ${parametres.attestation}")
    }
}
