package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.FileUpload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadRepository extends JpaRepository<FileUpload, String> {

  List<FileUpload> findByIndexId(String indexId);

  List<FileUpload> findByIndexIdAndStatus(String indexId, String status);

  Optional<FileUpload> findByS3Key(String s3Key);

  void deleteByIndexId(String indexId);
}
