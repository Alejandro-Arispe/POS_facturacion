package com.pos.siat.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.pos.siat.domain.SiatPuntoVenta;

import jakarta.persistence.LockModeType;

public interface SiatPuntoVentaRepository extends JpaRepository<SiatPuntoVenta, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SiatPuntoVenta> findByNitEmisorAndIdSucursalAndIdPuntoVenta(String nitEmisor, Integer idSucursal, Integer idPuntoVenta);
}
