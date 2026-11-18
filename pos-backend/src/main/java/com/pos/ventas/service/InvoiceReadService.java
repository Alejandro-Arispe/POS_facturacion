package com.pos.ventas.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.access.*;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.ventas.repo.FacturaRepository;
import com.pos.empresas.repo.CajaSesionRepository;

@Service
public class InvoiceReadService {
    private final FacturaRepository invoices;
    private final CajaSesionRepository sessions;
    private final DataReadPolicy policy;
    private final com.pos.siat.service.SiatFacturaService siat;
    private final com.pos.clientes.repo.ClienteRepository clientes;
    private final com.pos.common.access.EmpresaAccess access;
    public InvoiceReadService(FacturaRepository invoices,CajaSesionRepository sessions,DataReadPolicy policy,
            com.pos.siat.service.SiatFacturaService siat,com.pos.clientes.repo.ClienteRepository clientes,
            com.pos.common.access.EmpresaAccess access) {
        this.invoices=invoices;this.sessions=sessions;this.policy=policy;this.siat=siat;
        this.clientes=clientes;this.access=access;
    }

    /** HU-16: facturas de las compras del cliente autenticado. */
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine() {
        var user=SecurityUtils.currentUser();
        access.requireUsuarioActivo(user);
        List<Integer> ids=clientes.findByIdUsuario(user.getIdUsuario()).stream().map(com.pos.clientes.domain.Cliente::getIdCliente).toList();
        List<Map<String,Object>> result=new ArrayList<>();
        if(ids.isEmpty()) return result;
        for(var invoice:invoices.findByVenta_IdClienteInOrderByFechaEmisionDesc(ids)) {
            Map<String,Object> row=siat.resumen(invoice);
            row.put("iva",invoice.getIva());
            row.put("pdf_generado","/api/facturas/me/"+invoice.getIdFactura()+"/pdf");
            row.put("qr_code_data",invoice.getQrCodeData());
            result.add(row);
        }
        return result;
    }

    /** HU-16/18: PDF de una factura solo si pertenece a una compra del cliente autenticado. */
    public record Pdf(String nombre, byte[] bytes) { }

    @Transactional
    public Pdf minePdf(Integer id) {
        var user=SecurityUtils.currentUser();
        access.requireUsuarioActivo(user);
        var invoice=invoices.findById(id).orElse(null);
        Integer idCliente=invoice==null?null:invoice.getVenta().getIdCliente();
        boolean own=idCliente!=null && clientes.findById(idCliente)
                .map(c -> java.util.Objects.equals(c.getIdUsuario(),user.getIdUsuario())).orElse(false);
        if(!own) throw com.pos.common.exception.ApiException.notFound("Factura no encontrada.");
        return new Pdf("Factura-"+invoice.getNumeroFactura()+".pdf",siat.pdfDe(invoice));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(Integer company) {
        var user=SecurityUtils.currentUser();
        var branches=policy.branches(user,company,"invoice:reprint","sale:read");
        if(branches.isEmpty()) {policy.require(user,company,null,"invoice:reprint","sale:read");return List.of();}
        List<Map<String,Object>> result=new ArrayList<>();
        for(var invoice:invoices.findByIdEmpresa(company)) {
            var session=sessions.findWithCajaSucursalEmpresa(invoice.getVenta().getIdCajaSesion()).orElse(null);
            if(session==null || !branches.contains(session.getCaja().getSucursal().getIdSucursal())) continue;
            Map<String,Object> row=siat.resumen(invoice);
            row.put("iva",invoice.getIva());
            row.put("xml_generado","/api/facturas/"+invoice.getIdFactura()+"/xml");row.put("pdf_generado","/api/facturas/"+invoice.getIdFactura()+"/pdf");
            result.add(row);
        }
        return result;
    }
}
