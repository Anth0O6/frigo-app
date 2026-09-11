package com.frigopro.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * L'archive est le filet de sécurité du technicien : si elle se relit mal, la
 * perte est totale et silencieuse. Tout est éprouvable sur la JVM, `java.util.zip`
 * ne demandant rien à Android.
 */
class ArchiveSauvegardeTest {

    @get:Rule
    val dossier = TemporaryFolder()

    @Test
    fun `le json se relit sans toucher aux images`() = runTest {
        val archive = ecrire("""{"format":3}""", listOf(photo("a.jpg", "AAA"), photo("b.jpg", "BBB")))

        assertEquals("""{"format":3}""", ArchiveSauvegarde.lireJson(ByteArrayInputStream(archive)))
    }

    @Test
    fun `les images se retrouvent par leur nom`() = runTest {
        val archive = ecrire("{}", listOf(photo("a.jpg", "AAA"), photo("b.jpg", "BBB")))

        val recues = mutableMapOf<String, String>()
        ArchiveSauvegarde.extrairePhotos(ByteArrayInputStream(archive)) { nom, flux ->
            recues[nom] = flux.readBytes().decodeToString()
        }

        assertEquals(mapOf("a.jpg" to "AAA", "b.jpg" to "BBB"), recues)
    }

    /**
     * Le flux d'une entrée est celui de l'archive entière : si le `use` de
     * l'appelant le refermait, la deuxième photo n'arriverait jamais.
     */
    @Test
    fun `refermer le flux d'une image ne coupe pas la lecture des suivantes`() = runTest {
        val archive = ecrire("{}", listOf(photo("a.jpg", "AAA"), photo("b.jpg", "BBB")))

        val noms = mutableListOf<String>()
        ArchiveSauvegarde.extrairePhotos(ByteArrayInputStream(archive)) { nom, flux ->
            flux.use { it.readBytes() }
            noms += nom
        }

        assertEquals(listOf("a.jpg", "b.jpg"), noms)
    }

    @Test
    fun `une archive sans sauvegarde json ne donne rien`() = runTest {
        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie).use { archive ->
            archive.putNextEntry(ZipEntry("autre-chose.txt"))
            archive.write("bonjour".toByteArray())
            archive.closeEntry()
        }

        assertNull(ArchiveSauvegarde.lireJson(ByteArrayInputStream(sortie.toByteArray())))
    }

    @Test
    fun `une archive se reconnait a sa signature`() {
        val archive = ecrire("{}", emptyList())

        assertTrue(ArchiveSauvegarde.estUneArchive(BufferedInputStream(ByteArrayInputStream(archive))))
    }

    /** Les sauvegardes déjà faites sont du JSON en clair, et doivent rester lisibles. */
    @Test
    fun `un fichier texte n'est pas pris pour une archive, et reste intact`() {
        val texte = """{"format":2,"interventions":[]}"""
        val flux = BufferedInputStream(ByteArrayInputStream(texte.toByteArray()))

        assertFalse(ArchiveSauvegarde.estUneArchive(flux))
        assertEquals("le flux doit être rendu au premier octet", texte, flux.readBytes().decodeToString())
    }

    @Test
    fun `un fichier plus court que la signature n'est pas une archive`() {
        val flux = BufferedInputStream(ByteArrayInputStream("PK".toByteArray()))

        assertFalse(ArchiveSauvegarde.estUneArchive(flux))
    }

    /** Un fichier manquant au moment de l'export ne doit pas faire échouer l'archive. */
    @Test
    fun `une image absente est simplement omise`() = runTest {
        val archive = ecrire("{}", listOf(photo("a.jpg", "AAA"), File(dossier.root, "disparue.jpg")))

        val noms = mutableListOf<String>()
        ArchiveSauvegarde.extrairePhotos(ByteArrayInputStream(archive)) { nom, _ -> noms += nom }

        assertEquals(listOf("a.jpg"), noms)
    }

    private fun ecrire(json: String, photos: List<File>): ByteArray {
        val sortie = ByteArrayOutputStream()
        ArchiveSauvegarde.ecrire(sortie, json, photos)
        return sortie.toByteArray()
    }

    private fun photo(nom: String, contenu: String): File =
        dossier.newFile(nom).apply { writeText(contenu) }
}
