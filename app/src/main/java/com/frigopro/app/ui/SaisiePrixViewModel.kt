package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Article
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.MaterielRepository
import com.frigopro.app.data.ParametresRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La saisie groupée des prix du magasin : remplir un tarif fournisseur d'un
 * trait, au lieu d'ouvrir trente-huit fiches.
 *
 * ## Une saisie, puis une écriture
 *
 * À la différence de la fiche d'un article, qui écrit à chaque frappe, rien
 * n'est écrit ici avant le geste « Enregistrer ». C'est le motif de la section
 * du déplacement d'un devis, et pour une raison plus forte encore : une fiche
 * écrit une ligne, cette page en écrirait trente-huit à chaque caractère — et
 * l'on veut pouvoir relire tout un tarif avant qu'il parte sur un devis.
 *
 * ## Ce qui se perd, et ce qui ne se perd pas
 *
 * La saisie vit **dans ce ViewModel et non dans l'écran**, ce qui la fait
 * survivre à un changement d'onglet : on interrompt souvent une saisie de tarif
 * pour répondre à un appel ou regarder la tournée, et revenir sur une page vide
 * serait perdre une demi-heure. Elle ne survit pas à la fermeture de
 * l'application, et c'est la seule perte possible — le retour système, lui,
 * demande quoi faire de ce qui n'est pas enregistré (voir `EcranSaisiePrix`).
 */
