-- 1. Enable pgcrypto for password hashing (bcrypt)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Create tables

-- Clinics
CREATE TABLE clinics (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  subscription_status TEXT NOT NULL DEFAULT 'trial' CHECK (subscription_status IN ('trial', 'active', 'expired', 'suspended')),
  subscription_plan TEXT NULL,
  subscription_start_date DATE NULL,
  subscription_end_date DATE NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Users (Custom users table for authentication)
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone TEXT UNIQUE NOT NULL CHECK (phone ~ '^\+?[0-9]{10,15}$'), -- validates phone number length and numeric content
  password_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('super_admin', 'clinic_admin', 'doctor', 'customer')),
  clinic_id UUID NULL REFERENCES clinics(id) ON DELETE SET NULL,
  specialization TEXT NULL,
  created_by_clinic_id UUID NULL REFERENCES clinics(id) ON DELETE SET NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  
  -- Constraints
  CONSTRAINT check_clinic_scope CHECK (
    (role = 'super_admin' AND clinic_id IS NULL) OR
    (role = 'customer' AND clinic_id IS NULL) OR
    (role IN ('clinic_admin', 'doctor') AND clinic_id IS NOT NULL)
  )
);

-- Subscription Logs (Audit trail for subscription changes)
CREATE TABLE subscription_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  action TEXT NOT NULL, -- e.g., 'activate', 'renew', 'suspend', 'expire', 'change_plan'
  plan TEXT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  changed_by UUID NOT NULL REFERENCES users(id),
  notes TEXT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Clinic Customers (Many-to-many link between clinics and global customers)
CREATE TABLE clinic_customers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  CONSTRAINT unique_clinic_customer UNIQUE(clinic_id, customer_id)
);

-- Bookings
CREATE TABLE bookings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  requested_date DATE NOT NULL CHECK (requested_date >= CURRENT_DATE),
  reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'completed', 'cancelled')),
  assigned_doctor_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
  rejection_reason TEXT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  
  CONSTRAINT check_assigned_doctor CHECK (
    (status = 'accepted' AND assigned_doctor_id IS NOT NULL) OR
    (status <> 'accepted')
  )
);

-- Treatments (Clinic-scoped treatment logs)
CREATE TABLE treatments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  doctor_id UUID NOT NULL REFERENCES users(id) ON DELETE SET NULL,
  notes TEXT NOT NULL CHECK (length(trim(notes)) > 0),
  visit_date DATE NOT NULL DEFAULT CURRENT_DATE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- App Versions (For GitHub-release based updater)
CREATE TABLE app_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  version_code INT NOT NULL UNIQUE,
  version_name TEXT NOT NULL,
  apk_url TEXT NOT NULL,
  release_notes TEXT NOT NULL,
  force_update BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Device Tokens (Secure FCM storage)
CREATE TABLE device_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  UNIQUE(user_id, token)
);

-- 3. Indexes for performance
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_clinic_id ON users(clinic_id);
CREATE INDEX idx_clinic_customers_customer ON clinic_customers(customer_id);
CREATE INDEX idx_bookings_clinic_id ON bookings(clinic_id);
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_treatments_customer_id ON treatments(customer_id);
CREATE INDEX idx_treatments_clinic_id ON treatments(clinic_id);
CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);


-- 4. Custom JWT Helpers and Password Verification

-- Retreive Supabase JWT Secret dynamically or fall back
-- App settings configuration (highly secure, only readable inside database)
CREATE TABLE app_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
ALTER TABLE app_settings ENABLE ROW LEVEL SECURITY;
-- No public RLS policies are defined, meaning this table is completely hidden from REST API queries.

CREATE OR REPLACE FUNCTION get_jwt_secret() RETURNS text AS $$
DECLARE
  secret text;
BEGIN
  -- Read from app_settings
  SELECT value INTO secret FROM app_settings WHERE key = 'jwt_secret';
  IF secret IS NULL OR secret = '' THEN
    -- Fallback to Supabase environment setting
    secret := current_setting('app.settings.jwt_secret', true);
  END IF;
  IF secret IS NULL OR secret = '' THEN
    -- Fallback to default
    secret := 'super-secret-jwt-token-with-at-least-32-characters-long';
  END IF;
  RETURN secret;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Base64url Encoder helper
CREATE OR REPLACE FUNCTION encode_base64url(bin bytea) RETURNS text AS $$
DECLARE
  b64 text;
