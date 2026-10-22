package com.instagramdemo.photo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CitusTableDistributionInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public CitusTableDistributionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_dist_partition
                        WHERE logicalrelid = 'sb.photos'::regclass
                    ) THEN
                        PERFORM create_distributed_table(
                            'sb.photos', 'id', colocate_with := 'none'
                        );
                    END IF;

                    IF NOT EXISTS (
                        SELECT 1 FROM pg_dist_partition
                        WHERE logicalrelid = 'sb.photo_tags'::regclass
                    ) THEN
                        PERFORM create_distributed_table(
                            'sb.photo_tags', 'photo_id', colocate_with := 'sb.photos'
                        );
                    END IF;
                END
                $$;
                """);
    }
}