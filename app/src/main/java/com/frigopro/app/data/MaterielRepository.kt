package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/**
 * Le magasin : ce qu'on achète, ce qu'on en a, et chez qui.
 *
 * Trois décisions se lisent ici plutôt que dans le SQL :
 *
 * 1. **Rien n'est stocké de ce qui se déduit.** Le manque (`quantite <
 *    minimum`), la marge, la valeur du stock se recalculent à chaque lecture.
 *    Un booléen « à racheter » en base serait faux à la première sortie de
 *    stock — même règle qu'« en retard » sur une facture.
 * 2. **Un mouvement de stock est une addition, pas une réécriture.** Poser une
 *    pièce chez un client retire une unité ; ce qui compte est le delta, et
 *    c'est lui que l'appelant fournit. Faire écrire la quantité absolue aurait
 *    écrasé la sortie d'hier si deux écrans se répondaient mal.
 * 3. **Le tri passe par un `Collator` français**, comme le carnet de clients :
 *    `COLLATE NOCASE` rejetterait « Économiseur » après « Zéolithe ».
 */
class MaterielRepository(private val dao: MaterielDao) {

    private fun collateur() = Collator.getInstance(Locale.FRENCH)

    // — Les fournisseurs ————————————————————————————————————————————————

    /**
     * Les fournisseurs, **les préférés d'abord**, puis par ordre alphabétique.
     *
     * La préférence remonte plutôt qu'elle ne colore : quand on cherche un
     * numéro à 7 h du matin, ce qu'on veut est que le bon soit en haut, pas
     * qu'il soit joliment marqué au milieu de douze autres.
     */
    val fournisseurs: Flow<List<Fournisseur>> = dao.observerFournisseurs().map { liste ->
        val collateur = collateur()
        liste.sortedWith(
            compareByDescending<Fournisseur> { it.prefere }
                .thenComparator { a, b -> collateur.compare(a.nom, b.nom) },
        )
    }

    suspend fun enregistrerFournisseur(fournisseur: Fournisseur): Fournisseur {
        val nettoye = fournisseur.copy(
            nom = fournisseur.nom.trim(),
            telephone = fournisseur.telephone.trim(),
            email = fournisseur.email.trim(),
            adresse = fournisseur.adresse.trim(),
            ville = fournisseur.ville.trim(),
            siteCatalogue = normaliserAdresseWeb(fournisseur.siteCatalogue),
            notes = fournisseur.notes.trim(),
            modifieLe = Instant.now(),
        )
        dao.enregistrerFournisseur(nettoye)
        return nettoye
    }

    /** Le renommage suit les articles ; la suppression coupe le lien et laisse le nom. */
    suspend fun renommerFournisseur(id: String, nom: String) {
        val propre = nom.trim()
        if (propre.isNotBlank()) dao.renommerFournisseur(id, propre)
    }

    suspend fun supprimerFournisseur(id: String) = dao.supprimerFournisseur(id)

    // — Les articles ————————————————————————————————————————————————————

    /** Le magasin, avec ce qu'on a de chaque article aux deux endroits. */
    val magasin: Flow<List<ArticleEnStock>> =
        combine(dao.observerArticles(), dao.observerStocks()) { articles, stocks ->
            val parArticle = stocks.groupBy { it.articleId }
            val collateur = collateur()
            articles
                .sortedWith { a, b -> collateur.compare(a.designation, b.designation) }
                .map { article ->
                    ArticleEnStock(
                        article = article,
                        stocks = parArticle[article.id].orEmpty().associateBy { it.lieu },
                    )
                }
        }

    /**
     * Ce qu'il faut racheter, le plus manquant d'abord.
     *
     * C'est la seule liste du magasin qui appelle une action, et c'est elle que
     * l'écran met en tête : un inventaire se consulte, un manque se traite.
     */
    val aReapprovisionner: Flow<List<ArticleEnStock>> = magasin.map { liste ->
        liste.filter { it.enAlerte }
            .sortedByDescending { entree ->
                entree.aReapprovisionner.maxOfOrNull { lieu ->
                    entree.stocks[lieu]?.manquant ?: 0.0
                } ?: 0.0
            }
    }

    suspend fun enregistrerArticle(article: Article): Article {
        val nettoye = article.copy(
            reference = article.reference.trim(),
            designation = article.designation.trim(),
            fournisseurNom = article.fournisseurNom.trim(),
            // Un prix négatif n'a pas de sens et fausserait toute marge qui en
            // découle : il est ramené à zéro, qui veut dire « pas renseigné ».
            prixAchat = article.prixAchat.coerceAtLeast(0.0),
            prixVente = article.prixVente.coerceAtLeast(0.0),
            unite = article.unite.trim(),
            modifieLe = Instant.now(),
        )
        dao.enregistrerArticle(nettoye)
        return nettoye
    }

    suspend fun supprimerArticle(id: String) = dao.supprimerArticle(id)

    /** Les articles tels qu'en base, lus une fois : ce sur quoi une saisie se reporte. */
    suspend fun articles(): List<Article> = dao.tousLesArticles()

