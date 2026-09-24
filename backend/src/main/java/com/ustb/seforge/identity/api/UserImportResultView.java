package com.ustb.seforge.identity.api;

import java.util.List;

public record UserImportResultView(int total, int created, int skipped, int failed,
                                   List<UserImportRowView> rows) {
}
