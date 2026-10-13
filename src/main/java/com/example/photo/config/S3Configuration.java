package com.example.photo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3Configuration {

    @Bean
    public S3Client s3Client(
            @Value("${app.aws.s3.region}") String region,
            @Value("${app.aws.profile:}") String profileName) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));
        if (!profileName.isBlank()) {
            builder.credentialsProvider(ProfileCredentialsProvider.builder()
                    .profileName(profileName)
                    .build());
        }
        return builder.build();
    }
}