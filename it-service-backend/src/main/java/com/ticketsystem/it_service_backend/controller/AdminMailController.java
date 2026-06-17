package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.service.EmailService;
import com.ticketsystem.it_service_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only mail diagnostics. Lets an administrator verify that the configured
 * SMTP server (e.g. a real provider such as Gmail, instead of dev Mailpit) is
 * actually able to deliver mail, without having to trigger a full ticket flow.
 */
@Log4j2
@Tag(name = "Mail Yönetimi", description = "Yönetici SMTP testi")
@RestController
@RequestMapping("/api/v1/admin/mail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailController {

    private final EmailService emailService;
    private final UserService userService;

    /**
     * Sends a test email to the calling admin's own address SYNCHRONOUSLY and
     * reports the outcome, so the configured SMTP setup can be validated end to
     * end. Sending to self (not an arbitrary address) keeps the endpoint from
     * being abused as an open relay / spam vector.
     *
     * @param jwt the authenticated admin
     * @return {@code {"success": <bool>, "recipient": <email>, "error": <reason|"">}}
     */
    @Operation(summary = "Test maili gönder (Admin)",
            description = """
                    Oturum açan yöneticinin kendi e-posta adresine senkron bir test maili
                    gönderir ve sonucu döner. Gerçek SMTP (ör. Gmail) yapılandırmasının
                    çalışıp çalışmadığını ticket akışı tetiklemeden doğrulamak içindir.
                    Mail yalnızca çağıran yöneticinin kendisine gider (kötüye kullanım/spam önlenir).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşlem tamamlandı (success alanına bakın)"),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN erişebilir")
    })
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@AuthenticationPrincipal Jwt jwt) {
        User admin = userService.getUserById(jwt.getSubject());
        log.info("Admin test maili isteği. Kullanıcı: {}, Alıcı: {}", admin.getId(), admin.getEmail());

        String error = emailService.sendTestEmail(admin);
        boolean success = error == null;

        // LinkedHashMap: null error'ı "" olarak veriyoruz (Map.of null kabul etmez).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("recipient", admin.getEmail() == null ? "" : admin.getEmail());
        body.put("error", success ? "" : error);
        return ResponseEntity.ok(body);
    }
}
