# MCO 2 — Backend Requirements

**Module title:** Cloud-Enabled, Data-Driven, & Enhanced User Experience
**Objective:** Enhance the Habit Flow Android app by adding cloud
synchronization, background processing, authentication, and notifications,
while preserving the offline-first user experience delivered in MCO 1.

This document is the authoritative requirements list for the MCO 2 backend
phase. The implementation plan that satisfies these requirements lives in
[`MCO2_PLAN.md`](./MCO2_PLAN.md).

---

## 1. Functional requirements

### 1.1 Authentication (added scope)

The user must be able to create and sign in to a personal account.

| ID | Requirement |
|---|---|
| AUTH-1 | Sign-up with **email**, **username**, and **password** |
| AUTH-2 | Sign-in with **email + password** |
| AUTH-3 | Sign-out clears local cache (cloud data remains intact) |
| AUTH-4 | Username is **unique** across the user base and stored in the user's Firestore profile document |
| AUTH-5 | Password reset via email link (Firebase Auth built-in) |
| AUTH-6 | Authenticated user identity (`uid`) gates all cloud reads/writes — no anonymous cloud access |
| AUTH-7 | Auth state is reactive: the app navigates to the auth screen on sign-out and to home on sign-in without restart |

### 1.2 Cloud integration

| ID | Requirement |
|---|---|
| CLOUD-1 | Habits sync to **Firebase Firestore** under `users/{uid}/habits/{habitUuid}` |
| CLOUD-2 | Habit logs sync to Firestore under `users/{uid}/habit_logs/{logUuid}` |
| CLOUD-3 | All cloud writes include a server-resolvable `updatedAt` timestamp |
| CLOUD-4 | Cloud backup: a user signing in on a new device receives all their habits and logs |
| CLOUD-5 | Cloud restore: reinstalling the app and signing in restores the user's data without manual export/import |
| CLOUD-6 | Multi-device consistency: changes on Device A appear on Device B once both reconnect (eventual consistency, last-write-wins) |

### 1.3 Offline-first data strategy

| ID | Requirement |
|---|---|
| OFF-1 | All reads come from Room **first**. The UI never blocks on a network call |
| OFF-2 | All writes commit to Room **first**, then enqueue a background sync |
| OFF-3 | When offline, the app remains fully functional (create / edit / delete / log habits, view streaks, view history) |
| OFF-4 | Sync runs in the background using **WorkManager** with `NetworkType.CONNECTED` constraint |
| OFF-5 | Sync runs **periodically** (every ~15 min minimum, per WorkManager) **and** on **connectivity restored** |
| OFF-6 | A manual "Sync now" trigger is available (at minimum from Settings) |
| OFF-7 | Conflict resolution: **latest `updatedAt` wins**. Tie-breaker: deterministic (e.g., higher `uuid` lexicographically) to avoid ping-pong |
| OFF-8 | Deletes are propagated as **tombstones** (`isDeleted = true`), so deletes on Device A reach Device B |

### 1.4 Reminders & notifications

| ID | Requirement |
|---|---|
| NOTIF-1 | Each habit can have an optional **reminder time** (HH:mm, device local time) |
| NOTIF-2 | Reminder time is configurable per habit — set on the habit form, editable later |
| NOTIF-3 | At reminder time, a system notification fires: "Time to: {habit title}" |
| NOTIF-4 | Tapping the notification opens the habit detail screen |
| NOTIF-5 | Reminders persist across device reboots (rescheduled by a `BOOT_COMPLETED` receiver) |
| NOTIF-6 | If a daily habit was not logged by end-of-day, a "missed habit" notification fires (best-effort; may be cut if behind schedule — see Day 10 in the plan) |
| NOTIF-7 | Disabling a habit cancels its scheduled reminder |
| NOTIF-8 | Runtime permission `POST_NOTIFICATIONS` is requested politely on Android 13+ |

### 1.5 Dependency injection

| ID | Requirement |
|---|---|
| DI-1 | **Hilt** replaces the manual `HabitFlowApplication` lazy fields and `HabitFlowViewModelFactory` |
| DI-2 | All ViewModels use `@HiltViewModel` and receive dependencies via constructor injection |
| DI-3 | All repositories, data sources, DAOs, schedulers, and the Firestore instance are provided via Hilt modules |
| DI-4 | WorkManager workers use `@HiltWorker` + `HiltWorkerFactory` for injection |

---

## 2. Non-functional requirements

