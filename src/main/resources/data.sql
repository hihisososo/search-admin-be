-- 개발/운영 환경 초기 데이터
INSERT INTO index_environments (environment_type, index_name, index_status, document_count, is_indexing, created_at, updated_at)
VALUES 
  ('DEV', 'products-dev', 'INACTIVE', 0, false, NOW(), NOW()),
  ('PROD', 'products-search', 'INACTIVE', 0, false, NOW(), NOW())
ON CONFLICT (environment_type) DO NOTHING; 