    /**
     * Écrit d'un coup ce que la saisie groupée des prix a changé, et rend le
     * nombre d'articles réellement écrits.
     *
     * Trois règles, et chacune répare une façon de mal écrire :
     *
     * 1. **Une seule écriture**, par `enregistrerArticles` dont Room fait une
     *    transaction : trente-huit prix enregistrés à moitié laisseraient un
     *    magasin dont on ne sait plus quelle ligne vient du tarif de ce mois.
     * 2. **Seulement ce qui a changé.** Une ligne touchée puis remise à sa valeur
     *    n'est pas réécrite, et ne prend donc pas un `modifieLe` neuf : c'est lui
     *    qui départagera deux versions le jour d'une synchronisation, et une date
     *    posée sans modification ferait gagner une ligne qui n'a rien à dire.
     * 3. **La saisie se reporte sur l'article lu maintenant**, pas sur celui que
     *    l'écran avait chargé : seuls ses quatre champs bougent (voir
     *    [PrixArticle]). Un article supprimé entre-temps est ignoré plutôt que
     *    recréé — une ligne revenue d'entre les morts par un prix serait pire
     *    qu'un prix perdu.
     *
     * La copie du nom de fournisseur **suit le lien** au moment d'écrire, comme
     * [renommerFournisseur] la fait suivre : un fournisseur renommé pendant la
     * saisie n'en laisse pas l'ancien nom. Un fournisseur supprimé pendant la
     * saisie coupe le lien et garde le nom, exactement comme
     * [supprimerFournisseur] l'aurait fait s'il était passé après.
     */
    suspend fun enregistrerPrix(saisies: List<PrixArticle>): Int {
        if (saisies.isEmpty()) return 0
        val articles = dao.tousLesArticles().associateBy { it.id }
        val fournisseurs = dao.tousLesFournisseurs().associateBy { it.id }
        val maintenant = Instant.now()

        val ecrits = saisies.mapNotNull { saisie ->
            val actuel = articles[saisie.articleId] ?: return@mapNotNull null
            val lien = saisie.fournisseurId?.let { fournisseurs[it] }
            val resolue = when {
                saisie.fournisseurId == null -> saisie
                lien != null -> saisie.copy(fournisseurNom = lien.nom)
                else -> saisie.copy(fournisseurId = null)
            }
            val nouveau = resolue.appliqueA(actuel)
            if (nouveau == actuel) null else nouveau.copy(modifieLe = maintenant)
        }
        if (ecrits.isNotEmpty()) dao.enregistrerArticles(ecrits)
        return ecrits.size
    }

    // — Les stocks ——————————————————————————————————————————————————————

    /**
     * Pose la quantité et le seuil d'un article à un endroit.
     *
     * C'est l'inventaire : on compte ce qu'on a et on l'écrit. Pour un
     * mouvement — une pièce posée, une pièce reçue — c'est [bouger] qu'il faut,
     * qui additionne au lieu d'écraser.
     */
    suspend fun definirStock(
        articleId: String,
        lieu: LieuStock,
        quantite: Double,
        minimum: Double,
    ): Stock {
        val existant = dao.stock(articleId, lieu)
        val ligne = (existant ?: Stock(articleId = articleId, lieu = lieu)).copy(
            // Une quantité négative est un compte faux, pas une dette : on ne
            // doit pas des pièces à son propre magasin.
            quantite = quantite.coerceAtLeast(0.0),
            minimum = minimum.coerceAtLeast(0.0),
            modifieLe = Instant.now(),
        )
        dao.enregistrerStock(ligne)
        return ligne
    }

    /**
     * Ajoute ou retire au stock d'un endroit.
     *
     * L'appelant donne le **delta** — `-1.0` pour une pièce posée chez un
     * client, `+10.0` pour une livraison reçue —, et le dépôt le reporte sur ce
     * qu'il y avait. C'est ce qui rend l'opération juste quand deux écrans
     * l'appellent à quelques secondes d'écart.
     *
     * Le seuil est conservé : un mouvement ne le concerne pas.
     */
    suspend fun bouger(articleId: String, lieu: LieuStock, delta: Double): Stock {
        val existant = dao.stock(articleId, lieu)
        val ligne = (existant ?: Stock(articleId = articleId, lieu = lieu)).let {
            it.copy(
                quantite = (it.quantite + delta).coerceAtLeast(0.0).arrondiCentieme(),
                modifieLe = Instant.now(),
            )
        }
        dao.enregistrerStock(ligne)
        return ligne
    }

    /**
     * Déplace du stock de l'atelier vers le camion, ou l'inverse.
     *
     * Le geste du matin : on charge ce dont on aura besoin. Deux mouvements
     * plutôt qu'un champ « en transit », parce qu'il n'y a pas d'état
     * intermédiaire — une pièce est dans le camion ou elle ne l'est pas.
     */
    suspend fun transferer(articleId: String, depuis: LieuStock, vers: LieuStock, quantite: Double) {
        if (depuis == vers || quantite <= 0.0) return
        // Jamais plus que ce qu'il y a : charger quatre détendeurs quand il y en
        // a trois laisserait l'atelier à -1, un compte que rien ne rattraperait.
        val disponible = dao.stock(articleId, depuis)?.quantite ?: 0.0
        val bouge = minOf(quantite, disponible)
        if (bouge <= 0.0) return

        bouger(articleId, depuis, -bouge)
        bouger(articleId, vers, bouge)
    }
}

/**
 * Ce qu'il faut ajouter devant une adresse tapée sans protocole.
 *
 * « www.fournisseur.fr » n'est pas une adresse qu'un navigateur ouvre par une
 * intention Android : il lui faut un schéma. Le corriger au moment où c'est
 * saisi vaut mieux qu'un bouton qui ne fait rien — et `https` plutôt que `http`,
 * parce qu'un catalogue fournisseur demande souvent de se connecter.
 */
internal fun normaliserAdresseWeb(saisie: String): String {
    val propre = saisie.trim()
    if (propre.isBlank()) return ""
    val minuscules = propre.lowercase()
    return if (minuscules.startsWith("http://") || minuscules.startsWith("https://")) {
        propre
    } else {
        "https://$propre"
    }
}
