package com.project;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="CARD_REQUEST") public class CardRequest {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false) private Integer customerId;
@Column(nullable=false) private String cardName;
@Column(nullable=false) private String cardType;
@Column(nullable=false) private String status="PENDING";
private String note; private String managerUsername; private LocalDateTime createdAt; private LocalDateTime updatedAt;
@PrePersist void created(){if(createdAt==null)createdAt=LocalDateTime.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=LocalDateTime.now();}
public Long getId(){return id;} public Integer getCustomerId(){return customerId;} public void setCustomerId(Integer v){customerId=v;} public String getCardName(){return cardName;} public void setCardName(String v){cardName=v;} public String getCardType(){return cardType;} public void setCardType(String v){cardType=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getNote(){return note;} public void setNote(String v){note=v;} public String getManagerUsername(){return managerUsername;} public void setManagerUsername(String v){managerUsername=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
