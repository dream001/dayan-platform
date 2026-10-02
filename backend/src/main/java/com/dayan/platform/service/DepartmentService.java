package com.dayan.platform.service;

import com.dayan.platform.dto.RbacDtos.DepartmentRequest;
import com.dayan.platform.vo.RbacViews.DepartmentNode;
import java.util.List;

public interface DepartmentService {

    List<DepartmentNode> tree();

    DepartmentNode create(DepartmentRequest request);

    DepartmentNode update(long id, DepartmentRequest request);

    void delete(long id);
}
