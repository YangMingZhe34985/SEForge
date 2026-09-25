package com.ustb.seforge.analytics.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Low-cardinality operational gauges. -1 denotes unavailable rather than a false healthy zero. */
@Component
public class OperationalMetrics implements MeterBinder {
    private final JdbcTemplate jdbc;
    public OperationalMetrics(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public void bindTo(MeterRegistry registry){
        registry.gauge("seforge.jobs.pending",this,m->m.count("SELECT COUNT(*) FROM async_job WHERE status IN ('PENDING','QUEUED','RUNNING','RETRY_WAIT')"));
        registry.gauge("seforge.jobs.dead_letters",this,m->m.count("SELECT COUNT(*) FROM async_job WHERE status='DEAD_LETTER'"));
        registry.gauge("seforge.analytics.pending_changes",this,m->m.count("SELECT COUNT(*) FROM analytics_change WHERE applied=false"));
    }
    private double count(String sql){try{Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}catch(RuntimeException failure){return -1;}}
}
