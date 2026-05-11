package com.habitflow.app.data.repository

import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.HabitLogEntity
import com.habitflow.app.data.local.entity.toHabit
import com.habitflow.app.data.local.entity.toEntity
import com.habitflow.app.data.remote.FirestoreHabitDataSource
import com.habitflow.app.data.remote.FirestoreLogDataSource
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.domain.repository.AuthRepository
import com.habitflow.app.domain.repository.HabitRepository
import com.habitflow.app.domain.util.StreakCalculator
import com.habitflow.app.data.local.entity.HabitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Day 5 — Repository refactor: local-first + dirty flag + user isolation.
 *
 * This class replaces [RoomHabitRepository] as the production implementation
 * of [HabitRepository]. Three key upgrades over the old implementation:
 *
 * 1. **User isolation** — reads (habits, logs) are filtered to the signed-in
 *    user's `user_id`. Users on the same device no longer see each other's data.
 *
 * 2. **user_id stamping** — every new habit and log is written with the
 *    current user's uid so it can be synced to the correct Firestore path.
 *
 * 3. **Local-first writes** — every mutation commits to Room first
 *    (so the UI responds instantly even offline), marks `is_synced = false`,
 *    then immediately attempts a direct Firestore push in the background.
 *    If the push fails (no network), the row stays dirty and the Day 6
 *    WorkManager worker will retry it later.
 *
 * All database and network work runs on [Dispatchers.IO] — the UI thread
 * is never blocked (NF-10).
 */
