# MCO 2 — Backend Implementation Plan

**Branch:** `claude/habit-flow-backend-BZY2u`
**Phase 1 starts:** May 1, 2026 (Day 1)
**Target finish:** May 14, 2026 (Day 14) — assumed deadline, best-effort
**Working agreement:** see [`AGREEMENT.md`](./AGREEMENT.md)
**Requirements satisfied:** see [`MCO2_REQUIREMENTS.md`](./MCO2_REQUIREMENTS.md)

This plan is the day-by-day sequence we will follow. Every day's deliverable
respects the working agreement: source-first, then explanation, then manual
Android Studio steps, then Claude applies the change after the developer
confirms.

---

## 0. Pre-flight (before Day 1)

| Item | Owner | Status |
|---|---|---|
| Rename top-level Gradle project folder `HabitFlow-UI/` → `HabitFlow/` | Developer (manual in Android Studio: close project, rename folder, reopen, sync) | Pending |
| Move `app/src/main/.../utils/FakeDataSource.kt` → `app/src/test/.../fixtures/FakeDataSource.kt`, mark as test fixture | Claude (with developer review) | Pending |
| Create a Firebase project ("HabitFlow") in the Firebase console | Developer | Pending |
| Add Android app in Firebase: package `com.habitflow.app`, debug SHA-1 from `./gradlew signingReport` | Developer | Pending |
| Download `google-services.json` to `app/` | Developer | Pending |
| Enable **Email/Password** sign-in in Firebase Auth console | Developer | Pending |
| Enable **Cloud Firestore** in production mode (we'll write strict rules on Day 4) | Developer | Pending |

Claude will produce a checklist with screenshots/CLI equivalents for each
Firebase console step on Day 1 morning.

---

## 1. Architecture target (end-state)

```
presentation/
  ├─ ui/auth/           ← SignInScreen, SignUpScreen (NEW)
  ├─ ui/screens/...     ← unchanged screens, plus reminder time picker on form
  └─ viewmodel/         ← @HiltViewModel + @Inject (refactored)

domain/
  ├─ model/             ← Habit, HabitLog, UserProfile, AuthState (NEW)
  ├─ repository/        ← HabitRepository, AuthRepository (NEW), UserRepository (NEW)
  └─ usecase/           ← optional thin use-cases for sync trigger, sign-in flow

data/
  ├─ local/
  │   ├─ HabitDatabase.kt           ← v3, real Migration(2,3)
  │   ├─ dao/HabitDao.kt            ← + sync queries (dirty rows, tombstones)
  │   ├─ dao/UserDao.kt             ← NEW
  │   └─ entity/                    ← entities + new sync columns
  ├─ remote/
  │   ├─ FirebaseAuthDataSource.kt  ← NEW (sign-in, sign-up, sign-out, observe state)
  │   ├─ FirestoreHabitDataSource.kt
  │   └─ FirestoreLogDataSource.kt
  ├─ repository/
  │   ├─ HabitRepositoryImpl.kt     ← coordinator: local-first writes, dirty flag, merge
  │   ├─ AuthRepositoryImpl.kt
  │   └─ UserRepositoryImpl.kt
  ├─ sync/
  │   ├─ SyncManager.kt             ← schedules + triggers HabitSyncWorker
  │   └─ HabitSyncWorker.kt         ← @HiltWorker, push-then-pull, last-write-wins
  ├─ notification/
  │   ├─ ReminderScheduler.kt       ← AlarmManager wrapper
  │   ├─ ReminderReceiver.kt        ← BroadcastReceiver, posts notification
  │   ├─ BootReceiver.kt            ← reschedules on BOOT_COMPLETED
  │   └─ NotificationHelper.kt      ← channel setup, post()
  └─ preferences/
      └─ ThemePreferences.kt        ← migrated to DataStore (Day 12)

di/
  ├─ DatabaseModule.kt
  ├─ NetworkModule.kt        ← FirebaseAuth, FirebaseFirestore providers
  ├─ RepositoryModule.kt
  ├─ DataSourceModule.kt
  ├─ NotificationModule.kt
  └─ WorkerModule.kt         ← HiltWorkerFactory wiring
```

---

## 2. Day-by-day plan

Each row lists the **deliverable**, the **files touched**, and the **demo
criterion** the developer can run to confirm Day N is done.

### Week 1 — Foundations

#### Day 1 (May 1) — Hilt foundation
- **Deliverable:** the app boots on Hilt with the same MCO 1 behavior; `HabitFlowViewModelFactory` is deleted.
- **Files touched:** `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`, `HabitFlowApplication.kt` (now `@HiltAndroidApp`), all 5 ViewModels (now `@HiltViewModel`), all Compose screen entry points (use `hiltViewModel()`), new `di/DatabaseModule.kt`, `di/RepositoryModule.kt`. Delete `ViewModelFactory.kt`.
- **Demo:** create / log / delete a habit; everything works exactly as before.
- **Risk:** KSP + Hilt version mismatch — pin Hilt to a version compatible with Kotlin 2.1.21.

#### Day 2 (May 2) — Schema v3 + real migration + sync metadata
- **Deliverable:** Room v3 with `uuid`, `user_id`, `updated_at`, `is_deleted`, `is_synced` on both entities; `reminder_time`, `reminder_enabled` on habits; new `user_profile` table; `Migration(2,3)` preserves existing user data; `fallbackToDestructiveMigration()` removed.
- **Files touched:** `HabitEntity.kt`, `HabitLogEntity.kt`, new `UserProfileEntity.kt`, `HabitDatabase.kt`, `HabitDao.kt` (+ `UserDao.kt`), `RoomHabitRepository.kt` (mappers updated). Schema JSON committed under `app/schemas/`.
- **Demo:** install MCO 1 build → upgrade to MCO 2 build → existing habits and logs are still there; new columns visible in the Room schema JSON.

#### Day 3 (May 3) — Auth: sign-up, sign-in, sign-out
- **Deliverable:** Firebase Auth wired in; sign-up creates a Firestore profile doc + reserves the username in `usernames/{usernameLower}`; sign-in works; sign-out clears local cache.
- **Files touched:** `FirebaseAuthDataSource.kt`, `AuthRepository(Impl).kt`, `UserRepository(Impl).kt`, `AuthViewModel.kt`, `SignInScreen.kt`, `SignUpScreen.kt`, `NavGraph.kt` (auth gate), `NetworkModule.kt`.
- **UI exception:** two new screens are unavoidable. They are **functional, not designed** — Material 3 defaults only.
- **Demo:** sign up with email + username + password; close app; reopen → still signed in. Sign out → land on sign-in screen.

#### Day 4 (May 4) — Firestore data sources + security rules
- **Deliverable:** `FirestoreHabitDataSource` and `FirestoreLogDataSource` implement push (upsert with server `updatedAt`), pull (snapshot listener for the user's collection), and delete (tombstone write). Firestore security rules deployed.
- **Files touched:** `FirestoreHabitDataSource.kt`, `FirestoreLogDataSource.kt`, `firestore.rules` (committed under `firebase/`), `DataSourceModule.kt`.
- **Demo:** unit test (or a debug button) writes a habit straight to Firestore; another device signed in as the same user receives it via the listener.

#### Day 5 (May 5) — Repository refactor (local-first + dirty flag)
- **Deliverable:** `HabitRepositoryImpl` replaces `RoomHabitRepository`. Writes commit to Room with `is_synced = false` and `updated_at = now`, then enqueue a one-time sync. Reads are unchanged from MCO 1's perspective. `RoomHabitRepository` is renamed/removed.
- **Files touched:** `HabitRepositoryImpl.kt` (new), `HabitRepository.kt` (interface unchanged externally; internal additions for sync state), `RepositoryModule.kt`.
- **Demo:** offline → create a habit → habit appears in UI immediately, `is_synced` is false in the DB; turn airplane mode off → still no sync yet (worker not built); cloud write happens via the temporary "Sync now" debug button.

#### Day 6 (May 6) — WorkManager sync worker
- **Deliverable:** `HabitSyncWorker` (`@HiltWorker`) implements push-then-pull with last-write-wins. Triggered: (a) one-time after each local write, (b) periodically every 15 min when network is connected, (c) one-time on connectivity restored.
- **Files touched:** `HabitSyncWorker.kt`, `SyncManager.kt`, `WorkerModule.kt`, `HabitFlowApplication.kt` (`Configuration.Provider` for HiltWorkerFactory), manifest (`<provider>` removed for default WorkManager init).
- **Demo:** create a habit offline → enable Wi-Fi → within ~30s the habit shows up in Firestore console; create on Device B → within ~15 min appears on Device A.

#### Day 7 (May 7) — Multi-device consistency + buffer
- **Deliverable:** edit-on-A / edit-on-B / delete-on-A scenarios all converge correctly. Tombstones propagate. Tie-breaking by `uuid` lexicographic order. Manual "Sync now" in Settings calls `SyncManager.requestImmediate()`.
- **Files touched:** `HabitSyncWorker.kt` (conflict logic), `SettingsScreen.kt` (one button + last-sync timestamp text), `SettingsViewModel.kt`.
- **Demo:** the four scenarios pass on two devices.
- **Buffer:** any week-1 spillover lands here.

### Week 2 — Reminders & polish

#### Day 8 (May 8) — Notifications scaffolding
- **Deliverable:** notification channel created on app start; `POST_NOTIFICATIONS` runtime permission asked once; a debug "fire test notification" works.
- **Files touched:** `NotificationHelper.kt`, manifest (permissions), `MainActivity.kt` (permission request), `NotificationModule.kt`.
- **Demo:** test notification fires, opens the app when tapped.

#### Day 9 (May 9) — Per-habit reminder scheduling
- **Deliverable:** `ReminderScheduler` schedules a daily `AlarmManager` alarm per habit at its `reminder_time`; `ReminderReceiver` posts the notification; `BootReceiver` reschedules everything after reboot. Disabling a habit cancels its alarm.
- **Files touched:** `ReminderScheduler.kt`, `ReminderReceiver.kt`, `BootReceiver.kt`, manifest (receivers + `RECEIVE_BOOT_COMPLETED`), `HabitRepositoryImpl.kt` (calls scheduler on add/update/disable/delete).
- **Demo:** set a habit reminder for "now + 1 minute"; lock device; wait → notification fires; reboot device; wait → still fires the next day.

#### Day 10 (May 10) — Missed-habit notifications *(cuttable)*
- **Deliverable:** end-of-day periodic worker checks for daily habits with no log today and notifies once. Disabled or non-daily habits are skipped.
- **Files touched:** `MissedHabitWorker.kt`, `SyncManager.kt` (schedules it).
- **Demo:** don't log a daily habit; wait until 21:00 (configurable) → "You missed: {habit}" notification.
- **Cut criterion:** if Day 9 spilled over, drop this day. Requirement NOTIF-6 is the lowest-priority.

#### Day 11 (May 11) — Reminder UI on habit form
- **Deliverable:** `HabitFormScreen` gains a "Reminder time" row (toggle + time picker). Editing existing habits respects the existing reminder.
- **Files touched:** `HabitFormScreen.kt`, `HabitFormViewModel.kt`.
- **Demo:** create a habit with a reminder via the UI (no debug shortcuts); reminder fires at the chosen time.

#### Day 12 (May 12) — DataStore migration + ProGuard + INTERNET check
- **Deliverable:** `ThemePreferences` migrated from `SharedPreferences` to Preferences DataStore (with `SharedPreferencesMigration` so existing users keep their dark-mode setting). `proguard-rules.pro` adds keep rules for Firebase models, kotlinx-serialization (if used), and Hilt-generated classes. `<uses-permission android:name="android.permission.INTERNET" />` confirmed.
- **Files touched:** `ThemePreferences.kt`, `proguard-rules.pro`, manifest, `SettingsViewModel.kt`.
- **Demo:** `./gradlew assembleRelease` produces an APK that signs in and syncs without R8 stripping anything.

#### Day 13 (May 13) — Edge cases & tests
- **Deliverable:**
  - Sign-out wipes Room (`DELETE FROM habits/habit_logs/user_profile`) so a different user signing in on the same device doesn't see leftover data.
  - Sync error path: 3 retries with exponential backoff, then fail visibly (banner in Settings showing "Last sync failed: {reason}").
  - No-network UX: small banner/icon in the home top bar when offline; clears once connectivity returns.
  - Unit tests: `StreakCalculatorTest` (kept), new tests for the conflict-resolution function (pure Kotlin, fed with fixture data).
- **Files touched:** `AuthRepositoryImpl.kt`, `HabitSyncWorker.kt`, `HomeScreen.kt` (offline indicator only), `ConflictResolverTest.kt`.

#### Day 14 (May 14) — Documentation, smoke test, freeze
- **Deliverable:**
  - Update `README.md` with MCO 2 architecture diagram and Firebase setup steps.
  - Add `docs/SYNC.md` describing the dirty-flag + tombstone + last-write-wins protocol with a sequence diagram.
  - End-to-end smoke test on two physical devices (or two emulators with different AVDs):
    1. Sign up on Device A, create 3 habits, log today.
    2. Sign in same account on Device B → all 3 habits + today's log appear.
    3. Edit habit on B, delete habit on A, both offline; reconnect → both converge.
    4. Reboot Device A → reminders still fire.
- **Demo:** the four-step smoke test passes; `./gradlew test` and `./gradlew assembleDebug` are both green; branch is pushed and ready for review.

---

## 3. Sync protocol — concrete contract

For developer reference. This is the algorithm `HabitSyncWorker` implements;
no other code does sync.

```
fun sync(): Result {
  1. user = auth.currentUser ?: return Result.success()       // not signed in: nothing to do

  2. // PUSH dirty local rows
  for (habit in dao.getDirtyHabits(user.uid)) {
    firestore.upsert(habit)                                   // write with serverTimestamp() updatedAt
    dao.markSynced(habit.uuid, serverTimestamp)               // is_synced = true
  }
  for (log in dao.getDirtyLogs(user.uid)) { ...same... }

  3. // PULL remote changes since last successful sync
  remoteHabits = firestore.habitsUpdatedSince(lastPulledAt)
  for (remote in remoteHabits) {
    local = dao.findByUuid(remote.uuid)
    when {
      local == null                          -> dao.insert(remote)
      remote.updatedAt > local.updated_at    -> dao.update(remote)        // remote wins
      remote.updatedAt < local.updated_at    -> /* local wins, will push next cycle */
      remote.updatedAt == local.updated_at   -> if (remote.uuid > local.uuid) dao.update(remote)  // tie-break
    }
    if (remote.isDeleted) dao.applyTombstone(remote.uuid)
  }
  // same loop for logs

  4. lastPulledAt = serverTimestamp
  return Result.success()
}

// Failure: 3 retries with exponential backoff (10s, 30s, 90s), then surface error.
```

---

## 4. Tracking & status

Daily status will be reported in chat at end-of-day in this format:

```
Day N — {title}
✅ shipped: {bullet list}
⚠ blocked / deferred: {bullet list, with reason}
➡ next: Day N+1 — {title}
```

If Day N slips, the developer decides: extend by a day (compressing the
buffer / cutting Day 10), or trim scope from Day N itself.

---

## 5. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Hilt + KSP + Kotlin 2.1.21 version mismatch | Medium | Pin to known-good versions on Day 1; fall back to KAPT if blocked |
| Firestore offline cache fights our Room cache (double-source-of-truth) | Medium | Disable Firestore client persistence (`setPersistenceEnabled(false)`) — Room is the only local cache |
| WorkManager + Hilt initialization order | Low | Use `Configuration.Provider` on Application; remove default WorkManager initializer in manifest |
| `AlarmManager` exact-alarm restrictions on API 31+ | Medium | Use `setInexactRepeating` (acceptable for daily reminders) — no `SCHEDULE_EXACT_ALARM` permission needed |
| Username uniqueness race condition | Low | Use a Firestore transaction that creates `usernames/{usernameLower}` and the user doc atomically |
| ProGuard strips Firebase models in release | High if forgotten | Day 12 explicitly adds keep rules and runs a release build |
| Two-week deadline | High | Day 7 buffer + Day 10 cuttable + clear cut authority with the developer |
