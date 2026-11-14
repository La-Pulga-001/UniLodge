-- UniLodge initial schema (PostgreSQL)
-- Every table is created through Flyway so the application never relies on
-- Hibernate auto-DDL. Column names match the JPA entity mappings exactly.

CREATE TABLE universities (
    id            uuid PRIMARY KEY,
    name          varchar(160) NOT NULL,
    short_name    varchar(30),
    city          varchar(120),
    region        varchar(120),
    latitude      double precision NOT NULL,
    longitude     double precision NOT NULL,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_universities_name ON universities (lower(name));

CREATE TABLE users (
    id                uuid PRIMARY KEY,
    email             varchar(160) NOT NULL,
    password_hash     varchar(100),
    full_name         varchar(80) NOT NULL,
    phone             varchar(40),
    role              varchar(20) NOT NULL,
    status            varchar(30) NOT NULL,
    university_id     uuid REFERENCES universities (id),
    business_name     varchar(120),
    avatar_file       varchar(200),
    email_verified    boolean NOT NULL DEFAULT false,
    admin_verified_at timestamptz,
    original_admin    boolean NOT NULL DEFAULT false,
    admin_note        varchar(1000),
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    version           bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_role_status ON users (role, status);

CREATE TABLE properties (
    id                     uuid PRIMARY KEY,
    landlord_id            uuid NOT NULL REFERENCES users (id),
    university_id          uuid NOT NULL REFERENCES universities (id),
    title                  varchar(120) NOT NULL,
    description            text NOT NULL,
    property_type          varchar(30) NOT NULL,
    rent_amount            numeric(12, 2) NOT NULL,
    negotiable             boolean NOT NULL DEFAULT false,
    utilities_included     boolean NOT NULL DEFAULT false,
    utilities_description  varchar(300),
    furnished              varchar(30) NOT NULL,
    bedrooms               integer NOT NULL,
    bathrooms              integer NOT NULL,
    address                varchar(200) NOT NULL,
    city                   varchar(120) NOT NULL,
    region                 varchar(120),
    latitude               double precision NOT NULL,
    longitude              double precision NOT NULL,
    distance_to_campus_km  double precision,
    move_in_date           date NOT NULL,
    lease_terms            varchar(500),
    status                 varchar(30) NOT NULL,
    admin_note             varchar(1000),
    reviewed_at            timestamptz,
    reviewed_by            uuid REFERENCES users (id),
    view_count             bigint NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    version                bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_properties_status ON properties (status);
CREATE INDEX ix_properties_university ON properties (university_id);
CREATE INDEX ix_properties_landlord ON properties (landlord_id);
CREATE INDEX ix_properties_rent ON properties (rent_amount);
CREATE INDEX ix_properties_distance ON properties (distance_to_campus_km);

CREATE TABLE property_amenities (
    property_id uuid NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    amenity     varchar(60) NOT NULL
);
CREATE INDEX ix_property_amenities_property ON property_amenities (property_id);

CREATE TABLE property_images (
    id            uuid PRIMARY KEY,
    property_id   uuid NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    file_name     varchar(200) NOT NULL,
    original_name varchar(255),
    content_type  varchar(80) NOT NULL,
    file_size     bigint NOT NULL,
    is_cover      boolean NOT NULL DEFAULT false,
    sort_order    integer NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_property_images_property ON property_images (property_id);

CREATE TABLE saved_properties (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    property_id uuid NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint NOT NULL DEFAULT 0,
    CONSTRAINT ux_saved_properties UNIQUE (user_id, property_id)
);

CREATE TABLE student_posts (
    id                 uuid PRIMARY KEY,
    author_id          uuid NOT NULL REFERENCES users (id),
    university_id      uuid NOT NULL REFERENCES universities (id),
    title              varchar(120) NOT NULL,
    body               text NOT NULL,
    preferred_location varchar(160),
    budget_max         numeric(12, 2),
    roommates_needed   integer,
    status             varchar(20) NOT NULL,
    admin_note         varchar(1000),
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_student_posts_status ON student_posts (status);
CREATE INDEX ix_student_posts_university ON student_posts (university_id);
CREATE INDEX ix_student_posts_author ON student_posts (author_id);

CREATE TABLE saved_student_posts (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    uuid NOT NULL REFERENCES student_posts (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint NOT NULL DEFAULT 0,
    CONSTRAINT ux_saved_student_posts UNIQUE (user_id, post_id)
);

CREATE TABLE comments (
    id         uuid PRIMARY KEY,
    post_id    uuid NOT NULL REFERENCES student_posts (id) ON DELETE CASCADE,
    author_id  uuid NOT NULL REFERENCES users (id),
    body       text NOT NULL,
    status     varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_comments_post ON comments (post_id);
CREATE INDEX ix_comments_author ON comments (author_id);

CREATE TABLE otp_tokens (
    id          uuid PRIMARY KEY,
    email       varchar(160) NOT NULL,
    purpose     varchar(30) NOT NULL,
    code_hash   varchar(128) NOT NULL,
    attempts    integer NOT NULL DEFAULT 0,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_otp_lookup ON otp_tokens (lower(email), purpose, created_at DESC);

CREATE TABLE admin_invitations (
    id          uuid PRIMARY KEY,
    email       varchar(160) NOT NULL,
    token       varchar(64) NOT NULL UNIQUE,
    invited_by  uuid NOT NULL REFERENCES users (id),
    status      varchar(20) NOT NULL,
    expires_at  timestamptz NOT NULL,
    accepted_at timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_admin_invitations_email ON admin_invitations (lower(email));

CREATE TABLE admin_audit_logs (
    id           uuid PRIMARY KEY,
    actor_id     uuid REFERENCES users (id),
    action       varchar(40) NOT NULL,
    target_type  varchar(40),
    target_id    varchar(60),
    target_label varchar(200),
    details      varchar(2000),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_audit_created ON admin_audit_logs (created_at DESC);