class HabitRepositoryImpl @Inject constructor(
    private val dao: HabitDao,
    private val authRepository: AuthRepository,
    private val habitDataSource: FirestoreHabitDataSource,
    private val logDataSource: FirestoreLogDataSource,
) : HabitRepository {

    // Background scope: lives as long as the app process, like AuthRepositoryImpl.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Exposed StateFlows ──────────────────────────────────────────────────

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    override val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    private val _completedTodayIds = MutableStateFlow<Set<Long>>(emptySet())
    override val completedTodayIds: StateFlow<Set<Long>> = _completedTodayIds.asStateFlow()

    private val _completedDates = MutableStateFlow<Map<Long, List<LocalDate>>>(emptyMap())
    override val completedDates: StateFlow<Map<Long, List<LocalDate>>> = _completedDates.asStateFlow()

    init {
        // Whenever the signed-in user changes, re-subscribe to THAT user's data.
        // This reacts to sign-in, sign-out, and cold starts.
        scope.launch {
            authRepository.authState.collect { authState ->
                val uid = authRepository.currentUserId
                if (uid != null) {
                    observeUserHabits(uid)
                } else {
                    // Signed out: clear the displayed habit list immediately.
                    _habits.value = emptyList()
                    _completedTodayIds.value = emptySet()
                    _completedDates.value = emptyMap()
                }
            }
        }
    }

    /**
     * Starts collecting Room's user-scoped habit + log streams and pushes
     * merged, streak-enriched domain models into the StateFlows.
     * Launched in the init block whenever the user changes.
     */
    private fun observeUserHabits(uid: String) {
        scope.launch {
            combine(
                dao.getAllHabitsByUser(uid),
                dao.getAllLogsByUser(uid),
            ) { entities, logs ->

                val logsByHabit = logs.groupBy { it.habitId }
                val today = LocalDate.now().toEpochDay()

                val todayIds = logs
                    .filter { it.dateCompleted == today }
                    .map { it.habitId }
                    .toSet()
                _completedTodayIds.value = todayIds

                val datesMap = logsByHabit.mapValues { (_, habitLogs) ->
                    habitLogs.map { LocalDate.ofEpochDay(it.dateCompleted) }
                        .distinct()
                        .sortedDescending()
                }
                _completedDates.value = datesMap

                entities.map { entity ->
                    val habitDates = datesMap[entity.id] ?: emptyList()
                    val freq = try {
                        HabitFrequency.valueOf(entity.frequency)
                    } catch (_: Exception) { HabitFrequency.DAILY }
                    val streaks = StreakCalculator.calculate(habitDates, freq)
                    entity.toHabit(
                        currentStreak = streaks.currentStreak,
                        longestStreak = streaks.longestStreak,
                        totalCompletions = habitDates.size,
                    )
                }
            }.collect { _habits.value = it }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the current user's uid, or null if signed out. */
    private val currentUid: String? get() = authRepository.currentUserId

    /**
     * After each local Room write, tries to push the dirty row to Firestore
     * immediately. If it fails (offline / network error), the row stays
     * dirty with `is_synced = false` and the Day 6 WorkManager will retry.
     */
    private fun syncHabitNow(entity: HabitEntity) {
        val uid = currentUid ?: return          // not signed in: nothing to push
        scope.launch {
            try {
                habitDataSource.push(uid, entity)
                dao.markHabitSynced(entity.uuid)
            } catch (_: Exception) {
                // Sync failed silently — dirty flag remains; Day 6 worker retries.
            }
        }
    }

    // ─── Mutations ───────────────────────────────────────────────────────────

    /**
     * Saves a new habit to Room stamped with the current [user_id], then
     * pushes to Firestore in the background (OFF-2).
     */
    override suspend fun addHabit(habit: Habit): Long {
        val uid = currentUid
        val entity = habit.toEntity(userId = uid, isSynced = false)
        val id = dao.upsertHabit(entity)
        // Push with the auto-assigned id so the entity is complete
        syncHabitNow(entity.copy(id = id))
        return id
    }

    /**
     * Updates an existing habit in Room (preserving uuid and user_id),
     * marks it dirty, then syncs to Firestore in the background.
     */
    override suspend fun updateHabit(habit: Habit) {
        val existing = dao.getHabitById(habit.id)
        val entity = if (existing != null) {
            existing.copy(
                title       = habit.title,
                description = habit.description,
                frequency   = habit.frequency.name,
                startDate   = habit.startDate.toEpochDay(),
                isEnabled   = habit.isEnabled,
                color       = habit.color,
                updatedAt   = System.currentTimeMillis(),
                isSynced    = false,
            )
        } else {
            habit.toEntity(userId = currentUid, isSynced = false)
        }
        dao.upsertHabit(entity)
        syncHabitNow(entity)
    }

    /**
     * Soft-deletes a habit (tombstone) in Room, then pushes the tombstone
     * to Firestore so other devices learn about the deletion (OFF-8).
     */
    override suspend fun deleteHabit(habitId: Long) {
        val uid = currentUid
        val timestamp = System.currentTimeMillis()
        dao.deleteHabit(habitId, timestamp)

        // Push tombstone to Firestore
        if (uid != null) {
            val entity = dao.getHabitById(habitId)
                ?: dao.getHabitByIdIncludeDeleted(habitId)
            if (entity != null) {
                scope.launch {
                    try { habitDataSource.pushTombstone(uid, entity.uuid) }
                    catch (_: Exception) { /* retried by Day 6 worker */ }
                }
            }
        }
    }

    /** Pauses or reactivates a habit locally, then syncs. */
    override suspend fun setEnabled(habitId: Long, enabled: Boolean) {
        val timestamp = System.currentTimeMillis()
        dao.setEnabled(habitId, enabled, timestamp)
        dao.getHabitById(habitId)?.let { syncHabitNow(it) }
    }

    /**
     * Toggles today's completion. The log is written with [user_id] so it
     * can be synced to the correct Firestore path. Then syncs the log.
     */
    override suspend fun toggleTodayCompletion(habitId: Long): Boolean {
        val today = LocalDate.now().toEpochDay()
        val timestamp = System.currentTimeMillis()
        val uid = currentUid

        val isNowDone = dao.toggleLog(habitId, today, timestamp)

        // After toggle, push the log to Firestore
        if (uid != null) {
            scope.launch {
                try {
                    val log = dao.getLogForDate(habitId, today)
                    val habit = dao.getHabitById(habitId)
                    if (log != null && habit != null) {
                        // Ensure log has user_id stamped (it may be missing on old rows)
                        val stampedLog = if (log.userId == null) {
                            val updated = log.copy(userId = uid)
                            dao.upsertLog(updated)
                            updated
                        } else log
                        logDataSource.push(uid, stampedLog, habit.uuid)
                        dao.markLogSynced(stampedLog.uuid)
                    }
                } catch (_: Exception) { /* retried by Day 6 worker */ }
            }
        }
        return isNowDone
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    override suspend fun getHabitById(id: Long): Habit? {
        val entity = dao.getHabitById(id) ?: return null
        val dates = _completedDates.value[id] ?: emptyList()
        val freq = try { HabitFrequency.valueOf(entity.frequency) }
                   catch (_: Exception) { HabitFrequency.DAILY }
        val streaks = StreakCalculator.calculate(dates, freq)
        return entity.toHabit(
            currentStreak    = streaks.currentStreak,
            longestStreak    = streaks.longestStreak,
            totalCompletions = dates.size,
        )
    }

    override suspend fun getCompletedDates(habitId: Long): List<LocalDate> =
        _completedDates.value[habitId] ?: emptyList()

    override suspend fun getWeeklyProgress(habitId: Long): List<Boolean> {
        val today = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val dates = (_completedDates.value[habitId] ?: emptyList()).toSet()
        return (0..6).map { weekStart.plusDays(it.toLong()) in dates }
    }
}
