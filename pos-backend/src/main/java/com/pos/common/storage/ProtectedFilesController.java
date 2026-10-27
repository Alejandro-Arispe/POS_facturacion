package com.pos.common.storage;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.access.*;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.productos.repo.ProductoRepository;
import com.pos.ventas.domain.Factura;
import com.pos.ventas.repo.FacturaRepository;
import com.pos.empresas.repo.CajaSesionRepository;

@RestController
public class ProtectedFilesController {
    private final ProductoRepository products;
    private final FacturaRepository invoices;
    private final CajaSesionRepository sessions;
    private final EmpresaAccess access;
    private final DataReadPolicy policy;
    private final RbacResolver rbac;
    private final Path media,xml;
    public ProtectedFilesController(ProductoRepository products,FacturaRepository invoices,CajaSesionRepository sessions,
            EmpresaAccess access,DataReadPolicy policy,RbacResolver rbac,@Value("${app.media-dir}") String media,@Value("${app.xml-dir}") String xml) {
        this.products=products;this.invoices=invoices;this.sessions=sessions;this.access=access;this.policy=policy;this.rbac=rbac;
        this.media=Paths.get(media).toAbsolutePath().normalize();this.xml=Paths.get(xml).toAbsolutePath().normalize();
    }
    @GetMapping("/media/products/{filename}")
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> image(@PathVariable String filename) {
        String reference="/media/products/"+filename;
        var user=SecurityUtils.currentUser();
        boolean readable=products.findByImagen(reference).stream().anyMatch(p -> access.canPermiso(user,p.getIdEmpresa(),null,"product:read")
            && rbac.field(rbac.resolve(user,p.getIdEmpresa(),null),"product","read","imagen"));
        if(!readable) throw ApiException.notFound("Imagen no encontrada.");
        String ext=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String type=switch(ext){case "jpg","jpeg" -> "image/jpeg";case "png" -> "image/png";case "webp" -> "image/webp";case "gif" -> "image/gif";default -> throw ApiException.notFound("Imagen no encontrada.");};
        return content(read(media,"products/"+filename),MediaType.parseMediaType(type),null);
    }
    @GetMapping("/api/facturas/{id}/{format}")
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> invoice(@PathVariable Integer id,@PathVariable String format) {
        if(!Set.of("xml","pdf").contains(format)) throw ApiException.notFound("Formato no encontrado.");
        Factura invoice=invoices.findById(id).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        var session=sessions.findWithCajaSucursalEmpresa(invoice.getVenta().getIdCajaSesion()).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        var branch=session.getCaja().getSucursal();
        var grants=policy.require(SecurityUtils.currentUser(),branch.getEmpresa().getIdEmpresa(),branch.getIdSucursal(),"invoice:reprint","sale:read");
        policy.productFields(grants,ProductFields.ALL.toArray(String[]::new));
        String stored=format.equals("xml")?invoice.getXmlGenerado():invoice.getPdfGenerado();
        byte[] bytes;
        if(format.equals("xml") && stored!=null && stored.stripLeading().startsWith("<")) bytes=stored.getBytes(StandardCharsets.UTF_8);
        else {
            if(stored==null || !stored.startsWith("/XMLS/")) throw ApiException.notFound("Archivo de factura no encontrado.");
            bytes=read(xml,stored.substring("/XMLS/".length()));
        }
        return content(bytes,format.equals("xml")?MediaType.APPLICATION_XML:MediaType.APPLICATION_PDF,"factura-"+id+"."+format);
    }
    @GetMapping({"/XMLS/{*path}","/media/{*path}"})
    public void rejectUnboundFiles() {throw ApiException.notFound("Archivo no encontrado.");}

    private byte[] read(Path root,String relative) {
        try {
            Path path=root.resolve(relative).normalize();
            if(!path.startsWith(root) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) throw ApiException.notFound("Archivo no encontrado.");
            return Files.readAllBytes(path);
        } catch(java.io.IOException ex) {throw ApiException.notFound("Archivo no encontrado.");}
    }
    private ResponseEntity<byte[]> content(byte[] bytes,MediaType type,String name) {
        var response=ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff");
        if(name!=null) response.header("Content-Disposition","attachment; filename=\""+name+"\"");
        return response.body(bytes);
    }
}
