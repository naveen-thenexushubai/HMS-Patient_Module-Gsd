package com.hospital.fhir;

import com.hospital.patient.api.dto.EmergencyContactDto;
import com.hospital.patient.api.dto.PatientDetailResponse;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.PatientStatus;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.*;

/**
 * Maps PatientDetailResponse to a hand-crafted FHIR R4 Patient resource.
 * No HAPI FHIR library dependency — avoids 50MB+ transitive dependency tree.
 * Produces Map<String,Object> that Jackson serializes to FHIR-compliant JSON.
 */
@Component
public class FhirPatientMapper {

    /**
     * Maps a PatientDetailResponse to a FHIR R4 Patient resource.
     * Reference: https://hl7.org/fhir/R4/patient.html
     */
    public Map<String, Object> toFhirPatient(PatientDetailResponse patient) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", patient.getBusinessId().toString());

        // meta
        if (patient.getLastModifiedAt() != null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("lastUpdated", patient.getLastModifiedAt().toString());
            resource.put("meta", meta);
        }

        // identifier: patientId
        List<Map<String, Object>> identifiers = new ArrayList<>();
        Map<String, Object> identifier = new LinkedHashMap<>();
        identifier.put("system", "urn:hospital:patient-id");
        identifier.put("value", patient.getPatientId());
        identifiers.add(identifier);
        resource.put("identifier", identifiers);

        // active
        resource.put("active", PatientStatus.ACTIVE.equals(patient.getStatus()));

        // name
        List<Map<String, Object>> names = new ArrayList<>();
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("use", "official");
        name.put("family", patient.getLastName());
        name.put("given", List.of(patient.getFirstName()));
        names.add(name);
        resource.put("name", names);

        // telecom
        List<Map<String, Object>> telecoms = new ArrayList<>();
        if (patient.getPhoneNumber() != null) {
            Map<String, Object> phone = new LinkedHashMap<>();
            phone.put("system", "phone");
            phone.put("value", patient.getPhoneNumber());
            phone.put("use", "mobile");
            telecoms.add(phone);
        }
        if (patient.getEmail() != null) {
            Map<String, Object> email = new LinkedHashMap<>();
            email.put("system", "email");
            email.put("value", patient.getEmail());
            telecoms.add(email);
        }
        if (!telecoms.isEmpty()) resource.put("telecom", telecoms);

        // gender
        if (patient.getGender() != null) {
            resource.put("gender", mapGender(patient.getGender()));
        }

        // birthDate
        if (patient.getDateOfBirth() != null) {
            resource.put("birthDate", patient.getDateOfBirth().toString());
        }

        // address
        if (patient.getAddressLine1() != null) {
            List<Map<String, Object>> addresses = new ArrayList<>();
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("use", "home");
            List<String> lines = new ArrayList<>();
            lines.add(patient.getAddressLine1());
            if (patient.getAddressLine2() != null) lines.add(patient.getAddressLine2());
            address.put("line", lines);
            if (patient.getCity() != null) address.put("city", patient.getCity());
            if (patient.getState() != null) address.put("state", patient.getState());
            if (patient.getZipCode() != null) address.put("postalCode", patient.getZipCode());
            addresses.add(address);
            resource.put("address", addresses);
        }

        // contact (emergency contacts)
        if (patient.getEmergencyContacts() != null && !patient.getEmergencyContacts().isEmpty()) {
            List<Map<String, Object>> contacts = new ArrayList<>();
            for (EmergencyContactDto ec : patient.getEmergencyContacts()) {
                Map<String, Object> contact = new LinkedHashMap<>();
                // relationship coding
                List<Map<String, Object>> relCodings = new ArrayList<>();
                Map<String, Object> relCoding = new LinkedHashMap<>();
                relCoding.put("system", "http://terminology.hl7.org/CodeSystem/v2-0131");
                relCoding.put("code", "C");
                relCoding.put("display", ec.getRelationship() != null ? ec.getRelationship() : "Emergency Contact");
                relCodings.add(relCoding);
                Map<String, Object> relCC = new LinkedHashMap<>();
                relCC.put("coding", relCodings);
                contact.put("relationship", List.of(relCC));
                // name
                Map<String, Object> contactName = new LinkedHashMap<>();
                contactName.put("text", ec.getName());
                contact.put("name", contactName);
                // telecom
                if (ec.getPhoneNumber() != null) {
                    Map<String, Object> contactPhone = new LinkedHashMap<>();
                    contactPhone.put("system", "phone");
                    contactPhone.put("value", ec.getPhoneNumber());
                    contact.put("telecom", List.of(contactPhone));
                }
                contacts.add(contact);
            }
            resource.put("contact", contacts);
        }

        return resource;
    }

    /**
     * Maps a list of patients to a FHIR R4 Bundle (searchset).
     */
    public Map<String, Object> toFhirBundle(List<PatientDetailResponse> patients, int total) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", total);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (PatientDetailResponse patient : patients) {
            Map<String, Object> entry = new LinkedHashMap<>();
            Map<String, Object> fullUrl = new LinkedHashMap<>();
            entry.put("fullUrl", "urn:uuid:" + patient.getBusinessId());
            entry.put("resource", toFhirPatient(patient));
            entries.add(entry);
        }
        bundle.put("entry", entries);
        return bundle;
    }

    private String mapGender(Gender gender) {
        return switch (gender) {
            case MALE -> "male";
            case FEMALE -> "female";
            case OTHER -> "other";
            default -> "unknown";
        };
    }
}
