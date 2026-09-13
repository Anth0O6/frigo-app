package com.frigopro.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Le rappel des factures échues.
 *
 * C'est la seule chose de l'application qui se manifeste **sans qu'on l'ait
 * ouverte**, et cela demande d'être justifié : un impayé est le seul événement
 * du métier qui se produise pendant qu'on ne regarde pas. Une intervention, un
 * contrôle d'étanchéité, un devis attendent qu'on ouvre l'application ; une
 * facture, elle, devient exigible toute seule un matin, et ne rien faire coûte
 * de l'argent.
 *
 * Trois précautions, dans cet ordre d'importance :
 *
 * - **L'application marche entièrement sans.** Les impayés paraissent de toute
 *   façon sur l'accueil. La notification ne fait que porter au-dehors ce qui est
 *   déjà là, et une permission refusée ne retire rien.
 * - **Une notification par jour au plus**, et une seule pour toutes les
 *   factures : un rappel par impayé, un matin où trois arrivent à échéance,
 *   serait la meilleure façon de faire couper les notifications pour de bon.
 * - **Rien n'est stocké.** L'échéance dépassée se recalcule au moment du
 *   rappel, comme partout ailleurs — un « en retard » en base serait faux le
 *   lendemain.
 */
object RappelsFactures {

    /** Le canal, nommé pour que l'utilisateur sache ce qu'il coupe. */
    const val CANAL = "factures-echues"

    private const val TRAVAIL = "rappel-factures-echues"

    private const val NOTIFICATION = 4_201

    /** L'heure du rappel : le matin, quand la tournée se prépare. */
    private val HEURE_DU_RAPPEL: LocalTime = LocalTime.of(8, 0)

    /**
     * Le montant du rappel, en français.
     *
     * La locale est **dite** et non laissée au système, comme dans `Nombres` :
     * un `%.2f` sans locale suit celle de la machine, et le même code écrit
     * « 600,00 » sur un téléphone français et « 600.00 » ailleurs. Le rappel est
     * lu par quelqu'un qui vient de facturer en euros.
     */
    private const val MONTANT = "%,.2f €"


    /**
     * Crée le canal de notification.
     *
     * Posé au démarrage et non au premier rappel : un canal absent fait ignorer
     * la notification en silence, et le seul moment où l'on peut s'en apercevoir
     * est celui où elle aurait dû paraître.
     */
    fun poserLeCanal(contexte: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Même prudence qu'à côté : rien de tout ceci ne doit faire échouer un
        // démarrage.
        runCatching { poserLeCanalOuEchouer(contexte) }
    }

    private fun poserLeCanalOuEchouer(contexte: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Factures échues",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Un rappel le matin quand une facture a dépassé son échéance."
        }
        contexte.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(canal)
    }

    /**
     * Programme le rappel quotidien.
     *
     * `KEEP` et non `UPDATE` : reprogrammer à chaque démarrage remettrait le
     * délai initial à zéro, et l'application ouverte tous les matins à 7 h 30
     * n'aurait jamais notifié — le travail serait toujours reporté au lendemain.
     */
    fun planifier(contexte: Context) {
        // Enveloppé, et ce n'est pas de la superstition : `WorkManager.getInstance`
        // lève quand l'initialisation n'a pas eu lieu, ce qui arrive hors d'un
        // vrai téléphone — sous Robolectric, par exemple, où la classe
        // `Application` est instanciée comme ailleurs. Or ceci s'exécute dans
        // `onCreate` : une exception y empêcherait l'application de démarrer, et
        // le rappel du matin ne vaut pas ce prix. Il est facultatif par
        // construction, il l'est donc aussi quand il échoue.
        runCatching {
            val travail = PeriodicWorkRequestBuilder<RappelFacturesWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delaiJusquAuRappel().toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
                TRAVAIL,
                ExistingPeriodicWorkPolicy.KEEP,
                travail,
            )
        }
    }

    /** Le temps qui reste jusqu'au prochain [HEURE_DU_RAPPEL]. */
    internal fun delaiJusquAuRappel(
        maintenant: LocalDateTime = LocalDateTime.now(),
    ): Duration {
        val aujourdhui = maintenant.toLocalDate().atTime(HEURE_DU_RAPPEL)
        val cible = if (maintenant.isBefore(aujourdhui)) aujourdhui else aujourdhui.plusDays(1)
        return Duration.between(maintenant, cible)
    }

    /**
     * Ce que dit le rappel.
     *
     * Séparé de l'envoi pour la raison qui vaut partout ici : le texte se
     * vérifie sans téléphone, l'envoi non. Il nomme le client quand il n'y en a
     * qu'un — « Boucherie Morel » appelle un geste, « 1 facture échue » demande
     * d'ouvrir l'application pour savoir laquelle.
     */
    internal fun texte(echues: List<FactureChiffree>): Pair<String, String> {
        val montant = echues.sumOf { it.totalTtc }.auCentime()
        fun format(valeur: Double) = String.format(Locale.FRANCE, MONTANT, valeur)
        val titre = if (echues.size == 1) {
            "1 facture échue"
        } else {
            "${echues.size} factures échues"
        }
        val detail = if (echues.size == 1) {
            val facture = echues.single().facture
            listOf(facture.clientNom, facture.numero)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        } else {
            ""
        }
        val corps = listOf(detail, format(montant) + " à encaisser")
            .filter { it.isNotBlank() }
            .joinToString(" — ")
        return titre to corps
    }

    /**
     * Publie le rappel.
     *
     * Sans permission, ne fait rien et ne se plaint pas : l'utilisateur a dit
     * non, et l'accueil continue de montrer les impayés.
     */
    fun notifier(contexte: Context, echues: List<FactureChiffree>) {
        if (echues.isEmpty() || !notificationsAutorisees(contexte)) return

        val (titre, corps) = texte(echues)
        val ouverture = contexte.packageManager
            .getLaunchIntentForPackage(contexte.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pression = ouverture?.let {
            PendingIntent.getActivity(
                contexte,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(contexte, CANAL)
            // Le flocon monochrome : une icône de notification doit être d'une
            // seule couleur, et c'est exactement ce à quoi il sert déjà pour les
            // icônes thématisées d'Android 13.
            .setSmallIcon(com.frigopro.app.R.drawable.ic_launcher_monochrome)
            .setContentTitle(titre)
            .setContentText(corps)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corps))
            .setAutoCancel(true)
            .setContentIntent(pression)
            .build()

        runCatching {
            NotificationManagerCompat.from(contexte).notify(NOTIFICATION, notification)
        }
    }

    /** La permission est accordée — toujours vraie avant Android 13. */
    fun notificationsAutorisees(contexte: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexte, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Le travail quotidien : lire les factures, en retenir les échues, notifier.
 *
 * Il lit la base directement plutôt que de recevoir un résultat : c'est le
 * propre d'un travail différé, qui s'exécute quand l'application est fermée et
 * qu'aucun écran ne lui passe quoi que ce soit.
 */
class RappelFacturesWorker(
    contexte: Context,
    parametres: WorkerParameters,
) : CoroutineWorker(contexte, parametres) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? com.frigopro.app.FrigoProApplication
            ?: return Result.success()
        val echues = application.conteneur.factures.enRetard(LocalDate.now()).first()
        RappelsFactures.notifier(applicationContext, echues)
        return Result.success()
    }
}
