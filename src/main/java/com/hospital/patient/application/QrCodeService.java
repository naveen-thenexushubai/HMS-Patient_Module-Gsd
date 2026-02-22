package com.hospital.patient.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class QrCodeService {

    private static final int QR_SIZE = 256;

    /**
     * Generates a 256×256 PNG QR code encoding the patient's businessId UUID.
     * The QR content is the plain UUID string — scanners call
     * GET /api/v1/patients/{businessId} to load the patient profile.
     */
    public byte[] generateForPatient(UUID businessId) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(businessId.toString(), BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code for patient " + businessId, e);
        }
    }
}
