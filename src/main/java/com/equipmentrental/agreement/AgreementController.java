package com.equipmentrental.agreement;

import com.equipmentrental.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Documents")
public class AgreementController {

    private final AgreementService service;

    public AgreementController(AgreementService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/reservations/{id}/agreement", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the rental agreement PDF (owner or ADMIN)",
            description = "Available for CONFIRMED and COMPLETED reservations.")
    @ApiResponse(responseCode = "200", description = "Rental agreement",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> agreement(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        AgreementFile file = service.generate(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
