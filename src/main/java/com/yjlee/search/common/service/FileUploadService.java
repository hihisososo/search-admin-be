package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.FileUploadResult;

public interface FileUploadService {

  FileUploadResult uploadFile(String fileName, String basePath, String content, String version);
}
