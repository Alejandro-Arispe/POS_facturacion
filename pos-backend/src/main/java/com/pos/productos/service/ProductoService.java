package com.pos.productos.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.inventario.service.InventarioService;
import com.pos.productos.domain.CategoriaProducto;
import com.pos.productos.domain.Producto;
import com.pos.productos.domain.SubcategoriaProducto;
import com.pos.productos.dto.ProductoDtos;
import com.pos.productos.repo.CategoriaProductoRepository;
import com.pos.productos.repo.ProductoRepository;
import com.pos.productos.repo.SubcategoriaProductoRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.UsuarioRolRepository;
import com.pos.common.access.RbacResolver;
import com.pos.common.access.FieldAuthorization;
import com.pos.common.access.ProductFields;
import com.pos.common.storage.ProductImageStorage;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

@Service
public class ProductoService {

    private final EmpresaAccess empresaAccess;
    private final CategoriaProductoRepository categoriaRepository;
    private final SubcategoriaProductoRepository subcategoriaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioService inventarioService;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RbacResolver rbac;
    private final FieldAuthorization fields;
    private final ProductImageStorage images;

    public ProductoService(
            EmpresaAccess empresaAccess,
            CategoriaProductoRepository categoriaRepository,
            SubcategoriaProductoRepository subcategoriaRepository,
            ProductoRepository productoRepository,
            InventarioService inventarioService,
            UsuarioRolRepository usuarioRolRepository,
            RbacResolver rbac, FieldAuthorization fields, ProductImageStorage images) {
        this.empresaAccess = empresaAccess;
        this.categoriaRepository = categoriaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.productoRepository = productoRepository;
        this.inventarioService = inventarioService;
        this.usuarioRolRepository = usuarioRolRepository;
        this.rbac=rbac; this.fields=fields; this.images=images;
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.CategoriaResponse> listarCategorias() {
        return categoriaRepository.findAllByOrderByNombreAsc().stream().map(this::toCategoria).toList();
    }

    @Transactional
    public ProductoDtos.CategoriaResponse crearCategoria(Usuario usuario, Integer idEmpresa, ProductoDtos.CategoriaCreate datos) {
        if (idEmpresa != null) {
            empresaAccess.requireEmpresa(usuario, idEmpresa);
        } else {
            empresaAccess.requireUsuarioActivo(usuario);
        }
        CategoriaProducto categoria = new CategoriaProducto();
        categoria.setIdEmpresa(idEmpresa);
        categoria.setNombre(datos.getNombre());
        categoria.setDescripcion(datos.getDescripcion());
        categoria.setActivo(datos.getActivo() == null || datos.getActivo());
        try {
            return toCategoria(categoriaRepository.save(categoria));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("Ya existe una categoria con ese nombre.");
        }
    }

    @Transactional(readOnly = true)
    public ProductoDtos.CategoriaResponse obtenerCategoria(Integer id) {
        return toCategoria(categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada.")));
    }

    @Transactional
    public ProductoDtos.CategoriaResponse actualizarCategoria(Integer id, ProductoDtos.CategoriaUpdate datos) {
        CategoriaProducto categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada."));
        if (datos.getNombre() != null) {
            categoria.setNombre(datos.getNombre());
        }
        if (datos.getDescripcion() != null) {
            categoria.setDescripcion(datos.getDescripcion());
        }
        if (datos.getActivo() != null) {
            categoria.setActivo(datos.getActivo());
        }
        try {
            return toCategoria(categoriaRepository.save(categoria));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("Ya existe una categoria con ese nombre.");
        }
    }

    @Transactional
    public ProductoDtos.SubcategoriaResponse crearSubcategoria(ProductoDtos.SubcategoriaCreate datos) {
        CategoriaProducto categoria = requireCategoriaActiva(datos.getIdCategoriaProducto());
        SubcategoriaProducto sub = new SubcategoriaProducto();
        sub.setCategoriaProducto(categoria);
        sub.setNombre(datos.getNombre());
        sub.setDescripcion(datos.getDescripcion());
        sub.setActivo(datos.getActivo() == null || datos.getActivo());
        return toSubcategoria(subcategoriaRepository.save(sub));
    }

    @Transactional(readOnly = true)
    public ProductoDtos.SubcategoriaResponse obtenerSubcategoria(Integer id) {
        return toSubcategoria(subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada.")));
    }

    @Transactional
    public ProductoDtos.SubcategoriaResponse actualizarSubcategoria(Integer id, ProductoDtos.SubcategoriaUpdate datos) {
        SubcategoriaProducto sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada."));
        if (datos.getIdCategoriaProducto() != null) {
            sub.setCategoriaProducto(requireCategoriaActiva(datos.getIdCategoriaProducto()));
        }
        if (datos.getNombre() != null) {
            sub.setNombre(datos.getNombre());
        }
        if (datos.getDescripcion() != null) {
            sub.setDescripcion(datos.getDescripcion());
        }
        if (datos.getActivo() != null) {
            sub.setActivo(datos.getActivo());
        }
        return toSubcategoria(subcategoriaRepository.save(sub));
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> listarProductos(Usuario usuario) {
        empresaAccess.requireUsuarioActivo(usuario);
        Map<Integer,RbacResolver.Grants> readable=new LinkedHashMap<>();
        for (Integer company : empresasDelUsuario(usuario)) {
            if(empresaAccess.canPermiso(usuario,company,null,"product:read")) readable.put(company,rbac.resolve(usuario,company,null));
        }
        if (readable.isEmpty()) return List.of();
        return productoRepository.findByIdEmpresaInOrderByNombreAsc(readable.keySet()).stream()
                .map(p->toProducto(p,readable.get(p.getIdEmpresa()))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String,Object> obtenerProducto(Usuario usuario,Integer idProducto) {
        Producto p=requireProductoDeUsuario(usuario,idProducto);
        return toProducto(p,authorize(usuario,p.getIdEmpresa(),"read"));
    }

    @Transactional
    public Map<String,Object> actualizarProducto(Usuario usuario,Integer idProducto,ProductoDtos.ProductoUpdate datos) {
        return actualizarConImagen(usuario,idProducto,datos,null);
    }

    @Transactional
    public Map<String,Object> actualizarConImagen(Usuario usuario,Integer idProducto,ProductoDtos.ProductoUpdate datos,MultipartFile image) {
        Producto p=requireProductoDeUsuario(usuario,idProducto);
        RbacResolver.Grants grants=authorize(usuario,p.getIdEmpresa(),"update");
        Set<String> supplied=new LinkedHashSet<>(datos.getProvided());
        if (image!=null) supplied.add("imagen");
        fields.require(grants,"product","update",supplied);
        validateValues(datos,false);
        SubcategoriaProducto sub=datos.getProvided().contains("id_subcategoria") && datos.getIdSubcategoria()!=null
                ? requireSubcategoriaEmpresa(datos.getIdSubcategoria(),p.getIdEmpresa()) : null;
        if (!Boolean.TRUE.equals(p.getActivo()) && Boolean.TRUE.equals(datos.getActivo())) empresaAccess.requireProductCapacity(p.getIdEmpresa());
        // Validate every input before assigning anything, including image storage.
        String newImage=image==null?null:storeImage(image);
        apply(p,datos,sub);
        if (image!=null) p.setImagen(newImage);
        persist(p);
        return toProducto(p,grants);
    }

    @Transactional
    public Map<String,Object> actualizarImagen(Usuario usuario,Integer idProducto,MultipartFile image) {
        if (image==null) throw ApiException.badRequest("Debe enviar una imagen.");
        return actualizarConImagen(usuario,idProducto,new ProductoDtos.ProductoUpdate(),image);
    }

    @Transactional
    public Map<String,String> eliminarProducto(Usuario usuario,Integer idProducto) {
        Producto p=requireProductoDeUsuario(usuario,idProducto);
        authorize(usuario,p.getIdEmpresa(),"delete");
        try { productoRepository.delete(p); productoRepository.flush(); }
        catch (DataIntegrityViolationException ex) { throw ApiException.conflict("El producto tiene operaciones relacionadas. Desactívalo para conservar su historial.","Producto referenciado."); }
        return Map.of("mensaje","Producto eliminado correctamente.");
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> listarPorEmpresa(Usuario usuario,Integer company) {
        RbacResolver.Grants grants=authorize(usuario,company,"read");
        return productoRepository.findByIdEmpresaOrderByNombreAsc(company).stream().map(p->toProducto(p,grants)).toList();
    }

    @Transactional(readOnly = true)
    public String exportar(Usuario user,Integer company,List<String> requested,List<Integer> ids) {
        var grants=authorize(user,company,"read");
        List<String> columns=requested==null?List.of("id_producto","nombre","precio"):requested;
        List<String> allowed=new ArrayList<>();
        for(String column:columns) {
            if(column==null) throw ApiException.badRequest("Columna no admitida.");
            String field=column.equals("subcategoria")?"id_subcategoria":column;
            if(!column.equals("id_producto") && !ProductFields.ALL.contains(field)) throw ApiException.badRequest("Columna no admitida.");
            if((column.equals("id_producto") || fields.allows(grants,"product","read",field)) && !allowed.contains(column)) allowed.add(column);
        }
        if(allowed.isEmpty()) allowed.add("id_producto");
        StringBuilder csv=new StringBuilder("\uFEFF");
        csv.append(allowed.stream().map(c->csvCell(c.equals("id_producto")?"ID":c.equals("subcategoria")?"Subcategoría":ProductFields.LABELS.get(c))).collect(Collectors.joining(";"))).append("\r\n");
        for(Producto product:productoRepository.findByIdEmpresaOrderByNombreAsc(company)) {
            if(ids!=null && !ids.contains(product.getIdProducto())) continue;
            Map<String,Object> row=toProducto(product,grants);
            csv.append(allowed.stream().map(c->{
                Object value=row.get(c);
                if(value instanceof ProductoDtos.SubcategoriaResponse sub) value=sub.getNombre();
                if(c.equals("activo") && value instanceof Boolean active) value=active?"Activo":"Inactivo";
                return csvCell(value);
            }).collect(Collectors.joining(";"))).append("\r\n");
        }
        return csv.toString();
    }
    private String csvCell(Object value) {
        String text=value==null?"":String.valueOf(value);
        if(value instanceof String && text.matches("(?s)^[\\s\\x00-\\x1f]*[=+\\-@].*")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.SubcategoriaResponse> listarSubcategoriasPorEmpresa(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return subcategoriaRepository.findByIdEmpresa(idEmpresa).stream().map(this::toSubcategoria).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.CategoriaResponse> listarCategoriasConSubcategorias(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        List<CategoriaProducto> categorias = categoriaRepository.findByIdEmpresaOrderByNombreAsc(idEmpresa);
        List<SubcategoriaProducto> subs = subcategoriaRepository.findByIdEmpresa(idEmpresa);
        Map<Integer, List<SubcategoriaProducto>> porCategoria = subs.stream()
                .collect(Collectors.groupingBy(s -> s.getCategoriaProducto().getIdCategoriaProducto()));
        List<ProductoDtos.CategoriaResponse> result = new ArrayList<>();
        for (CategoriaProducto categoria : categorias) {
            ProductoDtos.CategoriaResponse dto = toCategoria(categoria);
            dto.setSubcategorias(porCategoria.getOrDefault(categoria.getIdCategoriaProducto(), List.of())
                    .stream().map(this::toSubcategoria).toList());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public Map<String,Object> crearProducto(Usuario usuario,Integer company,ProductoDtos.ProductoCreate data,MultipartFile image) {
        RbacResolver.Grants grants=authorize(usuario,company,"create");
        Set<String> supplied=new LinkedHashSet<>(data.getProvided());
        if (image!=null) supplied.add("imagen");
        fields.require(grants,"product","create",supplied);
        fields.require(grants,"product","create",ProductFields.REQUIRED_CREATE);
        validateValues(data,true);
        SubcategoriaProducto sub=requireSubcategoriaEmpresa(data.getIdSubcategoria(),company);
        if (!Boolean.FALSE.equals(data.getActivo())) empresaAccess.requireProductCapacity(company);
        String newImage=image==null?null:storeImage(image);
        Producto p=new Producto(); p.setIdEmpresa(company);
        apply(p,data,sub);
        if (image!=null) p.setImagen(newImage);
        persist(p);
        inventarioService.sincronizarStocksPorProducto(p.getIdProducto(),company);
        return toProducto(p,grants);
    }

    private void persist(Producto product) {
        try { productoRepository.saveAndFlush(product); }
        catch (DataIntegrityViolationException ex) { throw ApiException.badRequest("No se pudo guardar el producto por un conflicto con datos existentes."); }
    }

    private RbacResolver.Grants authorize(Usuario user,Integer company,String action) {
        empresaAccess.requireEmpresa(user,company);
        var grants=rbac.resolve(user,company,null);
        if (!grants.allows("product:"+action)) throw ApiException.forbidden("No tienes permiso para esta operación de productos.");
        return grants;
    }

    private SubcategoriaProducto requireSubcategoriaEmpresa(Integer id,Integer company) {
        SubcategoriaProducto sub=requireSubcategoriaActiva(id);
        if (sub.getCategoriaProducto()==null || !company.equals(sub.getCategoriaProducto().getIdEmpresa()))
            throw ApiException.notFound("Subcategoría no encontrada en esta empresa.");
        return sub;
    }

    private void validateValues(ProductoDtos.ProductoUpdate d,boolean create) {
        Set<String> sent=d.getProvided();
        if (create && !sent.containsAll(ProductFields.REQUIRED_CREATE)) throw ApiException.badRequest("Completa nombre, subcategoría, unidad de medida y precio.");
        if (sent.contains("nombre")) text(d.getNombre(),150,true,"nombre");
        if (sent.contains("unidad_medida")) text(d.getUnidadMedida(),50,true,"unidad_medida");
        if (sent.contains("codigo_barra")) text(d.getCodigoBarra(),100,false,"codigo_barra");
        if (sent.contains("descripcion")) text(d.getDescripcion(),10000,false,"descripcion");
        if (sent.contains("id_subcategoria") && (d.getIdSubcategoria()==null || d.getIdSubcategoria()<1)) throw ApiException.badRequest("Selecciona una subcategoría válida.");
        if (sent.contains("activo") && d.getActivo()==null) throw ApiException.badRequest("El estado no puede ser nulo.");
        if (sent.contains("precio")) money(d.getPrecio(),false);
        if (sent.contains("costo")) money(d.getCosto(),true);
        if (sent.contains("imagen") && d.getImagen()!=null) throw ApiException.badRequest("Envía la imagen como archivo o null para retirarla.");
    }

    private void text(String value,int length,boolean required,String field) {
        if ((required && (value==null || value.isBlank())) || (value!=null && value.length()>length)) throw ApiException.badRequest("Valor inválido para "+field+".");
    }
    private void money(BigDecimal value,boolean nullable) {
        if (value==null && nullable) return;
        if (value==null || value.signum()<0 || value.scale()>2 || value.compareTo(new BigDecimal("9999999999.99"))>0)
            throw ApiException.badRequest("El importe debe ser no negativo y tener hasta dos decimales.");
    }
    private void apply(Producto p,ProductoDtos.ProductoUpdate d,SubcategoriaProducto sub) {
        for (String key:d.getProvided()) switch (key) {
            case "nombre" -> p.setNombre(d.getNombre().strip());
            case "descripcion" -> p.setDescripcion(d.getDescripcion());
            case "codigo_barra" -> p.setCodigoBarra(d.getCodigoBarra());
            case "id_subcategoria" -> p.setSubcategoria(sub);
            case "unidad_medida" -> p.setUnidadMedida(d.getUnidadMedida().strip());
            case "precio" -> p.setPrecio(d.getPrecio());
            case "costo" -> p.setCosto(d.getCosto());
            case "activo" -> p.setActivo(d.getActivo());
            case "imagen" -> p.setImagen(null);
            default -> throw ApiException.badRequest("Atributo no admitido: "+key);
        }
    }
    private String storeImage(MultipartFile file) {
        String path=images.save(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status!=STATUS_COMMITTED) images.remove(path); }
        });
        return path;
    }

    private CategoriaProducto requireCategoriaActiva(Integer id) {
        CategoriaProducto categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada."));
        if (!Boolean.TRUE.equals(categoria.getActivo())) {
            throw ApiException.badRequest("La categoria esta inactiva.");
        }
        return categoria;
    }

    private SubcategoriaProducto requireSubcategoriaActiva(Integer id) {
        SubcategoriaProducto sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada."));
        if (!Boolean.TRUE.equals(sub.getActivo())) {
            throw ApiException.badRequest("La subcategoria esta inactiva.");
        }
        return sub;
    }

    private ProductoDtos.CategoriaResponse toCategoria(CategoriaProducto categoria) {
        ProductoDtos.CategoriaResponse dto = new ProductoDtos.CategoriaResponse();
        dto.setIdCategoriaProducto(categoria.getIdCategoriaProducto());
        dto.setIdEmpresa(categoria.getIdEmpresa());
        dto.setNombre(categoria.getNombre());
        dto.setDescripcion(categoria.getDescripcion());
        dto.setActivo(categoria.getActivo());
        return dto;
    }

    private ProductoDtos.SubcategoriaResponse toSubcategoria(SubcategoriaProducto sub) {
        ProductoDtos.SubcategoriaResponse dto = new ProductoDtos.SubcategoriaResponse();
        dto.setIdSubcategoria(sub.getIdSubcategoria());
        dto.setIdCategoriaProducto(sub.getCategoriaProducto().getIdCategoriaProducto());
        dto.setNombre(sub.getNombre());
        dto.setDescripcion(sub.getDescripcion());
        dto.setActivo(sub.getActivo());
        return dto;
    }

    private Producto requireProductoDeUsuario(Usuario usuario, Integer idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        if (producto.getIdEmpresa() == null) {
            throw ApiException.notFound("Producto no encontrado.");
        }
        empresaAccess.requireEmpresa(usuario, producto.getIdEmpresa());
        return producto;
    }

    private Set<Integer> empresasDelUsuario(Usuario usuario) {
        Set<Integer> empresas = new HashSet<>();
        for (UsuarioRol relacion : usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(usuario.getIdUsuario())) {
            if (relacion.getIdEmpresa() != null) {
                empresas.add(relacion.getIdEmpresa());
            }
        }
        return empresas;
    }

    private Map<String,Object> toProducto(Producto p,RbacResolver.Grants grants) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id_producto",p.getIdProducto()); result.put("id_empresa",p.getIdEmpresa());
        fields.project(result,grants,"product","nombre",p.getNombre());
        fields.project(result,grants,"product","descripcion",p.getDescripcion());
        fields.project(result,grants,"product","codigo_barra",p.getCodigoBarra());
        fields.project(result,grants,"product","unidad_medida",p.getUnidadMedida());
        fields.project(result,grants,"product","precio",p.getPrecio());
        fields.project(result,grants,"product","costo",p.getCosto());
        fields.project(result,grants,"product","imagen",p.getImagen());
        fields.project(result,grants,"product","activo",p.getActivo());
        if (fields.allows(grants,"product","read","id_subcategoria")) {
            result.put("id_subcategoria",p.getSubcategoria()==null?null:p.getSubcategoria().getIdSubcategoria());
            result.put("subcategoria",p.getSubcategoria()==null?null:toSubcategoria(p.getSubcategoria()));
        }
        return result;
    }
}
