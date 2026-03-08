# Security Documentation

RevPay handles sensitive financial data. This document outlines the comprehensive security measures implemented across the application.

## Authentication

RevPay employs a robust multi-factor authentication (MFA) system.

*   **Secure Login System:** Users must authenticate using a combination of Email/Phone and a strong password. Brute-force protection is enforced by tracking failed `login_attempts` in the `users` table.
*   **OTP Email Verification (2FA):** Upon successful password validation, a 6-digit One-Time Password (OTP) is generated and sent to the user's registered email via SMTP. The OTP is valid for a strict 5-minute window before expiration.
*   **Session Management:** Spring Security (or manual session binding) binds the active user identity to a secure, HTTP-only server session.

## Password Security

*   **Password Hashing:** Passwords are never stored in plaintext. They are hashed using secure, industry-standard cryptographic algorithms (e.g., BCrypt or PBKDF2 via Spring Security's `PasswordEncoder`).
*   **Security Questions:** Account recovery relies on user-defined security questions. The answers to these questions are also hashed in the database, preventing internal exposure.

## Transaction Security

*   **Transaction PIN Validation:** Every outbound money movement (sending money, paying an invoice, withdrawing to a bank) requires the user to input a separate, 4-6 digit Transaction PIN. This ensures that even if a session is hijacked, funds cannot be moved.
*   **Database Constraints:** Pl/SQL routines (e.g., `transfer_money`) check for sufficient balance atomically, preventing race conditions or double-spending.

## Authorization

RevPay implements **Role-Based Access Control (RBAC)** to restrict interface and functional access.

*   **Personal User (`ROLE_USER` / `PERSONAL`):** Has access to the digital wallet, P2P transfers, loan applications, and personal spending analytics.
*   **Business User (`ROLE_BUSINESS` / `BUSINESS`):** Inherits personal capabilities but gains access to the Business Dashboard, Invoice Generation, Customer Management, and Revenue Analytics.

UI elements and Controller endpoints are guarded based on these roles.

## Data Protection

The PCI-DSS scope is minimized through strict data-at-rest encryption.

*   **Encrypted Card Numbers:** Credit and Debit card numbers (PANs) are encrypted using AES-256 (Advanced Encryption Standard) before being stored in the `cards` table. Only the last 4 digits are ever decrypted for UI display.
*   **Encrypted Bank Account Numbers:** Checking and Routing numbers are symmetrically encrypted in the `bank_accounts` table.
*   **Prepared Statements:** All database interactions use parameterized queries (via Spring JdbcTemplate/JPA) to entirely eliminate SQL Injection vulnerabilities.

## Security Monitoring

*   **Failed Login Detection:** Accounts lock out after a threshold of failed login attempts to prevent dictionary attacks.
*   **Notification Alerts:** System alerts are instantly dispatched to users (via in-app notification and email) for critical security events, such as password changes, new device logins, or unusually large transaction volumes.
