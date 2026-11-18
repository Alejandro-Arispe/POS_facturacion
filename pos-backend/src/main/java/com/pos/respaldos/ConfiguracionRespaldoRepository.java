package com.pos.respaldos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionRespaldoRepository extends JpaRepository<ConfiguracionRespaldo, Integer> {

    @Query("SELECT c FROM ConfiguracionRespaldo c WHERE c.empresa.idEmpresa = :idEmpresa")
    Optional<ConfiguracionRespaldo> findByEmpresaId(Integer idEmpresa);

    boolean existsByEmpresaIdEmpresa(Integer idEmpresa);

    /** Configuraciones activas con empresa y sucursal ya cargadas (el scheduler trabaja fuera de transaccion). */
    @Query("SELECT c FROM ConfiguracionRespaldo c JOIN FETCH c.empresa LEFT JOIN FETCH c.sucursal WHERE c.activo = true")
    List<ConfiguracionRespaldo> findActivasConEmpresa();
}

