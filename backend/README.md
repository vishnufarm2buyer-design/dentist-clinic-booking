# Webhook & FCM Notification Backend

This is a lightweight Node.js Express service designed to be deployed on **Render (Free Tier)**. It receives database webhook events from Supabase and forwards push notifications to Android devices via the **Firebase Admin SDK**.

---

## Environment Variables

Configure these variables on Render:

| Variable | Description | Example |
| :--- | :--- | :--- |
| `PORT` | The port the Express server runs on. | `3000` |
| `WEBHOOK_SECRET` | A secure, random token shared with Supabase to authenticate incoming webhooks. | `MySuperSecretKey123!` |
| `SUPABASE_URL` | Your Supabase Project API URL. | `https://your-project.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase **`service_role`** key (bypasses RLS to query user device tokens). | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The **entire text content** of your Firebase Service Account JSON key file. | `{"type": "service_account", "project_id": ...}` |

---

## Local Setup & Testing

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create a `.env` file in the `backend/` directory:
   ```env
   PORT=3000
   WEBHOOK_SECRET=MySuperSecretKey123!
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_SERVICE_ROLE_KEY=your_supabase_service_role_key
   FIREBASE_SERVICE_ACCOUNT_JSON={"type": "service_account", ...}
   ```

3. Run the server:
   ```bash
   npm start
   ```

---

## Deploy to Render (Free Tier)

1. Push this project folder (specifically the `backend/` directory or the parent repository containing it) to a GitHub repository.
2. Go to [Render](https://render.com) and click **New -> Web Service**.
3. Link your GitHub repository.
4. Set the following settings:
   - **Environment:** `Node`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
5. Under **Advanced**, add the environment variables listed above.
6. Click **Deploy Web Service**. Render will provision a public URL (e.g., `https://dentist-booking-notification.onrender.com`).

---

## Configure Supabase Database Webhook

Once the Render backend is live, set up a webhook in your Supabase project:

1. In Supabase Dashboard, go to **Database -> Webhooks** (or Database Triggers).
2. Click **Create Webhook**:
   - **Name:** `notify_bookings`
   - **Table:** `bookings`
   - **Events:** `Insert` and `Update`
   - **Type:** `HTTP Request`
   - **HTTP Method:** `POST`
   - **URL:** `https://your-render-app-url.onrender.com/webhooks/bookings`
3. Under **Headers**:
   - Add Header `Content-Type: application/json`
   - Add Header `x-webhook-secret: <Your WEBHOOK_SECRET value>`
4. Click **Create**.

Your database changes will now trigger instant push notifications to your Clinic Admins, Doctors, and Customers!
