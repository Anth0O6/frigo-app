package com.frigopro.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
