package com.instagramdemo.photo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Configuration {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(@Value("${app.aws.profile:}") String profileName) {
        if (!profileName.isBlank()) {
        return ProfileCredentialsProvider.builder()
                    .profileName(profileName)
            .build();
        }
    return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Client s3Client(
        @Value("${app.aws.s3.region}") String region,
        AwsCredentialsProvider credentialsProvider) {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .build();
    }

    @Bean
    public S3Presigner s3Presigner(
        @Value("${app.aws.s3.region}") String region,
        AwsCredentialsProvider credentialsProvider) {
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .build();
    }
}