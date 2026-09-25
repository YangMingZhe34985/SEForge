-- Reindex/delete must not erase citations from previously completed answers.
-- Source, quote, page and section remain immutable snapshots; the live chunk link is optional.
ALTER TABLE message_citation DROP FOREIGN KEY fk_message_citation_chunk;
ALTER TABLE message_citation MODIFY chunk_id BIGINT NULL;
ALTER TABLE message_citation ADD CONSTRAINT fk_message_citation_chunk
    FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (id) ON DELETE SET NULL;
