package com.frigopro.app.data

/**
 * Ce qu'un changement de fluide demande comme travail.
 *
 * La distinction est la première question du dépannage, et elle décide de
 * l'ampleur du chantier : une conversion qui impose de vidanger l'huile n'est
 * pas une recharge, c'est une intervention d'une demi-journée avec un
 * compresseur à rincer.
 */
enum class AmpleurSubstitution(val libelle: String, val detail: String) {

    /**
     * Le fluide se substitue **sur l'huile en place**.
     *
     * C'est ce que le commerce appelle un « drop-in », et le terme est trompeur :
     * il ne veut jamais dire « on transvase et c'est fini ». Le détendeur se
     * règle, la charge se refait, et le fluide se pèse — mais le compresseur
     * reste en place avec son huile.
     */
    SANS_VIDANGE(
        "Sans vidange d'huile",
        "L'huile minérale ou alkylbenzène en place convient. Le détendeur se " +
            "règle et la charge se refait, mais le compresseur ne s'ouvre pas.",
    ),

    /**
     * Le fluide impose une **huile polyolester**.
     *
     * Les HFC ne transportent pas l'huile minérale : elle se dépose dans
     * l'évaporateur et ne revient plus au compresseur, qui finit par tourner à
     * sec. Trois vidanges successives sont la règle du métier pour descendre
     * sous 5 % de minérale résiduelle.
     */
    VIDANGE_POE(
        "Vidange vers POE",
        "Le fluide exige une huile polyolester. L'huile minérale en place ne " +
            "remonte pas au compresseur et le fait tourner à sec : il faut " +
            "vidanger, en général trois fois.",
    ),
}

/**
 * Une piste de remplacement, et ce qu'elle change.
 *
 * **Ce n'est jamais un verdict**, exactement comme [Depannage] : l'application
 * dit ce qu'elle sait de la famille du fluide, pas ce que le constructeur du
 * compresseur autorise sur *cette* machine. Un tableau de substitution ne
 * connaît ni le modèle du compresseur, ni son année, ni sa garantie — et ces
 * trois-là décident. Le dernier mot est donc toujours renvoyé au constructeur,
 * et [AvertissementSubstitution] le porte.
 *
 * @param remarques ce que cette substitution précise a de particulier, au-delà
 *   de ce que le GWP, la classe et le glissement disent déjà — ceux-là se
 *   déduisent du catalogue et n'ont pas à être recopiés ici.
 */
data class Substitution(
    val origine: String,
    val remplacant: String,
    val ampleur: AmpleurSubstitution,
    val remarques: List<String> = emptyList(),
)

/**
 * Ce qui change entre deux fluides, déduit du catalogue.
 *
 * Rien n'est recopié : le GWP vient de [Fluides], la classe de sécurité aussi,
 * et le glissement de [CourbesSaturation]. Une table qui aurait redit ces
 * chiffres aurait divergé du catalogue à la première correction.
 */
data class EcartFluides(val origine: String, val remplacant: String) {

    val gwpOrigine: Int? get() = Fluides.gwp(origine)

    val gwpRemplacant: Int? get() = Fluides.gwp(remplacant)

    /**
     * De combien le GWP baisse, en pourcentage, ou `null` si l'un des deux est
     * inconnu. Négatif si le remplaçant est pire — ce qui existe, et doit se
     * voir plutôt que d'être tu.
     */
    val baisseGwp: Double?
        get() {
            val avant = gwpOrigine?.takeIf { it > 0 } ?: return null
            val apres = gwpRemplacant ?: return null
            return ((avant - apres).toDouble() / avant * 100.0).arrondiDixieme()
        }

    val classeOrigine: ClasseSecurite? get() = Fluides.classeSecurite(origine)

    val classeRemplacant: ClasseSecurite? get() = Fluides.classeSecurite(remplacant)

    /**
     * Le remplaçant est inflammable là où l'original ne l'était pas.
     *
     * C'est **le changement qui coûte le plus cher à découvrir tard** : il
     * commande les outils, le brasage, la charge admise dans un local et la
     * qualification du technicien. Un écran doit le dire avant le GWP, qui ne
     * décide que d'une paperasse.
     */
    val devientInflammable: Boolean
        get() = classeOrigine?.inflammable == false && classeRemplacant?.inflammable == true

    /** Le glissement du remplaçant à 0 °C, quand sa courbe est connue. */
    val glissementRemplacantK: Double?
        get() = CourbesSaturation.temperatureA(remplacant, PRESSION_REPERE_BAR_ABS)
            ?.glissementK
            ?.arrondiDixieme()

