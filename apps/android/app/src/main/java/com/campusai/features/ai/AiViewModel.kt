package com.campusai.features.ai

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEngineRouter
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.AiRoutingException
import com.campusai.core.ai.NetworkAvailability
import com.campusai.core.ai.PersonalDeepSeekAiEngine
import com.campusai.core.agent.CaesarAgentEngine
import com.campusai.core.agent.CaesarAppTools
import com.campusai.core.agent.CaesarIdempotencyStore
import com.campusai.core.agent.CaesarMemoryStore
import com.campusai.core.agent.RoomCaesarTraceSink
import com.campusai.core.database.AiReportEntity
import com.campusai.core.database.CampusDao
import com.campusai.core.database.DailyGreetingEntity
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.localai.LocalModelMode
import com.campusai.core.health.BandLiveGateway
import com.campusai.core.health.BandLiveProviderGateway
import com.campusai.core.health.BandLiveSnapshot
import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthGateway
import com.campusai.core.health.HealthGatewayFactory
import com.campusai.core.health.HealthPeriods
import com.campusai.core.health.HealthSnapshot
import com.campusai.core.health.HealthSyncCoordinator
import com.campusai.core.health.HealthSyncReason
import com.campusai.core.health.mifitness.MiFitnessAccountException
import com.campusai.core.health.mifitness.MiFitnessAccountService
import com.campusai.core.health.mifitness.MiFitnessCredentialStore
import com.campusai.core.health.mifitness.MiFitnessStepsSyncException
import com.campusai.core.health.mifitness.MiFitnessStepsSyncScheduler
import com.campusai.core.health.mifitness.MiFitnessStepsSyncWorker
import com.campusai.core.health.mifitness.MiFitnessSummaryHealthGateway
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.AiReport
import com.campusai.core.model.DailyGreeting
import com.campusai.core.network.PersonalDeepSeekClient
import com.campusai.core.preferences.UserPreferencesRepository
import com.campusai.core.security.PersonalDeepSeekKeyStore
import com.campusai.core.profile.ProfileRepository
import com.campusai.features.community.CampusRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AiUiState(
    val mode: AiMode = AiMode.FAST,
    val provider: AiProvider = AiProvider.AUTO,
    val resolvedProvider: AiProvider? = null,
    val messages: List<AiConversationMessage> = emptyList(),
    val streaming: Boolean = false,
    val stage: String = "",
    val model: String = "",
    val elapsedMs: Long = 0,
    val error: String? = null,
    val errorCode: String? = null,
    val canUseCloudOnce: Boolean = false,
    val pendingCloudPrompt: String? = null,
    val pendingCloudDisplayPrompt: String? = null,
    val pendingContextJson: String? = null,
    val pendingPresentationJson: String? = null,
    val contextSelection: AiContextSelection = AiContextSelection(),
    val pendingImages: List<CaesarImageAttachment> = emptyList(),
    val importingImage: Boolean = false,
    val lockedLocalModelId: String? = null,
)

data class CaesarHealthUiState(
    val loading: Boolean = false,
    val miFitnessConfigured: Boolean = false,
    val miFitnessSyncing: Boolean = false,
    val miFitnessStatus: MiFitnessUiStatus = MiFitnessUiStatus.IDLE,
    val miFitnessLastSyncAt: Long? = null,
    val miFitnessFormResetKey: Long = 0L,
    val availability: HealthAvailability? = null,
    val grantedPermissionCount: Int = 0,
    val requiredPermissionCount: Int = 0,
    val snapshot: HealthSnapshot? = null,
    val band: BandLiveSnapshot? = null,
    val healthError: String? = null,
    val bandError: String? = null,
    val actionMessage: String? = null,
)

enum class MiFitnessUiStatus {
    IDLE,
    VALIDATING,
    REFRESHING,
    DELETING,
    SUCCESS,
    AUTH_ERROR,
    NETWORK_ERROR,
    STORAGE_ERROR,
}

data class CaesarMemoryUiItem(
    val id: String,
    val type: String,
    val content: String,
    val confirmed: Boolean,
    val expiresAt: Long?,
)

