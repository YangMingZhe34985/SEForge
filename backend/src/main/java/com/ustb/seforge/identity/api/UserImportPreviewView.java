package com.ustb.seforge.identity.api;

import java.util.List;

public record UserImportPreviewView(String digest, int total, int valid, int rejected,
                                    List<UserImportRowView> rows) {
}
