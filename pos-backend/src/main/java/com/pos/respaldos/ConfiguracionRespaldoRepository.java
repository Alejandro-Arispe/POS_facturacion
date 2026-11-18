package com.pos.respaldos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionRespaldoRepository extends JpaRepository<ConfiguracionRespaldo, Integer> {

    @Query("SELECT c FROM ConfiguracionRespaldo c WHERE c.empresa.idEmpresa = :idEmpresa")
    Optional<ConfiguracionRespaldo> findByEmpresaId(Integer idEmpresa);

    boolean existsByEmpresaIdEmpresa(Integer idEmpresa);
}

