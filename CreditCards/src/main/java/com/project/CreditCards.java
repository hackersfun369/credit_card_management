package com.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;


@Entity
@Table(
    name = "CREDIT_CARDS",
    indexes = {
        @Index(name = "idx_credit_cards_customer_id", columnList = "CUST_ID")
    }
)
public class CreditCards {

    public enum CardName {
        GOLD,
        SILVER,
        PLATINUM,
        ULTRA_PREMIUM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="CREDIT_ID")
    private Integer creditId;

    @Column(name = "CUST_ID", nullable = false)
    private Integer customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name="CARD_NAME")
    private CardName cardName;
    
    @Enumerated(EnumType.STRING)
    @Column(name="CARD_TYPE")
    private CardType cardType;
    
    @Column(name="CARD_LIMIT")
    private Double cardLimit;
    @Column(name="AVAILABLE_CREDIT")
    private Double availableCredit;
    @Column(name="CARD_NUMBER")
    private Long cardNumber;
    @Column(name="CARD_HOLDER_NAME")
    private String cardHolderName;
    @Column(name="EXPIRY_DATE")
    private LocalDate expiryDate;
    @Column(name="DUE_DATE")
    private LocalDate dueDate;
    @Column(name="CREATED_DATE", nullable = false)
    private LocalDateTime createDate;
    @Enumerated(EnumType.STRING)
    @Column(name="CARD_STATUS", nullable = false)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name="REPLACEMENT_OF_CREDIT_ID")
    private Integer replacementOfCreditId;

    @Column(name="REPLACED_BY_CREDIT_ID")
    private Integer replacedByCreditId;
    
	public LocalDateTime getCreateDate() {
		return createDate;
	}
	public void setCreateDate(LocalDateTime createDate) {
		this.createDate = createDate;
	}
	public Integer getCreditId() {
		return creditId;
	}
	public void setCreditId(Integer creditId) {
		this.creditId = creditId;
	}
	public Integer getCustomerId() {
		return customerId;
	}
	public void setCustomerId(Integer customerId) {
		this.customerId = customerId;
	}
	public CardName getCardName() {
		return cardName;
	}
	public void setCardName(CardName cardName) {
		this.cardName = cardName;
	}
	public CardType getCardType() {
		return cardType;
	}
	public void setCardType(CardType cardType) {
		this.cardType = cardType;
	}
	public Double getCardLimit() {
		return cardLimit;
	}
	public void setCardLimit(Double cardLimit) {
		this.cardLimit = cardLimit;
	}
	public Double getAvailableCredit() {
		return availableCredit;
	}
	public void setAvailableCredit(Double availableCredit) {
		this.availableCredit = availableCredit;
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
	public LocalDate getExpiryDate() {
		return expiryDate;
	}
	public void setExpiryDate(LocalDate localDate) {
		this.expiryDate = localDate;
	}
	public LocalDate getDueDate() {
		return dueDate;
	}
	public void setDueDate(LocalDate localDate) {
		this.dueDate = localDate;
	}
	
    public Integer getReplacementOfCreditId() { return replacementOfCreditId; }
    public void setReplacementOfCreditId(Integer replacementOfCreditId) { this.replacementOfCreditId = replacementOfCreditId; }
    public Integer getReplacedByCreditId() { return replacedByCreditId; }
    public void setReplacedByCreditId(Integer replacedByCreditId) { this.replacedByCreditId = replacedByCreditId; }    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    @PrePersist
    void setCreationTimestamp() { if (createDate == null) createDate = LocalDateTime.now(); if (status == null) status = CardStatus.ACTIVE; }
}