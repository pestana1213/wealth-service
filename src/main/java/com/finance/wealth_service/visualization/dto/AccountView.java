package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record AccountView(UUID id, String name, BigDecimal currentAmount) {}
