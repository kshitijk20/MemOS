-- Answer feedback (thumbs up/down with an optional comment), plus answer metadata:
-- whether the assistant could not answer from the documents, and the standalone search
-- query used when a follow-up question was rewritten.
ALTER TABLE messages
    ADD COLUMN feedback         VARCHAR(10),
    ADD COLUMN feedback_comment TEXT,
    ADD COLUMN feedback_at      TIMESTAMPTZ,
    ADD COLUMN unanswered       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN search_query     TEXT,
    ADD CONSTRAINT chk_messages_feedback CHECK (feedback IN ('UP', 'DOWN'));

CREATE INDEX idx_messages_feedback ON messages (feedback, feedback_at DESC) WHERE feedback IS NOT NULL;
