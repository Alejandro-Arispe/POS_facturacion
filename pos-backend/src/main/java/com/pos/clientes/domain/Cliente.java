package com.pos.clientes.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "cliente",
        indexes = @jakarta.persistence.Index(name="ix_cliente_empresa_usuario",columnList="id_empresa,id_usuario"),
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cliente_usuario_codigo",
                columnNames = {"id_usuario", "codigo_cliente"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "id_empresa")
    private Integer idEmpresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="id_empresa",insertable=false,updatable=false,foreignKey=@jakarta.persistence.ForeignKey(name="fk_cliente_empresa"))
    private com.pos.empresas.domain.Empresa empresa;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    private void inferCompany() {
        if(idEmpresa==null && categoriaCliente!=null) idEmpresa=categoriaCliente.getIdEmpresa();
        if(categoriaCliente!=null && !java.util.Objects.equals(idEmpresa,categoriaCliente.getIdEmpresa()))
            throw new IllegalArgumentException("La categoría y el cliente deben pertenecer a la misma empresa.");
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_cliente")
    private CategoriaCliente categoriaCliente;

    @Column(name = "codigo_cliente", nullable = false, length = 50)
    private String codigoCliente;

    @Column(name = "saldo_credito", precision = 12, scale = 2)
    private BigDecimal saldoCredito = BigDecimal.ZERO;

    @Column(name = "limite_credito", precision = 12, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
