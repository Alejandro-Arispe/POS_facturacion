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
    public InvoiceReadService(FacturaRepository invoices,CajaSesionRepository sessions,DataReadPolicy policy) {
        this.invoices=invoices;this.sessions=sessions;this.policy=policy;
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
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("id_factura",invoice.getIdFactura());row.put("id_venta",invoice.getVenta().getIdVenta());
            row.put("fecha_emision",invoice.getFechaEmision());row.put("nit_cliente",invoice.getNitCliente());
            row.put("numero_factura",invoice.getNumeroFactura());row.put("monto_total",invoice.getMontoTotal());row.put("iva",invoice.getIva());
            row.put("xml_generado","/api/facturas/"+invoice.getIdFactura()+"/xml");row.put("pdf_generado","/api/facturas/"+invoice.getIdFactura()+"/pdf");
            result.add(row);
        }
        return result;
    }
}
