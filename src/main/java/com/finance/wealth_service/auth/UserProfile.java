package com.finance.wealth_service.auth;

/** What the identity provider says about the caller. {@code email} is null unless it is verified. */
public record UserProfile(String name, String email) {}
