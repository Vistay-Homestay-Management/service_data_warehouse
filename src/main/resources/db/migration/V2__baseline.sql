SET search_path TO app, extensions, public;

CREATE TABLE etl_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_name varchar(160) NOT NULL,
    source_service varchar(80) NOT NULL,
    load_type varchar(24) NOT NULL,
    watermark_from timestamptz,
    watermark_to timestamptz,
    status varchar(24) NOT NULL DEFAULT 'RUNNING',
    rows_read bigint NOT NULL DEFAULT 0,
    rows_inserted bigint NOT NULL DEFAULT 0,
    rows_updated bigint NOT NULL DEFAULT 0,
    rows_rejected bigint NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_message text,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_etl_load_type CHECK (load_type IN ('FULL', 'INCREMENTAL', 'BACKFILL', 'REPLAY')),
    CONSTRAINT ck_etl_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_etl_counts CHECK (rows_read >= 0 AND rows_inserted >= 0 AND rows_updated >= 0 AND rows_rejected >= 0),
    CONSTRAINT ck_etl_watermark CHECK (watermark_to IS NULL OR watermark_from IS NULL OR watermark_to >= watermark_from),
    CONSTRAINT ck_etl_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX ix_etl_runs_pipeline ON etl_runs(pipeline_name, started_at DESC);

CREATE TABLE ingested_events (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    event_version integer NOT NULL CHECK (event_version > 0),
    source_service varchar(80) NOT NULL,
    aggregate_type varchar(100),
    aggregate_id uuid,
    correlation_id varchar(100),
    causation_id varchar(100),
    occurred_at timestamptz NOT NULL,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    payload jsonb NOT NULL,
    payload_sha256 char(64) NOT NULL,
    processing_status varchar(24) NOT NULL DEFAULT 'RECEIVED',
    processing_error text,
    CONSTRAINT ck_ingested_event_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_ingested_event_status CHECK (processing_status IN ('RECEIVED', 'PROJECTED', 'FAILED', 'QUARANTINED'))
);
CREATE INDEX ix_ingested_event_type ON ingested_events(source_service, event_type, occurred_at);
CREATE INDEX ix_ingested_event_retry ON ingested_events(processing_status, ingested_at) WHERE processing_status IN ('RECEIVED', 'FAILED');

CREATE TABLE ingestion_checkpoints (
    source_name varchar(160) NOT NULL,
    partition_key varchar(160) NOT NULL DEFAULT 'default',
    last_cursor varchar(500),
    last_event_id uuid,
    last_source_updated_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (source_name, partition_key)
);

CREATE TABLE data_quality_issues (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    etl_run_id uuid REFERENCES etl_runs(id) ON DELETE SET NULL,
    source_service varchar(80) NOT NULL,
    source_record_type varchar(100) NOT NULL,
    source_record_id varchar(180),
    rule_code varchar(100) NOT NULL,
    severity varchar(16) NOT NULL,
    description varchar(2000) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'OPEN',
    detected_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    resolution_note varchar(2000),
    CONSTRAINT ck_data_quality_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
    CONSTRAINT ck_data_quality_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED'))
);
CREATE INDEX ix_data_quality_open ON data_quality_issues(status, severity, detected_at DESC) WHERE status IN ('OPEN', 'ACKNOWLEDGED');

CREATE TABLE dim_date (
    date_key integer PRIMARY KEY,
    calendar_date date NOT NULL UNIQUE,
    day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    day_name varchar(16) NOT NULL,
    day_of_month smallint NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    week_of_year smallint NOT NULL CHECK (week_of_year BETWEEN 1 AND 53),
    month_number smallint NOT NULL CHECK (month_number BETWEEN 1 AND 12),
    month_name varchar(16) NOT NULL,
    quarter_number smallint NOT NULL CHECK (quarter_number BETWEEN 1 AND 4),
    year_number smallint NOT NULL,
    is_weekend boolean NOT NULL,
    is_holiday boolean NOT NULL DEFAULT false,
    holiday_name varchar(120)
);

