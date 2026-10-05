package com.springlensai.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
    @NotBlank @Size(max = 300) String confirmation) {
        
}
