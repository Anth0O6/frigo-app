package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Article
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.CategorieFournisseur
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LieuStock
import com.frigopro.app.data.MaterielRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Les filtres du carnet de fournisseurs.
 *
 * Trois questions, et elles ne se posent pas au même moment : « qui vend des
 * fluides ? » quand on sait ce qu'il faut, « qui est-ce que j'appelle
 * d'habitude ? » quand on est pressé, « qui est près d'ici ? » quand on est
 * déjà sur la route.
 *
 * La **localisation** est une ville saisie et non une position GPS, et c'est
 * une décision : demander la permission de localisation pour ranger une liste
 * de numéros serait disproportionné, et l'application n'en a que deux, toutes
 * deux justifiées. La ville de l'intervention en cours est d'ailleurs une
 * meilleure réponse que la position réelle — on cherche un fournisseur près du
 * **chantier**, pas près de l'endroit où l'on consulte son téléphone.
 */
data class FiltresFournisseurs(
    val categorie: CategorieFournisseur? = null,
    val preferesSeulement: Boolean = false,
    val ville: String = "",
) {

    val actif: Boolean get() = categorie != null || preferesSeulement || ville.isNotBlank()

    fun retient(fournisseur: Fournisseur): Boolean {
        if (categorie != null && fournisseur.categorie != categorie) return false
        if (preferesSeulement && !fournisseur.prefere) return false
        val cherchee = ville.trim()
        if (cherchee.isNotBlank() && !fournisseur.ville.contains(cherchee, ignoreCase = true)) {
            return false
        }
        return true
    }
}