-- SCD Type 2: location/model/status changes create a new current row.
CREATE TABLE dim_property (
    property_key bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    property_id uuid NOT NULL,
    property_code varchar(50) NOT NULL,
    property_name varchar(180) NOT NULL,
    property_type varchar(32),
    building_id uuid,
    building_name varchar(180),
    area_id uuid,
    area_name varchar(160),
    city_id uuid,
    city_name varchar(120),
    operating_model varchar(24),
    operational_status varchar(32),
    bedroom_count smallint,
    capacity smallint,
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    current_row boolean NOT NULL DEFAULT true,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    CONSTRAINT ck_dim_property_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_dim_property_current CHECK (
        (current_row AND valid_to IS NULL)
        OR (NOT current_row AND valid_to IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_dim_property_current ON dim_property(property_id) WHERE current_row;
CREATE INDEX ix_dim_property_history ON dim_property(property_id, valid_from, valid_to);

CREATE TABLE dim_staff (
    staff_key bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id uuid NOT NULL,
    employee_code varchar(40),
    display_name varchar(180) NOT NULL,
    user_type varchar(24),
    primary_role varchar(64),
    branch_reference_id uuid,
    active boolean NOT NULL,
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    current_row boolean NOT NULL DEFAULT true,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    CONSTRAINT ck_dim_staff_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_dim_staff_current CHECK (
        (current_row AND valid_to IS NULL)
        OR (NOT current_row AND valid_to IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_dim_staff_current ON dim_staff(admin_user_id) WHERE current_row;
CREATE INDEX ix_dim_staff_history ON dim_staff(admin_user_id, valid_from, valid_to);

-- Deliberately excludes name, phone, email, password and token data.
CREATE TABLE dim_customer_masked (
    customer_key bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id uuid NOT NULL UNIQUE,
    nationality_code char(3),
    age_band varchar(16),
    returning_guest boolean NOT NULL DEFAULT false,
    first_seen_date date,
    last_seen_date date,
    source_event_id uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_dim_customer_dates CHECK (last_seen_date IS NULL OR first_seen_date IS NULL OR last_seen_date >= first_seen_date)
);

CREATE TABLE dim_channel (
    channel_key smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_code varchar(40) NOT NULL UNIQUE,
    channel_name varchar(100) NOT NULL,
    channel_group varchar(24) NOT NULL,
    ota boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT ck_dim_channel_group CHECK (channel_group IN ('DIRECT', 'ADMIN', 'OTA', 'PARTNER', 'OTHER'))
);

INSERT INTO dim_channel(channel_code, channel_name, channel_group, ota) VALUES
    ('CUSTOMER_WEB', 'Customer Web', 'DIRECT', false),
    ('ADMIN_PORTAL', 'Admin Portal', 'ADMIN', false),
    ('BOOKING_COM', 'Booking.com', 'OTA', true),
    ('AGODA', 'Agoda', 'OTA', true),
    ('AIRBNB', 'Airbnb', 'OTA', true),
    ('OTHER_OTA', 'OTA khác', 'OTA', true),
    ('OTHER', 'Khác', 'OTHER', false)
ON CONFLICT (channel_code) DO NOTHING;

-- Grain: one row per booking, holding booking value (not cash/revenue).
CREATE TABLE fact_bookings (
    booking_id uuid PRIMARY KEY,
    booking_code varchar(40) NOT NULL,
    property_key bigint REFERENCES dim_property(property_key),
    customer_key bigint REFERENCES dim_customer_masked(customer_key),
    channel_key smallint REFERENCES dim_channel(channel_key),
    created_by_staff_key bigint REFERENCES dim_staff(staff_key),
    assigned_staff_key bigint REFERENCES dim_staff(staff_key),
    booking_date_key integer REFERENCES dim_date(date_key),
    check_in_date_key integer REFERENCES dim_date(date_key),
    check_out_date_key integer REFERENCES dim_date(date_key),
    stay_type varchar(24) NOT NULL,
    status varchar(32) NOT NULL,
    duration_value numeric(12,3) NOT NULL,
    price_unit varchar(16) NOT NULL,
    adults smallint NOT NULL,
    children smallint NOT NULL,
    subtotal_amount numeric(19,4) NOT NULL,
    discount_amount numeric(19,4) NOT NULL,
    fee_amount numeric(19,4) NOT NULL,
    tax_amount numeric(19,4) NOT NULL,
    booking_value numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    warehouse_updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_fact_booking_guests CHECK (adults > 0 AND children >= 0),
    CONSTRAINT ck_fact_booking_amounts CHECK (duration_value > 0 AND subtotal_amount >= 0 AND discount_amount >= 0 AND fee_amount >= 0 AND tax_amount >= 0 AND booking_value >= 0),
    CONSTRAINT ck_fact_booking_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_booking_dates ON fact_bookings(check_in_date_key, check_out_date_key);
CREATE INDEX ix_fact_booking_property ON fact_bookings(property_key, booking_date_key);

-- Grain: one property and one calendar night. This provides both numerator
-- and denominator for occupancy, including maintenance/off-market nights.
CREATE TABLE fact_property_nights (
    property_id uuid NOT NULL,
    service_date date NOT NULL,
    property_key bigint NOT NULL REFERENCES dim_property(property_key),
    date_key integer NOT NULL REFERENCES dim_date(date_key),
    booking_id uuid,
    eligible_for_sale boolean NOT NULL,
    occupied boolean NOT NULL,
    unavailable_reason varchar(32),
    allocated_booking_value numeric(19,4) NOT NULL DEFAULT 0,
    currency char(3) NOT NULL DEFAULT 'VND',
    source_event_id uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (property_id, service_date),
    CONSTRAINT ck_fact_property_night_state CHECK (
        (eligible_for_sale AND unavailable_reason IS NULL)
        OR (NOT eligible_for_sale AND unavailable_reason IS NOT NULL)
    ),
    CONSTRAINT ck_fact_property_night_occupancy CHECK (NOT occupied OR (eligible_for_sale AND booking_id IS NOT NULL)),
    CONSTRAINT ck_fact_property_night_reason CHECK (unavailable_reason IS NULL OR unavailable_reason IN ('MAINTENANCE', 'OWNER_USE', 'OFFBOARDING', 'SUSPENDED', 'OTHER')),
    CONSTRAINT ck_fact_property_night_value CHECK (allocated_booking_value >= 0),
    CONSTRAINT ck_fact_property_night_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_property_night_date ON fact_property_nights(date_key, eligible_for_sale, occupied);
CREATE INDEX ix_fact_property_night_dimension_keys ON fact_property_nights(property_key, date_key);

-- Grain: one verified financial transaction. PAYMENT/REFUND/PAYOUT are not
-- booking revenue and must be reported separately.
CREATE TABLE fact_financial_transactions (
    transaction_id uuid PRIMARY KEY,
    booking_id uuid,
    property_key bigint REFERENCES dim_property(property_key),
    customer_key bigint REFERENCES dim_customer_masked(customer_key),
    transaction_date_key integer REFERENCES dim_date(date_key),
    transaction_type varchar(24) NOT NULL,
    payment_method varchar(32),
    provider varchar(80),
    status varchar(24) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    warehouse_updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_fact_transaction_type CHECK (transaction_type IN ('PAYMENT', 'REFUND', 'PAYOUT', 'ADJUSTMENT')),
    CONSTRAINT ck_fact_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_fact_transaction_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_transaction_date ON fact_financial_transactions(transaction_date_key, transaction_type, status);

-- Grain: one revenue-recognition event/period according to an approved policy.
CREATE TABLE fact_revenue_recognition (
    recognition_id uuid PRIMARY KEY,
    booking_id uuid NOT NULL,
    property_key bigint REFERENCES dim_property(property_key),
    recognition_date_key integer REFERENCES dim_date(date_key),
    policy_code varchar(80) NOT NULL,
    recognized_amount numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_fact_revenue_amount CHECK (recognized_amount >= 0),
    CONSTRAINT ck_fact_revenue_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_revenue_date ON fact_revenue_recognition(recognition_date_key, property_key);

-- Grain: one receivable per daily/periodic snapshot date, preserving history.
CREATE TABLE fact_receivable_snapshots (
    receivable_id uuid NOT NULL,
    snapshot_date_key integer NOT NULL REFERENCES dim_date(date_key),
    booking_id uuid,
    customer_key bigint REFERENCES dim_customer_masked(customer_key),
    due_date_key integer REFERENCES dim_date(date_key),
    category varchar(40) NOT NULL,
    status varchar(24) NOT NULL,
    amount_due numeric(19,4) NOT NULL,
    amount_paid numeric(19,4) NOT NULL,
    amount_waived numeric(19,4) NOT NULL,
    outstanding_amount numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    PRIMARY KEY (receivable_id, snapshot_date_key),
    CONSTRAINT ck_fact_receivable_amounts CHECK (amount_due >= 0 AND amount_paid >= 0 AND amount_waived >= 0 AND outstanding_amount >= 0),
    CONSTRAINT ck_fact_receivable_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_receivable_snapshot ON fact_receivable_snapshots(snapshot_date_key, status, due_date_key);

CREATE TABLE fact_payable_snapshots (
    payable_id uuid NOT NULL,
    snapshot_date_key integer NOT NULL REFERENCES dim_date(date_key),
    property_key bigint REFERENCES dim_property(property_key),
    payee_type varchar(24) NOT NULL,
    due_date_key integer REFERENCES dim_date(date_key),
    category varchar(40) NOT NULL,
    status varchar(24) NOT NULL,
    amount_due numeric(19,4) NOT NULL,
    amount_paid numeric(19,4) NOT NULL,
    outstanding_amount numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    PRIMARY KEY (payable_id, snapshot_date_key),
    CONSTRAINT ck_fact_payable_amounts CHECK (amount_due >= 0 AND amount_paid >= 0 AND outstanding_amount >= 0),
    CONSTRAINT ck_fact_payable_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_payable_snapshot ON fact_payable_snapshots(snapshot_date_key, status, due_date_key);

CREATE TABLE fact_expenses (
    expense_id uuid PRIMARY KEY,
    property_key bigint REFERENCES dim_property(property_key),
    work_order_id uuid,
    incurred_date_key integer REFERENCES dim_date(date_key),
    expense_category varchar(40) NOT NULL,
    status varchar(24) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    CONSTRAINT ck_fact_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_fact_expense_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_expense_property_date ON fact_expenses(property_key, incurred_date_key);

CREATE TABLE fact_incidents (
    incident_id uuid PRIMARY KEY,
    property_key bigint REFERENCES dim_property(property_key),
    reported_date_key integer REFERENCES dim_date(date_key),
    resolved_date_key integer REFERENCES dim_date(date_key),
    incident_type varchar(32) NOT NULL,
    severity varchar(16) NOT NULL,
    status varchar(24) NOT NULL,
    responsibility varchar(24),
    resolution_minutes integer,
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    CONSTRAINT ck_fact_incident_duration CHECK (resolution_minutes IS NULL OR resolution_minutes >= 0)
);
CREATE INDEX ix_fact_incident_property ON fact_incidents(property_key, reported_date_key, severity);

CREATE TABLE fact_work_orders (
    work_order_id uuid PRIMARY KEY,
    property_key bigint REFERENCES dim_property(property_key),
    assigned_staff_key bigint REFERENCES dim_staff(staff_key),
    created_date_key integer REFERENCES dim_date(date_key),
    due_date_key integer REFERENCES dim_date(date_key),
    completed_date_key integer REFERENCES dim_date(date_key),
    work_type varchar(32) NOT NULL,
    priority varchar(16) NOT NULL,
    status varchar(24) NOT NULL,
    duration_minutes integer,
    actual_cost numeric(19,4) NOT NULL DEFAULT 0,
    currency char(3) NOT NULL DEFAULT 'VND',
    source_event_id uuid,
    source_updated_at timestamptz NOT NULL,
    CONSTRAINT ck_fact_work_order_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    CONSTRAINT ck_fact_work_order_cost CHECK (actual_cost >= 0),
    CONSTRAINT ck_fact_work_order_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_fact_work_order_property ON fact_work_orders(property_key, created_date_key, status);

CREATE TABLE fact_ota_sync (
    sync_job_id uuid PRIMARY KEY,
    channel_key smallint REFERENCES dim_channel(channel_key),
    property_key bigint REFERENCES dim_property(property_key),
    sync_date_key integer REFERENCES dim_date(date_key),
    sync_type varchar(32) NOT NULL,
    direction varchar(16) NOT NULL,
    status varchar(24) NOT NULL,
    attempt_count integer NOT NULL,
    duration_ms integer,
    source_event_id uuid,
    CONSTRAINT ck_fact_ota_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_fact_ota_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);
CREATE INDEX ix_fact_ota_sync_channel ON fact_ota_sync(channel_key, sync_date_key, status);

CREATE TABLE processed_events (
    event_id uuid NOT NULL,
    consumer_name varchar(120) NOT NULL,
    source_service varchar(80) NOT NULL,
    event_type varchar(160) NOT NULL,
    event_version integer NOT NULL CHECK (event_version > 0),
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);
