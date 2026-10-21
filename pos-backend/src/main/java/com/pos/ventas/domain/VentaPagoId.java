package com.pos.ventas.domain;

import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VentaPagoId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer idVenta;
    private Integer idMetodoPago;

    public VentaPagoId(Integer idVenta, Integer idMetodoPago) {
        this.idVenta = idVenta;
        this.idMetodoPago = idMetodoPago;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VentaPagoId that)) {
            return false;
        }
        return Objects.equals(idVenta, that.idVenta)
                && Objects.equals(idMetodoPago, that.idMetodoPago);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idVenta, idMetodoPago);
    }
}
