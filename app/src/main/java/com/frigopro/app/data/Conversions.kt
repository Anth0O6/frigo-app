package com.frigopro.app.data

/**
 * Les familles de grandeurs que le convertisseur sait traiter.
 *
 * Une grandeur ne se convertit qu'à l'intérieur de sa famille : proposer de
 * passer des bars aux kilowatts n'aurait aucun sens, et laisser l'écran filtrer
 * lui-même aurait demandé à chaque écran de connaître la physique.
 */
enum class FamilleUnite(val libelle: String) {
    PRESSION("Pression"),

    /**
     * Une **température**, pas un écart. La distinction n'est pas pédante : 10 °C
     * valent 50 °F, mais un écart de 10 K vaut 18 °F — la conversion d'une
     * température porte un décalage d'origine, celle d'un écart non. Les
     * confondre est l'erreur classique du convertisseur généraliste, et elle se
     * paie sur une surchauffe.
     */
    TEMPERATURE("Température"),

    ECART("Écart de température"),
    PUISSANCE("Puissance"),
    DEBIT("Débit"),
    MASSE("Masse"),
    LONGUEUR("Longueur"),
    VITESSE("Vitesse"),
}

/**
 * Une unité, et comment elle se ramène à l'unité de référence de sa famille.
 *
 * Toute unité est décrite comme une **fonction affine** de la référence :
 * `reference = valeur × facteur + decalage`. Le décalage ne sert qu'aux
 * températures, mais le porter pour toutes permet un seul chemin de calcul ;
 * traiter la température à part aurait fait deux codes de conversion, dont un
 * seul serait éprouvé.
 *
 * Les facteurs sont les valeurs exactes des définitions — un pouce **est**
 * 25,4 mm, une livre **est** 0,453 592 37 kg — et non des arrondis : un arrondi
 * recopié de proche en proche finit par dériver.
 */
