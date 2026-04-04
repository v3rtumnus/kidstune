package at.kidstune.kids.playback

import javax.inject.Qualifier

/**
 * Qualifier for the application-level [kotlinx.coroutines.CoroutineScope]
 * provided by [at.kidstune.kids.di.PlaybackModule].
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
