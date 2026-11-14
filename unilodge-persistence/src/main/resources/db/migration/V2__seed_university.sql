-- Seed the first institution: Palm University College (Shai Hills, Greater Accra).
-- Additional institutions can be added later from the admin dashboard without
-- any schema or code changes.

INSERT INTO universities (id, name, short_name, city, region, latitude, longitude, active, created_at, updated_at, version)
SELECT '11111111-1111-1111-1111-111111111111',
       'Palm University College',
       'Palm UC',
       'Shai Hills',
       'Greater Accra',
       5.8936,
       0.0378,
       true,
       now(),
       now(),
       0
WHERE NOT EXISTS (
    SELECT 1 FROM universities WHERE lower(name) = lower('Palm University College')
);
