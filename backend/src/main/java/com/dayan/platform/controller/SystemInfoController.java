package com.dayan.platform.controller;

import com.dayan.platform.service.SystemInfoService;
import com.dayan.platform.vo.ApplicationInfoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/system")
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    public SystemInfoController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @GetMapping("/info")
    public ApplicationInfoVO getApplicationInfo() {
        return systemInfoService.getApplicationInfo();
    }
}
