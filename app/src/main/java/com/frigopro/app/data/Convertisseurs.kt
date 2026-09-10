package com.frigopro.app.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Conversions entre les types `java.time` et les colonnes SQLite.
 *
 * Dates et heures sont stockées en texte de largeur fixe, ce qui les rend
 * lisibles à l'inspection et surtout triables et comparables directement en
 * SQL — c'est ce sur quoi reposent le `WHERE date = :date` et le
 * `ORDER BY heure` du DAO.
 */
object Convertisseurs {

    /**
     * `LocalTime.toString()` omet les secondes quand elles valent zéro et les
     * ajoute sinon : un format explicite garantit une largeur constante, donc
     * un tri lexicographique correct.
     */
    private val FORMAT_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @TypeConverter
    fun depuisDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun versDate(valeur: String?): LocalDate? = valeur?.let(LocalDate::parse)

    @TypeConverter
    fun depuisHeure(heure: LocalTime?): String? = heure?.format(FORMAT_HEURE)

    @TypeConverter
    fun versHeure(valeur: String?): LocalTime? = valeur?.let { LocalTime.parse(it, FORMAT_HEURE) }

    @TypeConverter
    fun depuisInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun versInstant(valeur: Long?): Instant? = valeur?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun depuisCategorie(categorie: CategoriePhoto?): String? = categorie?.name

    @TypeConverter
    fun versCategorie(valeur: String?): CategoriePhoto? = valeur?.let(CategoriePhoto::valueOf)

    @TypeConverter
    fun depuisStatut(statut: StatutIntervention?): String? = statut?.name

    @TypeConverter
    fun versStatut(valeur: String?): StatutIntervention? = valeur?.let(StatutIntervention::valueOf)
}
