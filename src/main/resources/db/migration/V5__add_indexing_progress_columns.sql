-- 색인 진행률 추적을 위한 컬럼 추가
ALTER TABLE index_environments
ADD COLUMN indexed_document_count BIGINT,
ADD COLUMN total_document_count BIGINT;