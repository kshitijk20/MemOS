-- The [n] the model used for this source in its answer. NULL for citations created before
-- numbering was stored; clients fall back to list order, which matched the old prompt order.
ALTER TABLE citations
    ADD COLUMN source_number INTEGER;