BEGIN
  b64 := encode(bin, 'base64');
  b64 := replace(b64, '=', '');
  b64 := replace(b64, '+', '-');
  b64 := replace(b64, '/', '_');
  b64 := replace(b64, chr(10), '');
  b64 := replace(b64, chr(13), '');
  RETURN b64;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- JWT Signer in pure PL/pgSQL
CREATE OR REPLACE FUNCTION sign_jwt(payload json, secret text) RETURNS text AS $$
DECLARE
  header text;
  payload_str text;
  header_b64 text;
  payload_b64 text;
  sign_input text;
  signature bytea;
  signature_b64 text;
  secret_bytes bytea;
BEGIN
  header := '{"alg":"HS256","typ":"JWT"}';
  payload_str := payload::text;
  
  header_b64 := encode_base64url(header::bytea);
  payload_b64 := encode_base64url(payload_str::bytea);
  
  sign_input := header_b64 || '.' || payload_b64;
  
  -- Try to decode secret from base64 (Supabase default). If it fails, treat it as raw text.
  BEGIN
    secret_bytes := decode(secret, 'base64');
  EXCEPTION WHEN OTHERS THEN
    secret_bytes := secret::bytea;
  END;
  
  signature := hmac(sign_input::bytea, secret_bytes, 'sha256');
  signature_b64 := encode_base64url(signature);
  
  RETURN sign_input || '.' || signature_b64;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- RPC Login Endpoint
CREATE OR REPLACE FUNCTION login(p_phone TEXT, p_password TEXT)
RETURNS JSON AS $$
DECLARE
  v_user RECORD;
  v_jwt_secret TEXT;
  v_payload JSON;
  v_token TEXT;
  v_expiry INTEGER;
BEGIN
  -- Look up user
  SELECT * INTO v_user FROM users WHERE phone = p_phone;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Invalid phone number or password' USING ERRCODE = '28000';
  END IF;

  -- Verify password
  IF crypt(p_password, v_user.password_hash) <> v_user.password_hash THEN
    RAISE EXCEPTION 'Invalid phone number or password' USING ERRCODE = '28000';
  END IF;

  -- Check if user's clinic is suspended or expired (optional flag in response, but login remains available)
  -- Create JWT Payload (expired in 7 days)
  v_expiry := extract(epoch from (now() + interval '7 days'))::integer;
  v_payload := json_build_object(
    'sub', v_user.id,
    'aud', 'authenticated',  -- REQUIRED by Supabase PostgREST
    'role', 'authenticated', -- Allow PostgREST RLS to accept it
    'exp', v_expiry,
    'phone', v_user.phone,
    'user_role', v_user.role
  );

  v_jwt_secret := get_jwt_secret();
  v_token := sign_jwt(v_payload, v_jwt_secret);

  RETURN json_build_object(
    'token', v_token,
    'user', json_build_object(
      'id', v_user.id,
      'phone', v_user.phone,
      'name', v_user.name,
      'role', v_user.role,
      'clinic_id', v_user.clinic_id,
      'specialization', v_user.specialization
    )
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- 5. Seeding helper
CREATE OR REPLACE FUNCTION create_user_with_hash(
  p_phone TEXT,
  p_password TEXT,
  p_name TEXT,
  p_role TEXT,
  p_clinic_id UUID,
  p_specialization TEXT DEFAULT NULL,
  p_created_by_clinic_id UUID DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
  v_user_id UUID;
  v_hash TEXT;
BEGIN
  v_hash := crypt(p_password, gen_salt('bf', 10));
  INSERT INTO users (phone, password_hash, name, role, clinic_id, specialization, created_by_clinic_id)
  VALUES (p_phone, v_hash, p_name, p_role, p_clinic_id, p_specialization, p_created_by_clinic_id)
  RETURNING id INTO v_user_id;
  RETURN v_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- 6. Row Level Security Helper Functions (SECURITY DEFINER to avoid recursion)

CREATE OR REPLACE FUNCTION get_current_user_role()
RETURNS text
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT role FROM users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION get_current_user_clinic_id()
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT clinic_id FROM users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION is_customer_in_clinic(clinic_uuid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM clinic_customers
    WHERE customer_id = auth.uid() AND clinic_id = clinic_uuid
  );
$$;

CREATE OR REPLACE FUNCTION is_clinic_subscription_active(clinic_uuid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM clinics
    WHERE id = clinic_uuid
      AND subscription_status IN ('active', 'trial')
      AND (subscription_end_date IS NULL OR subscription_end_date >= CURRENT_DATE)
  );
$$;


-- 7. Row Level Security Policies

-- USERS TABLE
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

CREATE POLICY users_select_policy ON users
  FOR SELECT USING (
    auth.uid() = id
    OR get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() IN ('clinic_admin', 'doctor')
      AND (
        clinic_id = get_current_user_clinic_id()
        OR (role = 'customer' AND EXISTS (
          SELECT 1 FROM clinic_customers
          WHERE customer_id = users.id AND clinic_id = get_current_user_clinic_id()
        ))
      )
    )
  );

CREATE POLICY users_insert_policy ON users
  FOR INSERT WITH CHECK (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'clinic_admin'
      AND (
        (role = 'doctor' AND clinic_id = get_current_user_clinic_id() AND created_by_clinic_id = get_current_user_clinic_id())
        OR (role = 'customer' AND clinic_id IS NULL AND created_by_clinic_id = get_current_user_clinic_id())
      )
    )
  );

