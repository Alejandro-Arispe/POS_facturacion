package com.pos.common.access;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.InvitacionRol;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@Transactional
public class RbacInvitations {
    private final EntityManager em;
    public RbacInvitations(EntityManager em) {
        this.em=em;
    }
    public String issue(Integer company,Integer user,Integer issuer,Integer role,long hours) {
        InvitacionRol i=new InvitacionRol();
        i.setId(UUID.randomUUID().toString());
        i.setIdEmpresa(company);
        i.setIdUsuario(user);
        i.setIdEmisor(issuer);
        i.setIdRol(role);
        i.setExpiraEn(Instant.now().plus(hours,ChronoUnit.HOURS));
        em.persist(i);
        return i.getId();
    }
    public void consume(Claims c,Integer company,Integer user,Integer issuer,Integer role) {
        String id=c.getId();
        InvitacionRol i=id==null?null:em.find(InvitacionRol.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(i==null || i.isConsumida() || i.isRevocada() || !i.getExpiraEn().isAfter(Instant.now())
        || !company.equals(i.getIdEmpresa()) || !user.equals(i.getIdUsuario())
        || !issuer.equals(i.getIdEmisor()) || !role.equals(i.getIdRol()))
        throw ApiException.badRequest("Invitacion usada, revocada o invalida. Solicita una nueva.");
        i.setConsumida(true);
    }
    public void revoke(Integer company,Integer user) {
        em.createQuery("update InvitacionRol i set i.revocada=true where i.idEmpresa=:company and i.idUsuario=:user and i.consumida=false")
        .setParameter("company",company).setParameter("user",user).executeUpdate();
    }
}
