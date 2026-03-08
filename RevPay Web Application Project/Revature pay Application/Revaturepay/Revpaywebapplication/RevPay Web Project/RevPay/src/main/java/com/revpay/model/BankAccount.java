package com.revpay.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_acct_gen")
    @SequenceGenerator(name = "bank_acct_gen", sequenceName = "bank_account_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number_encrypted", nullable = false, length = 500)
    private String accountNumberEncrypted;

    @Column(name = "routing_number", nullable = false, length = 50)
    private String routingNumber;

    @Column(name = "account_type", length = 20)
    private String accountType; // SAVINGS or CURRENT

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }

    public BankAccount() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumberEncrypted() {
        return accountNumberEncrypted;
    }

    public void setAccountNumberEncrypted(String accountNumberEncrypted) {
        this.accountNumberEncrypted = accountNumberEncrypted;
    }

    public String getRoutingNumber() {
        return routingNumber;
    }

    public void setRoutingNumber(String routingNumber) {
        this.routingNumber = routingNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
