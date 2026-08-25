# Free-Tier Services Setup Guide

This guide outlines how to set up the free-tier services for **Supabase**, **Firebase**, **Render**, and **GitHub**. Follow these steps to collect the credentials needed for our Dentist Clinic Booking Android Application and notification backend.

---

## 1. Supabase (Database & Custom Auth)
Supabase provides a generous free tier including a hosted PostgreSQL database, API gateways, and database webhooks.

### Steps:
1. Go to [supabase.com](https://supabase.com) and sign in/create a free account.
2. Click **New Project** and select the **Free Tier**.
3. Set your project name (e.g., `Dentist Booking`) and a strong **Database Password** (save this password safely!).
4. Wait 1-2 minutes for the database to provision.
5. Go to **Project Settings** (gear icon in the sidebar) -> **API**:
   - Copy the **Project URL** (we will use this as `SUPABASE_URL` in Android).
   - Copy the **`anon` `public` API Key** (we will use this as `SUPABASE_ANON_KEY` in Android).
   - Copy the **`service_role` secret API Key** (keep this secret! We will use this as `SUPABASE_SERVICE_ROLE_KEY` in our Render backend).

---

## 2. Firebase (Cloud Messaging)
Firebase Cloud Messaging (FCM) is completely free and allows us to send push notifications to Android devices.

### Steps:
1. Go to the [Firebase Console](https://console.firebase.google.com/) and sign in with your Google account.
2. Click **Add Project** and name it `Dentist Booking`. Disable Google Analytics (optional, to keep it lightweight).
3. Once the project is created, click the **Android icon** on the dashboard to register our app:
   - **Android package name:** `com.dentist.booking` (must match our app's package name).
   - **App nickname:** `Dentist Booking Android App`.
4. Click **Register App** and download the `google-services.json` file. (We will place this file in our `android/app/` folder).
5. Go to **Project Settings** (gear icon next to "Project Overview" in the sidebar) -> **Service Accounts**:
   - Click **Generate New Private Key** under the Firebase Admin SDK tab.
   - This downloads a JSON file containing your Firebase credentials (e.g., `dentist-booking-firebase-adminsdk-xxxx.json`).
   - **Keep this file secure!** We will use its contents for our Render backend variables (`FIREBASE_SERVICE_ACCOUNT_JSON`).

---

## 3. Render (Notification Backend Host)
Render offers a free tier for hosting web services (Node.js/Python). We will use it to host our webhook listener.

### Steps:
1. Go to [render.com](https://render.com) and create a free account.
2. You will deploy our Node.js app to Render (either by connecting your GitHub repository or deploying via web).
3. During setup, configure these environment variables on Render:
   - `PORT`: `3000` (or leave default).
   - `SUPABASE_URL`: (Copied from Supabase API settings).
   - `SUPABASE_SERVICE_ROLE_KEY`: (Copied from Supabase API settings).
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: (The entire text content of the Firebase service account JSON key file you downloaded).
   - `WEBHOOK_SECRET`: A secure random password of your choice (e.g. `MySuperSecretWebhookKey123!`). We will set this in the Supabase Webhook HTTP Header as `Authorization: Bearer <secret>` to ensure only Supabase can trigger notifications.

---

## 4. GitHub (APK Hosting & Releases)
GitHub is free for public and private repositories. We will host our source code and distribute production APK updates via GitHub Releases.

### Steps:
1. Go to [github.com](https://github.com) and create a repository (e.g., `dentist-clinic-booking`).
2. We will push our Android codebase here.
3. When we build the production APK, we will upload it to a **GitHub Release** (e.g. version `v1.0.0`).
4. Copy the direct download link of the uploaded APK (e.g., `https://github.com/your-username/dentist-clinic-booking/releases/download/v1.0.0/app-release.apk`).
5. We will insert this URL into the `app_versions` table in Supabase to trigger automatic updates in the Android app.
