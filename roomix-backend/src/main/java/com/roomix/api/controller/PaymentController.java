package com.roomix.api.controller;

import com.roomix.api.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final StripeService stripeService;

    /**
     * Liste les 3 packs de tokens disponibles.
     * GET /payments/packs
     */
    @GetMapping("/packs")
    public ResponseEntity<List<Map<String, Object>>> getPacks() {
        return ResponseEntity.ok(stripeService.getTokenPacks());
    }

    /**
     * Crée une session Stripe Checkout et retourne l'URL de paiement.
     * POST /payments/checkout
     * Body: { "pack": "STARTER" | "STANDARD" | "PRO" }
     */
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        String pack = body.get("pack");
        if (pack == null || pack.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le champ 'pack' est requis"));
        }
        try {
            String checkoutUrl = stripeService.createCheckoutSession(
                    userDetails.getUsername(), pack);
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Pack inconnu: " + pack));
        } catch (Exception e) {
            log.error("Erreur Stripe checkout: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erreur lors de la création du paiement"));
        }
    }

    /**
     * Webhook Stripe — reçoit les événements de paiement.
     * POST /payments/webhook
     * ⚠️  Cette route doit être exclue de la vérification JWT (configuré dans SecurityConfig).
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        if (sigHeader == null) {
            log.warn("Webhook Stripe reçu sans en-tête Stripe-Signature");
            return ResponseEntity.badRequest().body("Missing signature");
        }
        try {
            stripeService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Erreur webhook Stripe: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Webhook error");
        }
    }
}
