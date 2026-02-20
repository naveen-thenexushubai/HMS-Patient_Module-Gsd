---
phase: 03-operational-enhancements
plan: "03"
subsystem: api
tags: [multipart, file-upload, jpa, spring-security, hipaa, audit]

# Dependency graph
requires:
  - phase: 03-01
    provides: FileStorageService (store/load), V006 patient_photos table migration, app.storage.photos-dir config
  - phase: 01-02
    provides: PatientRepository.findLatestVersionByBusinessId() for patient existence check
  - phase: 00-04
    provides: @Audited annotation, Spring Security role enforcement, @PreAuthorize

provides:
  - PatientPhoto JPA entity mapping to patient_photos table
  - PatientPhotoRepository with findByPatientBusinessIdAndIsCurrentTrue() and deactivateCurrentPhotos()
  - PhotoService orchestrating upload (validate patient + store file + deactivate old + save metadata) and download (find current + load resource)
  - PhotoController: POST /api/v1/patients/{businessId}/photo (multipart, RECEPTIONIST/ADMIN, 201) and GET /api/v1/patients/{businessId}/photo (all roles, binary stream)
  - GlobalExceptionHandler handlers: MaxUploadSizeExceededException (400 File Too Large) and IllegalArgumentException (400 Invalid Request)

affects:
  - 03-04
  - 03-05
  - future-phases

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Multipart file upload via @RequestParam(\"file\") MultipartFile with consumes = MULTIPART_FORM_DATA_VALUE"
    - "Binary file streaming via ResponseEntity<Resource> with Content-Disposition: inline"
    - "Single-current-record invariant via @Modifying @Query for atomic flag deactivation"
    - "Object[] return from service to avoid wrapper DTO for two-value tuple (resource + contentType)"

key-files:
  created:
    - src/main/java/com/hospital/patient/domain/PatientPhoto.java
    - src/main/java/com/hospital/patient/infrastructure/PatientPhotoRepository.java
    - src/main/java/com/hospital/patient/application/PhotoService.java
    - src/main/java/com/hospital/patient/api/PhotoController.java
  modified:
    - src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java

key-decisions:
  - "Object[] return from PhotoService.getCurrentPhoto() to avoid creating a dedicated wrapper DTO for a two-value tuple (Resource + contentType String)"
  - "Content-Disposition: inline (not attachment) for GET photo endpoint — displays in browser, better for webcam/UI integration"
  - "@Modifying @Query for deactivateCurrentPhotos() — atomic bulk update avoids loading all photos into memory"
  - "MaxUploadSizeExceededException handled in @RestControllerAdvice — thrown before controller executes, cannot be caught in try/catch"

patterns-established:
  - "Binary file streaming: ResponseEntity<Resource> with MediaType.parseMediaType(contentType) and Content-Disposition: inline"
  - "Single-current-flag invariant: @Modifying @Query to deactivate old records before inserting new"

requirements-completed: []

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 3 Plan 03: Patient Photo Upload/Download Summary

**PatientPhoto entity + PhotoController (multipart upload and binary download) with FileStorageService integration, single-current-photo invariant, and RFC 7807 MaxUploadSizeExceededException handler**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T08:24:25Z
- **Completed:** 2026-02-20T08:26:42Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- PatientPhoto JPA entity and PatientPhotoRepository (findByPatientBusinessIdAndIsCurrentTrue, deactivateCurrentPhotos via @Modifying @Query) for patient_photos table (V006)
- PhotoService orchestrating upload flow (patient validation -> FileStorageService.store() -> deactivate old photo -> save metadata) and download flow (find current photo -> FileStorageService.load() -> return Resource)
- PhotoController exposing POST /api/v1/patients/{businessId}/photo (RECEPTIONIST/ADMIN, 201) and GET /api/v1/patients/{businessId}/photo (all roles, binary stream with Content-Type header)
- GlobalExceptionHandler extended with MaxUploadSizeExceededException (RFC 7807 400 "File Too Large") and IllegalArgumentException (400 "Invalid Request") handlers

## Task Commits

Each task was committed atomically:

1. **Task 1: PatientPhoto JPA entity + PatientPhotoRepository + GlobalExceptionHandler MaxUploadSizeExceededException** - `677160e` (feat)
2. **Task 2: PhotoService + PhotoController** - `85d9fa8` (feat)

**Plan metadata:** (this SUMMARY.md commit)

## Files Created/Modified

- `src/main/java/com/hospital/patient/domain/PatientPhoto.java` - JPA entity for patient_photos table: id, patientBusinessId, filename, contentType, fileSizeBytes, uploadedAt, uploadedBy, isCurrent with @Builder.Default on uploadedAt and isCurrent
- `src/main/java/com/hospital/patient/infrastructure/PatientPhotoRepository.java` - Spring Data JPA repository with findByPatientBusinessIdAndIsCurrentTrue() and @Modifying deactivateCurrentPhotos() JPQL query
- `src/main/java/com/hospital/patient/application/PhotoService.java` - Upload and download service: validates patient, stores file via FileStorageService, manages is_current flag, saves PatientPhoto record; getCurrentUsername() from SecurityContext
- `src/main/java/com/hospital/patient/api/PhotoController.java` - POST and GET endpoints with @PreAuthorize role checks and @Audited annotations; GET streams binary with Content-Disposition: inline
- `src/main/java/com/hospital/shared/exception/GlobalExceptionHandler.java` - Added MaxUploadSizeExceededException and IllegalArgumentException handlers before generic Exception handler

## Decisions Made

- Object[] return from PhotoService.getCurrentPhoto() to avoid creating a dedicated wrapper DTO for a two-value tuple (Resource + contentType String)
- Content-Disposition: inline (not attachment) for GET photo endpoint — displays in browser, better for webcam/UI integration
- @Modifying @Query for deactivateCurrentPhotos() — atomic bulk update avoids loading all photos into memory
- MaxUploadSizeExceededException handler placed in @RestControllerAdvice because the exception is thrown by the DispatcherServlet filter chain before the controller method executes

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. Storage directory configured via app.storage.photos-dir in application.yml, created at bean initialization by FileStorageService.

## Next Phase Readiness

- Photo upload and download API is fully functional for integration into quick registration workflow (Plan 03-02)
- FileStorageService integration complete — photos stored outside web root, served only via authenticated API
- HIPAA audit trail in place via @Audited on both upload and download endpoints
- MaxUploadSizeExceededException handler ensures 5MB limit returns proper RFC 7807 response instead of 500

---
*Phase: 03-operational-enhancements*
*Completed: 2026-02-20*
