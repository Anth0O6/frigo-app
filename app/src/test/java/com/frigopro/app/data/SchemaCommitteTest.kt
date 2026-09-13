package com.frigopro.app.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Le schéma committé correspond-il à la base que le code produit ?
 *
 * Ce test existe parce que le contrôle équivalent en intégration continue s'est
 * révélé incapable d'échouer. Le schéma n'est écrit que si KSP retraite
 * réellement les sources ; or Gradle le croit à jour — `app/schemas` n'est pas
 * déclaré parmi ses sorties — et même forcé, KSP garde son propre état
 * incrémental et conclut qu'il n'a rien à faire. Le contrôle comparait donc le
 * fichier committé **à lui-même**, et il est passé au vert sur un schéma dont
 * l'empreinte était le texte « à remplacer ».
 *
 * Ici, rien de tout cela : la base est **réellement créée** par Robolectric, et
 * Room y inscrit l'empreinte qu'il a compilée depuis les entités. Comparer cette
 * empreinte à celle du fichier committé ne dépend d'aucun cache.
 *
 * L'empreinte est la seule valeur d'un schéma qui ne se déduise pas du
 * précédent, et c'est elle dont [MigrationTest] a besoin pour recréer une base
 * d'une version antérieure. Un schéma committé avec une empreinte fausse ne se
 * voit donc qu'au moment d'écrire la migration *suivante* — trop tard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaCommitteTest {

    private val contexte: Context = RuntimeEnvironment.getApplication()

    private var base: FrigoProDatabase? = null

    @After
    fun fermer() {
        base?.close()
        contexte.deleteDatabase(FrigoProDatabase.NOM)
    }

    @Test
    fun `le schema de la version courante est committe, avec la bonne empreinte`() {
        val ouverte = FrigoProDatabase.creer(contexte).also { base = it }
        val sql = ouverte.openHelper.writableDatabase

        val version = sql.version
        val empreinteReelle = sql.query(
            "SELECT `identity_hash` FROM `room_master_table` WHERE `id` = 42",
        ).use { curseur ->
            assertTrue("Room n'a pas inscrit d'empreinte", curseur.moveToFirst())
            curseur.getString(0)
        }

        val fichier = fichierSchema(version)
        assertTrue(
            "Le schéma de la version $version n'est pas committé. Il se récupère " +
                "dans l'artefact `room-schemas` d'une build. Empreinte attendue : " +
                empreinteReelle,
            fichier != null,
        )

        val empreinteCommittee = EMPREINTE.find(fichier!!.readText())?.groupValues?.get(1)
        assertEquals(
            "L'empreinte du schéma committé ne correspond pas à celle que Room " +
                "compile depuis les entités. Corrigez `identityHash` et la ligne " +
                "`room_master_table` de ${fichier.name}.",
            empreinteReelle,
            empreinteCommittee,
        )
    }

    /**
     * Le fichier de schéma, cherché aux deux endroits possibles.
     *
     * Le répertoire de travail d'un test JVM est celui du module selon la façon
     * dont Gradle est invoqué, et la racine du projet selon une autre. Chercher
     * aux deux évite un test qui échouerait pour une raison sans rapport avec ce
     * qu'il vérifie.
     */
    private fun fichierSchema(version: Int): File? = listOf(
        File("schemas/${FrigoProDatabase::class.qualifiedName}/$version.json"),
        File("app/schemas/${FrigoProDatabase::class.qualifiedName}/$version.json"),
    ).firstOrNull { it.isFile }

    private companion object {
        val EMPREINTE = Regex("\"identityHash\"\\s*:\\s*\"([^\"]*)\"")
    }
}
