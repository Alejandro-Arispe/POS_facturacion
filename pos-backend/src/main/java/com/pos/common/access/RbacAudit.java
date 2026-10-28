package com.pos.common.access;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;
import com.pos.auditoria.service.BitacoraService;
import com.pos.usuarios.domain.Usuario;

@Component
public class RbacAudit {
    public record Change(Usuario actor,Integer company,String action,String detail){
    }
    private final ApplicationEventPublisher events;
    private final BitacoraService audit;
    public RbacAudit(ApplicationEventPublisher events,BitacoraService audit){
        this.events=events;
        this.audit=audit;
    }
    public void record(Usuario actor,Integer company,String action,String detail){
        events.publishEvent(new Change(actor,company,action,detail));
    }
    @TransactionalEventListener
    public void committed(Change c){
        audit.registrar(new BitacoraService.Evento(c.actor(),c.actor().getEmail(),c.company(),c.action(),"USUARIOS",null,null,200,c.detail()));
    }
}
