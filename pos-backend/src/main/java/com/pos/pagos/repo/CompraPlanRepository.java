package com.pos.pagos.repo;

import java.util.List;
import java.util.Optional;
import com.pos.pagos.domain.CompraPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraPlanRepository extends JpaRepository<CompraPlan, Integer> {
    Optional<CompraPlan> findByStripeSessionId(String sessionId);
    List<CompraPlan> findByUsuario_IdUsuarioAndEmpresaIsNullAndEstadoOrderByIdCompraPlanAsc(Integer userId, String estado);
}