CREATE POLICY users_update_policy ON users
  FOR UPDATE USING (
    auth.uid() = id
    OR get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'clinic_admin'
      AND (
        (role = 'doctor' AND clinic_id = get_current_user_clinic_id())
        OR (role = 'customer' AND EXISTS (
          SELECT 1 FROM clinic_customers
          WHERE customer_id = users.id AND clinic_id = get_current_user_clinic_id()
        ))
      )
    )
  );

CREATE POLICY users_delete_policy ON users
  FOR DELETE USING (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'clinic_admin'
      AND role = 'doctor'
      AND clinic_id = get_current_user_clinic_id()
    )
  );


-- CLINICS TABLE
ALTER TABLE clinics ENABLE ROW LEVEL SECURITY;

CREATE POLICY clinics_select_policy ON clinics
  FOR SELECT USING (
    get_current_user_role() = 'super_admin'
    OR id = get_current_user_clinic_id()
    OR is_customer_in_clinic(id)
  );

CREATE POLICY clinics_write_policy ON clinics
  FOR ALL USING (get_current_user_role() = 'super_admin');


-- SUBSCRIPTION LOGS
ALTER TABLE subscription_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY subscription_logs_select_policy ON subscription_logs
  FOR SELECT USING (
    get_current_user_role() = 'super_admin'
    OR clinic_id = get_current_user_clinic_id()
  );

CREATE POLICY subscription_logs_insert_policy ON subscription_logs
  FOR INSERT WITH CHECK (get_current_user_role() = 'super_admin');


-- CLINIC CUSTOMERS
ALTER TABLE clinic_customers ENABLE ROW LEVEL SECURITY;

CREATE POLICY clinic_customers_select_policy ON clinic_customers
  FOR SELECT USING (
    get_current_user_role() = 'super_admin'
    OR clinic_id = get_current_user_clinic_id()
    OR customer_id = auth.uid()
  );

CREATE POLICY clinic_customers_insert_policy ON clinic_customers
  FOR INSERT WITH CHECK (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'clinic_admin'
      AND clinic_id = get_current_user_clinic_id()
    )
  );

CREATE POLICY clinic_customers_delete_policy ON clinic_customers
  FOR DELETE USING (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'clinic_admin'
      AND clinic_id = get_current_user_clinic_id()
    )
  );


-- BOOKINGS TABLE
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;

CREATE POLICY bookings_select_policy ON bookings
  FOR SELECT USING (
    get_current_user_role() = 'super_admin'
    OR (get_current_user_role() = 'clinic_admin' AND clinic_id = get_current_user_clinic_id())
    OR (get_current_user_role() = 'doctor' AND assigned_doctor_id = auth.uid())
    OR (get_current_user_role() = 'customer' AND customer_id = auth.uid())
  );

CREATE POLICY bookings_insert_policy ON bookings
  FOR INSERT WITH CHECK (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'customer'
      AND customer_id = auth.uid()
      AND is_customer_in_clinic(clinic_id)
      AND is_clinic_subscription_active(clinic_id)
    )
  );

