package com.pos.ventas.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DatosCheque {

    @Column(name = "cheque_banco", length = 100)
    private String banco;

    @Column(name = "cheque_numero", length = 50)
    private String numero;

    @Column(name = "cheque_fecha_emision")
    private LocalDate fechaEmision;
}
