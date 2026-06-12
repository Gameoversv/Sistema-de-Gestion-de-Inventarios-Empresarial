-- V5: Seed data — categories, products, and one admin AppUser

INSERT INTO categories (name, description) VALUES
    ('Electronics',     'Electronic devices and accessories'),
    ('Clothing',        'Apparel and fashion items'),
    ('Food & Beverage', 'Food and drinks'),
    ('Tools & Hardware','Workshop and construction tools'),
    ('Office Supplies', 'Stationery and office equipment')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (sku, name, description, price, stock, category_id) VALUES
    ('ELEC-001', 'Laptop Pro 15"',      '15-inch laptop, 16 GB RAM, 512 GB SSD', 1299.99, 50,   1),
    ('ELEC-002', 'Wireless Mouse',      'Ergonomic 2.4 GHz wireless mouse',        29.99, 200,   1),
    ('ELEC-003', 'USB-C Hub 7-in-1',   '4K HDMI, USB 3.0, SD card reader',         49.99, 150,  1),
    ('CLOTH-001','Cotton T-Shirt L',   'Classic cotton t-shirt, size L',            19.99, 500,   2),
    ('FOOD-001', 'Arabica Coffee 1 kg','Premium single-origin arabica beans',        24.99, 100,   3),
    ('FOOD-002', 'Green Tea 50 bags',  'Organic green tea, individually wrapped',     9.99, 300,   3),
    ('TOOL-001', 'Cordless Drill 18 V','Brushless motor, 2-battery kit',             89.99,  75,  4),
    ('OFFC-001', 'A4 Paper Ream',      '500-sheet white A4, 80 g/m²',                8.99, 1000,  5),
    ('OFFC-002', 'Ballpoint Pens 10pk','Blue ink, medium tip, retractable',           4.99, 800,  5)
ON CONFLICT (sku) DO NOTHING;

-- Placeholder admin user synced from Keycloak on first login
INSERT INTO app_users (keycloak_id, email, display_name, role, enabled) VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin@inventory.local', 'System Admin', 'ADMIN', TRUE)
ON CONFLICT (keycloak_id) DO NOTHING;
