# Working Web Application Demonstration Guide

This guide walks through the primary user journeys and functional demonstrations of the RevPay platform. It outlines how a user navigates and utilizes the system from registration through advanced business features.

---

## Step 1: User Registration
**Objective:** Create a new RevPay account securely.
1. Target `http://localhost:8080/register`.
2. The user inputs their Full Name, Email Address, Phone Number, and creates a strong Password.
3. The user selects their desired Account Type: **Personal** or **Business**.
4. Upon submission, an OTP verification email is triggered. The user must verify their email address before the account is activated.

## Step 2: Login
**Objective:** Securely access the dashboard.
1. Target `http://localhost:8080/login`.
2. The user enters their registered Email/Phone and Password.
3. A 6-digit OTP is sent to the registered email address.
4. The user completes the 2FA challenge and is redirected to the main Dashboard (`/dashboard`).

## Step 3: Wallet Management
**Objective:** Fund the wallet and withdraw cash.
1. From the Dashboard, the user navigates to the **Wallet & Balance** page (`/wallet`).
2. Current balance is displayed prominently.
3. **Add Funds:** The user selects a linked funding source (e.g., a Debit Card), enters an amount, and inputs their Transaction PIN to simulate depositing money into the RevPay ecosystem.
4. **Withdraw Funds:** The user selects a linked Bank Account, enters an amount, and inputs their Transaction PIN to withdraw platform funds back to their traditional bank.

## Step 4: Send Money
**Objective:** Peer-to-Peer (P2P) transfers.
1. The user navigates to **Send Money** (`/transactions/send`).
2. The user identifies the recipient by entering their Email Address or Phone Number.
3. The user inputs the transfer amount and an optional note/description.
4. After verifying the recipient name dynamically, the user authorizes the transfer using their Transaction PIN.
5. The system atomically deducts funds from the sender and credits the receiver.

## Step 5: Request Money
**Objective:** Prompt another user for payment.
1. The user navigates to **Request Money** (`/transactions/request`).
2. The user enters the target Email/Phone, amount, and a reason for the request.
3. The recipient receives an instant Notification. The recipient can then click "Pay" on the notification, enter their PIN, and fulfill the request.

## Step 6: Payment Methods
**Objective:** Link external financial instruments.
1. The user navigates to **Manage Cards** (`/wallet/cards`).
2. **Add Debit/Credit Card:** The user inputs card specifics (Cardholder Name, Number, Expiry, CVV). The system encrypts this data before saving.
3. **Add Bank Account:** The user inputs Account Number and Routing/IFSC Code.
4. The user can set any method as their "Default" funding source.

## Step 7: Business Features
**Objective:** Manage business billing operations (Requires Business Role).
1. A Business User navigates to the **Business Dashboard** (`/business/dashboard`).
2. **Create Invoice:** The user navigates to `/business/invoices/create`.
3. The user specifies the Customer details and adds multiple line items (Item Name, Quantity, Unit Price). The total is auto-calculated.
4. The user generates the Invoice, which triggers a notification to the customer. When the customer pays the invoice via their dashboard, funds are credited to the business wallet, and the Invoice status updates to `PAID`.
5. **Track Revenue:** The dashboard displays real-time statistics on pending vs. paid invoices.

## Step 8: Analytics Dashboard
**Objective:** Visualize financial health.
1. The user navigates to **Analytics** (`/analytics`).
2. A macro overview displays Total Received, Total Sent, and Pending Amounts using Chart.js bar graphs.
3. **Spending Analytics:** The user navigates to `/analytics/spending` to view interactive pie charts detailing categorical spending and bar charts highlighting the most frequent payees (merchants or peers).
