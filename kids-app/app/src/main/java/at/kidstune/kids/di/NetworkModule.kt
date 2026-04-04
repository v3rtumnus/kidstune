package at.kidstune.kids.di

import at.kidstune.kids.BuildConfig
import at.kidstune.kids.data.remote.KidstuneApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            headers.append("Authorization", "Bearer ${BuildConfig.DEVICE_TOKEN}")
        }
    }

    @Provides @Singleton
    fun provideApiClient(httpClient: HttpClient): KidstuneApiClient =
        KidstuneApiClient(httpClient, BuildConfig.BASE_URL)
}
