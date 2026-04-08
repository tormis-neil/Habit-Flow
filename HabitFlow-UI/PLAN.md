# MCO 1 Backend Execution Plan

## [x] Task 1: Room Infrastructure
- [x] Implement `HabitEntity` and `HabitLogEntity` in the `data.local.entity` package.
- [x] Create `HabitDao` with required SQL operations.
- [x] Initialize `HabitDatabase` with TypeConverters for Date handling.

## [x] Task 2: Repository Merger
- [x] Implement `RoomHabitRepository` using the existing `HabitRepository` interface.
- [x] Map Room Flows to the UI State models (`HabitUiState`).
- [x] Replace `FakeDataSource` in `MainActivity` with the real Room implementation.

## [x] Task 3: Logic & Verification
- [x] Implement the Streak Calculation algorithm (consecutive days check).
- [x] Verify persistence: Add habit -> Close App -> Reopen -> Verify Data.
- [x] Run `./gradlew assembleDebug` to verify KSP generation.

## [x] Task 4: UI Refinement
- [x] Ensure all screens observe the new Room-backed StateFlows.
- [x] Verify "Empty State" visibility when no habits are in the database.
