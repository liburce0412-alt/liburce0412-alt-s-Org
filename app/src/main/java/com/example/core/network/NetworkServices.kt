package com.example.core.network

import android.util.Log
import com.example.BuildConfig
import com.example.core.model.AppUpdate
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// ==========================================
// Define Repositories Interfaces
// ==========================================

interface AiRepository {
    suspend fun analyzeTimeRecords(recordsJson: String): String
}

interface UpdateRepository {
    suspend fun checkUpdate(currentVersionCode: Int): AppUpdate?
}

// ==========================================
// Retrofit Endpoint Definitions
// ==========================================

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

interface UpdateApi {
    @GET
    suspend fun checkUpdateConfig(@Url url: String): UpdateConfig
}

// ==========================================
// Network Helper / Engine Singleton
// ==========================================

object NetworkClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val geminiRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val geminiApi: GeminiApi = geminiRetrofit.create(GeminiApi::class.java)

    private val generalRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/") // Placeholder base
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val updateApi: UpdateApi = generalRetrofit.create(UpdateApi::class.java)
}

// ==========================================
// Repository Implementation classes
// ==========================================

class AiRepositoryImpl(private val context: android.content.Context) : AiRepository {
    override suspend fun analyzeTimeRecords(recordsJson: String): String {
        val config = DynamicAiClient.loadConfig(context)
        
        val prompt = """
            以下是某个大学生用户近期记录的时间日志(柳比歇夫时间统计法格式):
            $recordsJson
            
            请作为专业学术咨询导师和时间管理大师，为该学生提供深度、个性化的时间分配反思与改善建议。
            请确保输出内容包括:
            1. 学习效率与核心成果反省 (今日/本期统计：指出表现优异的板块)
            2. 本期时间浪费分析 & 习惯洞察 (指出由于拖延、无聊导致的低效时间)
            3. 具体的一周时间改进柳比歇夫量化建议。
            
            请用中文回答，排版精美、有亲和力、多用 emoji 且专业！
        """.trimIndent()

        // If dynamic config provider has empty API key, fall back to default informative insight or GEMINI_API_KEY if configured
        if (config.apiKey.isEmpty()) {
            val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            if (geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && geminiKey != "GEMINI_API_KEY") {
                // Fallback to pre-configured Gemini
                val request = GeminiRequest(
                    contents = listOf(ContentJson(parts = listOf(PartJson(text = prompt)))),
                    generationConfig = GenerationConfigJson(temperature = 0.7f)
                )
                return try {
                    val response = NetworkClient.geminiApi.generateContent(geminiKey, request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "生成内容为空！请确认您已经配置了有效的深度智能提供商。"
                } catch (e: Exception) {
                    "离线引擎分析：记录正常。配置高级 DeepSeek / OpenAI 模型能使效果飞跃！"
                }
            } else {
                return """
                    💡 校园自律之星时间反思反馈（基础离线分析）：
                    
                    1. 【学时分配】：今日共完成了一定的深度自学或专栏阅读，学习纯度达标，维持良好的自习节奏。
                    2. 【自律盲点】：缺乏有氧运动和社交陪伴记录，长期自闭深度学习容易引起情绪倦怠。
                    3. 【效率建议】：推荐在下午 3 点安排 1 节「有氧漫步 / 社交聊天」，让大脑获得充足慢波反馈！
                    
                    （✨ 温馨提醒：您尚未配置专属 AI 密钥。请点击右上角「设置 -> AI配置中心」绑定您的 DeepSeek/OpenAI 密钥，安全密钥纯本地加密存储。）
                """.trimIndent()
            }
        }

        return DynamicAiClient.generateChat(context, prompt)
    }
}

class UpdateRepositoryImpl : UpdateRepository {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    override suspend fun checkUpdate(currentVersionCode: Int): AppUpdate? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val updateUrl = "https://yourdomain.app/update.json" // REPLACE with actual domain if needed
        try {
            val request = okhttp3.Request.Builder()
                .url(updateUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("UpdateCheck", "HTTP error check: ${response.code}")
                    return@withContext null
                }

                val responseStr = response.body?.string() ?: ""
                val adapter = moshi.adapter(UpdateConfig::class.java)
                val config = adapter.fromJson(responseStr)

                if (config != null) {
                    val localVersion = currentVersionCode
                    val remoteVersion = config.versionCode
                    Log.d("UpdateCheck", "local=$localVersion remote=$remoteVersion")

                    if (remoteVersion > localVersion) {
                        AppUpdate(
                            versionCode = config.versionCode,
                            versionName = config.versionName,
                            updateLog = config.updateLog,
                            apkUrl = config.apkUrl,
                            isForceUpdate = config.isForceUpdate,
                            isGrayUpdate = config.isGrayUpdate
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateCheck", "Update check failed", e)
            null
        }
    }
}
