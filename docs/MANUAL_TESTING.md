# HabitFlow: QA Manual Testing Checklist (Days 1–10)

This document outlines the core test cases required to validate the functionality implemented between Day 1 and Day 10 of the Habit-Flow backend integration.

## 1. Offline Core Functionality (Room DB & UI)
- [ ] **Create a Habit:** Tap "New Habit", fill out the form (Title, Description, Color, Frequency), and tap Save. Verify the habit immediately appears on the Home screen.
- [ ] **Edit a Habit:** Tap an existing habit, then click the Edit button. Change its title and color. Verify the updates reflect cleanly on the Home screen without needing to restart the app.
- [ ] **Complete a Habit:** On the Home screen, tap the checkmark icon next to a habit. Verify the icon fills in and the "Today's Progress" counter updates instantly.
- [ ] **Streak Calculation:** Mark a habit complete. Go to its details page and verify the "Current Streak" has incremented. Uncheck the habit and verify the streak reverts.
- [ ] **Delete a Habit:** Open a habit's detail page, click Delete, and confirm. Verify the habit permanently disappears from the Home screen and Progress screen.

## 2. Cloud Sync & Auth (Firestore & WorkManager)
- [ ] **Anonymous Sign-in:** When the app first launches, verify the app automatically logs in anonymously in the background without prompting you for a password.
- [ ] **Online Sync (Push):** Connect to Wi-Fi. Create a habit. Open your Firebase Firestore console in a web browser and verify a new document appears under the `users/{userId}/habits` collection in real-time.
- [ ] **Offline Resilience:** Turn **Airplane Mode ON**. Create a habit and complete it. Verify the UI handles it perfectly. Turn **Airplane Mode OFF**. Wait 15 minutes (or trigger the `HabitSyncWorker` manually), and verify the offline data eventually appears in your Firebase console.

## 3. Reminder Engine (AlarmManager & Receivers)
- [ ] **Schedule a Notification:** Create a habit, toggle "Daily Reminder" ON, and set the time for exactly 2 minutes from now. Close the app completely. Verify that a push notification appears on your device at the exact minute.
- [ ] **Actionable Notifications:** Tap the push notification that appears on your lock screen/notification tray. Verify it correctly opens the HabitFlow application.
- [ ] **Reboot Resilience:** Set a reminder for 5 minutes from now. Immediately **Restart your phone**. Do not open the HabitFlow app after restarting. Wait. Verify the notification still successfully fires! (This proves our `BootReceiver` is functioning).

## 4. Settings & Global Overrides (DataStore)
- [ ] **Dark Mode Toggle:** Go to Settings -> Appearance. Toggle Dark Mode. Verify the entire app theme shifts instantly and saves your choice even if you restart the app.
- [ ] **Global Notification Kill-Switch:** Go to Settings -> Notifications. Turn "Daily Reminders" **OFF**. Go create a new habit and set a reminder for 1 minute from now. Verify that **NO notification fires** (the global setting successfully overrides the individual habit setting).
- [ ] **Global Notification Restore:** Go back to Settings and turn "Daily Reminders" **ON**. Verify that your previously scheduled habits successfully resume sending push notifications.
