package com.equipmentrental.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap.admin")
public record AdminBootstrapProperties(String email, String password, String fullName) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[email=" + email + "]";
    }
}
