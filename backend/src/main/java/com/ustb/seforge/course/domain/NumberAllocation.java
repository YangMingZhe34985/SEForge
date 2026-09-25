package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Database-generated identity serializes allocation across concurrent API instances. */
@Entity
@Table(name = "number_allocations")
public class NumberAllocation extends BaseEntity {
    @Column(nullable = false, length = 8)
    private String kind;

    protected NumberAllocation() {}
    public NumberAllocation(String kind) { this.kind = kind; }
}