class AiViewModel(
    private val dao: CampusDao,
    context: Context,
    private val preferences: UserPreferencesRepository,
    private val modelManager: LocalModelManager,
    private val localEngine: AiEngine,
    private val personalKeyStore: PersonalDeepSeekKeyStore,
    private val campusRepository: CampusRepository = CampusRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val personalDeepSeekEngine: AiEngine = PersonalDeepSeekAiEngine(PersonalDeepSeekClient(personalKeyStore)),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val provider = AtomicReference(AiProvider.AUTO)
    private val network = NetworkAvailability(appContext)
    private val imageProcessor = CaesarImageProcessor(appContext)
    private val memoryStore = CaesarMemoryStore(dao)
    private val miFitnessCredentialStore = MiFitnessCredentialStore(appContext)
    private val miFitnessAccountService = MiFitnessAccountService(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val healthGateway: HealthGateway = HealthGatewayFactory.create(appContext)
    private val bandGateway: BandLiveGateway = BandLiveProviderGateway(appContext)
    private val healthSyncCoordinator = HealthSyncCoordinator(healthGateway, bandGateway)
    private val router = AiEngineRouter(
        personalDeepSeek = personalDeepSeekEngine,
        local = localEngine,
        provider = provider::get,
        personalKeyAvailable = personalKeyStore::hasKey,
        isOnline = network::isOnline,
        localState = { modelId -> modelManager.runtimeFor(modelId).selection.state },
    )
    private val caesarEngine = CaesarAgentEngine(
        delegate = router,
        tools = CaesarAppTools(
            dao = dao,
            campus = campusRepository,
            profile = profileRepository,
            health = healthGateway,
            idempotency = CaesarIdempotencyStore(dao),
            memory = memoryStore,
        ).registry(),
        traceSink = RoomCaesarTraceSink(dao),
    )
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()
    private val _healthState = MutableStateFlow(
        CaesarHealthUiState(miFitnessConfigured = miFitnessCredentialStore.hasCredentials()),
    )
    val healthState: StateFlow<CaesarHealthUiState> = _healthState.asStateFlow()
    val localModelSelection = modelManager.selection
    val localModelStates = modelManager.states
    fun localModelFor(modelId: String) = modelManager.runtimeFor(modelId).selection
    val history: StateFlow<List<AiReport>> = dao.getAiReportsFlow().map { rows -> rows.map(AiReportEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories: StateFlow<List<CaesarMemoryUiItem>> = dao.getAgentMemoriesFlow().map { rows ->
        rows.map { row -> CaesarMemoryUiItem(row.id, row.type, row.content, row.confirmedAt != null, row.expiresAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _dailyGreeting = MutableStateFlow<DailyGreeting?>(null)
    val dailyGreeting: StateFlow<DailyGreeting?> = _dailyGreeting.asStateFlow()

    private var generation: Job? = null
    private val generationEpoch = AiGenerationEpoch()
    private var greetingJob: Job? = null
    private var greetingAttemptedDate: String? = null
    private var conversationId = UUID.randomUUID().toString()
    private var conversationCreatedAt = System.currentTimeMillis()
    private var conversationTitle = ""
    private var conversationLocalModelId: String? = null
    private var recentlyDeleted: AiReport? = null
    private var activeOwnerUserId: String = ""
    private val healthLoadEpoch = AtomicLong(0L)
    private var miFitnessWorkLiveData: LiveData<WorkInfo?>? = null
    private var miFitnessWorkObserver: Observer<WorkInfo?>? = null

    init {
        viewModelScope.launch {
            preferences.preferences.collect { value ->
                provider.set(value.aiProvider)
                _state.value = _state.value.copy(provider = value.aiProvider)
            }
        }
    }

    fun setMode(mode: AiMode) {
        if (!_state.value.streaming) _state.value = _state.value.copy(mode = mode, error = null, errorCode = null)
    }

    fun selectLocalModel(mode: LocalModelMode) {
        if (_state.value.streaming) return
        modelManager.selectMode(mode)
    }

    fun setProvider(value: AiProvider) {
        if (_state.value.streaming) return
        provider.set(value)
        _state.value = _state.value.copy(
            provider = value,
            mode = if (value == AiProvider.LOCAL) AiMode.FAST else _state.value.mode,
            model = "",
            resolvedProvider = null,
            error = null,
            errorCode = null,
            canUseCloudOnce = false,
        )
        viewModelScope.launch { preferences.setAiProvider(value) }
    }

    fun setContextSelection(value: AiContextSelection) {
        if (!_state.value.streaming) _state.value = _state.value.copy(contextSelection = value)
    }

    fun refreshHealthStatus() {
        if (_healthState.value.loading) return
        viewModelScope.launch { loadHealthStatus() }
    }

    fun saveMiFitnessCredentials(userId: String, passToken: String) {
        if (_healthState.value.miFitnessSyncing) return
        healthLoadEpoch.incrementAndGet()
        _healthState.value = _healthState.value.copy(
            loading = false,
            miFitnessSyncing = true,
            miFitnessStatus = MiFitnessUiStatus.VALIDATING,
            healthError = null,
            actionMessage = "正在验证 Mi Fitness 中国区只读访问。",
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                miFitnessAccountService.validateAndSave(userId, passToken)
            }.fold(
                onSuccess = { summary ->
                    val previous = _healthState.value
                    _healthState.value = previous.copy(
                        miFitnessConfigured = true,
                        miFitnessSyncing = false,
                        miFitnessStatus = MiFitnessUiStatus.SUCCESS,
                        miFitnessLastSyncAt = summary.lastSyncAt,
                        miFitnessFormResetKey = previous.miFitnessFormResetKey + 1L,
                        band = null,
                        bandError = null,
                        actionMessage = "Mi Fitness 凭据已安全保存，今日步数已缓存。",
                    )
                    loadHealthStatus("Mi Fitness 今日步数已手动刷新。")
                },
                onFailure = { error ->
                    _healthState.value = _healthState.value.copy(
                        miFitnessConfigured = miFitnessCredentialStore.hasCredentials(),
                        loading = false,
                        miFitnessSyncing = false,
                        miFitnessStatus = error.toMiFitnessUiStatus(),
                        actionMessage = null,
                    )
                },
            )
        }
    }

    fun refreshMiFitnessSteps() {
        if (_healthState.value.miFitnessSyncing) return
        if (!miFitnessCredentialStore.hasCredentials()) {
            _healthState.value = _healthState.value.copy(
                miFitnessConfigured = false,
                miFitnessStatus = MiFitnessUiStatus.AUTH_ERROR,
                actionMessage = null,
            )
            return
        }
        if (!network.isOnline()) {
            _healthState.value = _healthState.value.copy(
                miFitnessStatus = MiFitnessUiStatus.NETWORK_ERROR,
                actionMessage = null,
            )
            return
        }
        healthLoadEpoch.incrementAndGet()
        val requestId = runCatching { MiFitnessStepsSyncScheduler.enqueue(appContext) }
            .getOrElse {
                _healthState.value = _healthState.value.copy(
                    miFitnessStatus = MiFitnessUiStatus.STORAGE_ERROR,
                    actionMessage = null,
                )
                return
            }
        _healthState.value = _healthState.value.copy(
            loading = false,
            miFitnessConfigured = true,
            miFitnessSyncing = true,
            miFitnessStatus = MiFitnessUiStatus.REFRESHING,
            healthError = null,
            actionMessage = "正在手动读取 Mi Fitness 今日步数。",
        )
        observeMiFitnessWork(requestId)
    }

    fun deleteMiFitnessCredentials() {
        if (
            _healthState.value.miFitnessSyncing &&
            _healthState.value.miFitnessStatus != MiFitnessUiStatus.REFRESHING
        ) return
        healthLoadEpoch.incrementAndGet()
        detachMiFitnessWorkObserver()
        workManager.cancelUniqueWork(MiFitnessStepsSyncScheduler.UNIQUE_WORK)
        _healthState.value = _healthState.value.copy(
            loading = false,
            miFitnessSyncing = true,
            miFitnessStatus = MiFitnessUiStatus.DELETING,
            actionMessage = null,
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { miFitnessAccountService.delete() }.fold(
                onSuccess = {
                    val previous = _healthState.value
                    _healthState.value = previous.copy(
                        miFitnessConfigured = false,
                        miFitnessSyncing = false,
                        miFitnessStatus = MiFitnessUiStatus.IDLE,
                        miFitnessLastSyncAt = null,
                        miFitnessFormResetKey = previous.miFitnessFormResetKey + 1L,
                        snapshot = previous.snapshot?.takeUnless {
                            MiFitnessSummaryHealthGateway.SOURCE_ID in it.originPackages
                        },
                        actionMessage = "Mi Fitness 凭据与本地步数缓存已删除。",
                    )
                    loadHealthStatus("Mi Fitness 凭据与本地步数缓存已删除。")
                },
                onFailure = {
                    _healthState.value = _healthState.value.copy(
                        miFitnessConfigured = miFitnessCredentialStore.hasCredentials(),
                        loading = false,
                        miFitnessSyncing = false,
                        miFitnessStatus = MiFitnessUiStatus.STORAGE_ERROR,
                    )
                },
            )
        }
    }

    fun startBandSession() {
        if (rejectBandActionWhileMiFitnessConfigured()) return
        runBandAction(
            action = bandGateway::startSession,
            successMessage = "已请求启动可见的手环实时会话。",
        )
    }

    fun stopBandSession() {
        if (rejectBandActionWhileMiFitnessConfigured()) return
        runBandAction(
            action = bandGateway::stopSession,
            successMessage = "已请求停止手环实时会话。",
        )
    }

    fun triggerBandHistorySync() {
        if (rejectBandActionWhileMiFitnessConfigured()) return
        if (_healthState.value.loading) return
        viewModelScope.launch {
            _healthState.value = _healthState.value.copy(loading = true, bandError = null)
            val result = healthSyncCoordinator.synchronize(
                period = HealthPeriods.parse("today"),
                reason = HealthSyncReason.USER,
                onStage = { _, message ->
                    _healthState.value = _healthState.value.copy(loading = true, actionMessage = message)
                },
            )
            val availability = healthGateway.availability()
            val granted = runCatching { healthGateway.grantedPermissions() }.getOrDefault(emptySet())
            _healthState.value = CaesarHealthUiState(
                loading = false,
                availability = availability,
                grantedPermissionCount = granted.size,
                requiredPermissionCount = healthGateway.readPermissions.size,
                snapshot = result.health,
                band = result.band,
                healthError = result.healthError?.let { "Health Connect 历史数据读取失败。" },
                bandError = result.bandError?.let { "CaesarBandBridge 历史同步失败。" },
                actionMessage = result.message,
            )
        }
    }

    private fun runBandAction(action: () -> Result<Unit>, successMessage: String) {
        if (_healthState.value.loading) return
        viewModelScope.launch {
            action().fold(
                onSuccess = {
                    _healthState.value = _healthState.value.copy(actionMessage = successMessage, bandError = null)
                    delay(650)
                    loadHealthStatus(successMessage)
                },
                onFailure = {
                    _healthState.value = _healthState.value.copy(
                        actionMessage = null,
                        bandError = "CaesarBandBridge 操作失败。",
                    )
                },
            )
        }
    }

    private suspend fun loadHealthStatus(actionMessage: String? = _healthState.value.actionMessage) {
        val requestEpoch = healthLoadEpoch.incrementAndGet()
        _healthState.value = _healthState.value.copy(loading = true)
        val configuredAtStart = miFitnessCredentialStore.hasCredentials()
        val availability = healthGateway.availability()
        val granted = runCatching { healthGateway.grantedPermissions() }.getOrDefault(emptySet())
        val healthResult = healthGateway.snapshot(HealthPeriods.parse("today"))
        val bandResult = if (configuredAtStart) null else bandGateway.snapshot()
        if (healthLoadEpoch.get() != requestEpoch) return
        val configured = miFitnessCredentialStore.hasCredentials()
        val previous = _healthState.value
        val cloudLastSync = healthResult.getOrNull()
            ?.takeIf { MiFitnessSummaryHealthGateway.SOURCE_ID in it.originPackages }
            ?.lastSyncAt
        _healthState.value = CaesarHealthUiState(
            loading = false,
            miFitnessConfigured = configured,
            miFitnessSyncing = previous.miFitnessSyncing,
            miFitnessStatus = previous.miFitnessStatus,
            miFitnessLastSyncAt = cloudLastSync ?: previous.miFitnessLastSyncAt,
            miFitnessFormResetKey = previous.miFitnessFormResetKey,
            availability = availability,
            grantedPermissionCount = granted.size,
            requiredPermissionCount = healthGateway.readPermissions.size,
            snapshot = healthResult.getOrNull(),
            band = if (configured) null else bandResult?.getOrNull(),
            healthError = healthResult.exceptionOrNull()?.let { "当前没有可读取的健康缓存。" },
            bandError = if (configured) null else bandResult?.exceptionOrNull()?.let {
                "CaesarBandBridge 状态不可用。"
            },
            actionMessage = actionMessage,
        )
    }

    private fun observeMiFitnessWork(requestId: UUID) {
        detachMiFitnessWorkObserver()
        val liveData = workManager.getWorkInfoByIdLiveData(requestId)
        lateinit var observer: Observer<WorkInfo?>
        observer = Observer { info ->
            if (info == null || !info.state.isFinished) return@Observer
            liveData.removeObserver(observer)
            if (miFitnessWorkObserver === observer) {
                miFitnessWorkLiveData = null
                miFitnessWorkObserver = null
            }
            viewModelScope.launch {
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    _healthState.value = _healthState.value.copy(
                        miFitnessSyncing = false,
                        miFitnessStatus = MiFitnessUiStatus.SUCCESS,
                    )
                    loadHealthStatus("Mi Fitness 今日步数已手动刷新。")
                } else {
                    val code = info.outputData.getString(MiFitnessStepsSyncWorker.KEY_ERROR_CODE)
                    _healthState.value = _healthState.value.copy(
                        miFitnessConfigured = miFitnessCredentialStore.hasCredentials(),
                        miFitnessSyncing = false,
                        miFitnessStatus = code.toMiFitnessUiStatus(),
                        actionMessage = null,
                    )
                }
            }
        }
        miFitnessWorkLiveData = liveData
        miFitnessWorkObserver = observer
        liveData.observeForever(observer)
    }

    private fun detachMiFitnessWorkObserver() {
        val liveData = miFitnessWorkLiveData
        val observer = miFitnessWorkObserver
        if (liveData != null && observer != null) liveData.removeObserver(observer)
        miFitnessWorkLiveData = null
        miFitnessWorkObserver = null
    }

    private fun rejectBandActionWhileMiFitnessConfigured(): Boolean {
        if (!miFitnessCredentialStore.hasCredentials()) return false
        _healthState.value = _healthState.value.copy(
            miFitnessConfigured = true,
            actionMessage = "Mi Fitness 云同步已启用；不会启动 Bridge，以免与小米服务争用手环连接。",
            bandError = null,
        )
        return true
    }

    private fun Throwable.toMiFitnessUiStatus(): MiFitnessUiStatus = when (
        (this as? MiFitnessStepsSyncException)?.code ?: (this as? MiFitnessAccountException)?.code
    ) {
        "invalid_credentials", "authentication_failed", "validation_failed", "credentials_missing" ->
            MiFitnessUiStatus.AUTH_ERROR
        "network_failed", "response_invalid", "record_out_of_window", "record_limit",
        "cursor_missing", "cursor_limit", "cursor_repeated", "page_limit", "aggregation_invalid",
        "sync_failed" -> MiFitnessUiStatus.NETWORK_ERROR
        else -> MiFitnessUiStatus.STORAGE_ERROR
    }

    private fun String?.toMiFitnessUiStatus(): MiFitnessUiStatus = when (this) {
        "credentials_missing", "authentication_failed" -> MiFitnessUiStatus.AUTH_ERROR
        "network_failed", "response_invalid", "record_out_of_window", "record_limit",
        "cursor_missing", "cursor_limit", "cursor_repeated", "page_limit", "aggregation_invalid",
        "sync_failed" -> MiFitnessUiStatus.NETWORK_ERROR
        else -> MiFitnessUiStatus.STORAGE_ERROR
    }

    fun attachImage(uri: android.net.Uri) {
        if (_state.value.streaming || _state.value.importingImage || _state.value.pendingImages.size >= 4) return
        _state.value = _state.value.copy(importingImage = true, error = null, errorCode = null)
        viewModelScope.launch {
            runCatching { imageProcessor.import(uri) }.fold(
                onSuccess = { image -> _state.value = _state.value.copy(pendingImages = _state.value.pendingImages + image, importingImage = false) },
                onFailure = { error -> _state.value = _state.value.copy(importingImage = false, error = error.message ?: "图片导入失败", errorCode = "image_import_failed") },
            )
        }
    }

    fun removeImage(index: Int) {
        if (_state.value.streaming) return
        val image = _state.value.pendingImages.getOrNull(index) ?: return
        imageProcessor.delete(image)
        _state.value = _state.value.copy(pendingImages = _state.value.pendingImages.toMutableList().also { it.removeAt(index) })
    }

    fun performSurfaceAction(actionId: String) {
        if (_state.value.streaming) return
        if (actionId.startsWith("memory.confirm:")) {
            val id = actionId.removePrefix("memory.confirm:")
            viewModelScope.launch {
                val confirmed = memoryStore.confirm(id)
                _state.value = _state.value.copy(
                    error = if (confirmed) null else "这条记忆已过期或不存在。",
                    errorCode = if (confirmed) null else "memory_confirmation_failed",
                )
            }
            return
        }
        if (!actionId.startsWith("confirm:")) return
        _state.value = _state.value.copy(streaming = true, stage = "正在执行已确认操作", error = null, errorCode = null)
        launchGeneration { token ->
            try {
                caesarEngine.confirm(actionId).collect(::consumeEvent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generationEpoch.isCurrent(token)) {
                    _state.value = _state.value.copy(streaming = false, stage = "", error = error.message ?: "确认操作执行失败", errorCode = "confirmation_failed")
                }
            }
            if (generationEpoch.isCurrent(token)) saveReport()
        }
    }

    fun confirmMemory(id: String) {
        viewModelScope.launch {
            val confirmed = memoryStore.confirm(id)
            if (!confirmed) _state.value = _state.value.copy(error = "这条记忆已过期或不存在。", errorCode = "memory_confirmation_failed")
        }
    }

    fun updateMemory(id: String, content: String) {
        viewModelScope.launch {
            val result = runCatching { memoryStore.updateContent(id, content) }
            if (result.getOrNull() != true) {
                _state.value = _state.value.copy(
                    error = result.exceptionOrNull()?.message ?: "这条记忆已不存在。",
                    errorCode = "memory_update_failed",
                )
            }
        }
    }

    fun forgetMemory(id: String) {
        viewModelScope.launch { memoryStore.forget(id) }
    }

    fun forgetAllMemories() {
        viewModelScope.launch { memoryStore.forgetAll() }
    }

    fun clearError() {
        _state.value = _state.value.copy(
            error = null,
            errorCode = null,
            canUseCloudOnce = false,
            pendingCloudPrompt = null,
            pendingCloudDisplayPrompt = null,
            pendingContextJson = null,
            pendingPresentationJson = null,
        )
    }

    fun newConversation() {
        if (_state.value.streaming) return
        conversationId = UUID.randomUUID().toString()
        conversationCreatedAt = System.currentTimeMillis()
        conversationTitle = ""
        conversationLocalModelId = null
        _state.value = AiUiState(
            mode = _state.value.mode,
            provider = provider.get(),
            contextSelection = _state.value.contextSelection,
        )
    }

    fun send(prompt: String, snapshot: AiContextSnapshot) {
        activeOwnerUserId = snapshot.userId
        CaesarDeterministicReply.forPrompt(prompt, hasImages = _state.value.pendingImages.isNotEmpty())?.let { reply ->
            appendDeterministicReply(prompt, reply)
            return
        }
        val base = CampusAiTaskFactory.create(CampusAiTask.CHAT, snapshot.records, snapshot.courses, prompt)
        sendInternal(base.withContext(CampusAiTask.CHAT, prompt, snapshot), cloudOnce = false)
    }

    private fun appendDeterministicReply(prompt: String, reply: String) {
        val displayPrompt = prompt.trim()
        if (displayPrompt.isBlank() || _state.value.streaming) return
        greetingJob?.cancel()
        greetingJob = null
        val existing = _state.value.messages.dropLastWhile { it.role == "assistant" && it.content.isBlank() }
        if (conversationTitle.isBlank()) conversationTitle = displayPrompt.take(42)
        _state.value = _state.value.copy(
            messages = existing +
                AiConversationMessage("user", displayPrompt) +
                AiConversationMessage("assistant", reply),
            streaming = false,
            stage = "",
            model = "Caesar∞ · 本机事实",
            resolvedProvider = AiProvider.LOCAL,
            error = null,
            errorCode = null,
            canUseCloudOnce = false,
            pendingCloudPrompt = null,
            pendingCloudDisplayPrompt = null,
            pendingContextJson = null,
            pendingPresentationJson = null,
            elapsedMs = 0,
        )
        viewModelScope.launch { saveReport() }
    }

    fun sendTask(task: CampusAiTask, snapshot: AiContextSnapshot, userInput: String = "") {
        activeOwnerUserId = snapshot.userId
        val base = CampusAiTaskFactory.create(task, snapshot.records, snapshot.courses, userInput)
        sendInternal(base.withContext(task, userInput, snapshot), cloudOnce = false)
    }

    private fun CampusAiPayload.withContext(
        task: CampusAiTask,
        prompt: String,
        snapshot: AiContextSnapshot,
    ) = copy(
        structuredContext = AiContextAssembler.enrich(
            base = structuredContext,
            task = task,
            prompt = prompt,
            snapshot = snapshot,
            selection = _state.value.contextSelection,
        ),
    )

    fun useCloudOnce() {
        val prompt = _state.value.pendingCloudPrompt ?: return
        val displayPrompt = _state.value.pendingCloudDisplayPrompt ?: prompt
        val context = JSONObject(_state.value.pendingContextJson ?: "{}")
        val presentation = AiTaskPresentation.fromJson(_state.value.pendingPresentationJson)
        sendInternal(CampusAiPayload(prompt, context, displayPrompt, presentation), cloudOnce = true)
    }

    fun openConversation(report: AiReport) {
        if (_state.value.streaming) return
        val messages = parseMessages(report.messagesJson)
        conversationId = report.id
        conversationCreatedAt = report.createdAt
        conversationTitle = report.title
        conversationLocalModelId = modelManager.modelIdFromLabel(report.model)
        provider.set(report.provider)
        _state.value = _state.value.copy(
            mode = report.mode,
            provider = report.provider,
            resolvedProvider = report.provider,
            model = report.model,
            messages = messages,
            error = null,
            errorCode = null,
            elapsedMs = 0,
            lockedLocalModelId = conversationLocalModelId,
        )
        viewModelScope.launch { preferences.setAiProvider(report.provider) }
    }

    fun deleteConversation(report: AiReport) {
        if (_state.value.streaming) return
        recentlyDeleted = report
        viewModelScope.launch {
            dao.deleteAiReport(report.id)
            if (report.id == conversationId) newConversation()
        }
    }

    fun undoDeleteConversation() {
        val report = recentlyDeleted ?: return
        recentlyDeleted = null
        viewModelScope.launch { dao.insertAiReport(AiReportEntity.fromDomain(report)) }
    }

    fun ensureDailyGreeting(snapshot: AiContextSnapshot) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val cacheId = "${snapshot.userId.ifBlank { "local" }}:$date"
        val dateKey = date.toString()
        val current = _dailyGreeting.value
        if (current?.localDate == date.toString() && DailyGreetingPolicy.isGrounded(current.text, snapshot, zone = zone)) return
        if (greetingAttemptedDate == dateKey) return
        if (greetingJob?.isActive == true) return
        greetingAttemptedDate = dateKey
        greetingJob = viewModelScope.launch {
            dao.getDailyGreeting(cacheId)?.toDomain()?.let { cached ->
                val isOldFallback = cached.text == DailyGreetingPolicy.fallback(snapshot.displayName, date)
                if (!isOldFallback && DailyGreetingPolicy.isGrounded(cached.text, snapshot, zone = zone)) {
                    _dailyGreeting.value = cached
                    greetingJob = null
                    return@launch
                }
            }
            val fallback = DailyGreetingPolicy.fallback(snapshot.displayName, date)
            _dailyGreeting.value = DailyGreeting(date.toString(), fallback, AiProvider.LOCAL, System.currentTimeMillis())
            if (_state.value.streaming) {
                greetingJob = null
                return@launch
            }

            val context = AiContextAssembler.greetingContext(snapshot)
            val request = AiRequest(
                mode = AiMode.FAST,
                messages = listOf(
                    AiConversationMessage(
                        "user",
                        "随机创作一句 12 至 20 个汉字的首页短副文案。用行动感或克制意象，不写问句；不要写具体钟点、天气、校园状态，缺少课程就绝不提上课或课程。风格示例仅供理解：把注意力交给眼前这一件事。不要照抄示例。灵感编号：${UUID.randomUUID().toString().take(8)}",
                    ),
                ),
                structuredContextJson = context.toString(),
                maxOutputTokens = 48,
            )
            val answer = StringBuilder()
            var resolved = AiProvider.LOCAL
            var failed = false
            runCatching {
                router.stream(request).collect { event ->
                    when (event) {
                        is AiEvent.Meta -> resolved = event.provider
                        is AiEvent.Delta -> answer.append(event.text)
                        is AiEvent.Error -> failed = true
                        else -> Unit
                    }
                }
            }.onFailure { failed = true }
            val candidate = if (failed) "" else sanitizeGreeting(answer.toString())
            val text = candidate.takeIf { it.isNotBlank() && DailyGreetingPolicy.isGrounded(it, snapshot, zone = zone) } ?: fallback
            if (text == fallback) {
                _dailyGreeting.value = DailyGreeting(date.toString(), fallback, AiProvider.LOCAL, System.currentTimeMillis())
            } else {
                persistGreeting(cacheId, date, text, resolved)
            }
            greetingJob = null
        }
    }

    private suspend fun persistGreeting(id: String, date: LocalDate, text: String, source: AiProvider) {
        val generatedAt = System.currentTimeMillis()
        dao.insertDailyGreeting(DailyGreetingEntity(id, date.toString(), text, source.name, generatedAt))
        _dailyGreeting.value = DailyGreeting(date.toString(), text, source, generatedAt)
    }

    private fun sanitizeGreeting(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("[\r\n]+"), " ")
        .replace(Regex("^[\\s\"“”'‘’]+|[\\s\"“”'‘’]+$"), "")
        .substringBefore("。")
        .trim()
        .take(24)

    fun cancel() {
        val activeJob = generation
        generation = null
        generationEpoch.invalidate()
        caesarEngine.cancel()
        router.cancel()
        personalDeepSeekEngine.cancel()
        localEngine.cancel()
        activeJob?.cancel()
        _state.value = _state.value.copy(streaming = false, stage = "", error = "已停止本次生成。", errorCode = "cancelled")
    }

    private fun sendInternal(payload: CampusAiPayload, cloudOnce: Boolean) {
        val prompt = payload.prompt
        val displayPrompt = payload.displayPrompt.ifBlank { prompt }
        if (prompt.isBlank() || _state.value.streaming) return
        greetingJob?.cancel()
        greetingJob = null
        router.cancel()
        if (cloudOnce && !network.isOnline()) {
            _state.value = _state.value.copy(error = "当前没有可用网络，无法在本次切换到 DeepSeek。", errorCode = "deepseek_offline")
            return
        }
        val existing = if (cloudOnce) {
            val withoutPlaceholder = _state.value.messages.dropLastWhile { it.role == "assistant" && it.content.isBlank() }
            if (withoutPlaceholder.lastOrNull()?.role == "user") withoutPlaceholder.dropLast(1) else withoutPlaceholder
        } else {
            _state.value.messages
        }
        val attachments = if (cloudOnce) emptyList() else _state.value.pendingImages
        val localModelId = conversationLocalModelId
            ?: modelManager.selection.value.manifest.id.also { conversationLocalModelId = it }
        if (conversationTitle.isBlank()) conversationTitle = displayPrompt.trim().take(42)
        val initial = existing +
            AiConversationMessage("user", displayPrompt.trim(), attachmentPaths = attachments.map(CaesarImageAttachment::localPath)) +
            AiConversationMessage("assistant", "", payload.presentation?.toJson())
        _state.value = _state.value.copy(
            messages = initial,
            streaming = true,
            stage = "思考中",
            error = null,
            errorCode = null,
            canUseCloudOnce = false,
            pendingCloudPrompt = null,
            pendingCloudDisplayPrompt = null,
            pendingContextJson = null,
            pendingPresentationJson = null,
            elapsedMs = 0,
            pendingImages = emptyList(),
            importingImage = false,
            lockedLocalModelId = localModelId,
        )
        val request = assembleCaesarTurnRequest(
            mode = _state.value.mode,
            existingMessages = existing,
            prompt = prompt,
            displayPrompt = displayPrompt,
            structuredContext = payload.structuredContext,
            attachments = attachments,
            sessionId = conversationId,
            ownerUserId = activeOwnerUserId,
            localModelId = localModelId,
            cloudOnce = cloudOnce,
        )
        launchGeneration { token ->
            val requestWithMemory = if (cloudOnce) request else request.copy(
                structuredContextJson = JSONObject(request.structuredContextJson)
                    .put("confirmedMemories", JSONArray(memoryStore.context().map { memory ->
                        JSONObject().put("id", memory.id).put("type", memory.type).put("content", memory.content).put("confidence", memory.confidence)
                    }))
                    .toString(),
            )
            try {
                if (cloudOnce) router.streamCloudOnce(requestWithMemory).collect(::consumeEvent)
                else caesarEngine.stream(requestWithMemory).collect(::consumeEvent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generationEpoch.isCurrent(token)) {
                    val routing = error as? AiRoutingException
                    _state.value = _state.value.copy(
                        streaming = false,
                        stage = "",
                        error = generationFailureMessage(error),
                        errorCode = routing?.code ?: "generation_failed",
                        canUseCloudOnce = routing?.canUseCloudOnce == true,
                        pendingCloudPrompt = prompt.takeIf { routing?.canUseCloudOnce == true },
                        pendingCloudDisplayPrompt = displayPrompt.takeIf { routing?.canUseCloudOnce == true },
                        pendingContextJson = request.structuredContextJson.takeIf { routing?.canUseCloudOnce == true },
                        pendingPresentationJson = payload.presentation?.toJson().takeIf { routing?.canUseCloudOnce == true },
                    )
                }
            }
            if (generationEpoch.isCurrent(token)) saveReport()
        }
    }

    private fun launchGeneration(block: suspend (token: Long) -> Unit) {
        val token = generationEpoch.begin()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block(token)
            } finally {
                if (generationEpoch.isCurrent(token)) generation = null
            }
        }
        generation = job
        job.start()
    }

    private fun consumeEvent(event: AiEvent) {
        when (event) {
            is AiEvent.Meta -> _state.value = _state.value.copy(model = event.model, resolvedProvider = event.provider)
            is AiEvent.Status -> _state.value = _state.value.copy(stage = event.stage, elapsedMs = event.elapsedMs)
            is AiEvent.Delta -> {
                if (event.text.isEmpty()) return
                _state.value = _state.value.copy(messages = _state.value.messages.toMutableList().also { list ->
                    val last = list.last()
                    list[list.lastIndex] = last.copy(content = last.content + event.text)
                })
            }
            is AiEvent.Done -> _state.value = _state.value.copy(streaming = false, stage = "", elapsedMs = event.elapsedMs)
            is AiEvent.Error -> _state.value = _state.value.copy(streaming = false, error = event.message, errorCode = event.code, stage = "")
            is AiEvent.ToolCallRequested -> Unit
            is AiEvent.ToolStarted -> _state.value = _state.value.copy(stage = "正在执行 ${event.name}")
            is AiEvent.ToolFinished -> _state.value = _state.value.copy(stage = if (event.success) "正在整理结果" else "工具执行未完成")
            is AiEvent.Surface -> _state.value = _state.value.copy(messages = _state.value.messages.toMutableList().also { list ->
                val last = list.lastOrNull() ?: return@also
                list[list.lastIndex] = last.copy(presentationJson = event.json)
            })
            is AiEvent.MemoryProposal -> Unit
        }
    }

    private suspend fun saveReport() {
        val final = _state.value
        val summary = final.messages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        if (summary.isBlank()) return
        val now = System.currentTimeMillis()
        val report = AiReport(
            id = conversationId,
            provider = final.resolvedProvider ?: final.provider,
            mode = final.mode,
            model = final.model,
            title = conversationTitle.ifBlank { "新的对话" },
            summary = summary.take(160),
            messagesJson = AiConversationCodec.encode(final.messages),
            createdAt = conversationCreatedAt,
            updatedAt = now,
        )
        dao.insertAiReport(AiReportEntity.fromDomain(report))
    }

    private fun parseMessages(raw: String): List<AiConversationMessage> = AiConversationCodec.decode(raw)

    override fun onCleared() {
        detachMiFitnessWorkObserver()
        greetingJob?.cancel()
        generationEpoch.invalidate()
        router.cancel()
        super.onCleared()
    }
}

internal fun generationFailureMessage(error: Throwable): String =
    (error as? AiRoutingException)?.message ?: "生成中断，请重试。"

internal class AiGenerationEpoch {
    private val value = AtomicLong(0)

    fun begin(): Long = value.incrementAndGet()

    fun invalidate() {
        value.incrementAndGet()
    }

    fun isCurrent(token: Long): Boolean = value.get() == token
}

class AiViewModelFactory(
    private val dao: CampusDao,
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val modelManager: LocalModelManager,
    private val localEngine: AiEngine,
    private val personalKeyStore: PersonalDeepSeekKeyStore,
    private val campusRepository: CampusRepository = CampusRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AiViewModel(dao, context, preferences, modelManager, localEngine, personalKeyStore, campusRepository, profileRepository) as T
}