    /**
     * Le remplaçant a un glissement notable là où l'original n'en avait pas.
     *
     * Deux conséquences concrètes : la charge se fait **en phase liquide** et la
     * recharge partielle après fuite fausse la composition — sur un zéotrope,
     * compléter une charge n'est pas rattraper une fuite.
     */
    val apporteDuGlissement: Boolean
        get() {
            val avant = CourbesSaturation.temperatureA(origine, PRESSION_REPERE_BAR_ABS)
            val apres = CourbesSaturation.temperatureA(remplacant, PRESSION_REPERE_BAR_ABS)
            if (avant == null || apres == null) return false
            return !avant.glissementNotable && apres.glissementNotable
        }

    private companion object {

        /**
         * La pression à laquelle on compare deux glissements.
         *
         * Cinq bar absolus, soit environ 4 bar au manomètre : c'est une basse
         * pression de réfrigération ordinaire, et surtout elle tombe dans la
         * plage calculée de tous les fluides du catalogue — comparer au point
         * critique de l'un et hors plage de l'autre n'aurait rien dit.
         */
        const val PRESSION_REPERE_BAR_ABS = 5.0
    }
}

/**
 * Les pistes de remplacement, fluide par fluide.
 *
 * ## Ce que cette table est, et ce qu'elle n'est pas
 *
 * Elle dit **quelle famille remplace quelle famille**, et ce que le changement
 * emporte comme travail. Elle ne dit pas si *cette* machine l'accepte : un
 * tableau ne connaît ni le compresseur, ni son année, ni sa garantie, et ce sont
 * ces trois-là qui décident. C'est la même retenue que l'aide au dépannage —
 * des pistes, jamais un verdict.
 *
 * ## D'où viennent les entrées
 *
 * Des **usages établis du métier** : les fluides de reconversion du R-22 après
 * son interdiction, ceux qui remplacent le R-404A depuis que le règlement
 * 517/2014 en restreint l'usage, et les A2L à bas GWP qui prennent la suite du
 * R-410A et du R-134a. Ce ne sont pas des recommandations de l'application mais
 * le constat de ce qui se pose sur le terrain.
 *
 * Rien n'est extrapolé : un fluide absent de cette table n'a **pas** de
 * remplaçant proposé, et l'écran le dit. Rapprocher deux fluides « parce qu'ils
 * se ressemblent » est exactement ce que le projet refuse pour le GWP, et ce
 * serait ici plus grave — c'est un compresseur qui en dépend.
 */
object Substitutions {

    /**
     * Ce qui doit être dit à chaque fois, quelle que soit la substitution.
     *
     * Nommé plutôt que recopié à l'écran, pour la même raison que
     * [Parametres.MENTION_FRANCHISE] : une formule qui diverge d'un endroit à
     * l'autre finit par dire deux choses différentes.
     */
    const val AVERTISSEMENT =
        "Ces pistes valent pour la famille du fluide, pas pour votre machine. " +
            "Le compresseur, son année et sa garantie décident : vérifiez la " +
            "compatibilité auprès du constructeur avant de convertir."

