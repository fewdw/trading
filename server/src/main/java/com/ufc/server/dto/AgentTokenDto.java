package com.ufc.server.dto;

/**
 * Response from {@code POST /api/admin/agents}: a 7-day session token the agent
 * uses as {@code Authorization: Bearer <token>} for the normal trading API.
 */
public record AgentTokenDto(String token) {}
