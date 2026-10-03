ALTER TABLE cloud_storage
    DROP CONSTRAINT ck_cloud_storage_provider;

ALTER TABLE cloud_storage
    ADD CONSTRAINT ck_cloud_storage_provider CHECK (
        provider IN (
            'TENCENT_COS',
            'ALIYUN_OSS',
            'VOLCENGINE_TOS',
            'HUAWEI_OBS',
            'AWS_S3',
            'AZURE_BLOB',
            'CLOUDFLARE_R2',
            'MINIO'
        )
    );
