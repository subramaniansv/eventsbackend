package com.social.app.module.iam.models;

import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User{
    private UUID id;
    private String email;
    @JsonProperty(access = Access.WRITE_ONLY)
    @JsonAlias("password")  // tests / clients may send 'password' instead of 'passwordHash'
    private String passwordHash;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private boolean emailVerified;
    // Transient output-only: true when the account has a real (bcrypt) password.
    // False for Google-only accounts so the UI can hide the change-password form.
    @JsonProperty(access = Access.READ_ONLY)
    private boolean hasPassword;
    // Google OAuth unique identifier (sub claim). Stored for fast lookup on
    // Google sign-in; never exposed in API responses.
    @JsonProperty(access = Access.WRITE_ONLY)
    private String googleSub;
    // Registration-only: name of the org to create alongside the user account.
    @JsonProperty(access = Access.WRITE_ONLY)
    private String orgName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
    public UserStatus getStatus() {
        return status;
    }
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    public boolean isEmailVerified() {
        return emailVerified;
    }
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    public boolean isHasPassword() {
        return hasPassword;
    }
    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }
    public String getGoogleSub() {
        return googleSub;
    }
    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }
    public String getOrgName() {
        return orgName;
    }
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
}
