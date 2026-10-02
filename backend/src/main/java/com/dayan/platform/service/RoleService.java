package com.dayan.platform.service;

import com.dayan.platform.dto.RbacDtos.RoleRequest;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.RoleDetail;
import com.dayan.platform.vo.RbacViews.RoleSummary;
import java.util.Set;

public interface RoleService {

    PageResponse<RoleSummary> page(int page, int size, String keyword, Boolean enabled);

    RoleDetail detail(long id);

    RoleDetail create(RoleRequest request);

    RoleDetail update(long id, RoleRequest request);

    void delete(long id);

    void assignUsers(long id, Set<Long> userIds, long operatorId);

    void grantPermissions(long id, Set<Long> permissionIds);
}