| ID | Requirement |
|---|---|
| NF-1 | **Architecture:** MVVM + Repository preserved from MCO 1; clean separation of UI / ViewModel / domain / data |
| NF-2 | **Single source of truth:** Room locally; Firestore in the cloud; the repository merges them. UI never reads Firestore directly |
| NF-3 | **Schema migrations:** every Room version bump ships a `Migration(from, to)`; `fallbackToDestructiveMigration()` is removed |
| NF-4 | **Crash safety:** sync failures are retried with exponential backoff; one bad row does not abort the whole sync batch |
| NF-5 | **Security:** Firestore security rules restrict each user to `users/{uid}/**`. No client may read or write another user's data |
| NF-6 | **Privacy:** no analytics, crash reporting, or third-party SDKs beyond Firebase Auth + Firestore in this phase |
| NF-7 | **Build hygiene:** ProGuard / R8 keep rules cover Firebase models and any reflective serialization |
| NF-8 | **Min SDK 26 / Target SDK 35** maintained from MCO 1 |
| NF-9 | **Testability:** repositories and sync logic are testable without a real Firestore (use the `FakeDataSource`-derived fixture from MCO 1) |
| NF-10 | **Performance:** UI thread does no IO. All Room and network calls run on `Dispatchers.IO` (or Firestore's own threading) |

---

## 3. Schema deltas (additive — preserves MCO 1 data)

### 3.1 `HabitEntity` — new columns

| Column | Type | Default | Purpose |
|---|---|---|---|
| `uuid` | `String` | random UUID at insert | Stable cross-device identifier (this is the Firestore document ID) |
| `user_id` | `String?` | null until signed in | Owning user's Firebase `uid` |
| `updated_at` | `Long` | `System.currentTimeMillis()` | Conflict resolution timestamp |
| `is_deleted` | `Boolean` | `false` | Tombstone flag for soft delete + sync propagation |
| `is_synced` | `Boolean` | `false` | Dirty flag — true once Firestore acknowledges the write |
| `reminder_time` | `String?` (HH:mm) | null | Optional daily reminder time |
| `reminder_enabled` | `Boolean` | `false` | Whether the reminder is active |

### 3.2 `HabitLogEntity` — new columns

| Column | Type | Default | Purpose |
|---|---|---|---|
| `uuid` | `String` | random UUID at insert | Stable cross-device identifier |
| `user_id` | `String?` | null until signed in | Owning user's Firebase `uid` |
| `updated_at` | `Long` | `System.currentTimeMillis()` | Conflict resolution timestamp |
| `is_deleted` | `Boolean` | `false` | Tombstone flag |
| `is_synced` | `Boolean` | `false` | Dirty flag |

### 3.3 New table: `user_profile` (single-row local cache of the signed-in user)

| Column | Type | Notes |
|---|---|---|
| `uid` | `String` (PK) | Firebase Auth UID |
| `email` | `String` | From Firebase Auth |
| `username` | `String` | From Firestore profile document; must be unique |
| `created_at` | `Long` | Account creation timestamp |

### 3.4 Migrations

- **Version 2 → Version 3:** add the seven columns to `habits`, four to `habit_logs`, create `user_profile`, backfill existing rows with `uuid = randomUUID()`, `updated_at = now`, `is_synced = false`.
- `fallbackToDestructiveMigration()` is **removed** in this version bump.

---

## 4. Firestore data model

```
users/{uid}                                  ← profile document
  ├─ email: string
  ├─ username: string
  ├─ usernameLower: string                   ← for case-insensitive uniqueness checks
  └─ createdAt: timestamp

users/{uid}/habits/{habitUuid}               ← document id == HabitEntity.uuid
  ├─ title: string
  ├─ description: string
  ├─ frequency: "DAILY" | "WEEKLY"
  ├─ startDate: number (epoch day)
  ├─ isEnabled: boolean
  ├─ color: string
  ├─ reminderTime: string | null             ← "HH:mm"
  ├─ reminderEnabled: boolean
  ├─ isDeleted: boolean
  └─ updatedAt: timestamp                    ← server timestamp on write

users/{uid}/habit_logs/{logUuid}             ← document id == HabitLogEntity.uuid
  ├─ habitUuid: string                       ← parent habit's uuid (NOT local Long id)
  ├─ dateCompleted: number (epoch day)
  ├─ timestamp: number (ms)
  ├─ isDeleted: boolean
  └─ updatedAt: timestamp

usernames/{usernameLower}                    ← reservation doc for uniqueness
  └─ uid: string
```

### Firestore security rules (high level)

```
match /databases/{db}/documents {
  match /users/{uid}/{document=**} {
    allow read, write: if request.auth != null && request.auth.uid == uid;
  }
  match /usernames/{username} {
    allow read: if request.auth != null;                        // anyone signed in can check availability
    allow create: if request.auth != null
                  && request.resource.data.uid == request.auth.uid;
    allow delete: if request.auth != null
                  && resource.data.uid == request.auth.uid;
  }
}
```

---

## 5. Permissions & manifest changes

| Permission | Why |
|---|---|
| `android.permission.INTERNET` | Firestore + Auth network access |
| `android.permission.ACCESS_NETWORK_STATE` | WorkManager constraint awareness |
| `android.permission.POST_NOTIFICATIONS` | Reminders on Android 13+ (runtime-requested) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule reminders after device reboot |

Components added to the manifest:

- `BootReceiver` — listens for `android.intent.action.BOOT_COMPLETED`, reschedules all reminders.
- `ReminderReceiver` — receives the alarm, posts the notification.

---

## 6. New Gradle dependencies

| Dependency | Purpose |
|---|---|
| `com.google.dagger:hilt-android` + `hilt-compiler` | DI |
| `androidx.hilt:hilt-navigation-compose` | `@HiltViewModel` lookup from Compose |
| `androidx.hilt:hilt-work` | Inject into Workers |
| `androidx.work:work-runtime-ktx` | Background sync |
| `com.google.firebase:firebase-bom` | Firebase BoM (version-aligned) |
| `com.google.firebase:firebase-auth-ktx` | Authentication |
| `com.google.firebase:firebase-firestore-ktx` | Cloud database |
| `com.google.gms:google-services` (plugin) | Firebase config integration |
| `androidx.datastore:datastore-preferences` | Replace `SharedPreferences` for `ThemePreferences` |

---

## 7. Out of scope (this phase)

- UI redesign, animations, theming changes beyond what reminders/auth strictly need
- Social features (friends, sharing, leaderboards)
- Crash reporting / analytics
- Paid tier / billing
- Widget / Wear OS / tablet adaptations
- Custom REST backend (we chose Firebase to fit the timeline)
