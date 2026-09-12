package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * « J'ai comparé cette courbe à ma réglette, elle est juste. »
 *
 * Les courbes de saturation sont des constantes physiques et vivent donc dans le
 * code (voir [CourbesSaturation]), comme les GWP. Mais **qui les a vérifiées est
 * une donnée de l'utilisateur**, et elle va en base : c'est lui qui engage sa
 * responsabilité en s'appuyant dessus pour régler un détendeur, et c'est donc à
 * lui de dire quand il y consent.
 *
 * Sans cette ligne, la réglette annonce le fluide comme non vérifié et le dit à
 * l'écran. C'est la même prudence que le catalogue livré sans prix, avec un enjeu
 * supérieur : un prix faux se rattrape, une surchauffe fausse casse un
 * compresseur.
 *
 * L'identifiant est le nom normalisé du fluide — il n'y a qu'une courbe par
 * fluide, et une clé tirée au hasard n'apporterait rien qu'une jointure de plus.
 */
@Entity(tableName = "verifications_fluide")
data class VerificationFluide(
    @PrimaryKey val fluide: String,
    val verifieLe: Instant = Instant.EPOCH,
    /** Qui a vérifié, recopié des réglages : une vérification s'assume. */
    val par: String = "",
)

@Dao
interface VerificationFluideDao {

    @Query("SELECT * FROM verifications_fluide")
    fun observerToutes(): Flow<List<VerificationFluide>>

    @Query("SELECT * FROM verifications_fluide")
    suspend fun toutes(): List<VerificationFluide>

    @Upsert
    suspend fun enregistrer(verification: VerificationFluide)

    @Upsert
    suspend fun enregistrerToutes(verifications: List<VerificationFluide>)

    @Query("DELETE FROM verifications_fluide WHERE fluide = :fluide")
    suspend fun effacer(fluide: String)
}

/**
 * Les fluides dont la courbe a été contrôlée.
 *
 * Le dépôt n'expose qu'un ensemble de noms : c'est tout ce dont la réglette a
 * besoin pour décider si elle affiche un avertissement, et travailler sur un
 * `Set` évite à l'écran de parcourir une liste à chaque pastille dessinée.
 */
class VerificationFluideRepository(private val dao: VerificationFluideDao) {

    val verifies: Flow<Set<String>> = dao.observerToutes().map { liste ->
        liste.map { Fluides.normaliser(it.fluide) }.toSet()
    }

    /**
     * Marque un fluide vérifié, ou retire la marque.
     *
     * Retirer est aussi important que poser : quelqu'un qui s'aperçoit qu'il a
     * coché trop vite doit pouvoir revenir en arrière, sinon il n'osera plus
     * cocher du tout.
     */
    suspend fun basculer(fluide: String, verifie: Boolean, par: String) {
        val nom = Fluides.normaliser(fluide)
        if (verifie) {
            dao.enregistrer(VerificationFluide(fluide = nom, verifieLe = Instant.now(), par = par))
        } else {
            dao.effacer(nom)
        }
    }
}
