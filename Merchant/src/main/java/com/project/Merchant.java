package com.project;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

 enum MerchantCategory {
    GROCERY,
    RESTAURANT,
    HOSPITAL,
    HOTEL,
    FUEL,
    PHARMACY,
    EDUCATION,
    TRAVEL,
    ENTERTAINMENT,
    ECOMMERCE,
    ELECTRONICS,
    UTILITIES,
    OTHER
}

 enum MerchantStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED,
    SUSPENDED
}

 @Entity
 @Table(name = "MERCHANT")
 public class Merchant {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long merchantId;

     private String firstName;
     private String lastName;
     private String mid;

     @Enumerated(EnumType.STRING)
     private MerchantCategory merchantCategory;

     private long merchantAccountNmber;

     @Enumerated(EnumType.STRING)
     private MerchantStatus status;

     private String bankName;
     private String ifscCode;

     public Long getMerchantId() {
         return merchantId;
     }

     public void setMerchantId(Long merchantId) {
         this.merchantId = merchantId;
     }

     public String getFirstName() {
         return firstName;
     }

     public void setFirstName(String firstName) {
         this.firstName = firstName;
     }

     public String getLastName() {
         return lastName;
     }

     public void setLastName(String lastName) {
         this.lastName = lastName;
     }

     public String getMid() {
         return mid;
     }

     public void setMid(String mid) {
         this.mid = mid;
     }

     public MerchantCategory getMerchantCategory() {
         return merchantCategory;
     }

     public void setMerchantCategory(MerchantCategory merchantCategory) {
         this.merchantCategory = merchantCategory;
     }

     public long getMerchantAccountNmber() {
         return merchantAccountNmber;
     }

     public void setMerchantAccountNmber(long merchantAccountNmber) {
         this.merchantAccountNmber = merchantAccountNmber;
     }

     public MerchantStatus getStatus() {
         return status;
     }

     public void setStatus(MerchantStatus status) {
         this.status = status;
     }

     public String getBankName() {
         return bankName;
     }

     public void setBankName(String bankName) {
         this.bankName = bankName;
     }

     public String getIfscCode() {
         return ifscCode;
     }

     public void setIfscCode(String ifscCode) {
         this.ifscCode = ifscCode;
     }
 }