package com.finance.wealth_service.auth;

public record AuthenticatedUser(String issuer, String subject) {}