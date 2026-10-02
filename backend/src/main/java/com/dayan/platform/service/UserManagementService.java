package com.dayan.platform.service;

import com.dayan.platform.dto.RbacDtos.UserCreateRequest;
import com.dayan.platform.dto.RbacDtos.UserUpdateRequest;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.UserSummary;
import java.util.Set;

public interface UserManagementService {

    PageResponse<UserSummary> page(
            int page,
            int size,
            String keyword,
            Long departmentId,
            Boolean enabled
    );

    UserSummary detail(long id);

    UserSummary create(UserCreateRequest request, long operatorId);

    UserSummary update(long id, UserUpdateRequest request);

    void changeStatus(long id, boolean enabled, long operatorId);

    void resetPassword(long id, String newPassword);

    void assignRoles(long id, Set<Long> roleIds, long operatorId);
}
