package at.kidstune.kids

import android.app.Application
import androidx.work.Configuration
import at.kidstune.kids.sync.KidstuneWorkerFactory
import at.kidstune.kids.sync.SyncManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KidstuneApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: KidstuneWorkerFactory
    @Inject lateinit var syncManager: SyncManager

    /**
     * Provides a custom WorkManager configuration so [KidstuneWorkerFactory] can
     * inject Hilt-managed dependencies into [at.kidstune.kids.sync.SyncWorker].
     * WorkManager's default Jetpack Startup auto-initializer is disabled in AndroidManifest.xml.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Register the 15-minute periodic sync. KEEP policy makes this a no-op
        // if a work chain is already enqueued, so calling on every launch is safe.
        syncManager.registerPeriodicSync()
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .diskCache {
                // 200 MB disk cache for cover art – kids browse albums repeatedly offline
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
}
