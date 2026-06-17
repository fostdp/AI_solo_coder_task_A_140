package com.sandship.stability.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ships")
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ship_type", nullable = false, length = 50)
    private String shipType = "方艄沙船";

    @Column(name = "length_overall", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthOverall;

    @Column(name = "breadth_molded", nullable = false, precision = 10, scale = 2)
    private BigDecimal breadthMolded;

    @Column(name = "depth_molded", nullable = false, precision = 10, scale = 2)
    private BigDecimal depthMolded;

    @Column(name = "design_draft", nullable = false, precision = 10, scale = 2)
    private BigDecimal designDraft;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal displacement;

    @Column(name = "lightship_weight", nullable = false, precision = 12, scale = 2)
    private BigDecimal lightshipWeight;

    @Column(name = "deadweight_tons", nullable = false, precision = 12, scale = 2)
    private BigDecimal deadweightTons;

    @Column(name = "metacentric_height_design", nullable = false, precision = 8, scale = 4)
    private BigDecimal metacentricHeightDesign = new BigDecimal("0.8");

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
