package com.hospital.patient.api;

import com.hospital.patient.api.dto.MergePreviewResponse;
import com.hospital.patient.api.dto.MergeRequest;
import com.hospital.patient.api.dto.PatientDetailResponse;
import com.hospital.patient.application.PatientMergeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only patient merge endpoints.
 * Allows previewing and executing a duplicate patient merge.
 */
@RestController
@RequestMapping("/api/v1/admin/patients")
@PreAuthorize("hasRole('ADMIN')")
public class PatientMergeController {

    @Autowired
    private PatientMergeService patientMergeService;

    /**
     * GET /api/v1/admin/patients/merge-preview?sourceId={UUID}&targetId={UUID}
     * Preview what a merge would do: shows both patients and child entity counts.
     * Does NOT execute the merge.
     */
    @GetMapping("/merge-preview")
    public ResponseEntity<MergePreviewResponse> previewMerge(
        @RequestParam UUID sourceId,
        @RequestParam UUID targetId
    ) {
        MergePreviewResponse preview = patientMergeService.previewMerge(sourceId, targetId);
        return ResponseEntity.ok(preview);
    }

    /**
     * POST /api/v1/admin/patients/merge
     * Execute a patient merge: reassigns all child entities from source to target,
     * then marks source as INACTIVE. Returns the updated target patient.
     */
    @PostMapping("/merge")
    public ResponseEntity<PatientDetailResponse> executeMerge(
        @Valid @RequestBody MergeRequest request
    ) {
        PatientDetailResponse merged = patientMergeService.mergePatients(
            request.getSourceId(), request.getTargetId()
        );
        return ResponseEntity.ok(merged);
    }
}