CREATE POLICY bookings_update_policy ON bookings
  FOR UPDATE USING (
    get_current_user_role() = 'super_admin'
    OR (get_current_user_role() = 'clinic_admin' AND clinic_id = get_current_user_clinic_id())
    OR (get_current_user_role() = 'doctor' AND assigned_doctor_id = auth.uid())
    OR (get_current_user_role() = 'customer' AND customer_id = auth.uid())
  )
  WITH CHECK (
    get_current_user_role() = 'super_admin'
    OR (get_current_user_role() = 'clinic_admin' AND clinic_id = get_current_user_clinic_id())
    OR (get_current_user_role() = 'doctor' AND assigned_doctor_id = auth.uid() AND status = 'completed')
    OR (get_current_user_role() = 'customer' AND customer_id = auth.uid() AND status = 'cancelled')
  );


-- TREATMENTS TABLE (CRITICAL PRIVACY RULE ENFORCED)
ALTER TABLE treatments ENABLE ROW LEVEL SECURITY;

CREATE POLICY treatments_select_policy ON treatments
  FOR SELECT USING (
    get_current_user_role() = 'super_admin'
    -- Clinic Admins and Doctors can only see treatments of their own clinic
    OR (get_current_user_role() IN ('clinic_admin', 'doctor') AND clinic_id = get_current_user_clinic_id())
    -- Customers can see all their own treatment histories
    OR (get_current_user_role() = 'customer' AND customer_id = auth.uid())
  );

CREATE POLICY treatments_insert_policy ON treatments
  FOR INSERT WITH CHECK (
    get_current_user_role() = 'super_admin'
    OR (
      get_current_user_role() = 'doctor'
      AND doctor_id = auth.uid()
      AND clinic_id = get_current_user_clinic_id()
      AND EXISTS (
        SELECT 1 FROM bookings
        WHERE id = booking_id AND assigned_doctor_id = auth.uid() AND customer_id = treatments.customer_id
      )
    )
  );


-- APP VERSIONS
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;

CREATE POLICY app_versions_select_policy ON app_versions
  FOR SELECT USING (true); -- Public read

CREATE POLICY app_versions_write_policy ON app_versions
  FOR ALL USING (get_current_user_role() = 'super_admin');


-- DEVICE TOKENS
ALTER TABLE device_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY device_tokens_policy ON device_tokens
  FOR ALL USING (user_id = auth.uid());


-- 8. Triggers for Business Validation & Bookings

-- Prevent duplicate pending booking requests to the same clinic
CREATE OR REPLACE FUNCTION check_duplicate_pending_booking()
RETURNS trigger AS $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM bookings
    WHERE customer_id = NEW.customer_id
      AND clinic_id = NEW.clinic_id
      AND status = 'pending'
      AND id <> NEW.id
  ) THEN
    RAISE EXCEPTION 'You already have a pending booking request for this clinic.' USING ERRCODE = '23505';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_duplicate_pending_booking
  BEFORE INSERT ON bookings
  FOR EACH ROW
  EXECUTE FUNCTION check_duplicate_pending_booking();


-- Update booking timestamps
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = timezone('utc'::text, now());
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_bookings_timestamp
  BEFORE UPDATE ON bookings
  FOR EACH ROW
  EXECUTE FUNCTION update_modified_column();


-- 9. Secure Password Management RPCs

CREATE OR REPLACE FUNCTION change_password(p_new_password TEXT)
RETURNS BOOLEAN AS $$
BEGIN
  UPDATE users
  SET password_hash = crypt(p_new_password, gen_salt('bf', 10))
  WHERE id = auth.uid();
  RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION admin_reset_password(p_user_id UUID, p_new_password TEXT)
RETURNS BOOLEAN AS $$
BEGIN
  -- Check authorization
  IF get_current_user_role() = 'super_admin' OR (
    get_current_user_role() = 'clinic_admin' AND (
      (SELECT clinic_id FROM users WHERE id = p_user_id) = get_current_user_clinic_id()
      OR
      EXISTS (
        SELECT 1 FROM clinic_customers
        WHERE customer_id = p_user_id AND clinic_id = get_current_user_clinic_id()
      )
    )
  ) THEN
    UPDATE users
    SET password_hash = crypt(p_new_password, gen_salt('bf', 10))
    WHERE id = p_user_id;
    RETURN TRUE;
  ELSE
    RAISE EXCEPTION 'Unauthorized to reset password for this user.' USING ERRCODE = '42501';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
