package com.concept.media;

import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * The real one.
 *
 * <p>Nothing here sets an encryption header. The bucket has default encryption
 * turned on, so objects are encrypted whether we ask or not -- and asking would
 * mean signing an {@code x-amz-server-side-encryption} header, which the
 * browser would then have to send back on a presigned request, which would mean
 * adding it to the bucket's CORS rule. A header that changes nothing is not
 * worth a CORS change.
 */
public class S3MediaStore implements MediaStore {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3MediaStore(S3Client client, S3Presigner presigner,
                        @Value("${app.media.bucket}") String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public URI readUrl(String key, Duration validFor) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return URI.create(presigner.presignGetObject(request).url().toString());
    }

    @Override
    public void delete(String key) {
        // S3 treats deleting something that is not there as a success, which is
        // the behaviour this interface promises.
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
