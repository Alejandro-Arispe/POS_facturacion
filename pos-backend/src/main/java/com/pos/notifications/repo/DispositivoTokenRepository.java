package com.pos.notifications.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.pos.notifications.domain.DispositivoToken;

public interface DispositivoTokenRepository extends JpaRepository<DispositivoToken, Integer> {

    Optional<DispositivoToken> findByToken(String token);

    List<DispositivoToken> findByIdEmpresa(Integer idEmpresa);

    List<DispositivoToken> findByUidUsuario(String uidUsuario);

    List<DispositivoToken> findByUidUsuarioIn(Collection<String> uidUsuarios);

    @Transactional
    void deleteByToken(String token);
}
