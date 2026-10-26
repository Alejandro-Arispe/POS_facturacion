package com.pos.pagos.domain;

import java.time.LocalDate;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Plan;
import com.pos.usuarios.domain.Usuario;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "compra_plan")
@Getter @Setter @NoArgsConstructor
public class CompraPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCompraPlan;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_plan", nullable = false)
    private Plan plan;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_empresa", unique = true)
    private Empresa empresa;
    @Column(nullable = false) private String estado = "pendiente";
    @Column(unique = true) private String stripeSessionId;
    @Column(length = 2048) private String checkoutUrl;
    private LocalDate fechaPago;
}
