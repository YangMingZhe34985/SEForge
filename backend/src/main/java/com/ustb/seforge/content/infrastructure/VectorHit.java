package com.ustb.seforge.content.infrastructure;

import java.util.Map;

public record VectorHit(String vectorId, double score, String text, Map<String, Object> metadata) {
}
