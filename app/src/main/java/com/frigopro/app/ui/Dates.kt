package com.frigopro.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Format d'heure partagé par la liste et le formulaire. */
internal val FORMAT_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val FORMAT_JOUR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** « Lundi 8 septembre », majuscule initiale comprise. */
internal fun libelleDate(date: LocalDate): String =
    date.format(FORMAT_JOUR).replaceFirstChar { it.uppercase(Locale.FRENCH) }

private val FORMAT_JOUR_ANNEE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/**
 * « 8 sept. 2026 » : l'année est indispensable dès qu'on regarde en arrière,
 * ce que fait l'historique d'une machine — à la différence de la tournée du
 * jour, où elle n'apprendrait rien.
 */
internal fun libelleDateAvecAnnee(date: LocalDate): String = date.format(FORMAT_JOUR_ANNEE)

/**
 * Libellé d'une journée dans la barre de navigation : les trois jours autour
 * d'aujourd'hui sont nommés plutôt que datés, c'est ce qu'un technicien lit le
 * plus souvent.
 */
internal fun titreJour(date: LocalDate, aujourdhui: LocalDate = LocalDate.now()): String =
    when (date) {
        aujourdhui -> "Aujourd'hui"
        aujourdhui.minusDays(1) -> "Hier"
        aujourdhui.plusDays(1) -> "Demain"
        else -> libelleDate(date)
    }

/**
 * Le sélecteur Material 3 raisonne en millisecondes UTC à minuit : passer par
 * le fuseau local décalerait la date d'un jour selon l'heure qu'il est.
 */
internal fun LocalDate.versMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.versLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * L'heure locale d'un horodatage : « 08:00 », ou un tiret s'il n'y en a pas.
 *
 * Le fuseau du système, et non UTC : c'est l'heure à laquelle le technicien
 * est arrivé chez son client qui compte, pas celle de Greenwich.
 */
internal fun heureLocale(instant: Instant?): String = instant
    ?.atZone(java.time.ZoneId.systemDefault())
    ?.toLocalTime()
    ?.format(FORMAT_HEURE)
    ?: "—"

/** « 14/05 », la date courte des historiques et du registre. */
internal fun jourCourt(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM"))

/**
 * « LUN », le jour de la semaine des pastilles du planning.
 *
 * Tronqué à trois lettres : le français abrège en « lun. », et le point mangerait
 * une pastille déjà étroite. La locale est imposée, comme partout ailleurs ici.
 */
internal fun jourSemaineCourt(date: LocalDate): String = date.dayOfWeek
    .getDisplayName(TextStyle.SHORT, Locale.FRENCH)
    .uppercase(Locale.FRENCH)
    .take(3)

/**
 * « sept. », le mois abrégé d'une pastille de date.
 *
 * La locale est imposée plutôt que laissée au système : l'application est en
 * français, et un téléphone réglé en anglais afficherait « Sep » au milieu
 * d'une phrase française.
 */
internal fun moisCourt(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM", Locale.FRENCH))

/** Le lundi de la semaine où tombe [date] : l'ancrage du planning. */
internal fun lundiDe(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())
