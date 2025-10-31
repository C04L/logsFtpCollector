package org.coal;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class S3Connection {

    private static Configuration configuration = Configuration.getInstance();
    private static S3Client s3Client;

     public static void initS3Client() {
         SdkHttpClient httpClient = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofMinutes(1))
                .build();

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
            configuration.getS3KeyId(),
            configuration.getS3SecretKey()
        );

        S3Configuration s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build();

        s3Client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
            .endpointOverride(URI.create(configuration.getS3Endpoint()))
            .serviceConfiguration(s3Config)
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .putAdvancedOption(SdkAdvancedClientOption.DISABLE_HOST_PREFIX_INJECTION, true)
                .build())
            .httpClient(httpClient)
            .region(Region.of(configuration.getS3Region()))
            .build();
    }

    public static S3Client getS3Client() {
         if  (s3Client == null) {
            initS3Client();
         }
         return s3Client;
    }
}
