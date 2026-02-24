package com.twohearts.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import io.minio.*
import java.io.ByteArrayInputStream
import java.util.UUID

private val logger = KotlinLogging.logger {}

class MinioService(config: ApplicationConfig) {

    private val endpoint  = config.property("minio.endpoint").getString()
    private val accessKey = config.property("minio.accessKey").getString()
    private val secretKey = config.property("minio.secretKey").getString()
    private val bucket    = config.property("minio.bucket").getString()

    private val client: MinioClient by lazy {
        MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
    }

    init {
        ensureBucketExists()
    }

    private fun ensureBucketExists() {
        try {
            val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
                // Make publicly readable
                val policy = """
                    {"Version":"2012-10-17","Statement":[{
                      "Effect":"Allow","Principal":"*",
                      "Action":"s3:GetObject",
                      "Resource":"arn:aws:s3:::$bucket/*"
                    }]}
                """.trimIndent()
                client.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build()
                )
                logger.info { "MinIO bucket '$bucket' created" }
            }
        } catch (e: Exception) {
            logger.warn { "Could not initialize MinIO bucket: ${e.message}" }
        }
    }

    /**
     * Upload raw bytes and return the public URL.
     */
    fun uploadPhoto(userId: String, bytes: ByteArray, contentType: String): String {
        val ext = when {
            contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
            contentType.contains("png") -> "png"
            contentType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val key = "photos/$userId/${UUID.randomUUID()}.$ext"

        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        )

        val url = "$endpoint/$bucket/$key"
        logger.info { "Photo uploaded: $url" }
        return url
    }
}
