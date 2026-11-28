package com.habitloop.user.dto;

/** Minimal same-tenant member label; deliberately excludes email and other profile fields. */
public record UserDisplayName(String id, String displayName) {
}
