# QuickVoice Reminders 🎙️✅

**QuickVoice** is a high-performance Android reminder application designed for speed and simplicity. It allows users to schedule reminders in under 3 seconds using natural language—whether spoken or typed. Its secure AI service and resilient local parser understand "when" and "what" you need to do without requiring complex manual form filling.

---

## 🚀 Key Features

### 1. Ultra-Fast capture
* **Hybrid AI-Local Parsing**: Uses a smart "AI-First, Heuristic-Fallback" architecture.
* **AI Engine**: Uses a server-side AI service to extract tasks, dates, times, locations, and urgency from complex, unstructured sentences like *"Remind me next Friday at noon to follow up with the guy I met at the gym"*.
* **Resilient Offline NLP**: If the internet is down or an API key is not provided, a rule-based heuristic parser takes over in <5ms. This ensures basic reminders like *"Buy milk after 10 minutes"* or *"Call mom tomorrow at 5pm"* always work instantly.
* **Manual Entry (+)**: A dedicated Floating Action Button for those who prefer traditional manual reminder creation with smart defaults.
* **Voice-to-Text Review**: Speak your reminder, review the transcript, and edit details in an interactive popup before saving.

### 2. Smart Scheduling & Notifications
* **Natural Time Support**: Understands phrases like "tonight", "next Friday", "in 2 hours", "after 10 minutes", or "10 p.m.".
* **Nag Mode ⚡**: For critical tasks, Nag Mode repeats notifications every 5 minutes until you mark the task as complete.
* **Custom Notification Tones**: Personalized alert sounds selectable via the app settings.
* **Snooze Support**: Quick actions on notifications allow you to snooze tasks by 10 minutes, which automatically updates the task's schedule in the app.

### 3. Productivity Dashboard
* **Metric Badges**: Quick filters for Pending, Today, Urgent Nag, and Completed tasks.
* **Search & Filter**: Find any reminder by text, location, or category instantly.
* **Undo Delete**: Accidentally deleted a reminder? Restore it instantly with the one-tap Undo option.
* **Clear Completed**: Easily sweep away all finished tasks to keep your dashboard clean.

### 4. Modern Android Integration
* **Home Screen Widget**: Built with **Android Glance**, providing a glanceable view of your tasks and a one-tap shortcut to voice recording.
* **Material 3 Design**: A beautiful, responsive dark-themed UI following modern Material Design principles.
* **Ad-Free Option**: Integrated Google Play Billing for a "Pro" upgrade at just $5/month.

---

## 🛠 Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **Architecture**: MVVM with StateFlow and Coroutines
* **Database**: Room Persistence Library
* **AI/NLP**: Server-side AI parsing and custom rule-based NLP
* **Backend**: Node.js, Express, and PostgreSQL
* **Networking**: OkHttp
* **Background Tasks**: AlarmManager for precise scheduling
* **Ads**: Google AdMob (Optimized frequency: Interstitial every 3rd schedule)
* **Widgets**: Android Glance (Jetpack Compose for Widgets)

---

## 📦 Setup & Installation

### Prerequisites
* Android Studio Ladybug or newer.
* JDK 17+.
* An Android device or emulator (API 24+).

### API Configuration
Provider credentials belong only in `backend/.env`; they are never included in the Android build. See `backend/README.md` for server and PostgreSQL setup.
1. Deploy or start the backend.
2. Create a file named `.env` in the root directory of the project.
3. Add the public backend URL:
   ```env
   BACKEND_BASE_URL=https://api.example.com
   ```

### Building the Project
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Run the `app` module on your device.

---

## 📈 Monetization & Ads
* **Banner Ads**: Non-intrusive banners at the bottom of the screen.
* **Interstitial Ads**: Shown every 3rd successful reminder schedule to balance user experience and revenue.
* **Pro Upgrade**: Users can remove all ads and unlock premium badges via a Google Play In-App Purchase ($5/month).

---

## 🤝 Contributing
Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
