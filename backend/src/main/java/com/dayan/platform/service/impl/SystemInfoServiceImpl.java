package com.dayan.platform.service.impl;

import com.dayan.platform.mapper.ApplicationInfoMapper;
import com.dayan.platform.repository.ApplicationMetadataRepository;
import com.dayan.platform.service.SystemInfoService;
import com.dayan.platform.vo.ApplicationInfoVO;
import org.springframework.stereotype.Service;

@Service
public class SystemInfoServiceImpl implements SystemInfoService {

    private final ApplicationMetadataRepository repository;
    private final ApplicationInfoMapper mapper;

    public SystemInfoServiceImpl(
            ApplicationMetadataRepository repository,
            ApplicationInfoMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ApplicationInfoVO getApplicationInfo() {
        return mapper.toView(repository.get());
    }
}
