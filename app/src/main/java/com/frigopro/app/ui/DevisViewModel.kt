package com.frigopro.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Article
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Capture
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.LecteurTarif
import com.frigopro.app.data.LectureTarif
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.LignesDegressives
import com.frigopro.app.data.MaterielRepository
import com.frigopro.app.data.OrigineTrajet
import com.frigopro.app.data.PalierPrestation
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.DevisFacture
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
import com.frigopro.app.data.RaisonEchec
import com.frigopro.app.data.ResultatItineraire
import com.frigopro.app.data.ScansDevis
import com.frigopro.app.data.ServiceItineraire
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.data.TarifDegressif
import com.frigopro.app.data.Trajet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Les trois chiffres de l'en-tête des devis.
 *
 * « En cours » compte en TTC : c'est le montant que le client verrait, et donc
 * celui qui décide s'il faut relancer. Le hors taxes n'intéresse personne à ce
 * stade — il n'entre en jeu qu'une fois le devis accepté.
 */
data class CompteursDevis(
    val enAttente: Int = 0,
    val acceptes: Int = 0,
    val pipelineTtc: Double = 0.0,
) {

    companion object {

        fun de(devis: List<DevisChiffre>): CompteursDevis {
            val attente = devis.filter { it.enAttente }
            return CompteursDevis(
                enAttente = attente.size,
                acceptes = devis.count { it.devis.statut == StatutDevis.ACCEPTE },
                pipelineTtc = attente.sumOf { it.totalTtc },
            )
        }
    }
}

/**
 * Les devis rangés en deux, et c'est tout ce que le rangement fait.
 *
 * Un devis qui a produit une facture est une affaire close : le laisser dans la
 * liste principale y mélange ce qu'il reste à faire et ce qui est fait, et c'est
 * la première qui est la question de l'écran. Il n'est pas **supprimé** pour
 * autant — c'est la pièce qui dit ce que le client a accepté, et on y revient
 * quand il conteste la facture.
 */
data class DevisRanges(
    val enCours: List<DevisChiffre> = emptyList(),
    val facturés: List<DevisFacturé> = emptyList(),
)

/** Un devis facturé, et de quelle facture il s'agit. */
data class DevisFacturé(val chiffre: DevisChiffre, val facture: DevisFacture)

