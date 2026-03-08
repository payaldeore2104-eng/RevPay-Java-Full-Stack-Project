package com.revpay.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "cards")
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cards_seq")
    @SequenceGenerator(name = "cards_seq", sequenceName = "card_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "card_number_encrypted", nullable = false)
    private String cardNumberEncrypted;

    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;

    @Column(name = "expiry_date", nullable = false, length = 10)
    private String expiryDate;

    @Column(name = "cvv", length = 10)
    private String cvv;

    @Column(name = "billing_address")
    private String billingAddress;

    @Column(name = "card_pin")
    private String cardPin;

    @Column(name = "is_default")
    private Integer isDefault = 0;

    @Column(name = "card_type")
    private String cardType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Date createdAt;

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

    public String getCardNumberEncrypted() {
        return cardNumberEncrypted;
    }

    public void setCardNumberEncrypted(String cardNumberEncrypted) {
        this.cardNumberEncrypted = cardNumberEncrypted;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getCardPin() {
        return cardPin;
    }

    public void setCardPin(String cardPin) {
        this.cardPin = cardPin;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
