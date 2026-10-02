---
name: knowledge-dayan-backend-files
description: >
  Covers file metadata, MinIO object operations, validation, and compensation behavior.
  Navigate when: changing upload, list, download, preview, delete, or storage recovery.
  Excludes: account/RBAC rules (see ../identity-access/) and audit queries (see ../audit-data/).
  Keywords: FileServiceImpl, StoredFileMapper, MinIO, upload, preview, delete, compensation.
---

## Module Structure

Files store objects in MinIO and searchable lifecycle metadata in PostgreSQL.

### Directory Layout
- `backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java` — Storage workflow
- `backend/src/main/java/com/dayan/platform/repository/storage/` — MinIO abstraction
- `backend/src/main/java/com/dayan/platform/repository/mapper/StoredFileMapper.java` — Metadata state queries

### Key Entry Points
- `FileController.upload()` in `backend/src/main/java/com/dayan/platform/controller/FileController.java` — Multipart upload
- `FileServiceImpl.delete()` in `backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java` — Recoverable deletion

## Gotchas
- Upload writes the object before metadata and removes the object when metadata insertion fails; reversing that sequence can leave visible metadata without content (`backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java`)
- Delete uses READY/FAILED → DELETING → row deletion and records FAILED when object or final database deletion fails, enabling retry without hiding the inconsistency (`backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java`, `backend/src/main/java/com/dayan/platform/repository/mapper/StoredFileMapper.java`)

## Architecture
- The object-storage interface keeps service recovery logic independent from the MinIO SDK and makes failure-path testing deterministic (`backend/src/main/java/com/dayan/platform/repository/storage/ObjectStorage.java`)
- Presigned preview URLs carry an inline content disposition while downloads stream through the API with a sanitized attachment filename (`backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java`, `backend/src/main/java/com/dayan/platform/controller/FileController.java`)

## Security Considerations
- File validation applies configured byte and MIME allowlists before reading storage, and filenames remove path segments and control characters before persistence (`backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java`, `backend/src/main/java/com/dayan/platform/service/impl/SafeFileName.java`)
