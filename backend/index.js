const express = require('express');
const admin = require('firebase-admin');
const { createClient } = require('@supabase/supabase-js');
require('dotenv').config();

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;
const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET;

// 1. Initialize Firebase Admin SDK using Environment Variable JSON
try {
  if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON environment variable.");
  }
  const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  console.log("Firebase Admin SDK initialized successfully.");
} catch (error) {
  console.error("Failed to initialize Firebase Admin SDK:", error.message);
  process.exit(1);
}

// 2. Initialize Supabase Client with service_role to bypass RLS for token retrieval
const supabase = createClient(
  process.env.SUPABASE_URL || "",
  process.env.SUPABASE_SERVICE_ROLE_KEY || ""
);

// 3. Webhook listener endpoint
app.post('/webhooks/bookings', async (req, res) => {
  // Verify Webhook Signature Secret
  const incomingSecret = req.headers['x-webhook-secret'];
  if (!incomingSecret || incomingSecret !== WEBHOOK_SECRET) {
    return res.status(401).json({ error: "Unauthorized access: Secret mismatch" });
  }

  const { type, table, record, old_record } = req.body;
  
  if (table !== 'bookings') {
    return res.status(400).json({ error: "Invalid table hook" });
  }

  console.log(`Received bookings webhook event: ${type}`);

  try {
    // --- CASE 1: New Booking Created (Notify Clinic Admin) ---
    if (type === 'INSERT' && record.status === 'pending') {
      // Find all Clinic Admins for this clinic
      const { data: admins, error: adminErr } = await supabase
        .from('users')
        .select('id')
        .eq('clinic_id', record.clinic_id)
        .eq('role', 'clinic_admin');

      if (adminErr) throw adminErr;

      if (admins && admins.length > 0) {
        const adminIds = admins.map(a => a.id);
        
        // Fetch their registered device tokens
        const { data: tokens, error: tokenErr } = await supabase
          .from('device_tokens')
          .select('token')
          .in('user_id', adminIds);

        if (tokenErr) throw tokenErr;

        if (tokens && tokens.length > 0) {
          const deviceTokens = tokens.map(t => t.token);
          await sendPushNotification(
            deviceTokens,
            "New Booking Request 🦷",
            `A patient has requested a booking on ${record.requested_date}.`
          );
        }
      }
    }

    // --- CASE 2: Booking Status Updated ---
    if (type === 'UPDATE') {
      const statusChanged = record.status !== old_record.status;

      // Subcase A: Accepted & Doctor Assigned
      if (statusChanged && record.status === 'accepted') {
        // 1. Notify the patient
        const { data: custTokens, error: custErr } = await supabase
          .from('device_tokens')
          .select('token')
          .eq('user_id', record.customer_id);

        if (custErr) throw custErr;

        if (custTokens && custTokens.length > 0) {
          const tokens = custTokens.map(t => t.token);
          await sendPushNotification(
            tokens,
            "Appointment Confirmed! 🎉",
            `Your booking request for ${record.requested_date} has been accepted.`
          );
        }

        // 2. Notify the assigned doctor
        if (record.assigned_doctor_id) {
          const { data: docTokens, error: docErr } = await supabase
            .from('device_tokens')
            .select('token')
            .eq('user_id', record.assigned_doctor_id);

          if (docErr) throw docErr;

          if (docTokens && docTokens.length > 0) {
            const tokens = docTokens.map(t => t.token);
            await sendPushNotification(
              tokens,
              "New Appointment Assigned 🩺",
              `You have been assigned to a booking request scheduled for ${record.requested_date}.`
            );
          }
        }
      }

      // Subcase B: Rejected
      if (statusChanged && record.status === 'rejected') {
        const { data: custTokens, error: custErr } = await supabase
          .from('device_tokens')
          .select('token')
          .eq('user_id', record.customer_id);

        if (custErr) throw custErr;

        if (custTokens && custTokens.length > 0) {
          const tokens = custTokens.map(t => t.token);
          const reasonText = record.rejection_reason ? ` Reason: ${record.rejection_reason}` : '';
          await sendPushNotification(
            tokens,
            "Booking Status Update",
            `Your booking request for ${record.requested_date} was rejected.${reasonText}`
          );
        }
      }
    }

    return res.status(200).json({ success: true });
  } catch (err) {
    console.error("Webhook notification failure:", err.message);
    return res.status(500).json({ error: err.message });
  }
});

// Helper function to send push notifications via Firebase Admin SDK
async function sendPushNotification(tokens, title, body) {
  // Remove duplicates and empty values
  const uniqueTokens = [...new Set(tokens.filter(t => t && t.trim().length > 0))];
  
  if (uniqueTokens.length === 0) return;

  const payload = {
    notification: {
      title: title,
      body: body
    },
    data: {
      title: title,
      body: body
    }
  };

  try {
    const response = await admin.messaging().sendEachForMulticast({
      tokens: uniqueTokens,
      notification: payload.notification,
      data: payload.data
    });
    console.log(`Successfully sent ${response.successCount} notifications; ${response.failureCount} failed.`);
  } catch (error) {
    console.error("FCM Send Multicast failure:", error);
  }
}

app.get('/health', (req, res) => {
  res.status(200).json({ status: "healthy" });
});

app.listen(PORT, () => {
  console.log(`Notification service running on port ${PORT}`);
});
