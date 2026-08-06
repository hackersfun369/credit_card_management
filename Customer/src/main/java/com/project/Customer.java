package com.project;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;

@Entity
@Table(name="CUSTOMER")
public class Customer {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CUST_ID")
    private Integer custId;
	@Column(name = "CUST_FIRST_NAME")
	private String firstName;
	@Column(name = "CUST_LAST_NAME")
	private String lastName;
	@Column(name = "LOCATION")
	private String location;
	@Column(name = "PHONE_NUMBER")
	private Long phoneNumber;
	@Column(name = "AADHAR_NUMBER")
	private Long aadharNumber;
	@Column(name="ACCOUNT_NUMBER")
	private Long accountNumber;
	@CreationTimestamp
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @Column(name="CREATED_DATE", nullable=false, updatable=false)
    private LocalDateTime createdDate;
	
	public LocalDateTime getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(LocalDateTime createdDate) {
		this.createdDate = createdDate;
	}

	public Long getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(Long accountNumber) {
		this.accountNumber = accountNumber;
	}

	
	public Customer(){
		
	}
	
	public Customer(Integer custId,String firstName, String lastName, String location,Long phoneNumber, Long aadharNumber) {
	
		this.custId = custId;
		this.firstName = firstName;
		this.lastName = lastName;
		this.location = location;
		this.phoneNumber = phoneNumber;
		this.aadharNumber = aadharNumber;
		
	}

	public Integer getCustId() {
		return custId;
	}

	public void setCustId(Integer custId) {
		this.custId = custId;
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

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Long getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(Long phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public Long getAadharNumber() {
		return aadharNumber;
	}

	public void setAadharNumber(Long aadharNumber) {
		this.aadharNumber = aadharNumber;
	}
	
	
    @PrePersist
    void setCreationTimestamp() { if (createdDate == null) createdDate = LocalDateTime.now(ZoneOffset.UTC); }
}
