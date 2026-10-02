package com.dayan.platform.mapper;

import com.dayan.platform.model.ApplicationMetadata;
import com.dayan.platform.vo.ApplicationInfoVO;
import org.springframework.stereotype.Component;

@Component
public class ApplicationInfoMapper {

    public ApplicationInfoVO toView(ApplicationMetadata metadata) {
        return new ApplicationInfoVO(
                metadata.name(),
                metadata.environment(),
                metadata.version()
        );
    }
}
