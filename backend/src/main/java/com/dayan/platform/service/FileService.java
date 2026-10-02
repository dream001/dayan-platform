package com.dayan.platform.service;

import com.dayan.platform.dto.FileQuery;
import com.dayan.platform.vo.FileViews.FileView;
import com.dayan.platform.vo.FileViews.PreviewView;
import com.dayan.platform.vo.PageResponse;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileView upload(MultipartFile multipartFile, long uploaderId);

    PageResponse<FileView> list(FileQuery query);

    FileView detail(long id);

    Download download(long id);

    PreviewView preview(long id);

    void delete(long id);

    record Download(
            String originalName,
            String contentType,
            long sizeBytes,
            InputStream inputStream
    ) {
    }
}