/**
 * Le magasin : ce qu'on a, ce qu'il faut racheter, et chez qui.
 *
 * Il tient l'article et le fournisseur ouverts **par leur identifiant** et non
 * par leur valeur, comme [EquipementsViewModel] : ce qu'affiche l'écran vient
 * alors toujours de la base, si bien qu'une quantité corrigée s'y voit sans
 * rien recopier et qu'une suppression referme la fiche d'elle-même.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterielViewModel(private val materiel: MaterielRepository) : ViewModel() {

    private val _recherche = MutableStateFlow("")

    val recherche: StateFlow<String> = _recherche.asStateFlow()

    /**
     * Le magasin, **le manquant d'abord**.
     *
     * Un inventaire se consulte, un manque se traite : c'est la seule liste de
     * cet écran qui appelle une action, et elle passe donc devant. Le reste suit
     * dans l'ordre alphabétique que le dépôt lui donne.
     */
    val magasin: StateFlow<List<ArticleEnStock>> =
        combine(materiel.magasin, _recherche) { liste, saisie ->
            val cherche = saisie.trim().lowercase()
            liste.filter {
                cherche.isBlank() ||
                    it.article.designation.lowercase().contains(cherche) ||
                    it.article.reference.lowercase().contains(cherche) ||
                    it.article.fournisseurNom.lowercase().contains(cherche)
            }.sortedWith(compareByDescending<ArticleEnStock> { it.enAlerte }.thenBy { it.article.designation })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /** Ce qu'il faut racheter : le chiffre de l'en-tête, et celui de l'accueil. */
    val aReapprovisionner: StateFlow<List<ArticleEnStock>> = materiel.aReapprovisionner
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /**
     * Ce que le magasin vaut, au prix d'achat.
     *
     * Au prix d'achat et jamais au prix de vente : du stock n'est pas du chiffre
     * d'affaires, c'est de l'argent immobilisé.
     */
    val valeurStock: StateFlow<Double> = materiel.magasin
        .map { liste -> liste.sumOf { it.valeurAchat } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), 0.0)

    private val _articleOuvert = MutableStateFlow<String?>(null)

    /** L'article ouvert, relu de la base à chaque écriture. */
    val articleOuvert: StateFlow<ArticleEnStock?> =
        combine(materiel.magasin, _articleOuvert) { liste, id ->
            id?.let { ouvert -> liste.firstOrNull { it.article.id == ouvert } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    // — Les fournisseurs ————————————————————————————————————————————————

    private val _filtres = MutableStateFlow(FiltresFournisseurs())

    val filtres: StateFlow<FiltresFournisseurs> = _filtres.asStateFlow()

    val fournisseurs: StateFlow<List<Fournisseur>> =
        combine(materiel.fournisseurs, _filtres) { liste, filtres ->
            liste.filter(filtres::retient)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /** Tous les fournisseurs, sans filtre : ce que la fiche d'un article propose. */
    val tousLesFournisseurs: StateFlow<List<Fournisseur>> = materiel.fournisseurs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    private val _fournisseurOuvert = MutableStateFlow<String?>(null)

    val fournisseurOuvert: StateFlow<Fournisseur?> =
        combine(materiel.fournisseurs, _fournisseurOuvert) { liste, id ->
            id?.let { ouvert -> liste.firstOrNull { it.id == ouvert } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    /**
     * La fiche en cours de **création**, qui n'est pas encore en base.
     *
     * Elle est tenue à part de [articleOuvert] et [fournisseurOuvert], qui lisent
     * la base : une fiche neuve n'y est pas encore, et l'y écrire à l'ouverture
     * laisserait une ligne vide derrière un formulaire abandonné.
     */
    private val _brouillonArticle = MutableStateFlow<Article?>(null)

    val brouillonArticle: StateFlow<Article?> = _brouillonArticle.asStateFlow()

    private val _brouillonFournisseur = MutableStateFlow<Fournisseur?>(null)

    val brouillonFournisseur: StateFlow<Fournisseur?> = _brouillonFournisseur.asStateFlow()

    // — Les gestes ——————————————————————————————————————————————————————

    fun onRecherche(saisie: String) {
        _recherche.value = saisie
    }

    fun onFiltres(filtres: FiltresFournisseurs) {
        _filtres.value = filtres
    }

    fun onOuvrirArticle(id: String) {
        _articleOuvert.value = id
    }

    fun onFermerArticle() {
        _articleOuvert.value = null
    }

    fun onNouvelArticle() {
        _brouillonArticle.value = Article(designation = "")
    }

    fun onBrouillonArticle(article: Article) {
        _brouillonArticle.value = article
    }

    fun onAnnulerBrouillonArticle() {
        _brouillonArticle.value = null
    }

    /** Une saisie sans désignation laisse le formulaire ouvert plutôt que d'écrire à moitié. */
    fun onEnregistrerArticle() {
        val article = _brouillonArticle.value ?: return
        if (article.designation.isBlank()) return

        _brouillonArticle.value = null
        viewModelScope.launch { materiel.enregistrerArticle(article) }
    }

    fun onSupprimerArticle(id: String) {
        _articleOuvert.value = null
        viewModelScope.launch { materiel.supprimerArticle(id) }
    }

    /** L'inventaire : on compte ce qu'on a et on l'écrit. */
    fun onDefinirStock(articleId: String, lieu: LieuStock, quantite: Double, minimum: Double) {
        viewModelScope.launch { materiel.definirStock(articleId, lieu, quantite, minimum) }
    }

    /** Un mouvement : une pièce posée, une livraison reçue. */
    fun onBouger(articleId: String, lieu: LieuStock, delta: Double) {
        viewModelScope.launch { materiel.bouger(articleId, lieu, delta) }
    }

    /** Le geste du matin : charger le camion depuis l'atelier. */
    fun onTransferer(articleId: String, depuis: LieuStock, vers: LieuStock, quantite: Double) {
        viewModelScope.launch { materiel.transferer(articleId, depuis, vers, quantite) }
    }

    fun onOuvrirFournisseur(id: String) {
        _fournisseurOuvert.value = id
    }

    fun onFermerFournisseur() {
        _fournisseurOuvert.value = null
    }

    fun onNouveauFournisseur() {
        _brouillonFournisseur.value = Fournisseur(nom = "")
    }

    fun onBrouillonFournisseur(fournisseur: Fournisseur) {
        _brouillonFournisseur.value = fournisseur
    }

    fun onAnnulerBrouillonFournisseur() {
        _brouillonFournisseur.value = null
    }

    fun onEnregistrerFournisseur() {
        val fournisseur = _brouillonFournisseur.value ?: return
        if (fournisseur.nom.isBlank()) return

        _brouillonFournisseur.value = null
        viewModelScope.launch { materiel.enregistrerFournisseur(fournisseur) }
    }

    fun onSupprimerFournisseur(id: String) {
        _fournisseurOuvert.value = null
        viewModelScope.launch { materiel.supprimerFournisseur(id) }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                MaterielViewModel(conteneur.materiel)
            }
        }
    }
}
