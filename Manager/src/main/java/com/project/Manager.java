package com.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

enum STATUS{
	ACTIVE,
	INACTIVE
}

@Entity
@Table(name="MANAGER")
public class Manager {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long managerId;
	private String firstName;
	private String lastName;
	@Column(unique = true, nullable = false)
	private Long phoneNumber;
	private STATUS status;
	private String address;
	@Column(unique = true, nullable = false)
	private String username;
	@Column(nullable = false)
	private String passwordHash;
	@Column(nullable = false)
	private String role = "ADMIN";
	
	public Long getManagerId() {
		return managerId;
	}
	public void setManagerId(Long managerId) {
		this.managerId = managerId;
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
	public Long getPhoneNumber() {
		return phoneNumber;
	}
	public void setPhoneNumber(Long phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	public STATUS getStatus() {
		return status;
	}
	public void setStatus(STATUS status) {
		this.status = status;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }
	
}
