# MCO 2 — Backend Implementation Plan

**Branch:** `claude/habit-flow-backend-BZY2u`
**Phase 1 starts:** May 4, 2026 (Day 1, Mon)
**Target finish:** May 16, 2026 (Day 12, Sat) — Sun May 10 is a rest day; 12 working days total
**Working agreement:** see [`AGREEMENT.md`](./AGREEMENT.md)
**Requirements satisfied:** see [`MCO2_REQUIREMENTS.md`](./MCO2_REQUIREMENTS.md)

> **Schedule revision (May 3, 2026):** the original 14-day plan (May 1–14) was
> compressed to 12 working days (May 4–16, Sun May 10 off) because pre-flight
> consumed the original Day 1–3 window. Two cuts were applied, both already
> flagged as cuttable in the original §5 risk register: the Day 7 buffer was
> absorbed into Day 6 (WorkManager + multi-device merged), and Day 10
> (missed-habit notifications, requirement NOTIF-6) was dropped from scope.
> All other MCO 2 requirements remain in scope.

This plan is the day-by-day sequence we will follow. Every day's deliverable
respects the working agreement: source-first, then explanation, then manual
Android Studio steps, then Claude applies the change after the developer
confirms.

---

## 0. Pre-flight (completed May 3, 2026)

| Item | Owner | Status |
|---|---|---|
| Rename top-level Gradle project folder `HabitFlow-UI/` → `HabitFlow/` | Developer | ✅ Done (PR #2) |
| Move `app/src/main/.../utils/FakeDataSource.kt` → `app/src/test/.../fixtures/FakeHabitRepository.kt`, mark as test fixture | Developer | ✅ Done (PR #3) |
| Create a Firebase project ("HabitFlow") in the Firebase console | Developer | ✅ Done |
| Add Android app in Firebase: package `com.habitflow.app`, debug SHA-1 from `./gradlew signingReport` | Developer | ✅ Done |
| Download `google-services.json` to `app/` (and remove from `.gitignore` since the repo is private) | Developer | ✅ Done (PR #4) |
| Enable **Email/Password** sign-in in Firebase Auth console | Developer | ✅ Done |
| Enable **Cloud Firestore** in production mode (we'll write strict rules on Day 4) | Developer | ✅ Done |
| Move project out of OneDrive to a non-synced path (`C:\habitflow`) — bonus, resolved Git lock issues | Developer | ✅ Done |

**Recorded environment:**

- **Firebase project ID:** `habitflow-92bfb`
- **Firebase project URL:** https://console.firebase.google.com/u/0/project/habitflow-92bfb/overview
- **Firestore region:** `nam5` (US multi-region — adds ~200–300 ms latency from SEA; locked at creation, accepted trade-off)
- **Local project path:** `C:\habitflow\HabitFlow\`
- **`google-services.json`:** committed at `HabitFlow/app/google-services.json`

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
      └─ ThemePreferences.kt        ← migrated to DataStore (Day 10)

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

### Week 1 — Foundations (Mon May 4 → Sat May 9)

#### Day 1 (Mon, May 4) — Hilt foundation
- **Deliverable:** the app boots on Hilt with the same MCO 1 behavior; `HabitFlowViewModelFactory` is deleted.
- **Files touched:** `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`, `HabitFlowApplication.kt` (now `@HiltAndroidApp`), all 5 ViewModels (now `@HiltViewModel`), all Compose screen entry points (use `hiltViewModel()`), new `di/DatabaseModule.kt`, `di/RepositoryModule.kt`. Delete `ViewModelFactory.kt`.
- **Demo:** create / log / delete a habit; everything works exactly as before.
- **Risk:** KSP + Hilt version mismatch — pin Hilt to a version compatible with Kotlin 2.1.21.

#### Day 2 (Tue, May 5) — Schema v3 + real migration + sync metadata
- **Deliverable:** Room v3 with `uuid`, `user_id`, `updated_at`, `is_deleted`, `is_synced` on both entities; `reminder_time`, `reminder_enabled` on habits; new `user_profile` table; `Migration(2,3)` preserves existing user data; `fallbackToDestructiveMigration()` removed.
- **Files touched:** `HabitEntity.kt`, `HabitLogEntity.kt`, new `UserProfileEntity.kt`, `HabitDatabase.kt`, `HabitDao.kt` (+ `UserDao.kt`), `RoomHabitRepository.kt` (mappers updated). Schema JSON committed under `app/schemas/`.
- **Demo:** install MCO 1 build → upgrade to MCO 2 build → existing habits and logs are still there; new columns visible in the Room schema JSON.

#### Day 3 (Wed, May 6) — Auth: sign-up, sign-in, sign-out
- **Deliverable:** Firebase Auth wired in; sign-up creates a Firestore profile doc + reserves the username in `usernames/{usernameLower}`; sign-in works; sign-out clears local cache.
- **Files touched:** `FirebaseAuthDataSource.kt`, `AuthRepository(Impl).kt`, `UserRepository(Impl).kt`, `AuthViewModel.kt`, `SignInScreen.kt`, `SignUpScreen.kt`, `NavGraph.kt` (auth gate), `NetworkModule.kt`.
- **UI exception:** two new screens are unavoidable. They are **functional, not designed** — Material 3 defaults only.
- **Demo:** sign up with email + username + password; close app; reopen → still signed in. Sign out → land on sign-in screen.

#### Day 4 (Thu, May 7) — Firestore data sources + security rules
- **Deliverable:** `FirestoreHabitDataSource` and `FirestoreLogDataSource` implement push (upsert with server `updatedAt`), pull (snapshot listener for the user's collection), and delete (tombstone write). Firestore security rules deployed.
- **Files touched:** `FirestoreHabitDataSource.kt`, `FirestoreLogDataSource.kt`, `firestore.rules` (committed under `firebase/`), `DataSourceModule.kt`.
- **Demo:** unit test (or a debug button) writes a habit straight to Firestore; another device signed in as the same user receives it via the listener.

#### Day 5 (Fri, May 8) — Repository refactor (local-first + dirty flag)
- **Deliverable:** `HabitRepositoryImpl` replaces `RoomHabitRepository`. Writes commit to Room with `is_synced = false` and `updated_at = now`, then enqueue a one-time sync. Reads are unchanged from MCO 1's perspective. `RoomHabitRepository` is renamed/removed.
- **Files touched:** `HabitRepositoryImpl.kt` (new), `HabitRepository.kt` (interface unchanged externally; internal additions for sync state), `RepositoryModule.kt`.
- **Demo:** offline → create a habit → habit appears in UI immediately, `is_synced` is false in the DB; turn airplane mode off → still no sync yet (worker not built); cloud write happens via the temporary "Sync now" debug button.

#### Day 6 (Sat, May 9) — WorkManager sync worker + multi-device consistency
> *Merged from the original Day 6 (WorkManager) and Day 7 (multi-device + buffer). The buffer day is sacrificed to fit the new window.*
- **Deliverable (Part A — WorkManager):** `HabitSyncWorker` (`@HiltWorker`) implements push-then-pull with last-write-wins. Triggered: (a) one-time after each local write, (b) periodically every 15 min when network is connected, (c) one-time on connectivity restored.
- **Deliverable (Part B — multi-device):** edit-on-A / edit-on-B / delete-on-A scenarios all converge correctly. Tombstones propagate. Tie-breaking by `uuid` lexicographic order. Manual "Sync now" in Settings calls `SyncManager.requestImmediate()`.
- **Files touched:** `HabitSyncWorker.kt` (push-pull + conflict logic), `SyncManager.kt`, `WorkerModule.kt`, `HabitFlowApplication.kt` (`Configuration.Provider` for HiltWorkerFactory), manifest (`<provider>` removed for default WorkManager init), `SettingsScreen.kt` (one button + last-sync timestamp), `SettingsViewModel.kt`.
- **Demo:** create a habit offline → enable Wi-Fi → within ~30s the habit shows up in Firestore console; create on Device B → within ~15 min appears on Device A; the four conflict scenarios pass on two devices.

> **Sun May 10 — rest day, no commits planned.**

### Week 2 — Reminders & polish (Mon May 11 → Sat May 16)

#### Day 7 (Mon, May 11) — Notifications scaffolding
- **Deliverable:** notification channel created on app start; `POST_NOTIFICATIONS` runtime permission asked once; a debug "fire test notification" works.
- **Files touched:** `NotificationHelper.kt`, manifest (permissions), `MainActivity.kt` (permission request), `NotificationModule.kt`.
- **Demo:** test notification fires, opens the app when tapped.

#### Day 8 (Tue, May 12) — Per-habit reminder scheduling
- **Deliverable:** `ReminderScheduler` schedules a daily `AlarmManager` alarm per habit at its `reminder_time`; `ReminderReceiver` posts the notification; `BootReceiver` reschedules everything after reboot. Disabling a habit cancels its alarm.
- **Files touched:** `ReminderScheduler.kt`, `ReminderReceiver.kt`, `BootReceiver.kt`, manifest (receivers + `RECEIVE_BOOT_COMPLETED`), `HabitRepositoryImpl.kt` (calls scheduler on add/update/disable/delete).
- **Demo:** set a habit reminder for "now + 1 minute"; lock device; wait → notification fires; reboot device; wait → still fires the next day.

> **Cut from scope:** the original Day 10 (missed-habit notifications, requirement NOTIF-6) was removed in the May 3 schedule revision. The end-of-day "you missed habit X" notification will not ship in MCO 2. Per-habit scheduled reminders (Day 8) still ship.

#### Day 9 (Wed, May 13) — Reminder UI on habit form
- **Deliverable:** `HabitFormScreen` gains a "Reminder time" row (toggle + time picker). Editing existing habits respects the existing reminder.
- **Files touched:** `HabitFormScreen.kt`, `HabitFormViewModel.kt`.
- **Demo:** create a habit with a reminder via the UI (no debug shortcuts); reminder fires at the chosen time.

#### Day 10 (Thu, May 14) — DataStore migration + ProGuard + INTERNET check
- **Deliverable:** `ThemePreferences` migrated from `SharedPreferences` to Preferences DataStore (with `SharedPreferencesMigration` so existing users keep their dark-mode setting). `proguard-rules.pro` adds keep rules for Firebase models, kotlinx-serialization (if used), and Hilt-generated classes. `<uses-permission android:name="android.permission.INTERNET" />` confirmed.
- **Files touched:** `ThemePreferences.kt`, `proguard-rules.pro`, manifest, `SettingsViewModel.kt`.
- **Demo:** `./gradlew assembleRelease` produces an APK that signs in and syncs without R8 stripping anything.

#### Day 11 (Fri, May 15) — Edge cases & tests
- **Deliverable:**
  - Sign-out wipes Room (`DELETE FROM habits/habit_logs/user_profile`) so a different user signing in on the same device doesn't see leftover data.
  - Sync error path: 3 retries with exponential backoff, then fail visibly (banner in Settings showing "Last sync failed: {reason}").
  - No-network UX: small banner/icon in the home top bar when offline; clears once connectivity returns.
  - Unit tests: `StreakCalculatorTest` (kept), new tests for the conflict-resolution function (pure Kotlin, fed with fixture data and `FakeHabitRepository`).
- **Files touched:** `AuthRepositoryImpl.kt`, `HabitSyncWorker.kt`, `HomeScreen.kt` (offline indicator only), `ConflictResolverTest.kt`.

#### Day 12 (Sat, May 16) — Documentation, smoke test, freeze
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

If Day N slips, the developer decides: extend by a day (pushing the
smoke test out — the schedule has **no remaining buffer** after the
May 3 revision), or trim scope from Day N itself.

---

## 5. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Hilt + KSP + Kotlin 2.1.21 version mismatch | Medium | Pin to known-good versions on Day 1; fall back to KAPT if blocked |
| Firestore offline cache fights our Room cache (double-source-of-truth) | Medium | Disable Firestore client persistence (`setPersistenceEnabled(false)`) — Room is the only local cache |
| WorkManager + Hilt initialization order | Low | Use `Configuration.Provider` on Application; remove default WorkManager initializer in manifest |
| `AlarmManager` exact-alarm restrictions on API 31+ | Medium | Use `setInexactRepeating` (acceptable for daily reminders) — no `SCHEDULE_EXACT_ALARM` permission needed |
| Username uniqueness race condition | Low | Use a Firestore transaction that creates `usernames/{usernameLower}` and the user doc atomically |
| ProGuard strips Firebase models in release | High if forgotten | Day 10 explicitly adds keep rules and runs a release build |
| Compressed 12-day deadline (no remaining buffer) | High | Day 7 buffer was absorbed into Day 6; Day 10 (NOTIF-6) was cut. Any further slip extends Day 12 (smoke test) or trims a same-day deliverable — developer decides on the spot. |
