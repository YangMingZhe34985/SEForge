package com.ustb.seforge.identity.api;

public record CsrfView(String headerName, String parameterName, String token) {
}
