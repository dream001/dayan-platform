package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.common.api.RawResponse;
import com.dayan.platform.dto.FileQuery;
import com.dayan.platform.service.FileService;
import com.dayan.platform.service.FileService.Download;
import com.dayan.platform.vo.FileViews.FileView;
import com.dayan.platform.vo.FileViews.PreviewView;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${app.api.base-path}/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('file:upload')")
    @Audited(
            module = "FILE",
            action = "UPLOAD",
            targetType = "FILE",
            targetId = "#result == null ? #file.originalFilename : #result.id()"
    )
    public FileView upload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return fileService.upload(file, ((Number) jwt.getClaim("uid")).longValue());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('file:view')")
    public PageResponse<FileView> list(@Valid @ModelAttribute FileQuery query) {
        return fileService.list(query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('file:view')")
    public FileView detail(@PathVariable long id) {
        return fileService.detail(id);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('file:download')")
    @RawResponse
    public ResponseEntity<InputStreamResource> download(@PathVariable long id) {
        Download download = fileService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(safeMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.inputStream()));
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('file:preview')")
    public PreviewView preview(@PathVariable long id) {
        return fileService.preview(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:delete')")
    @Audited(module = "FILE", action = "DELETE", targetType = "FILE", targetId = "#id")
    public void delete(@PathVariable long id) {
        fileService.delete(id);
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
