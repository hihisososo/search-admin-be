package com.yjlee.search.async.repository;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {

  List<AsyncTask> findByStatusInOrderByCreatedAtDesc(List<AsyncTaskStatus> statuses);

  Page<AsyncTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

  boolean existsByTaskTypeAndStatusIn(AsyncTaskType taskType, List<AsyncTaskStatus> statuses);

  List<AsyncTask> findByStatusOrderByCreatedAtAsc(AsyncTaskStatus status);

  List<AsyncTask> findByStatus(AsyncTaskStatus status);

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM async_tasks t WHERE t.task_type = :taskType AND t.status IN :statuses FOR UPDATE)",
      nativeQuery = true)
  boolean existsByTaskTypeAndStatusInForUpdate(
      @Param("taskType") String taskType, @Param("statuses") List<String> statuses);
}
