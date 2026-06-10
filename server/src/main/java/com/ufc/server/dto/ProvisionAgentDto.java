package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to provision (find-or-create) an AI trading agent account. Used by the
 * {@code agents} service against {@code POST /api/admin/agents}; admin-key gated.
 */
public record ProvisionAgentDto(@NotBlank String username) {}
