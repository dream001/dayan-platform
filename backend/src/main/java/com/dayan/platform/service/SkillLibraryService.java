package com.dayan.platform.service;

import com.dayan.platform.dto.SkillLibraryDtos.SkillAssetRequest;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.SkillLibraryViews.ProjectOption;
import com.dayan.platform.vo.SkillLibraryViews.SkillLibrary;
import com.dayan.platform.vo.SkillLibraryViews.SkillSample;
import java.util.List;

public interface SkillLibraryService {

    List<ProjectOption> projectOptions(long userId, boolean platformAdmin);

    SkillLibrary skills(
            Long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    );

    PageResponse<SkillSample> samples(
            String skillKey,
            Long projectId,
            String mediaType,
            int page,
            int size,
            long userId,
            boolean platformAdmin
    );

    void save(String skillKey, SkillAssetRequest request);
}