class SaisiePrixViewModel(
    private val materiel: MaterielRepository,
    private val parametres: ParametresRepository,
) : ViewModel() {

    private val _ouverte = MutableStateFlow(false)

    /** La saisie remplace la liste du magasin tant qu'elle est ouverte. */
    val ouverte: StateFlow<Boolean> = _ouverte.asStateFlow()

    /** Ce qu'on a tapé, par article — seulement les lignes touchées. */
    private val _saisies = MutableStateFlow<Map<String, SaisiePrix>>(emptyMap())

    private val _cochees = MutableStateFlow<Set<String>>(emptySet())

    private val _recherche = MutableStateFlow("")

    private val _filtre = MutableStateFlow(FiltrePrix.TOUS)

    /** Le magasin tel qu'en base, dans l'ordre alphabétique du dépôt. */
    private val articles = materiel.magasin.map { liste -> liste.map { it.article } }

    private val coefficient = parametres.parametres.map { it.coefficientMateriel }

    /** La recherche et le filtre, réunis : `combine` ne prend pas plus de cinq flux. */
    private val filtres = combine(_recherche, _filtre) { recherche, filtre -> recherche to filtre }

    /**
     * Ce que l'écran montre, recomposé à chaque frappe et à chaque écriture.
     *
     * Le coefficient y est lu en flux et non à l'ouverture : réglé dans l'onglet
     * voisin pendant la saisie, il se met à proposer sans qu'il faille rouvrir.
     */
    val etat: StateFlow<EtatSaisiePrix> =
        combine(articles, coefficient, _saisies, _cochees, filtres) { liste, coef, saisies, cochees, paire ->
            composerSaisiePrix(
                articles = liste,
                coefficient = coef,
                saisies = saisies,
                cochees = cochees,
                recherche = paire.first,
                filtre = paire.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), EtatSaisiePrix())

    /** Le carnet, pour attribuer un fournisseur aux lignes cochées. */
    val fournisseurs: StateFlow<List<Fournisseur>> = materiel.fournisseurs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    // — Ouvrir, fermer ————————————————————————————————————————————————————

    fun onOuvrir() {
        _ouverte.value = true
    }

    /**
     * Ferme en abandonnant ce qui n'est pas enregistré.
     *
     * L'écran ne l'appelle qu'après avoir demandé, quand il y a quelque chose à
     * perdre : c'est un abandon choisi, jamais un abandon subi.
     */
    fun onAbandonner() {
        _saisies.value = emptyMap()
        _cochees.value = emptySet()
        _recherche.value = ""
        _filtre.value = FiltrePrix.TOUS
        _ouverte.value = false
    }

    // — La saisie ———————————————————————————————————————————————————————

    fun onRecherche(saisie: String) {
        _recherche.value = saisie
    }

    fun onFiltre(filtre: FiltrePrix) {
        _filtre.value = filtre
    }

    fun onReference(article: Article, reference: String) =
        modifier(article) { it.copy(reference = reference) }

    /** Un prix d'achat : la vente le suit tant qu'elle n'a pas été tapée. */
    fun onPrixAchat(article: Article, prix: Double?) =
        modifier(article) { it.copy(prixAchat = prix) }

    /**
     * Un prix de vente tapé : il fait foi, et aucun calcul ne l'écrasera.
     * `null` — un champ vidé — rend la main au coefficient.
     */
    fun onPrixVente(article: Article, prix: Double?) =
        modifier(article) { it.copy(venteTouchee = true, venteManuelle = prix) }

    /**
     * La saisie d'une ligne, créée à partir de la base au premier geste.
     *
     * L'article est passé par l'écran plutôt que relu ici dans `etat.value` :
     * c'est l'écran qui le montre, et c'est donc lui qui sait sur quelle version
     * le doigt s'est posé.
     */
    private fun modifier(article: Article, transformation: (SaisiePrix) -> SaisiePrix) {
        _saisies.update { saisies ->
            saisies + (article.id to transformation(saisies[article.id] ?: SaisiePrix.de(article)))
        }
    }

    // — La sélection, et le fournisseur en lot ——————————————————————————

    fun onCocher(articleId: String) {
        _cochees.update { if (articleId in it) it - articleId else it + articleId }
    }

    /**
     * Coche ou décoche **ce que l'écran montre** : c'est ainsi qu'on désigne
     * « toutes les goulottes » — une recherche, puis un geste.
     */
    fun onCocherTout(articles: List<Article>, cocher: Boolean) {
        val identifiants = articles.map { it.id }.toSet()
        _cochees.update { if (cocher) it + identifiants else it - identifiants }
    }

    /**
     * Donne un fournisseur à toutes les lignes cochées, le lien et sa copie.
     *
     * `null` détache, et **garde le nom** comme la fiche d'un article le fait :
     * un article doit continuer de dire d'où il venait. La sélection est vidée
     * ensuite — le lot est fait, et une sélection qui survivrait au geste en
     * attirerait un second par mégarde.
     *
     * Une ligne cochée puis masquée par la recherche reçoit le fournisseur elle
     * aussi — on compose une sélection en plusieurs recherches —, et l'écran le
     * dit en comptant les lignes cochées masquées comprises. Les articles sont
     * donc relus en base plutôt que pris à l'écran, qui ne montre que les
     * visibles.
     */
    fun onAttribuerFournisseur(fournisseur: Fournisseur?) {
        val cochees = _cochees.value
        if (cochees.isEmpty()) return
        _cochees.value = emptySet()
        viewModelScope.launch {
            val parId = materiel.articles().associateBy { it.id }
            cochees.mapNotNull { parId[it] }.forEach { article ->
                modifier(article) { saisie ->
                    saisie.copy(
                        fournisseurId = fournisseur?.id,
                        fournisseurNom = fournisseur?.nom ?: saisie.fournisseurNom,
                    )
                }
            }
        }
    }

    // — L'écriture ——————————————————————————————————————————————————————

    /**
     * Écrit d'un coup ce qui a changé, et retire de la saisie ce qui est écrit.
     *
     * Le coefficient et les articles sont **relus en base** au moment d'écrire,
     * et non pris dans `etat.value` : un `stateIn(WhileSubscribed)` que plus
     * personne ne collecte — l'écran vient de se fermer sur « Enregistrer et
     * fermer » — resterait figé à sa dernière valeur, voire à sa valeur initiale.
     * C'est le défaut qui avait fait partir des factures sans logo.
     *
     * La saisie n'est vidée qu'**après** l'écriture, et seulement des lignes qui
     * n'ont pas bougé depuis : vider d'abord aurait fait clignoter les anciens
     * prix le temps que la base réponde, et vider tout aurait perdu un chiffre
     * tapé pendant l'écriture.
     */
    fun onEnregistrer() {
        val instantane = _saisies.value
        if (instantane.isEmpty()) return
        _cochees.value = emptySet()
        viewModelScope.launch {
            val coef = parametres.lire().coefficientMateriel
            val parId = materiel.articles().associateBy { it.id }
            val prix = instantane.mapNotNull { (id, saisie) ->
                parId[id]?.let { saisie.versPrix(it, coef) }
            }
            materiel.enregistrerPrix(prix)
            _saisies.update { courantes ->
                courantes.filter { (id, saisie) -> instantane[id] != saisie }
            }
        }
    }

    /** Le geste du retour quand il y a quelque chose à garder : écrire, puis fermer. */
    fun onEnregistrerEtFermer() {
        onEnregistrer()
        _recherche.value = ""
        _filtre.value = FiltrePrix.TOUS
        _ouverte.value = false
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                SaisiePrixViewModel(conteneur.materiel, conteneur.parametres)
            }
        }
    }
}
