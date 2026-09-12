package com.frigopro.app.data

/**
 * Un fluide caloporteur, et les deux grandeurs dont le bilan a besoin.
 *
 * **Les valeurs sont données à une condition de référence**, dite ici et reprise
 * à l'écran : la masse volumique d'une eau glycolée varie de quelques pourcents
 * entre 0 et 40 °C, et celle de l'air de près de moitié entre une chambre froide
 * et une toiture en août. Le bilan obtenu est donc un **ordre de grandeur juste**,
 * de quoi dire si un échangeur rend ce qu'il doit rendre, pas un relevé de
 * réception.
 *
 * Le dire est la seule façon honnête de livrer ce calcul : un chiffre à trois
 * décimales laisserait croire à une précision que la physique ne donne pas ici.
 */
enum class Caloporteur(
    val libelle: String,
    /** Masse volumique, en kg/m³. */
    val masseVolumique: Double,
    /** Chaleur massique, en kJ/(kg·K). */
    val chaleurMassique: Double,
    val reference: String,
) {
    EAU("Eau", 998.2, 4.182, "à 20 °C"),

    /** Monoéthylène glycol à 30 %, le mélange courant d'un circuit d'eau glacée. */
    EAU_GLYCOLEE_MEG_30("Eau glycolée MEG 30 %", 1045.0, 3.65, "à 0 °C"),

    /** Monopropylène glycol à 30 %, celui des circuits alimentaires. */
    EAU_GLYCOLEE_MPG_30("Eau glycolée MPG 30 %", 1032.0, 3.80, "à 0 °C"),

    AIR("Air", 1.204, 1.006, "à 20 °C, 1 013 hPa"),
}

/**
 * La puissance échangée par un circuit secondaire : `P = débit × ρ × cp × Δt`.
 *
 * C'est le contrôle qui dit si un évaporateur à eau glacée ou une batterie à
 * détente directe rend ce qu'elle doit rendre : on mesure un débit et un écart de
 * température, et l'on compare à la plaque. Le faire de tête sur un capot de
 * camionnette est exactement le genre de calcul qu'on rate, et les trois facteurs
 * à retenir sont ceux qu'on oublie.
 *
 * Isolé du dessin, sans dépendance Android, et éprouvé : même motif que
 * [ReductionPhoto] et [CourbesSaturation].
 */
object PuissanceEchangee {

    /** Les secondes d'une heure : le débit se lit en m³/h, la puissance en kW. */
    private const val SECONDES_PAR_HEURE = 3600.0

    /**
     * La puissance, en kilowatts.
     *
     * `null` pour un débit ou un écart absent : une case vide est un état normal
     * d'un formulaire en cours de frappe, pas une erreur à signaler.
     *
     * Un écart **négatif** n'est pas refusé, et c'est voulu : selon le sens où l'on
     * place les sondes, l'écart se lit dans un sens ou dans l'autre, et c'est la
     * valeur absolue qui porte la puissance. Refuser aurait obligé à se souvenir
     * quelle sonde nommer « entrée ».
     */
    fun kilowatts(
        debitM3ParHeure: Double?,
        ecartK: Double?,
        caloporteur: Caloporteur,
    ): Double? {
        val debit = debitM3ParHeure ?: return null
        val ecart = ecartK ?: return null
        if (debit < 0) return null
        val puissance = debit * caloporteur.masseVolumique * caloporteur.chaleurMassique *
            kotlin.math.abs(ecart) / SECONDES_PAR_HEURE
        return puissance.arrondiCentieme()
    }

    /**
     * L'écart de température qu'il faudrait pour une puissance visée, à débit
     * donné — l'autre sens de la même formule.
     *
     * C'est le sens utile au réglage : la plaque annonce une puissance, on connaît
     * le débit, et l'on veut savoir quel Δt attendre avant de conclure que
     * l'échangeur est encrassé. `null` si le débit est nul, faute de quoi il
     * faudrait un écart infini.
     */
    fun ecartAttenduK(
        puissanceKw: Double?,
        debitM3ParHeure: Double?,
        caloporteur: Caloporteur,
    ): Double? {
        val puissance = puissanceKw ?: return null
        val debit = debitM3ParHeure ?: return null
        if (debit <= 0) return null
        val ecart = puissance * SECONDES_PAR_HEURE /
            (debit * caloporteur.masseVolumique * caloporteur.chaleurMassique)
        return ecart.arrondiDixieme()
    }

    /**
     * Le débit qu'il faudrait pour une puissance visée, à écart donné.
     *
     * Le troisième sens, celui du dimensionnement d'une pompe. `null` pour un
     * écart nul : transporter une puissance sans écart de température demanderait
     * un débit infini, et c'est une question mal posée plutôt qu'un cas limite.
     */
    fun debitAttenduM3ParHeure(
        puissanceKw: Double?,
        ecartK: Double?,
        caloporteur: Caloporteur,
    ): Double? {
        val puissance = puissanceKw ?: return null
        val ecart = ecartK ?: return null
        if (ecart == 0.0) return null
        val debit = puissance * SECONDES_PAR_HEURE /
            (caloporteur.masseVolumique * caloporteur.chaleurMassique * kotlin.math.abs(ecart))
        return debit.arrondiCentieme()
    }
}
