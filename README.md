# QuickVoice Reminders 🎙️✅

**QuickVoice** is a high-performance Android reminder application designed for speed and simplicity. It allows users to schedule reminders in under 3 seconds using natural language—whether spoken or typed. Powered by Gemini AI and a resilient local NLP engine, it understands "when" and "what" you need to do without requiring complex manual form filling.

---

## 🚀 Key Features

### 1. Ultra-Fast Voice & Text Capture
* **Hybrid AI-Local Parsing**: Uses a smart "AI-First, Heuristic-Fallback" architecture.
* **AI Engine**: Uses Google's **Gemini 2.5 Flash** (via Firebase AI) to extract tasks, dates, times, locations, and urgency from complex, unstructured sentences like *"Remind me next Friday at noon to follow up with the guy I met at the gym"*.
* **Resilient Offline NLP**: If the internet is down or an API key is not provided, a rule-based heuristic parser takes over in <5ms. This ensures basic reminders like *"Buy milk after 10 minutes"* or *"Call mom tomorrow at 5pm"* always work instantly without a cloud connection.
* **Manual Confirmation**: A review popup allows you to fine-tune AI-extracted details (Date, Time, Category, Urgency) before saving.

### 2. Smart Scheduling & Notifications
* **Natural Time Support**: Understands phrases like "tonight", "next Friday", "in 2 hours", or "after 10 minutes".
* **Nag Mode ⚡**: For critical tasks, Nag Mode repeats notifications every 5 minutes until you mark the task as complete.
* **Snooze Support**: Quick actions on notifications allow you to snooze tasks by 10 minutes or complete them instantly.

### 3. Productivity Dashboard
* **Metric Badges**: Quick filters for Pending, Today, Urgent Nag, and Completed tasks.
* **Categorization**: Auto-categorizes tasks into Personal, Work, Errands, Health, Finance, or General based on context.
* **Search & Filter**: Find any reminder by text, location, or date.

### 4. Modern Android Integration
* **Home Screen Widget**: Built with **Android Glance**, providing a glanceable view of your tasks and a one-tap shortcut to voice recording.
* **Material 3 Design**: A beautiful, dark-themed UI following modern Material Design principles.
* **Ad-Free Option**: Integrated Google Play Billing for a one-time "Pro" upgrade.

---

## 🛠 Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **Architecture**: MVVM with StateFlow and Coroutines
* **Database**: Room Persistence Library
* **AI/NLP**: Google Gemini 2.5 Flash & Custom Rule-based NLP
* **Networking**: Retrofit & OkHttp
* **Background Tasks**: AlarmManager for precise scheduling
* **Ads**: Google AdMob (optimized frequency)
* **Widgets**: Android Glance (Jetpack Compose for Widgets)

---

## 📦 Setup & Installation

### Prerequisites
* Android Studio Ladybug or newer.
* JDK 17+.
* An Android device or emulator (API 24+).

### API Configuration
The project uses the **Secrets Gradle Plugin** to manage sensitive keys.
1. Obtain a **Gemini API Key** from the [Google AI Studio](https://aistudio.google.com/).
2. Create a file named `.env` in the root directory of the project.
3. Add your key to the file:
   ```env
   GEMINI_API_KEY=your_api_key_here
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
* **Pro Upgrade**: Users can remove all ads via a Google Play In-App Purchase.

---

## 🤝 Contributing
Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
