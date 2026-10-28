package com.concept.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MediaConfig.class);

    @Test
    void aDeployedServerWithNoBucketHasPhotosSwitchedOffNotOnItsDisk() {
        runner.withPropertyValues("app.dev-mode=false").run(ctx ->
                assertThat(ctx.getBean(MediaStore.class)).isInstanceOf(UnavailableMediaStore.class));
        runner.run(ctx ->
                assertThat(ctx.getBean(MediaStore.class)).isInstanceOf(UnavailableMediaStore.class));
    }

    @Test
    void devModeUsesTheFilesystem() {
        runner.withPropertyValues("app.dev-mode=true").run(ctx ->
                assertThat(ctx.getBean(MediaStore.class)).isInstanceOf(LocalMediaStore.class));
    }

    /**
     * Throwaway credentials as system properties, which the SDK's default chain
     * reads first. Without them this passed only where the environment happened
     * to carry AWS credentials, and failed in CI, which has none.
     */
    @Test
    void aBucketMeansS3EvenInDevMode() {
        System.setProperty("aws.accessKeyId", "test-access-key");
        System.setProperty("aws.secretAccessKey", "test-secret-key");
        try {
            runner.withPropertyValues("app.dev-mode=true", "app.media.bucket=acadia-photos-test",
                            "app.media.region=ap-southeast-1")
                    .run(ctx -> {
                        MediaStore store = ctx.getBean(MediaStore.class);
                        assertThat(store).isInstanceOf(S3MediaStore.class);
                        // Presigning is local, so this runs without a bucket: the link
                        // names the bucket, is signed with these credentials, and
                        // expires when asked.
                        String link = store.readUrl("tenant/t/albums/a/p-thumb.jpg",
                                java.time.Duration.ofMinutes(5)).toString();
                        assertThat(link)
                                .contains("acadia-photos-test.s3.ap-southeast-1.amazonaws.com/tenant/t/albums/a/p-thumb.jpg")
                                .contains("X-Amz-Credential=test-access-key")
                                .contains("X-Amz-Signature=").contains("X-Amz-Expires=300");
                    });
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
        }
    }
}
