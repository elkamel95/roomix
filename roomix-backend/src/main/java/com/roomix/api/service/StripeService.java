package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import com.roomix.api.exception.ResourceNotFoundException;
import com.roomix.api.model.entity.TokenTransaction;
import com.roomix.api.model.entity.User;
import com.roomix.api.model.enums.TokenPack;
import com.roomix.api.repository.TokenTransactionRepository;
import com.roomix.api.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {

    private final AppProperties              appProperties;
    private final UserRepository             userRepository;
    private final TokenTransactionRepository transactionRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // Lister les packs
    // ──────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getTokenPacks() {
        TokenPack best = TokenPack.STARTER; // le moins cher par token = PRO (on calcule ci-dessous)
        double bestRate = Double.MAX_VALUE;
        for (TokenPack p : TokenPack.values()) {
            if (p.getCentPerToken() < bestRate) {
                bestRate = p.getCentPerToken();
                best = p;
            }
        }
        final TokenPack bestPack = best;

        return List.of(
            packToMap(TokenPack.STARTER,  bestPack),
            packToMap(TokenPack.STANDARD, bestPack),
            packToMap(TokenPack.PRO,      bestPack)
        );
    }

    private Map<String, Object> packToMap(TokenPack pack, TokenPack bestValuePack) {
        int savings = 0;
        if (pack != TokenPack.STARTER) {
            // % d'économie vs Starter (en €/token)
            double starterRate = TokenPack.STARTER.getCentPerToken();
            double thisRate    = pack.getCentPerToken();
            savings = (int) Math.round((1 - thisRate / starterRate) * 100);
        }
        return Map.of(
            "id",          pack.name(),
            "label",       pack.getLabel(),
            "tokens",      pack.getTokens(),
            "price",       pack.getPriceEurCents(),
            "priceFormatted", pack.getPriceFormatted(),
            "tagline",     pack.getTagline(),
            "description", pack.getDescription(),
            "savings",     savings,
            "bestValue",   pack == bestValuePack
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Créer une session Checkout Stripe
    // ──────────────────────────────────────────────────────────────────────────

    public String createCheckoutSession(String userEmail, String packName) throws StripeException {
        AppProperties.Stripe cfg = appProperties.getStripe();
        Stripe.apiKey = cfg.getSecretKey();

        TokenPack pack = TokenPack.valueOf(packName.toUpperCase());
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(cfg.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cfg.getCancelUrl())
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount((long) pack.getPriceEurCents())
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("ROOMIX – Pack " + pack.getLabel())
                                        .setDescription(pack.getTokens() + " tokens · " + pack.getDescription())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .putMetadata("userId", user.getId().toString())
                .putMetadata("pack",   pack.name())
                .build();

        Session session = Session.create(params);
        log.info("Stripe checkout créé: {} pour {} — pack {}", session.getId(), userEmail, pack.name());
        return session.getUrl();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Traiter le webhook Stripe
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        AppProperties.Stripe cfg = appProperties.getStripe();
        Stripe.apiKey = cfg.getSecretKey();

        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, cfg.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Webhook Stripe — signature invalide: {}", e.getMessage());
            throw new IllegalArgumentException("Signature Stripe invalide");
        }

        if (!"checkout.session.completed".equals(event.getType())) {
            log.debug("Webhook Stripe ignoré: {}", event.getType());
            return;
        }

        // Récupérer le sessionId depuis le JSON brut (évite les problèmes de désérialisation)
        String sessionId;
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser
                    .parseString(event.getData().toJson()).getAsJsonObject();
            sessionId = obj.get("id").getAsString();
        } catch (Exception e) {
            log.warn("Webhook checkout.session.completed — impossible d'extraire sessionId: {}", e.getMessage());
            return;
        }

        // Charger la session complète depuis l'API Stripe
        Session session;
        try {
            session = Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.error("Webhook — impossible de récupérer la session {}: {}", sessionId, e.getMessage());
            return;
        }

        // Idempotence : vérifier que cette session n'a pas déjà été traitée
        if (transactionRepository.findByReference(sessionId).isPresent()) {
            log.info("Webhook déjà traité pour session: {}", sessionId);
            return;
        }

        String userId = session.getMetadata().get("userId");
        String pack   = session.getMetadata().get("pack");

        if (userId == null || pack == null) {
            log.warn("Webhook checkout — métadonnées manquantes: userId={} pack={}", userId, pack);
            return;
        }

        User user = userRepository.findById(java.util.UUID.fromString(userId)).orElse(null);
        if (user == null) {
            log.error("Webhook checkout — utilisateur introuvable: {}", userId);
            return;
        }

        TokenPack tokenPack = TokenPack.valueOf(pack);
        int tokensToAdd = tokenPack.getTokens();

        user.setTokenBalance((user.getTokenBalance() != null ? user.getTokenBalance() : 0) + tokensToAdd);
        userRepository.save(user);

        TokenTransaction tx = TokenTransaction.builder()
                .user(user)
                .amount(tokensToAdd)
                .type("PURCHASE")
                .pack(pack)
                .reference(sessionId)
                .description("Achat pack " + tokenPack.getLabel() + " — " + tokenPack.getPriceFormatted())
                .build();
        transactionRepository.save(tx);

        log.info("✅ Tokens crédités: +{} pour {} (session: {})", tokensToAdd, user.getEmail(), sessionId);
    }
}
