package com.yjlee.search.async.repository;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {

  List<AsyncTask> findByTaskTypeAndStatusOrderByCreatedAtDesc(
      AsyncTaskType taskType, AsyncTaskStatus status);

  Page<AsyncTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

  List<AsyncTask> findByStatusInOrderByCreatedAtDesc(List<AsyncTaskStatus> statuses);

  List<AsyncTask> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

  boolean existsByTaskTypeAndStatusIn(AsyncTaskType taskType, List<AsyncTaskStatus> statuses);
}
