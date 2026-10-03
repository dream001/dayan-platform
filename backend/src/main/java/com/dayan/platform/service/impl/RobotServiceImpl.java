package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.RobotDtos.RobotRequest;
import com.dayan.platform.dto.RobotDtos.RobotType;
import com.dayan.platform.model.Robot;
import com.dayan.platform.repository.mapper.RobotMapper;
import com.dayan.platform.repository.query.RobotRows.CatalogRow;
import com.dayan.platform.repository.query.RobotRows.DatasetRow;
import com.dayan.platform.service.FileService;
import com.dayan.platform.service.RobotService;
import com.dayan.platform.vo.RobotViews.RobotDataset;
import com.dayan.platform.vo.RobotViews.RobotSummary;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RobotServiceImpl implements RobotService {

    private final RobotMapper robotMapper;
    private final FileService fileService;

    public RobotServiceImpl(RobotMapper robotMapper, FileService fileService) {
        this.robotMapper = robotMapper;
        this.fileService = fileService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RobotSummary> list(RobotType robotType, long userId, boolean admin) {
        String type = robotType == null ? null : robotType.name();
        return robotMapper.selectCatalog(type, userId, admin).stream()
                .map(this::summary)
                .toList();
    }

    @Override
    @Transactional
    public RobotSummary create(RobotRequest request, long userId, boolean admin) {
        requireAdmin(admin);
        rejectExistingName(request.name());
        Robot robot = new Robot();
        apply(robot, request);
        robot.setBuiltIn(false);
        robot.setDeleted(false);
        try {
            robotMapper.insert(robot);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Robot name already exists");
        }
        return findSummary(robot.getId(), userId, true);
    }

    @Override
    @Transactional
    public RobotSummary update(
            long id,
            RobotRequest request,
            long userId,
            boolean admin
    ) {
        requireAdmin(admin);
        Robot robot = requireRobot(id);
        if (!robot.getName().equals(request.name().trim())) {
            throw invalid("Robot name cannot be changed");
        }
        apply(robot, request);
        robot.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        robotMapper.updateById(robot);
        return findSummary(id, userId, true);
    }

    @Override
    @Transactional
    public void delete(long id, boolean admin) {
        requireAdmin(admin);
        requireRobot(id);
        robotMapper.softDelete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RobotDataset> datasets(long id, long userId, boolean admin) {
        Robot robot = requireRobot(id);
        return robotMapper.selectDatasets(robot.getName(), userId, admin).stream()
                .map(this::dataset)
                .toList();
    }

    private void apply(Robot robot, RobotRequest request) {
        if (request.iconFileId() != null) {
            var icon = fileService.detail(request.iconFileId());
            if (!icon.contentType().startsWith("image/")) {
                throw invalid("Robot icon must be an image file");
            }
            robot.setIconFileId(request.iconFileId());
            robot.setIconUrl("file:" + request.iconFileId());
        } else {
            if (!StringUtils.hasText(request.iconUrl())) {
                throw invalid("Robot icon is required");
            }
            validateUrl(request.iconUrl(), "iconUrl");
            robot.setIconFileId(null);
            robot.setIconUrl(request.iconUrl().trim());
        }
        if (StringUtils.hasText(request.introductionUrl())) {
            validateUrl(request.introductionUrl(), "introductionUrl");
        }
        robot.setName(request.name().trim());
        robot.setTitleZh(trimToNull(request.titleZh()));
        robot.setTitleEn(trimToNull(request.titleEn()));
        robot.setRobotType(request.robotType().name());
        robot.setActionMappingSupport(request.actionMappingSupport().name());
        robot.setDescription(trimToNull(request.description()));
        robot.setCompany(trimToNull(request.company()));
        robot.setIntroductionUrl(trimToNull(request.introductionUrl()));
    }

    private Robot requireRobot(long id) {
        Robot robot = robotMapper.selectActiveById(id);
        if (robot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Robot not found");
        }
        return robot;
    }

    private void rejectExistingName(String name) {
        Robot existing = robotMapper.selectAnyByName(name.trim());
        if (existing == null) {
            return;
        }
        String message = Boolean.TRUE.equals(existing.getDeleted())
                ? "Robot name belongs to a disabled robot; restore it or use another name"
                : "Robot name already exists";
        throw conflict(message);
    }

    private RobotSummary findSummary(long id, long userId, boolean admin) {
        return robotMapper.selectCatalog(null, userId, admin).stream()
                .filter(row -> row.id == id)
                .findFirst()
                .map(this::summary)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RobotSummary summary(CatalogRow row) {
        return new RobotSummary(
                row.id,
                row.name,
                row.iconFileId == null
                        ? row.iconUrl
                        : fileService.preview(row.iconFileId).url(),
                row.iconFileId,
                row.titleZh,
                row.titleEn,
                row.robotType,
                row.actionMappingSupport,
                row.description,
                row.company,
                row.introductionUrl,
                Boolean.TRUE.equals(row.builtIn),
                row.datasetCount == null ? 0 : row.datasetCount,
                row.createdAt,
                row.updatedAt
        );
    }

    private RobotDataset dataset(DatasetRow row) {
        return new RobotDataset(
                row.id,
                row.name,
                row.dataType,
                row.sizeBytes,
                row.durationSeconds,
                row.uploaderName,
                row.uploadedAt,
                row.annotationCount == null ? 0 : row.annotationCount
        );
    }

    private void validateUrl(String value, String field) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getUserInfo() != null) {
                throw invalid(field + " must be an HTTP or HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw invalid(field + " must be a valid URL");
        }
    }

    private void requireAdmin(boolean admin) {
        if (!admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