    private val TABLE: List<Substitution> = listOf(
        // — Le R-22, interdit depuis 2015 en charge neuve comme en recharge ——
        Substitution(
            "R22", "R407C", AmpleurSubstitution.VIDANGE_POE,
            listOf(
                "Le remplacement historique du R-22 en climatisation : capacité " +
                    "et pressions voisines, au prix d'une vidange vers POE.",
                "Son glissement impose une charge en phase liquide, et interdit " +
                    "de compléter une charge après fuite.",
            ),
        ),
        Substitution(
            "R22", "R427A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Conçu pour tolérer l'huile minérale résiduelle : c'est ce qui " +
                    "en fait une conversion sans ouvrir le compresseur.",
                "Capacité un peu inférieure au R-22 : sur une installation déjà " +
                    "juste, le froid peut manquer aux fortes charges.",
            ),
        ),
        Substitution(
            "R22", "R422D", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Conversion rapide, mais GWP élevé : c'est une solution de " +
                    "prolongation, pas de mise en conformité durable.",
            ),
        ),
        Substitution(
            "R22", "R438A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Formulé pour le retour d'huile minérale ; la surchauffe se " +
                    "recontrôle après conversion.",
            ),
        ),

        // — Le R-404A, que le règlement 517/2014 restreint fortement ————————
        Substitution(
            "R404A", "R449A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Le remplacement le plus courant en froid commercial : " +
                    "comportement proche, huile POE déjà en place sur un R-404A.",
                "Température de refoulement plus élevée : à surveiller sur les " +
                    "applications basse température.",
            ),
        ),
        Substitution(
            "R404A", "R448A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Très proche du R-449A ; le choix se fait souvent sur ce que le " +
                    "fournisseur a en stock.",
            ),
        ),
        Substitution(
            "R404A", "R452A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Glissement plus faible que les R-448A / R-449A et refoulement " +
                    "plus proche du R-404A : souvent préféré en transport et sur " +
                    "les installations sensibles au refoulement.",
                "En contrepartie, un GWP qui reste élevé.",
            ),
        ),
        Substitution(
            "R404A", "R454C", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Le vrai saut de GWP, mais c'est un A2L : outillage, brasage, " +
                    "charge admise dans le local et qualification changent.",
                "Glissement important — la charge se fait obligatoirement en " +
                    "phase liquide.",
            ),
        ),
        Substitution(
            "R404A", "R455A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Même famille de remplacement que le R-454C, avec un glissement " +
                    "encore plus marqué.",
            ),
        ),

        // — Le R-507A, jumeau du R-404A ————————————————————————————————————
        Substitution(
            "R507A", "R449A", AmpleurSubstitution.SANS_VIDANGE,
            listOf("Mêmes pistes que pour le R-404A, dont il partage l'usage."),
        ),
        Substitution(
            "R507A", "R452A", AmpleurSubstitution.SANS_VIDANGE,
            listOf("Choix de préférence quand le refoulement est le point sensible."),
        ),

        // — Le R-410A ——————————————————————————————————————————————————————
        Substitution(
            "R410A", "R32", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Corps pur : pas de glissement, et une recharge partielle reste " +
                    "possible — ce que le R-410A ne permettait déjà pas vraiment.",
                "Température de refoulement nettement plus élevée : c'est le " +
                    "point que le constructeur doit valider.",
                "A2L : la charge admise dans un local dépend de son volume.",
            ),
        ),
        Substitution(
            "R410A", "R454B", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Refoulement plus proche du R-410A que le R-32, pour un GWP du " +
                    "même ordre : c'est ce qui en fait le remplaçant retenu par " +
                    "plusieurs constructeurs.",
            ),
        ),
        Substitution(
            "R410A", "R452B", AmpleurSubstitution.SANS_VIDANGE,
            listOf("Très proche du R-454B, avec un glissement un peu plus marqué."),
        ),

        // — Le R-134a ——————————————————————————————————————————————————————
        Substitution(
            "R134A", "R513A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Azéotrope, donc **sans glissement** : c'est ce qui le distingue " +
                    "des autres remplaçants et ce qui rend la conversion simple.",
                "Reste un A1 : rien ne change à la façon de travailler.",
            ),
        ),
        Substitution(
            "R134A", "R450A", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "Capacité un peu inférieure au R-134a ; à vérifier sur une " +
                    "installation déjà juste.",
            ),
        ),
        Substitution(
            "R134A", "R1234YF", AmpleurSubstitution.SANS_VIDANGE,
            listOf(
                "GWP quasi nul, mais A2L : outillage et qualification changent.",
                "Capacité inférieure au R-134a — le dimensionnement se " +
                    "recontrôle, ce n'est pas une simple recharge.",
            ),
        ),

        // — Le R-407C ——————————————————————————————————————————————————————
        Substitution(
            "R407C", "R407F", AmpleurSubstitution.SANS_VIDANGE,
            listOf("Même famille ; meilleure capacité en froid commercial."),
        ),
        Substitution(
            "R407C", "R449A", AmpleurSubstitution.SANS_VIDANGE,
            listOf("Baisse de GWP sensible, sur une installation déjà en POE."),
        ),
    )

    /** Tous les fluides pour lesquels une piste existe. */
    val fluidesConvertibles: List<String> = TABLE.map { it.origine }.distinct().sorted()

    /** Une piste existe pour ce fluide. */
    fun couvert(fluide: String): Boolean = Fluides.normaliser(fluide) in fluidesConvertibles

    /**
     * Les pistes pour ce fluide, **le plus bas GWP d'abord**.
     *
     * L'ordre est un choix : c'est la direction que la réglementation impose, et
     * celle que le technicien devra prendre tôt ou tard. Les conversions de
     * prolongation restent proposées — elles dépannent une installation qu'on ne
     * remplacera pas cette année — mais plus bas dans la liste.
     *
     * Vide pour un fluide inconnu de la table : rien n'est rapproché « parce que
     * ça se ressemble ».
     */
    fun pour(fluide: String): List<Substitution> {
        val nom = Fluides.normaliser(fluide)
        return TABLE.filter { it.origine == nom }
            .sortedBy { Fluides.gwp(it.remplacant) ?: Int.MAX_VALUE }
    }

    /** L'écart entre le fluide en place et une piste, déduit du catalogue. */
    fun ecart(substitution: Substitution): EcartFluides =
        EcartFluides(substitution.origine, substitution.remplacant)
}