enum class Unite(
    val symbole: String,
    val famille: FamilleUnite,
    val facteur: Double,
    val decalage: Double = 0.0,
) {
    // — Pression, référence le bar : c'est ce que lit un manomètre de chantier —
    BAR("bar", FamilleUnite.PRESSION, 1.0),
    MILLIBAR("mbar", FamilleUnite.PRESSION, 0.001),
    KILOPASCAL("kPa", FamilleUnite.PRESSION, 0.01),
    MEGAPASCAL("MPa", FamilleUnite.PRESSION, 10.0),

    /** 1 psi = 0,068 947 572 9 bar, d'où les 14,504 psi du bar. */
    PSI("psi", FamilleUnite.PRESSION, 0.0689475729),

    KGF_CM2("kgf/cm²", FamilleUnite.PRESSION, 0.980665),

    // — Température, référence le degré Celsius —
    CELSIUS("°C", FamilleUnite.TEMPERATURE, 1.0),

    /** `(F − 32) / 1,8` mis sous forme affine. */
    FAHRENHEIT("°F", FamilleUnite.TEMPERATURE, 1.0 / 1.8, -32.0 / 1.8),

    KELVIN("K", FamilleUnite.TEMPERATURE, 1.0, -273.15),

    // — Écart de température : même échelle, aucun décalage —
    ECART_KELVIN("K", FamilleUnite.ECART, 1.0),
    ECART_CELSIUS("°C", FamilleUnite.ECART, 1.0),
    ECART_FAHRENHEIT("°F", FamilleUnite.ECART, 1.0 / 1.8),

    // — Puissance, référence le kilowatt —
    KILOWATT("kW", FamilleUnite.PUISSANCE, 1.0),
    WATT("W", FamilleUnite.PUISSANCE, 0.001),

    /** 1 BTU/h = 0,293 071 W, d'où les 3 412 BTU/h du kilowatt. */
    BTU_PAR_HEURE("BTU/h", FamilleUnite.PUISSANCE, 0.000293071),

    /**
     * La **frigorie par heure**, qui est la kilocalorie par heure comptée en
     * froid produit. Elle n'a plus cours légalement mais se dit encore sur les
     * chantiers, et une plaque d'avant 1980 la porte : savoir la lire est
     * exactement ce qu'on demande à un convertisseur de métier.
     */
    KCAL_PAR_HEURE("kcal/h", FamilleUnite.PUISSANCE, 0.001163),

    /** La tonne de froid américaine : la puissance qui fige une tonne courte de glace en 24 h. */
    TONNE_DE_FROID("TR", FamilleUnite.PUISSANCE, 3.516853),

    CHEVAL("ch", FamilleUnite.PUISSANCE, 0.7354988),

    // — Débit volumique, référence le mètre cube par heure —
    M3_PAR_HEURE("m³/h", FamilleUnite.DEBIT, 1.0),
    LITRE_PAR_SECONDE("L/s", FamilleUnite.DEBIT, 3.6),
    LITRE_PAR_MINUTE("L/min", FamilleUnite.DEBIT, 0.06),
    PIED3_PAR_MINUTE("cfm", FamilleUnite.DEBIT, 1.69901082),
    GALLON_PAR_MINUTE("gpm", FamilleUnite.DEBIT, 0.227124707),

    // — Masse, référence le kilogramme : l'unité du registre des fluides —
    KILOGRAMME("kg", FamilleUnite.MASSE, 1.0),
    GRAMME("g", FamilleUnite.MASSE, 0.001),
    LIVRE("lb", FamilleUnite.MASSE, 0.45359237),
    ONCE("oz", FamilleUnite.MASSE, 0.0283495231),

    // — Longueur, référence le mètre —
    METRE("m", FamilleUnite.LONGUEUR, 1.0),
    CENTIMETRE("cm", FamilleUnite.LONGUEUR, 0.01),
    MILLIMETRE("mm", FamilleUnite.LONGUEUR, 0.001),
    POUCE("in", FamilleUnite.LONGUEUR, 0.0254),
    PIED("ft", FamilleUnite.LONGUEUR, 0.3048),

    // — Vitesse, référence le mètre par seconde : la vitesse d'air en gaine —
    METRE_PAR_SECONDE("m/s", FamilleUnite.VITESSE, 1.0),
    KM_PAR_HEURE("km/h", FamilleUnite.VITESSE, 1.0 / 3.6),
    PIED_PAR_MINUTE("ft/min", FamilleUnite.VITESSE, 0.00508),
    PIED_PAR_SECONDE("ft/s", FamilleUnite.VITESSE, 0.3048),
    ;

    /** La valeur exprimée dans l'unité de référence de la famille. */
    fun versReference(valeur: Double): Double = valeur * facteur + decalage

    /** L'inverse : une valeur de référence ramenée dans cette unité. */
    fun depuisReference(reference: Double): Double = (reference - decalage) / facteur
}

/**
 * Le convertisseur d'unités.
 *
 * Il passe par l'unité de référence de la famille plutôt que de porter une table
 * de toutes les paires : avec huit unités de pression, une table directe
 * compterait cinquante-six entrées à tenir d'accord entre elles, là où huit
 * facteurs suffisent. La contrepartie — deux multiplications au lieu d'une — ne
 * se mesure pas.
 */
object Conversions {

    /** Les unités d'une famille, dans l'ordre où elles sont déclarées. */
    fun unites(famille: FamilleUnite): List<Unite> = Unite.entries.filter { it.famille == famille }

    /**
     * Convertit, ou rend `null` si les deux unités ne sont pas de la même
     * famille.
     *
     * Un refus plutôt qu'un nombre : convertir des bars en kilowatts n'a pas de
     * résultat « approximatif », cela n'a pas de résultat du tout, et rendre zéro
     * aurait donné un chiffre à lire.
     */
    fun convertir(valeur: Double, de: Unite, vers: Unite): Double? {
        if (de.famille != vers.famille) return null
        return vers.depuisReference(de.versReference(valeur))
    }

    /**
     * L'unité de référence d'une famille : la première déclarée.
     *
     * C'est aussi celle que le projet emploie partout ailleurs — le bar pour les
     * pressions, le kilogramme pour les charges — ce qui fait du convertisseur un
     * outil cohérent avec le reste de l'application plutôt qu'une annexe.
     */
    fun reference(famille: FamilleUnite): Unite = unites(famille).first()
}
