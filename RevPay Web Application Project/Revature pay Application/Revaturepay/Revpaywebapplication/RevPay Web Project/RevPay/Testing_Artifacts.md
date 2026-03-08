# Testing Artifacts

This document outlines the testing strategy, frameworks, and key scenarios validated within the RevPay system.

## Unit Testing

RevPay employs comprehensive unit testing to ensure individual components behave as expected in isolation.

*   **Testing Framework:** JUnit 5
*   **Mocking Framework:** Mockito (for isolating services from database dependencies)
*   **Assertion Library:** AssertJ / JUnit Assertions

### Tested Components

The core business logic resides in the Service Layer, which is heavily tested:

1.  **`UserService` Tests:**
    *   Validates user registration logic (checking for duplicate emails, password complexity).
    *   Validates OTP generation length and verification logic.
    *   Ensures 2FA setup correctly hashes security answers.

2.  **`WalletService` Tests:**
    *   Validates wallet balance updates (adding/withdrawing).
    *   Ensures low-balance checks trigger correctly.
    *   Validates the AES encryption/decryption of linked card numbers and bank accounts.
    *   Verifies default card selection logic.

3.  **`TransactionService` Tests:**
    *   Validates money transfer logic (sufficient balance checks).
    *   Ensures transaction PIN verification succeeds before finalizing transfers.
    *   Validates transaction history retrieval limits and sorting.

4.  **`InvoiceService` Tests:**
    *   Validates invoice creation (PDF generation logic, total calculation).
    *   Ensures state transitions (`DRAFT` → `SENT` → `PAID`).

5.  **`LoanService` Tests:**
    *   Validates EMI calculation formulas against expected mathematical outputs.
    *   Checks loan repayment logic (updating remaining balance vs. EMI).

## Integration Testing

Integration testing ensures that the various layers (Controller → Service → Repository → Database) function correctly together.

*   **Frameworks:** Spring Boot Test (`@SpringBootTest`), MockMvc for web layer testing.

### Key Integration Tests

*   **End-to-End Payment Workflow:** Simulates a user logging in, entering a transaction PIN, and calling the `transfer_money` stored procedure, verifying the database reflects the balance changes in both wallets.
*   **Loan Application Process:** Verifies an application transitions smoothly from Submission → Pending → Approved (simulated) → Repayment setup.
*   **Notification Generation:** Ensures that when a transaction completes, a corresponding row is inserted into the `notifications` table for the receiver.

## Test Scenarios (Manual & Automated)

The following core user journeys form the foundation of our testing suite:

1.  **User Login:**
    *   *Scenario:* User enters valid credentials, receives an OTP via email, enters the correct OTP within 5 minutes, and is redirected to the dashboard.
2.  **Send Money:**
    *   *Scenario:* User attempts to send an amount exceeding their balance. (Expected: Error).
    *   *Scenario:* User sends a valid amount, enters the correct transaction PIN. (Expected: Sender balance decreases, receiver balance increases, transaction recorded, notification sent).
3.  **Request Money:**
    *   *Scenario:* User sends a request, requestee approves it and enters PIN. (Expected: Funds move).
4.  **Add Card:**
    *   *Scenario:* User adds a card with invalid CVV format. (Expected: Validation error).
    *   *Scenario:* User adds a valid card. (Expected: Number is encrypted in the database).
5.  **Create Invoice:**
    *   *Scenario:* Business user creates an invoice, adds line items. (Expected: Total auto-calculates, PDF is generated and status is `DRAFT`).
6.  **Apply Loan:**
    *   *Scenario:* User calculates an EMI on a principal amount, submits financial details. (Expected: Loan status set to `PENDING`, document uploaded securely).