/**
 * La vue Devis de l'onglet Factures : la liste, et le devis ouvert.
 *
 * Même motif que partout ailleurs — le devis ouvert est retenu par son
 * identifiant, si bien qu'une ligne ajoutée ou un changement de statut se
 * voient sans rien recopier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevisViewModel(
    private val devis: DevisRepository,
    private val clients: ClientRepository,
    private val parametres: ParametresRepository,
    private val prestations: PrestationRepository,
    private val equipements: EquipementRepository,
    /**
     * Les factures, dont cet écran n'a besoin que d'une chose : savoir quel devis
     * en a déjà produit une, pour le ranger plus bas.
     */
    private val factures: FactureRepository,
    /** Le magasin : les références, les deux prix, et de quoi chiffrer du matériel. */
    private val materiel: MaterielRepository,
    /**
     * Ce qui produit le PDF. Une interface, parce que le dessin est
     * irréductiblement Android et qu'un ViewModel qui en dépendrait directement ne
     * se construirait plus dans un test JVM — voir [ProducteurPdf].
     */
    private val pdf: ProducteurPdf,
    /**
     * Le calcul d'itinéraire. Une interface, pour la même raison que [pdf] : le
     * réseau ne se joint pas depuis un test JVM, et un faux service suffit à
     * éprouver ce que l'écran en fait.
     */
    private val itineraires: ServiceItineraire,
    /** La lecture d'un devis fournisseur, même chemin que la saisie des prix. */
    private val lecteur: LecteurTarif = LecteurTarif { null },
    private val scans: ScansDevis = ScansDevis.Aucun,
) : ViewModel() {

    /**
     * Les devis avec leur montant.
     *
     * Le montant est la somme des lignes, recalculée par SQL (voir
     * [DevisRepository.devisChiffres]) : le recopier sur la ligne du devis
     * serait s'exposer à ce qu'il cesse d'être juste après une modification,
     * et un devis qui affiche un total faux est pire qu'un devis sans total.
     */
    val liste: StateFlow<DevisRanges> =
        combine(devis.devisChiffres, factures.devisFactures) { chiffres, liens ->
            DevisRanges(
                enCours = chiffres.filter { it.devis.id !in liens },
                facturés = chiffres.mapNotNull { chiffre ->
                    liens[chiffre.devis.id]?.let { DevisFacturé(chiffre, it) }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), DevisRanges())

    /**
     * Les compteurs de l'en-tête : ce qui attend, ce qui est gagné, et combien.
     *
     * « En cours » est le montant TTC de ce qui attend une réponse — c'est le
     * chiffre qui dit s'il faut relancer un client, et la raison d'être de
     * l'écran autant que la liste elle-même.
     */
    val compteurs: StateFlow<CompteursDevis> = devis.devisChiffres
        .map { CompteursDevis.de(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), CompteursDevis())

    /** Le catalogue, groupé par famille, tel que la feuille l'affiche. */
    val catalogue: StateFlow<Map<CategoriePrestation, List<Prestation>>> = prestations.parCategorie
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyMap())

    /**
     * Le magasin, pour y puiser du matériel.
     *
     * **Tout le magasin**, et non ce qui est en stock : il sert de tarifaire
     * autant que d'inventaire, et l'article qu'on chiffre est précisément celui
     * qu'on n'a pas encore acheté. Filtrer sur le camion aurait rendu
     * inchiffrable le cas le plus courant — une installation neuve.
     */
    val magasin: StateFlow<List<ArticleEnStock>> = materiel.magasin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val carnet: StateFlow<List<Client>> = clients.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val reglages: StateFlow<Parametres> = parametres.parametres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), Parametres())

    private val _ouvert = MutableStateFlow<String?>(null)

    val ouvert: StateFlow<String?> = _ouvert.asStateFlow()

    /** Le devis ouvert et ses lignes, d'où se calculent les totaux. */
    val complet: StateFlow<DevisComplet?> = _ouvert
        .flatMapLatest { id -> if (id == null) flowOf(null) else devis.observerComplet(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    /**
     * Le nombre d'unités intérieures de la machine visée par le devis.
     *
     * Vaut 1 quand le devis ne désigne aucune machine, ou qu'elle n'a pas d'unité :
     * un monosplit en a bien une, confondue avec son groupe, et traiter ce cas à
     * part aurait demandé deux chemins de calcul pour le même résultat. Le compte
     * est **dérivé** du parc et non stocké : ajouter une unité change ce que le
     * prochain devis propose, sans qu'on ait à y penser.
     *
     * Le devis visant une unité plutôt qu'un groupe compte pour une : on chiffre
     * alors ce qu'on fait sur cette unité-là.
     */
    val unitesVisees: StateFlow<Int> = combine(complet, equipements.parGroupe) { ouvert, parc ->
        unitesDe(ouvert?.devis?.equipementId, parc)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), 1)

    /**
     * Le parc du client du devis ouvert : les machines parmi lesquelles choisir
     * celle que le devis vise.
     *
     * C'est le chemin qui manquait au multi-split : [unitesVisees] savait compter
     * les unités de la machine du devis, mais aucun devis n'en désignait jamais
     * une, si bien que la quantité pré-remplie valait toujours un. Le parc d'un
     * seul client, parce qu'un devis chiffre ce qu'on fait chez lui.
     */
    val parc: StateFlow<List<GroupeMachines>> = combine(complet, equipements.parGroupe) { ouvert, groupes ->
        val client = ouvert?.devis?.clientId ?: return@combine emptyList()
        groupes.filter { it.groupe.clientId == client }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /**
     * Les paliers dégressifs du catalogue, pour que la boîte de quantité montre
     * ce que les tranches donneront. Pour l'affichage seulement : les lignes, elles,
     * se calculent sur les paliers relus en base au moment d'écrire.
     */
    val paliers: StateFlow<Map<String, List<PalierPrestation>>> = prestations.paliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyMap())

    /**
     * La facture qu'a produite le devis ouvert, s'il en a produit une.
     *
     * C'est ce qui fige le devis à l'écran (voir [DevisRepository.estFige]) : ses
     * lignes, son en-tête, son statut et sa suppression ne se proposent plus.
     */
    val facture: StateFlow<DevisFacture?> = combine(_ouvert, factures.devisFactures) { id, liens ->
        id?.let { liens[it] }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    /**
     * Ajoute une prestation du catalogue au devis ouvert.
     *
     * L'intitulé, l'unité et le prix sont **recopiés** sur la ligne, comme
     * partout ailleurs dans le projet : retirer une prestation du catalogue, ou
     * en changer le tarif, ne doit rien changer à un devis déjà envoyé.
     *
     * Une prestation comptée par unité arrive avec la quantité **pré-remplie** du
     * nombre d'unités de la machine du devis, et pas davantage : la ligne reste
     * modifiable, parce qu'une deuxième unité au même étage ne coûte pas le même
     * temps qu'une deuxième unité trois étages plus haut. Proposer est utile,
     * imposer serait faux. [quantite] est ce que l'écran a fait confirmer ; `null`
     * prend la proposition.
     *
     * Si elle porte des **paliers**, elle devient les lignes de [LignesDegressives]
     * — une par tranche de prix, « 1 u à 100 € » puis « 2e unité à 80 € » —, et non
     * une ligne au prix plein : les paliers réglés dans les Réglages étaient
     * jusqu'ici ignorés sans que rien ne le dise. Un forfait n'a pas de rang, et
     * ses éventuels paliers restent sans effet.
     *
     * Tout est **relu en base** dans la coroutine — le devis, le parc et les
     * paliers — plutôt que pris dans des `StateFlow` : `.value` sur un flux que
     * personne ne collecte reste à sa valeur initiale, et c'est le défaut qui a
     * déjà fait partir des factures sans logo.
     */
    fun onAjouterPrestation(prestation: Prestation, quantite: Int? = null) {
        val id = _ouvert.value ?: return
        viewModelScope.launch {
            val nombre = quantite ?: if (prestation.parUnite) {
                unitesDe(devis.lire(id)?.equipementId, equipements.parGroupe.first())
            } else {
                1
            }
            if (nombre <= 0) return@launch
            val tarif = TarifDegressif(
                base = prestation.prixUnitaire,
                paliers = prestations.paliers.first()[prestation.id].orEmpty(),
            )
            if (prestation.parUnite && tarif.degressif) {
                devis.ajouterLignes(id, LignesDegressives.pour(id, prestation, tarif, nombre))
            } else {
                devis.ajouterLigne(
                    devisId = id,
                    designation = prestation.designation,
                    quantite = nombre.toDouble(),
                    unite = prestation.unite,
                    prixUnitaire = prestation.prixUnitaire,
                )
            }
        }
    }

    /** Le trajet du devis ouvert, s'il en porte un. */
    val trajet: StateFlow<Trajet?> = _ouvert
        .flatMapLatest { id -> if (id == null) flowOf(null) else devis.observerTrajet(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    private val _calculEnCours = MutableStateFlow(false)

    /** Un calcul est en vol : l'écran désactive le bouton et le dit. */
    val calculEnCours: StateFlow<Boolean> = _calculEnCours.asStateFlow()

    private val _echecItineraire = MutableStateFlow<RaisonEchec?>(null)

    /**
     * Pourquoi le dernier calcul n'a rien rendu.
     *
     * Porté par l'état plutôt que jeté : l'écran doit dire *ce qui* a échoué,
     * parce que la suite n'est pas la même — une adresse se réécrit, une absence
     * de réseau se contourne en saisissant les kilomètres à la main.
     */
    val echecItineraire: StateFlow<RaisonEchec?> = _echecItineraire.asStateFlow()

    fun onOuvrir(document: Devis) {
        _ouvert.value = document.id
    }

    fun onFermer() {
        _ouvert.value = null
    }

    /**
     * Crée un devis et l'ouvre aussitôt.
     *
     * Le taux de TVA vient des réglages au moment de la création, puis le
     * devis en garde sa copie : un devis déjà envoyé ne doit pas se recalculer
     * parce qu'un réglage a bougé depuis.
     *
     * Les réglages sont **lus en base**, et non dans [reglages] : ce flux n'est
     * collecté que par l'écran des devis, et un devis créé depuis une
     * intervention — l'onglet Factures jamais ouvert — recevait sinon les défauts,
     * 20 % et assujetti, qui traversaient ensuite jusqu'à la facture. C'est le
     * défaut des factures sans logo, sous une autre forme.
     *
     * @param equipementId la machine que le devis vise, quand on la connaît
     *   déjà — celle de l'intervention d'où il naît. C'est ce qui fait proposer
     *   le nombre d'unités d'un bi-split sans avoir à la choisir.
     */
    fun onNouveau(client: Client?, equipementId: String? = null) {
        viewModelScope.launch {
            val reglesEnBase = parametres.lire()
            val machine = equipementId?.let { id ->
                equipements.equipements.first().firstOrNull { it.id == id }
            }
            val cree = devis.creer(
                client = client,
                equipement = machine,
                tauxTva = reglesEnBase.tauxTva,
                assujettiTva = reglesEnBase.assujettiTva,
            )
            _ouvert.value = cree.id
        }
    }

    fun onObjet(texte: String) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.enregistrer(courant.copy(objet = texte)) }
    }

    /**
     * Change le client du devis. La machine visée est oubliée si elle n'est pas
     * chez lui : un devis ne chiffre pas le parc d'un autre, et garder son nombre
     * d'unités ferait proposer des quantités qui ne le concernent pas.
     */
    fun onClient(client: Client) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch {
            val machine = courant.equipementId?.let { id ->
                equipements.equipements.first().firstOrNull { it.id == id }
            }
            val garder = machine != null && machine.clientId == client.id
            devis.enregistrer(
                courant.copy(
                    clientId = client.id,
                    clientNom = client.nom,
                    equipementId = if (garder) courant.equipementId else null,
                    equipementNom = if (garder) courant.equipementNom else "",
                ),
            )
        }
    }

    /**
     * La machine que le devis vise, ou aucune.
     *
     * Le nom est **recopié**, comme sur l'intervention et pour la même raison : un
     * devis de mars doit continuer de dire sur quoi il portait, même si la machine
     * a été renommée ou retirée depuis. Les lignes déjà posées ne bougent pas — la
     * quantité n'est qu'une proposition faite à l'ajout.
     */
    fun onMachine(machine: Equipement?) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch {
            devis.enregistrer(
                courant.copy(equipementId = machine?.id, equipementNom = machine?.nom.orEmpty()),
            )
        }
    }

    fun onStatut(statut: StatutDevis) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.changerStatut(courant, statut) }
    }

    fun onAjouterLigne(
        designation: String,
        quantite: Double,
        unite: String,
        prixUnitaire: Double,
        prixAchat: Double = 0.0,
    ) {
        val id = _ouvert.value ?: return
        viewModelScope.launch {
            devis.ajouterLigne(id, designation, quantite, unite, prixUnitaire, prixAchat)
        }
    }

    /**
     * Pose une ligne de **matériel** depuis le magasin.
     *
     * C'était le trou : le magasin tenait les références, les deux prix et les
     * fournisseurs, et aucun devis ne pouvait y puiser. Chiffrer la pose d'une
     * clim revenait à retaper la désignation et le prix d'un split qui était
     * déjà en fiche — ou à ne chiffrer que la main-d'œuvre.
     *
     * La référence entre dans l'intitulé quand il y en a une : c'est ce qu'un
     * client compare à un devis concurrent, et ce qu'on dicte au fournisseur en
     * commandant.
     *
     * Le **prix de vente est décidé par l'appelant** et non ici : la fiche en
     * propose un, on peut le ramener au prix d'achat — vendre le matériel au
     * coûtant — ou le recalculer d'un coefficient, et c'est la boîte de saisie
     * qui tranche parce que c'est là qu'on a le client en face. Le
     * **prix d'achat**, lui, est toujours celui de la fiche, et il est recopié
     * sur la ligne : c'est ce qui rend la marge calculable, et calculable pour
     * toujours sur les prix du jour du chiffrage.
     *
     * Le stock **ne bouge pas**. Un devis n'est pas une sortie de magasin : il
     * peut être refusé, et le matériel chiffré n'est souvent même pas acheté.
     * C'est la **pose** qui retranche du camion, et elle seule.
     */
    fun onAjouterArticle(article: Article, quantite: Double, prixVente: Double) {
        onAjouterLigne(
            designation = listOfNotNull(
                article.designation,
                article.reference.takeIf { it.isNotBlank() }?.let { "($it)" },
            ).joinToString(" "),
            quantite = quantite,
            unite = article.unite,
            prixUnitaire = prixVente,
            prixAchat = article.prixAchat,
        )
    }

    /**
     * Enregistre une ligne retouchée dans sa boîte : désignation, quantité, unité,
     * prix, et le geste « offerte ».
     *
     * C'est le seul chemin qui modifie une ligne, et il passe par une boîte qui
     * montre ce qui change. Avant, un **simple toucher offrait la ligne** — un
     * geste commercial accordé d'un contact involontaire, gants aux mains — et
     * l'appui long la supprimait sans rien demander, tandis que l'écran promettait
     * une quantité « modifiable ensuite » que rien ne permettait de modifier. Le
     * prix d'achat et le marqueur du déplacement traversent par `copy` : la boîte
     * ne les montre pas, et les perdre fausserait la marge ou le recalcul du
     * trajet.
     */
    fun onModifierLigne(ligne: LigneDevis) {
        viewModelScope.launch { devis.enregistrerLigne(ligne) }
    }

    /** Offre la TVA, ou reprend le geste. Sans objet en franchise en base. */
    fun onOffrirTva() {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.offrirTva(courant, !courant.tvaOfferte) }
    }

    /**
     * Inscrit une prestation au catalogue depuis le devis.
     *
     * Ajouter au catalogue sans quitter le chiffrage, c'est ce qui fait qu'on
     * l'enrichit vraiment : une pièce qu'il faut aller déclarer dans les Réglages
     * finit saisie en ligne libre, et le catalogue ne grossit jamais. La
     * prestation est posée **et** ajoutée au devis en cours, puisque c'est bien
     * pour lui qu'on la saisit.
     */
    fun onCreerPrestation(prestation: Prestation) {
        viewModelScope.launch {
            // La prestation enregistrée, et non celle reçue : le dépôt a nettoyé
            // l'intitulé et posé le rang, et c'est cette version-là que la ligne
            // du devis doit recopier.
            val creee = prestations.enregistrer(prestation) ?: return@launch
            if (_ouvert.value != null) onAjouterPrestation(creee)
        }
    }

    private val _documentPret = MutableStateFlow<Uri?>(null)

    /**
     * Le PDF qui vient d'être écrit, prêt à partir.
     *
     * Un `StateFlow` que l'écran remet à `null` après avoir ouvert le partage,
     * plutôt qu'un événement jeté : un événement perdu pendant une rotation aurait
     * produit un export sans rien envoyer, et l'utilisateur en aurait conclu que le
     * bouton ne marche pas.
     */
    val documentPret: StateFlow<Uri?> = _documentPret.asStateFlow()

    private val _echecExport = MutableStateFlow(false)

    /** L'export a échoué : dit en clair, plutôt qu'un bouton qui ne fait rien. */
    val echecExport: StateFlow<Boolean> = _echecExport.asStateFlow()

    /**
     * Écrit le devis ouvert en PDF, et annonce le fichier.
     *
     * Le statut n'est **pas** passé à « Envoyé » au passage : exporter n'est pas
     * envoyer, et le faire annoncerait un devis parti chez le client alors qu'il
     * attend dans une feuille de partage. C'est l'utilisateur qui change le statut,
     * quand il sait qu'il l'a envoyé.
     */
    fun onExporterPdf() {
        val ouvert = complet.value ?: return
        viewModelScope.launch {
            // Lus en base au moment d'écrire, comme pour la facture : un document
            // ne se construit pas sur des `StateFlow` qu'un autre écran collecte.
            val document = DocumentDevis.de(
                devis = ouvert,
                parametres = parametres.lire(),
                client = clients.clients.first().firstOrNull { it.id == ouvert.devis.clientId },
            )
            val produit = pdf.produire(document)
            if (produit != null) _documentPret.value = produit else _echecExport.value = true
        }
    }

    /** L'écran a ouvert le partage : le document n'a plus à être annoncé. */
    fun onDocumentPartage() {
        _documentPret.value = null
    }

    fun onEchecVu() {
        _echecExport.value = false
    }

    /** Retire une ligne : appelé depuis sa boîte, après confirmation. */
    fun onSupprimerLigne(ligne: LigneDevis) {
        viewModelScope.launch { devis.supprimerLigne(ligne) }
    }

    /**
     * Supprime un devis, ouvert ou non.
     *
     * L'identifiant est passé plutôt que pris sur le devis ouvert : la liste
     * supprime aussi, et c'est même de là que le geste sert le plus — une fausse
     * manœuvre crée un devis vide qu'on voit dans la liste, et l'obliger à
     * l'ouvrir pour l'effacer aurait ajouté deux gestes au rattrapage d'un seul.
     *
     * La feuille se referme quand c'est elle qu'on efface, et seulement alors :
     * remettre `_ouvert` à `null` sans regarder aurait fermé le devis en cours
     * de saisie parce qu'on en a supprimé un autre. Et seulement si le dépôt a
     * bien supprimé : un devis facturé ne s'efface pas, et le refermer aurait
     * fait croire le contraire.
     */
    fun onSupprimer(id: String) {
        viewModelScope.launch {
            if (devis.supprimer(id) && _ouvert.value == id) _ouvert.value = null
        }
    }

    // — Le déplacement facturé —

    /**
     * Le trajet tel qu'il doit être proposé à l'ouverture d'un devis qui n'en a
     * pas encore : au départ du dépôt, vers l'adresse du client.
     *
     * Rien n'est enregistré ici — c'est une proposition de saisie. Créer une
     * ligne de trajet à zéro kilomètre dès l'ouverture d'un devis aurait mis un
     * déplacement sur des devis qui n'en comportent pas : un devis rédigé au
     * bureau, par exemple.
     */
    fun trajetPropose(): Trajet {
        val document = complet.value?.devis
        val client = carnet.value.firstOrNull { it.id == document?.clientId }
        return Trajet(
            devisId = document?.id.orEmpty(),
            depart = reglages.value.adresseDepart,
            arrivee = client?.adresseComplete.orEmpty(),
        )
    }

    /** Efface le message d'échec : l'écran le fait quand on retouche une adresse. */
    fun onOublierEchec() {
        _echecItineraire.value = null
    }

    /**
     * Enregistre un trajet saisi à la main, et refait ses lignes de devis.
     *
     * L'origine passe à [OrigineTrajet.SAISI] et `calculeLe` est effacé : dès
     * qu'on retouche un chiffre, ce n'est plus le résultat du service, et laisser
     * « calculé le 12 mars » sur des valeurs corrigées aurait fait passer une
     * saisie pour un relevé.
     */
    fun onEnregistrerTrajet(saisi: Trajet) {
        viewModelScope.launch {
            devis.enregistrerDeplacement(
                saisi.copy(origine = OrigineTrajet.SAISI, calculeLe = null),
                parametres.lire().tarifDeplacement,
            )
        }
    }

    /**
     * Demande l'itinéraire au service, puis enregistre ce qu'il rend.
     *
     * Le trajet reçu ne remplace pas les choix de facturation déjà faits :
     * l'aller-retour et le geste commercial sont repris de [base]. Les écraser
     * aurait décoché l'aller-retour à chaque recalcul, et c'est exactement le
     * genre de perte qu'on ne remarque qu'en relisant le total.
     */
    fun onCalculerTrajet(base: Trajet) {
        if (_calculEnCours.value) return
        _calculEnCours.value = true
        _echecItineraire.value = null
        viewModelScope.launch {
            try {
                when (val resultat = itineraires.itineraire(base.depart, base.arrivee)) {
                    is ResultatItineraire.Trouve -> devis.enregistrerDeplacement(
                        base.copy(
                            distanceKm = resultat.distanceKm,
                            dureeMinutes = resultat.dureeMinutes,
                            peages = resultat.peages,
                            peagesConnus = resultat.peagesConnus,
                            origine = OrigineTrajet.CALCULE,
                            calculeLe = Instant.now(),
                        ),
                        parametres.lire().tarifDeplacement,
                    )

                    is ResultatItineraire.Echec -> _echecItineraire.value = resultat.raison
                }
            } finally {
                // Dans un `finally` : une annulation du scope — l'écran qu'on
                // quitte — laisserait sinon le bouton grisé pour de bon.
                _calculEnCours.value = false
            }
        }
    }

    /** Offre le déplacement, ou reprend le geste. */
    fun onOffrirDeplacement(offert: Boolean) {
        val courant = trajet.value ?: return
        viewModelScope.launch {
            devis.offrirDeplacement(courant, parametres.lire().tarifDeplacement, offert)
        }
    }

    /** Retire le déplacement du devis, et les lignes qu'il avait posées. */
    // — Un devis fournisseur repris sur le devis ————————————————————————

    private val _import = MutableStateFlow<ImportFournisseur?>(null)

    /**
     * Le devis fournisseur lu, en attente d'être ajouté ; `null` quand la feuille
     * est fermée. Rien n'en est écrit avant [onValiderImport].
     */
    val importFournisseur: StateFlow<ImportFournisseur?> = _import.asStateFlow()

    /** Un PDF ou une image choisis : la même lecture que la saisie des prix. */
    fun onLireFournisseur(adresse: String) {
        _import.value = ImportFournisseur(enCours = true)
        viewModelScope.launch { lireFournisseur(adresse) }
    }

    fun preparerScan(): Capture = scans.preparer()

    /**
     * La page photographiée se lit puis **s'efface, lue ou non** — voir
     * [ScansDevis]. Une prise de vue abandonnée n'ouvre rien.
     */
    fun onScanFournisseur(nom: String, pris: Boolean) {
        if (pris) _import.value = ImportFournisseur(enCours = true)
        viewModelScope.launch {
            try {
                if (pris) lireFournisseur(scans.adresse(nom))
            } finally {
                scans.effacer(nom)
            }
        }
    }

    /**
     * Rapproche chaque ligne du magasin **par la seule référence**, et reprend la
     * quantité du fournisseur. Le coefficient des Réglages est proposé, mais la
     * question « marger ou non » reste ouverte : voir [ChoixMarge]. Les réglages
     * sont relus en base, et non dans [reglages] : `.value` sur un flux que
     * personne ne collecte reste à sa valeur initiale.
     */
    private suspend fun lireFournisseur(adresse: String) {
        val rangees = lecteur.lire(adresse)
        if (rangees == null) {
            _import.value = ImportFournisseur(echec = LectureTarif.ECHEC_OUVERTURE)
            return
        }
        val tarif = LectureTarif.lire(rangees, materiel.fournisseurs.first())
        if (tarif.lignes.isEmpty()) {
            _import.value = ImportFournisseur(echec = LectureTarif.ECHEC_AUCUNE_LIGNE)
            return
        }
        val magasin = materiel.articles()
        val coefficient = parametres.parametres.first().coefficientMateriel.takeIf { it > 0.0 }
        _import.value = ImportFournisseur(
            fournisseur = tarif.fournisseur,
            date = tarif.date,
            coefficient = coefficient,
            lignes = tarif.lignes.mapIndexed { i, ligne ->
                LigneImport(
                    rang = i,
                    lue = ligne,
                    article = LectureTarif.parReference(ligne.reference, magasin),
                    quantite = ligne.quantite,
                )
            },
        )
    }

    fun onMargeImport(choix: ChoixMarge) = modifierImport { it.copy(marge = choix) }

    fun onCoefficientImport(coefficient: Double?) = modifierImport { it.copy(coefficient = coefficient) }

    fun onMettreAJourPrixImport(oui: Boolean) = modifierImport { it.copy(mettreAJourPrix = oui) }

    fun onRetenirLigneImport(rang: Int, retenue: Boolean) =
        modifierImport { it.avecLigne(rang) { l -> l.copy(retenue = retenue) } }

    fun onQuantiteImport(rang: Int, quantite: Double?) =
        modifierImport { it.avecLigne(rang) { l -> l.copy(quantite = quantite) } }

    /** Un coefficient propre à une ligne ; `null` — champ vidé — la rend au choix général. */
    fun onCoefficientLigneImport(rang: Int, coefficient: Double?) =
        modifierImport { it.avecLigne(rang) { l -> l.copy(coefficient = coefficient) } }

    fun onFermerImport() {
        _import.value = null
    }

    /**
     * Écrit l'import : les lignes cochées sur le devis, en une écriture, et les
     * prix d'achat lus sur les articles de même référence, en une autre.
     *
     * Les prix du magasin passent **après** les lignes et seulement si elles ont
     * été posées : sur un devis facturé entre-temps, rien n'est écrit du tout,
     * plutôt qu'un magasin mis à jour pour un ajout refusé.
     */
    fun onValiderImport() {
        val etat = _import.value ?: return
        if (etat.manque != null) return
        val id = _ouvert.value ?: return
        _import.value = null
        viewModelScope.launch {
            val posees = devis.ajouterLignes(id, etat.versLignes())
            if (posees.isEmpty()) return@launch
            val coefficient = parametres.parametres.first().coefficientMateriel
            materiel.enregistrerPrix(etat.versPrix(coefficient))
        }
    }

    private fun modifierImport(change: (ImportFournisseur) -> ImportFournisseur) {
        _import.update { courant -> courant?.let(change) }
    }

    fun onRetirerDeplacement() {
        val courant = trajet.value ?: return
        viewModelScope.launch { devis.supprimerDeplacement(courant.devisId) }
    }

    companion object {

        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        /**
         * Le nombre d'unités intérieures de [machine] dans [parc] : 1 sans machine,
         * pour un monosplit, et pour une unité visée seule. Une seule règle pour le
         * flux que l'écran affiche et pour la lecture faite au moment d'écrire.
         */
        private fun unitesDe(machine: String?, parc: List<GroupeMachines>): Int {
            if (machine == null) return 1
            return parc.firstOrNull { it.groupe.id == machine }?.nombreUnites ?: 1
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                DevisViewModel(
                    conteneur.devis,
                    conteneur.clients,
                    conteneur.parametres,
                    conteneur.prestations,
                    conteneur.equipements,
                    conteneur.factures,
                    conteneur.materiel,
                    ProducteurPdfAndroid(conteneur.documents, conteneur.photos),
                    conteneur.itineraires,
                    conteneur.lecteurDevis,
                    conteneur.scansDevis,
                )
            }
        }
    }
}
