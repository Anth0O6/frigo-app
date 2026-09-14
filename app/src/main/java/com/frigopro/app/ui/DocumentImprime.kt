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
 * La classe a d'abord décrit le seul devis. Elle en décrit **trois** depuis que
 * la facture et le compte-rendu existent, et c'est ce qui a valu de la renommer :
 * l'en-tête, le pied, la pagination et le nom du fichier sont exactement les
 * mêmes, seuls le titre, les dates et les mentions changent. Deux chaînes
 * d'impression auraient divergé au premier ajustement de maquette.
 *
 * Le compte-rendu est le seul à ne rien chiffrer, et c'est la seule chose qui
 * l'en distingue : il remplit [blocs] là où les deux autres remplissent [lignes]
 * et [totaux]. Les deux jeux s'excluent — un document est chiffré ou il ne
 * l'est pas — et c'est le seul endroit où la chaîne se sépare, au moment de
 * paginer. Voir [DocumentRapport].
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
    /**
     * Le tableau chiffré. Vide sur un compte-rendu, qui ne chiffre rien — d'où
     * le défaut : l'obliger à passer deux listes vides pour dire qu'il ne parle
     * pas d'argent aurait été une formalité, et une formalité se recopie mal.
     */
    val lignes: List<LigneImprimee> = emptyList(),
    val totaux: List<LigneTotalImprimee> = emptyList(),
    val mentions: List<String>,
    /** Le fichier du logo, `null` si l'entreprise n'en a pas posé. */
    val logoFichier: String?,
    val nomSansNumero: String = "document",
    /**
     * Les blocs d'un compte-rendu : vides sur un devis comme sur une facture.
     *
     * Leur présence est ce qui dit à l'impression quelle mise en page appliquer,
     * plutôt qu'un drapeau à côté qui pourrait se contredire avec le contenu.
     */
    val blocs: List<BlocImprime> = emptyList(),
    /** La signature du client, `null` tant qu'il n'a pas signé. */
    val signatureFichier: String? = null,
    /** Ce que la signature engage. Vide sur un document qui ne se signe pas. */
    val mentionSignature: String = "",
) {

    /** Le document est un compte-rendu : des blocs, et pas un centime. */
    val enBlocs: Boolean get() = blocs.isNotEmpty() || mentionSignature.isNotBlank()

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
