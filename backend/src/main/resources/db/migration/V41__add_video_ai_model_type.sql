ALTER TABLE ai_model
    DROP CONSTRAINT ck_ai_model_type;

ALTER TABLE ai_model
    ADD CONSTRAINT ck_ai_model_type CHECK (
        model_type IN (
            'CHAT',
            'EMBEDDING',
            'MULTIMODAL',
            'RERANK',
            'IMAGE',
            'VIDEO',
            'AUDIO'
        )
    );
