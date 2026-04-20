CREATE OR REPLACE FUNCTION bootstrap_first_owner_user(
    p_auth_user_id uuid,
    p_email text,
    p_organization_name text,
    p_location_name text,
    p_location_address text
)
RETURNS TABLE (
    organization_id uuid,
    location_id uuid,
    user_id uuid
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_existing_user app_users%ROWTYPE;
    v_organization_id uuid;
    v_location_id uuid;
    v_user_id uuid;
BEGIN
    IF p_auth_user_id IS NULL THEN
        RAISE EXCEPTION 'p_auth_user_id is required';
    END IF;
    IF p_email IS NULL OR btrim(p_email) = '' THEN
        RAISE EXCEPTION 'p_email is required';
    END IF;
    IF p_organization_name IS NULL OR btrim(p_organization_name) = '' THEN
        RAISE EXCEPTION 'p_organization_name is required';
    END IF;
    IF p_location_name IS NULL OR btrim(p_location_name) = '' THEN
        RAISE EXCEPTION 'p_location_name is required';
    END IF;
    IF p_location_address IS NULL OR btrim(p_location_address) = '' THEN
        RAISE EXCEPTION 'p_location_address is required';
    END IF;

    SELECT *
    INTO v_existing_user
    FROM app_users
    WHERE id = p_auth_user_id;

    IF FOUND THEN
        RETURN QUERY
            SELECT
                v_existing_user.organization_id,
                l.id,
                v_existing_user.id
            FROM locations l
            WHERE l.organization_id = v_existing_user.organization_id
              AND l.deactivated_at IS NULL
            ORDER BY l.created_at ASC
            LIMIT 1;
        RETURN;
    END IF;

    INSERT INTO organizations (name)
    VALUES (btrim(p_organization_name))
    RETURNING id INTO v_organization_id;

    INSERT INTO locations (organization_id, name, address)
    VALUES (v_organization_id, btrim(p_location_name), btrim(p_location_address))
    RETURNING id INTO v_location_id;

    INSERT INTO app_users (id, organization_id, email, role)
    VALUES (p_auth_user_id, v_organization_id, lower(btrim(p_email)), 'owner')
    RETURNING id INTO v_user_id;

    INSERT INTO notification_settings (organization_id)
    VALUES (v_organization_id)
    ON CONFLICT ON CONSTRAINT uq_notification_settings_organization DO NOTHING;

    RETURN QUERY
        SELECT v_organization_id, v_location_id, v_user_id;
END;
$$;

COMMENT ON FUNCTION bootstrap_first_owner_user(uuid, text, text, text, text)
IS 'Bootstrap a first-time Supabase Auth user into PharmaForecast. Frontend signup metadata expected: organization_name, location_name, location_address. The Supabase auth user id and email must be supplied by trusted backend context.';
