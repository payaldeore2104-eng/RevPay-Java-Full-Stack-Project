# RevPay – Digital Payment and Business Finance Platform

## Project Description

RevPay is a full-stack monolithic financial web application designed to facilitate secure and efficient financial management for both personal and business users. The platform provides a comprehensive suite of tools including:

*   **Digital Wallet:** A core feature for managing personal funds.
*   **Money Transfers:** Secure peer-to-peer (P2P) transactions.
*   **Payment Methods Management:** Link and manage bank accounts and credit/debit cards.
*   **Invoice Generation:** Tools for businesses to bill clients and track payments.
*   **Loan Application System:** A streamlined process for requesting and managing loans.
*   **Business Analytics Dashboard:** Visual insights into spending and revenue.
*   **Notification System:** Real-time alerts for account activity.

## Technology Stack

**Backend**
*   Java 17
*   Spring Boot 2.7
*   Spring Data JPA (Hibernate)

**Frontend**
*   HTML5
*   CSS3 (Custom styling with Dark Mode support)
*   JavaScript (Vanilla & Chart.js)
*   Thymeleaf (Server-side rendering)

**Database**
*   Oracle Database (Oracle 12c Dialect)
*   PL/SQL Stored Procedures & Triggers

**Tools & Infrastructure**
*   Maven (Dependency Management)
*   Git (Version Control)
*   JUnit (Testing Framework)
*   Logback / SLF4J (Logging)

## Features

**User Management**
*   User registration with role selection (Personal/Business)
*   Login authentication with 2FA
*   OTP email verification
*   Profile and preference management

**Wallet System**
*   Add money via linked payment methods
*   Withdraw money to linked accounts
*   Real-time balance tracking and low-balance alerts

**Payments**
*   Send money instantly using phone or email
*   Request money from other users
*   Secure transaction PIN authorization

**Business Features**
*   Invoice generation and tracking (Draft, Sent, Paid, Overdue)
*   Loan application and EMI calculations

**Analytics**
*   Categorized spending analytics
*   Revenue analysis and charting
*   Top payee identification and monthly trends

**Notifications**
*   Automated transaction alerts
*   Payment request notifications
*   Security and system alerts

## Installation Instructions

Follow these steps to run the RevPay application locally on your machine:

1.  **Clone the repository**
    ```bash
    git clone <repository_url>
    cd RevPay
    ```

2.  **Configure the Database**
    *   Ensure Oracle Database is installed and running.
    *   Create a user/schema for the application (e.g., `revpay_user`).
    *   Update `src/main/resources/application.properties` with your Oracle connection details (url, username, password).

3.  **Run SQL Scripts**
    *   Execute the initialization scripts located in `src/main/resources/` (e.g., `schema_mod1.sql` through `schema_mod5_6.sql` and `alter_cards.sql`) to generate the required tables, sequences, and stored procedures.

4.  **Configure Email Properties (Optional but Recommended)**
    *   Update `application.properties` with valid SMTP credentials for OTP and notification emails.

5.  **Build and Start the Application**
    ```bash
    mvn clean install
    mvn spring-boot:run
    ```

6.  **Access the Application**
    *   Open your web browser and navigate to: `http://localhost:8080/`
