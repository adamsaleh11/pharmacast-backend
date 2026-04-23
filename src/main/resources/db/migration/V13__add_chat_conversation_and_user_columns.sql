ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS conversation_id uuid,
    ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES app_users(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_chat_messages_location_conversation_created_at
    ON chat_messages(location_id, conversation_id, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_messages_location_user_created_at
    ON chat_messages(location_id, user_id, created_at);
