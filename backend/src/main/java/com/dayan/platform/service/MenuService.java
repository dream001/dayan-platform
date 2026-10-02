package com.dayan.platform.service;

import com.dayan.platform.dto.RbacDtos.MenuRequest;
import com.dayan.platform.dto.RbacDtos.MenuOrderRequest;
import com.dayan.platform.vo.RbacViews.MenuNode;
import java.util.List;

public interface MenuService {

    List<MenuNode> tree();

    MenuNode detail(long id);

    MenuNode create(MenuRequest request);

    MenuNode update(long id, MenuRequest request);

    List<MenuNode> reorder(MenuOrderRequest request);

    void delete(long id);

    List<MenuNode> currentUserMenus(long userId);
}
