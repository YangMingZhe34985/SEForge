package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20_000) String content) {
}
