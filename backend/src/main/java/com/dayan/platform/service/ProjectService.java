package com.dayan.platform.service;

import com.dayan.platform.dto.ProjectDtos.ProjectMemberRequest;
import com.dayan.platform.dto.ProjectDtos.ProjectRequest;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.ProjectViews.ProjectDetail;
import com.dayan.platform.vo.ProjectViews.ProjectMember;
import com.dayan.platform.vo.ProjectViews.ProjectOverview;
import com.dayan.platform.vo.ProjectViews.ProjectSummary;
import com.dayan.platform.vo.ProjectViews.ProjectUserOption;
import java.util.List;

public interface ProjectService {

    PageResponse<ProjectSummary> page(
            int page,
            int size,
            String keyword,
            String status,
            String projectType,
            long userId,
            boolean platformAdmin
    );

    ProjectOverview overview(long userId, boolean platformAdmin);

    ProjectDetail detail(long id, long userId, boolean platformAdmin);

    ProjectDetail create(ProjectRequest request, long operatorId);

    ProjectDetail update(
            long id,
            ProjectRequest request,
            long operatorId,
            boolean platformAdmin
    );

    ProjectDetail changeStatus(
            long id,
            String status,
            long operatorId,
            boolean platformAdmin
    );

    void delete(long id, long operatorId, boolean platformAdmin);

    List<ProjectMember> members(long id, long operatorId, boolean platformAdmin);

    ProjectMember saveMember(
            long id,
            ProjectMemberRequest request,
            long operatorId,
            boolean platformAdmin
    );

    void removeMember(long id, long userId, long operatorId, boolean platformAdmin);

    List<ProjectUserOption> userOptions(
            long id,
            String keyword,
            long operatorId,
            boolean platformAdmin
    );
}
