-- ==========================================
-- SEED DATA & INITIAL SUPER ADMIN CREATION
-- ==========================================

-- IMPORTANT: The Super Admin must NOT be created through the Android application.
-- Run this script in the Supabase SQL Editor to seed the initial Super Admin.

-- Replace '+1111111111' with your desired Super Admin phone number.
-- Replace 'SecureAdminPassword123!' with your desired secure plaintext password.
-- The custom function `create_user_with_hash` will hash the password securely using bcrypt.

SELECT create_user_with_hash(
  '+1111111111', 
  'SecureAdminPassword123!', 
  'Platform Super Admin', 
  'super_admin', 
  NULL
);

-- (Optional) Verify that the Super Admin was successfully inserted with a hashed password
-- SELECT id, phone, password_hash, name, role FROM users WHERE role = 'super_admin';


-- ==========================================
-- INITIAL APP VERSION SEED
-- ==========================================
-- This provides an initial record in `app_versions` for the app updater to check against.
INSERT INTO app_versions (version_code, version_name, apk_url, release_notes, force_update)
VALUES (
  1, 
  '1.0.0', 
  'https://github.com/placeholder/dentist-clinic-booking/releases/download/v1.0.0/app-release.apk', 
  'Initial production release of Dentist Clinic Booking App.', 
  FALSE
) ON CONFLICT (version_code) DO NOTHING;
