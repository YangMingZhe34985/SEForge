package com.ustb.seforge.identity.api;

import com.ustb.seforge.identity.domain.GlobalRole;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserRolesRequest(@NotNull Set<GlobalRole> roles) {
}
