package com.ustb.seforge.content.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class MinioConfigurationTest {
    @Test
    void missingCredentialsDoNotBlockStartupButOperationsReturnDiagnostic503() {
        var storage = new MinioObjectStorage(new SEForgeProperties());
        assertThatThrownBy(() -> storage.open("courses/1/test.pdf"))
                .isInstanceOfSatisfying(AppException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE);
                    assertThat(failure.getMessage()).contains("CONFIGURATION", "MinIO");
                });
    }
}
