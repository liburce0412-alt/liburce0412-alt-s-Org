package com.example.core.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val OCTET_MEDIA_TYPE = "application/octet-stream".toMediaType()

    val supabaseUrl: String
        get() = try {
            BuildConfig.SUPABASE_URL
        } catch (e: Exception) {
            "https://your-project.supabase.co"
        }

    var userJwt: String = ""

    val supabaseAnonKey: String
        get() = try {
            BuildConfig.SUPABASE_ANON_KEY
        } catch (e: Exception) {
            "your_anon_public_key"
        }

    fun isConfigured(): Boolean {
        val url = supabaseUrl
        val key = supabaseAnonKey
        return url.isNotEmpty() && !url.contains("your-project") &&
               key.isNotEmpty() && !key.contains("your_anon")
    }

    // ==========================================
    // 1. Supabase Auth REST
    // ==========================================
    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success("local_session_token_signed_up")
        }

        val url = "$supabaseUrl/auth/v1/signup"
        val bodyStr = """{"email":"$email","password":"$password"}"""
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", supabaseAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "")
                } else {
                    Result.failure(Exception("Signup error: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success("local_session_token_signed_in")
        }

        val url = "$supabaseUrl/auth/v1/token?grant_type=password"
        val bodyStr = """{"email":"$email","password":"$password"}"""
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", supabaseAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "")
                } else {
                    Result.failure(Exception("SignIn error: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 2. Supabase Storage REST (Avatar Upload)
    // ==========================================
    suspend fun uploadAvatar(filePath: String, userId: String): Result<String> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext Result.failure(Exception("File does not exist"))
        }

        if (!isConfigured()) {
            // Local Simulation URL
            Log.d("SupabaseStorage", "Supabase credentials are NOT set! Returning fallback avatar.")
            return@withContext Result.success("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200")
        }

        val fileName = "$userId/avatar.jpg"
        val url = "$supabaseUrl/storage/v1/object/avatars/$fileName"
        
        try {
            Log.d("StorageUpload", "bucket=avatars path=$fileName")
            Log.d("StorageUpload", "jwtExists=${userJwt.isNotBlank()}")

            val fileBytes = file.readBytes()
            val mediaType = "image/jpeg".toMediaType()
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $userJwt")
                .addHeader("Content-Type", "image/jpeg")
                .post(fileBytes.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "$supabaseUrl/storage/v1/object/public/avatars/$fileName"
                    Result.success(publicUrl)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("SupabaseStorage", "Storage Upload Error Details: $errorBody, response.code = ${response.code}")
                    Result.failure(Exception("Storage Upload error: ${response.code} $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 3. Supabase PostgREST (Data Sync)
    // ==========================================
    suspend fun selectAllTimeRecords(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Supabase local offline mode default"))
        }
        val url = "$supabaseUrl/rest/v1/time_records?select=*"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", supabaseAnonKey)
            .addHeader("Authorization", "Bearer $supabaseAnonKey")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "[]")
                } else {
                    Result.failure(Exception("PostgREST fetch error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertTimeRecord(recordJson: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success("inserted_locally")
        }
        val url = "$supabaseUrl/rest/v1/time_records"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", supabaseAnonKey)
            .addHeader("Authorization", "Bearer $supabaseAnonKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(recordJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "")
                } else {
                    Result.failure(Exception("PostgREST insert error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
