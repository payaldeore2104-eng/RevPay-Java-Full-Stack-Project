package com.revpay.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_seq", allocationSize = 1)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "transaction_pin")
    private String transactionPin;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "tax_id")
    private String taxId;

    private String address;

    @Column(name = "is_verified")
    private Integer isVerified = 0;

    @Column(name = "pref_notif_transactions")
    private Integer prefNotifTransactions = 1;

    @Column(name = "pref_notif_requests")
    private Integer prefNotifRequests = 1;

    @Column(name = "pref_notif_alerts")
    private Integer prefNotifAlerts = 1;

    @Column(name = "login_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "is_active")
    private Integer isLocked = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Date createdAt;

    // Fast Getters/Setters generated
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getTransactionPin() {
        return transactionPin;
    }

    public void setTransactionPin(String transactionPin) {
        this.transactionPin = transactionPin;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Integer isVerified) {
        this.isVerified = isVerified;
    }

    public Integer getPrefNotifTransactions() {
        return prefNotifTransactions;
    }

    public void setPrefNotifTransactions(Integer prefNotifTransactions) {
        this.prefNotifTransactions = prefNotifTransactions;
    }

    public Integer getPrefNotifRequests() {
        return prefNotifRequests;
    }

    public void setPrefNotifRequests(Integer prefNotifRequests) {
        this.prefNotifRequests = prefNotifRequests;
    }

    public Integer getPrefNotifAlerts() {
        return prefNotifAlerts;
    }

    public void setPrefNotifAlerts(Integer prefNotifAlerts) {
        this.prefNotifAlerts = prefNotifAlerts;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Integer getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Integer isLocked) {
        this.isLocked = isLocked;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
