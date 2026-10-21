package com.pos.notifications.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.notifications.domain.DispositivoToken;

public interface DispositivoTokenRepository extends JpaRepository<DispositivoToken, Integer> {

    Optional<DispositivoToken> findByToken(String token);

    List<DispositivoToken> findByIdEmpresa(Integer idEmpresa);

    List<DispositivoToken> findByUidUsuario(String uidUsuario);

    void deleteByToken(String token);
}
