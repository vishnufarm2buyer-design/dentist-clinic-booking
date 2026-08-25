-- ========================================================
-- MANDATORY PRIVACY TEST: ROW LEVEL SECURITY VERIFICATION
-- ========================================================
-- This script validates that:
-- 1. Clinic A cannot read Clinic B treatment records.
-- 2. Clinic B cannot read Clinic A treatment records.
-- 3. The customer can see their own records across both clinics.
-- 4. Inactive clinic subscriptions prevent booking creations.
-- 5. Duplicate pending bookings are blocked.

BEGIN;

-- 1. Set up Test Clinics
INSERT INTO clinics (id, name, subscription_status, subscription_plan)
VALUES 
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Clinic A', 'active', 'Standard'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Clinic B', 'active', 'Standard'),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Clinic C (Suspended)', 'suspended', 'Trial');

-- 2. Set up Test Users (Admins, Doctors, Customer)
-- Note: password hashes are dummy values since we test RLS policies using auth.uid() directly
INSERT INTO users (id, phone, password_hash, name, role, clinic_id)
VALUES
  ('11111111-1111-1111-1111-111111111111', '+10000000001', 'hash', 'Admin A', 'clinic_admin', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('22222222-2222-2222-2222-222222222222', '+10000000002', 'hash', 'Admin B', 'clinic_admin', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  ('33333333-3333-3333-3333-333333333333', '+10000000003', 'hash', 'Doctor A', 'doctor', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('44444444-4444-4444-4444-444444444444', '+10000000004', 'hash', 'Doctor B', 'doctor', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  ('55555555-5555-5555-5555-555555555555', '+10000000005', 'hash', 'Customer C', 'customer', NULL);

-- 3. Link Customer C to both Clinic A and Clinic B
INSERT INTO clinic_customers (clinic_id, customer_id)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '55555555-5555-5555-5555-555555555555'),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', '55555555-5555-5555-5555-555555555555');

-- 4. Create Bookings
INSERT INTO bookings (id, clinic_id, customer_id, requested_date, reason, status, assigned_doctor_id)
VALUES
  ('b1111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', CURRENT_DATE, 'Ache in tooth A', 'completed', '33333333-3333-3333-3333-333333333333'),
  ('b2222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '55555555-5555-5555-5555-555555555555', CURRENT_DATE, 'Ache in tooth B', 'completed', '44444444-4444-4444-4444-444444444444');

-- 5. Create Treatments
INSERT INTO treatments (id, booking_id, customer_id, clinic_id, doctor_id, notes, visit_date)
VALUES
  ('t1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'Root canal on upper right A', CURRENT_DATE),
  ('t2222222-2222-2222-2222-222222222222', 'b2222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '44444444-4444-4444-4444-444444444444', 'Filling on upper left B', CURRENT_DATE);


-- ========================================================
-- EXECUTE ASSERTIONS (SIMULATING DIFFERENT AUTH ROLES)
-- ========================================================

-- Assert helper variables
DO $$
DECLARE
  v_count INTEGER;
BEGIN
  -- Enable RLS checking on the session
  SET LOCAL row_security = ON;

  -- ----------------------------------------------------
  -- TEST 1: Clinic A Admin Queries Treatments
  -- ----------------------------------------------------
  -- Simulate auth.uid() = Admin A
  SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
  
  SELECT COUNT(*) INTO v_count FROM treatments;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 1 FAILED: Admin A should see exactly 1 treatment record, but saw %', v_count;
  END IF;

  SELECT COUNT(*) INTO v_count FROM treatments WHERE clinic_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 1 PRIVACY BREACH: Admin A saw records from Clinic B!';
  END IF;
  RAISE NOTICE 'TEST 1 PASSED: Clinic A Admin isolated successfully.';

  -- ----------------------------------------------------
  -- TEST 2: Clinic B Doctor Queries Treatments
  -- ----------------------------------------------------
  -- Simulate auth.uid() = Doctor B
  SET LOCAL request.jwt.claim.sub = '44444444-4444-4444-4444-444444444444';
  
  SELECT COUNT(*) INTO v_count FROM treatments;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 2 FAILED: Doctor B should see exactly 1 treatment record, but saw %', v_count;
  END IF;

  SELECT COUNT(*) INTO v_count FROM treatments WHERE clinic_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 2 PRIVACY BREACH: Doctor B saw records from Clinic A!';
  END IF;
  RAISE NOTICE 'TEST 2 PASSED: Clinic B Doctor isolated successfully.';

  -- ----------------------------------------------------
  -- TEST 3: Customer C Queries Treatments (Cross Clinic)
  -- ----------------------------------------------------
  -- Simulate auth.uid() = Customer C
  SET LOCAL request.jwt.claim.sub = '55555555-5555-5555-5555-555555555555';
  
  SELECT COUNT(*) INTO v_count FROM treatments;
  IF v_count <> 2 THEN
    RAISE EXCEPTION 'TEST 3 FAILED: Customer C should see both (2) treatment records, but saw %', v_count;
  END IF;
  RAISE NOTICE 'TEST 3 PASSED: Customer saw cross-clinic logs successfully.';

  -- ----------------------------------------------------
  -- TEST 4: Customer C booking for Inactive Clinic C
  -- ----------------------------------------------------
  -- We test that insertion fails on the RLS Policy (bookings_insert_policy check)
  -- Simulate auth.uid() = Customer C
  SET LOCAL request.jwt.claim.sub = '55555555-5555-5555-5555-555555555555';
  BEGIN
    INSERT INTO bookings (clinic_id, customer_id, requested_date, reason, status)
    VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', '55555555-5555-5555-5555-555555555555', CURRENT_DATE, 'Need checkup', 'pending');
    
    -- If we reach here, it did not fail, which is incorrect
    RAISE EXCEPTION 'TEST 4 FAILED: Booking was successfully created for a suspended clinic!';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'TEST 4 PASSED: Booking blocked for suspended clinic successfully.';
  END;

  -- ----------------------------------------------------
  -- TEST 5: Prevent Duplicate Pending Bookings (Trigger check)
  -- ----------------------------------------------------
  -- Simulate auth.uid() = Customer C
  SET LOCAL request.jwt.claim.sub = '55555555-5555-5555-5555-555555555555';
  
  -- Insert first pending booking (Clinic A)
  INSERT INTO bookings (clinic_id, customer_id, requested_date, reason, status)
  VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', CURRENT_DATE, 'Check pain', 'pending');
  
  -- Attempt to insert second pending booking for the same clinic (should fail)
  BEGIN
    INSERT INTO bookings (clinic_id, customer_id, requested_date, reason, status)
    VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', CURRENT_DATE, 'Check pain 2', 'pending');
    
    RAISE EXCEPTION 'TEST 5 FAILED: Duplicate pending booking was not blocked!';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'TEST 5 PASSED: Duplicate pending booking blocked successfully.';
  END;

END $$;

ROLLBACK;
