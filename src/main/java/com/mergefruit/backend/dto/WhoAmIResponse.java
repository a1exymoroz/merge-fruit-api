package com.mergefruit.backend.dto;

import java.util.List;

public record WhoAmIResponse(String username, List<String> roles) {
}
