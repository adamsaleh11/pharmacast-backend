-- Supabase RLS policy artifact for PharmaForecast.
-- Apply this in Supabase after the production Flyway schema is present.
-- Spring Boot still owns authorization decisions for backend API requests.
-- Supabase service_role is expected to bypass RLS for trusted backend orchestration.

CREATE OR REPLACE FUNCTION public.current_app_user_organization_id()
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT au.organization_id
    FROM public.app_users au
    WHERE au.id = auth.uid()
$$;

ALTER TABLE drugs ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE dispensing_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE forecasts ENABLE ROW LEVEL SECURITY;
ALTER TABLE drug_thresholds ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE csv_uploads ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY drugs_public_read
ON drugs
FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY drugs_service_role_all
ON drugs
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY organizations_member_access
ON organizations
FOR ALL
TO authenticated
USING (id = public.current_app_user_organization_id())
WITH CHECK (id = public.current_app_user_organization_id());

CREATE POLICY organizations_service_role_all
ON organizations
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY locations_member_access
ON locations
FOR ALL
TO authenticated
USING (organization_id = public.current_app_user_organization_id())
WITH CHECK (organization_id = public.current_app_user_organization_id());

CREATE POLICY locations_service_role_all
ON locations
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY app_users_member_access
ON app_users
FOR ALL
TO authenticated
USING (organization_id = public.current_app_user_organization_id())
WITH CHECK (organization_id = public.current_app_user_organization_id());

CREATE POLICY app_users_service_role_all
ON app_users
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY notification_settings_member_access
ON notification_settings
FOR ALL
TO authenticated
USING (organization_id = public.current_app_user_organization_id())
WITH CHECK (organization_id = public.current_app_user_organization_id());

CREATE POLICY notification_settings_service_role_all
ON notification_settings
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY notifications_member_access
ON notifications
FOR ALL
TO authenticated
USING (organization_id = public.current_app_user_organization_id())
WITH CHECK (organization_id = public.current_app_user_organization_id());

CREATE POLICY notifications_service_role_all
ON notifications
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY dispensing_records_member_access
ON dispensing_records
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY dispensing_records_service_role_all
ON dispensing_records
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY forecasts_member_access
ON forecasts
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY forecasts_service_role_all
ON forecasts
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY drug_thresholds_member_access
ON drug_thresholds
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY drug_thresholds_service_role_all
ON drug_thresholds
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY stock_adjustments_member_access
ON stock_adjustments
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY stock_adjustments_service_role_all
ON stock_adjustments
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY purchase_orders_member_access
ON purchase_orders
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY purchase_orders_service_role_all
ON purchase_orders
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY csv_uploads_member_access
ON csv_uploads
FOR ALL
TO authenticated
USING (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY csv_uploads_service_role_all
ON csv_uploads
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY chat_messages_member_access
ON chat_messages
FOR ALL
TO authenticated
USING (
    user_id = auth.uid()
    AND
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
)
WITH CHECK (
    user_id = auth.uid()
    AND
    location_id IN (
        SELECT l.id
        FROM public.locations l
        WHERE l.organization_id = public.current_app_user_organization_id()
    )
);

CREATE POLICY chat_messages_service_role_all
ON chat_messages
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);
