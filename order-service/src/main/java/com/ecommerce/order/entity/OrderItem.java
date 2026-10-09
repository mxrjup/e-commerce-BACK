package com.ecommerce.order.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Entity
@Table(name = "ligne_commande")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Order order;
    @Column(name = "variante_id", nullable = false)
    private Long variantId;
    @Column(name = "designation", nullable = false)
    private String designation;
    @Column(name = "prix_unitaire_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceExclTax;
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;
    @Column(name = "quantite", nullable = false)
    private int quantity;
    public OrderItem(Order order, Long variantId, String designation, BigDecimal unitPriceExclTax, BigDecimal vatRate, int quantity) {
        this.order = order;
        this.variantId = variantId;
        this.designation = designation;
        this.unitPriceExclTax = unitPriceExclTax;
        this.vatRate = vatRate;
        this.quantity = quantity;
    }


    public BigDecimal getSubTotalExclTax(){
        if(unitPriceExclTax == null){
            return BigDecimal.ZERO;
        }

        return unitPriceExclTax.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }


    public BigDecimal getSubTotalInclTax(){

        BigDecimal subTotalHt = getSubTotalExclTax();

        if(vatRate == null || vatRate.compareTo(BigDecimal.ZERO) == 0){
            return subTotalHt;
        }

        // taxe = (ht * vatRate) / 100
        BigDecimal vatAmount = subTotalHt.multiply(vatRate).divide(BigDecimal.valueOf(100), 2,RoundingMode.HALF_UP);

        return subTotalHt.add(vatAmount);
    }
}