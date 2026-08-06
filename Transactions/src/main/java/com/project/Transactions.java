package com.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

enum TransactionType {
    PURCHASE,
    REFUND,
    AUTHORIZATION,
    REVERSAL,
    CHARGEBACK
,
    PAYMENT
}

enum TransactionStatus {
    PENDING,
    AUTHORIZED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum PaymentMethod {
    CHIP,
    SWIPE,
    CONTACTLESS,
    ONLINE,
    MOBILE_WALLET
}

@Entity
@Table(
    name = "TRANSACTIONS",
    indexes = {
        @Index(name = "idx_transaction_card_number", columnList = "CARD_NUMBER"),
        @Index(name = "idx_transaction_merchant_id", columnList = "MERCHANT_ID"),
        @Index(name = "idx_transaction_transaction_date", columnList = "TRANSACTION_TIMESTAMP"),
        @Index(name = "idx_merchant_id_transaction_date", columnList = "MERCHANT_ID, TRANSACTION_TIMESTAMP")
    }
)
public class Transactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @Column(name = "CARD_NUMBER")
    private Long cardNumber;

    @Column(name = "CARD_HOLDER_NAME")
    private String cardHolderName;

    @Column(name = "AMOUNT")
    private BigDecimal amount;

    @Column(name = "CURRENCY")
    private String currency;

    @Column(name = "MERCHANT_ID")
    private Long merchantId;

    @Column(name = "MERCHANT_NAME")
    private String merchantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE")
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAYMENT_METHOD")
    private PaymentMethod paymentMethod;

    @Column(name = "TRANSACTION_TIMESTAMP")
    private LocalDateTime timestamp;

    @Column(name = "AUTHORIZATION_CODE")
    private String authorizationCode;

    @Column(name = "REFERENCE_NUMBER")
    private Long referenceNumber;

    @Column(name = "INTERNATIONAL")
    private boolean international;

    @Column(name = "FEE")
    private BigDecimal fee;

    @Column(name = "CREDIT_RESERVED", nullable = false)
    private boolean creditReserved;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(Long cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public Long getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(Long referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public boolean isInternational() {
        return international;
    }

    public void setInternational(boolean international) {
        this.international = international;
    }

    public boolean isCreditReserved() { return creditReserved; }
    public void setCreditReserved(boolean creditReserved) { this.creditReserved = creditReserved; }


    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }
}