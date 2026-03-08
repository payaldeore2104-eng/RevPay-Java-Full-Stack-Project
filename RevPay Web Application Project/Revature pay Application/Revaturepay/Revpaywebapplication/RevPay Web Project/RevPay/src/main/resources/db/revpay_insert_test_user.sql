-- Insert Test Admin User (password = 'admin123' bcrypt-hashed)
INSERT INTO users(full_name, email, phone, password_hash, role)
VALUES('Saikumar Patil', 'saikumarpatil31@gmail.com', '9000000000',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ROLE_ADMIN');

-- Give the admin a wallet with 10,000 INR
INSERT INTO wallet(user_id, balance, currency) VALUES(user_seq.CURRVAL, 10000.00, 'INR');

COMMIT;

SELECT id, full_name, email FROM users;
