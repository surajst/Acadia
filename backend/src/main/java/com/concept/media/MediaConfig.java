package com.concept.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;

/**
 * Picks a media store.
 *
 * <p>S3 when a bucket is named, the filesystem otherwise. The choice is on a
 * property rather than a profile so the deployed configuration is the one that
 * decides -- a profile would mean a machine that happens to run without `prod`
 * set silently writing a school's photographs to a container's disk, where they
 * would be lost on the next deploy and nobody would know until a parent asked.
 *
 * <h2>Credentials</h2>
 *
 * <p>Nothing here reads a key or a secret. {@link DefaultCredentialsProvider}
 * finds {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY} in the
 * environment itself, which keeps them out of the application's configuration,
 * out of its logs, and out of any stack trace: there is no field holding them
 * and no getter to print.
 */
@Configuration
public class MediaConfig {

    @Bean
    @ConditionalOnProperty(name = "app.media.bucket")
    public S3Client s3Client(@Value("${app.media.region}") String region) {
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.media.bucket")
    public S3Presigner s3Presigner(@Value("${app.media.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.media.bucket")
    public MediaStore s3MediaStore(S3Client client, S3Presigner presigner,
                                   @Value("${app.media.bucket}") String bucket) {
        return new S3MediaStore(client, presigner, bucket);
    }

    /**
     * A developer's machine and CI: the filesystem, and it says so on startup.
     */
    @Bean
    @ConditionalOnMissingBean(MediaStore.class)
    @ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
    public MediaStore localMediaStore(
            @Value("${app.media.local-dir:./data/media}") String dir,
            @Value("${app.media.local-base:http://localhost:8080}") String base) {
        System.out.println(">> Media store: LOCAL FILESYSTEM at " + dir
                + " (dev mode, no app.media.bucket). Correct for a developer machine and for CI.");
        return new LocalMediaStore(Path.of(dir), base);
    }

    /**
     * Anything deployed without a bucket: photos are switched off.
     *
     * <p>This used to fall back to the filesystem with a warning. On Render the
     * disk is wiped on each deploy, so that meant photographs silently lost.
     * Refusing to start was the other option, and would take the whole school's
     * app down over one feature. So the gallery reports that photos are not set
     * up, and the rest runs as normal.
     */
    @Bean
    @ConditionalOnMissingBean(MediaStore.class)
    public MediaStore unavailableMediaStore() {
        System.out.println(">> Media store: NOT SET UP -- no app.media.bucket, and not in dev mode. "
                + "Photo uploads are switched off until a bucket is configured.");
        return new UnavailableMediaStore();
    }
}
