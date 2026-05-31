package com.roomix.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TokenTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Montant en tokens : positif = crédit, négatif = débit */
    @Column(nullable = false)
    private Integer amount;

    /** PURCHASE | GENERATION | BONUS | REFUND */
    @Column(nullable = false, length = 20)
    private String type;

    /** Pack acheté (STARTER / STANDARD / PRO), null si non applicable */
    @Column(length = 20)
    private String pack;

    /** Stripe session ID ou project UUID selon le type */
    @Column(length = 255)
    private String reference;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
