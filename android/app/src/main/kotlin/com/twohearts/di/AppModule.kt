package com.twohearts.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twohearts.BuildConfig
import com.twohearts.data.api.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

object PrefKeys {
    val ACCESS_TOKEN  = stringPreferencesKey("access_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    val USER_ID       = stringPreferencesKey("user_id")
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore

    @Provides @Singleton
    fun provideTokenStore(store: DataStore<Preferences>): TokenStore = TokenStore(store)

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    @Provides @Singleton
    fun provideHttpClient(json: Json, tokenStore: TokenStore): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(Logging) {
            level  = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            logger = object : Logger { override fun log(message: String) { android.util.Log.d("Ktor", message) } }
        }
        install(HttpTimeout) {
            requestTimeoutMillis  = 30_000
            connectTimeoutMillis  = 15_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        // Attach JWT on every request that has a token
        install(DefaultRequest) {
            val token = tokenStore.getAccessTokenBlocking()
            if (!token.isNullOrBlank()) {
                headers.append("Authorization", "Bearer $token")
            }
        }
    }

    @Provides @Singleton
    fun provideApiService(client: HttpClient): ApiService =
        ApiService(client, BuildConfig.API_BASE_URL)
}

@Singleton
class TokenStore @javax.inject.Inject constructor(
    private val store: DataStore<Preferences>
) {
    suspend fun saveTokens(accessToken: String, refreshToken: String, userId: String) {
        store.edit {
            it[PrefKeys.ACCESS_TOKEN]  = accessToken
            it[PrefKeys.REFRESH_TOKEN] = refreshToken
            it[PrefKeys.USER_ID]       = userId
        }
    }

    suspend fun getAccessToken(): String?  = store.data.map { it[PrefKeys.ACCESS_TOKEN] }.first()
    suspend fun getRefreshToken(): String? = store.data.map { it[PrefKeys.REFRESH_TOKEN] }.first()
    suspend fun getUserId(): String?       = store.data.map { it[PrefKeys.USER_ID] }.first()
    suspend fun isLoggedIn(): Boolean      = !getAccessToken().isNullOrBlank()

    suspend fun clear() = store.edit {
        it.remove(PrefKeys.ACCESS_TOKEN)
        it.remove(PrefKeys.REFRESH_TOKEN)
        it.remove(PrefKeys.USER_ID)
    }

    // Blocking version for HttpClient interceptor (called in non-suspending context)
    fun getAccessTokenBlocking(): String? = runCatching {
        kotlinx.coroutines.runBlocking { getAccessToken() }
    }.getOrNull()
}
