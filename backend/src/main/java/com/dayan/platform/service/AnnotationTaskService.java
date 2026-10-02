package com.dayan.platform.service;

import com.dayan.platform.dto.AnnotationTaskDtos.CreateRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.BatchAnnotationRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.DatasetReviewRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.StatusRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.UpdateRequest;
import com.dayan.platform.vo.AnnotationTaskViews.StatusCounts;
import com.dayan.platform.vo.AnnotationTaskViews.TaskDetail;
import com.dayan.platform.vo.AnnotationTaskViews.TaskOptions;
import com.dayan.platform.vo.AnnotationTaskViews.TaskSummary;
import com.dayan.platform.vo.PageResponse;
import java.time.LocalDate;
import java.util.List;

public interface AnnotationTaskService {

    PageResponse<TaskSummary> page(
            int page,
            int size,
            String keyword,
            String status,
            Long projectId,
            Long annotatorId,
            Long reviewerId,
            LocalDate createdDate,
            long userId,
            boolean platformManager
    );

    StatusCounts counts(long userId, boolean platformManager);

    TaskOptions options(Long projectId, long userId, boolean platformManager);

    TaskDetail detail(
            long id,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    List<TaskDetail> create(
            CreateRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    TaskDetail update(
            long id,
            UpdateRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    TaskDetail changeStatus(
            long id,
            StatusRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    TaskDetail reviewDataset(
            long id,
            long relationId,
            DatasetReviewRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    TaskDetail batchAnnotate(
            long id,
            BatchAnnotationRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    );

    void delete(long id, long userId, boolean platformManager);
}
