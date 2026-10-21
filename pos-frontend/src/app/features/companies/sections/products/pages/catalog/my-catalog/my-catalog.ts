import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../../../shared/pipes/money.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../../../../../core/services/api.service';
import { CompanyService } from '../../../../../../../core/services/company.service';
import {
  CampoProducto,
  CamposProducto,
  CompanyPermissionCode,
  CompanyPermissionsService,
  EstadoCampoProducto,
  camposProductoPorDefecto,
} from '../../../../../../../core/services/company-permissions.service';
import {
  CrearProductoRequest,
  ProductService,
  Producto,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';
import { environments } from '../../../../../../../environments/environments';

type ProductoConCosto = Producto & { costo?: number | null };
type ProductoAlta = CrearProductoRequest & { costo: number | null };

@Component({
  selector: 'app-productos-catalog',
  standalone: true,
  imports: [MoneyPipe, FormsModule, Navbar, RouterLink, Sidebar],
  templateUrl: './my-catalog.html',
  styleUrl: './my-catalog.css',
})
export class ProductosPanel implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly productService = inject(ProductService);
  private readonly apiService = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = 'Empresa';
  protected activeTab: 'register' | 'list' = this.hasPermission('PRODUCTO_CREAR') ? 'register' : 'list';
  protected readonly apiBaseUrl = environments.apiBaseUrl;

  protected readonly productoForm: ProductoAlta = {
    id_subcategoria: 0,
    codigo_barra: '',
    nombre: '',
    descripcion: '',
    unidad_medida: '',
    precio: 0,
    costo: null,
    activo: true,
  };

  protected camposProducto: CamposProducto = camposProductoPorDefecto();
  protected camposCargados = false;
  protected productos: ProductoConCosto[] = [];
  protected subcategorias: SubcategoriaProducto[] = [];

  protected cargandoDatos = false;
  protected guardandoProducto = false;

  protected errorGeneral = '';
  protected mensajeGeneral = '';
  protected imagenProductoSeleccionada: File | null = null;

  ngOnInit(): void {
    this.cargarNombreEmpresa();
    this.cargarCamposProducto();
    this.cargarDatos();
  }

  protected estadoCampo(campo: CampoProducto): EstadoCampoProducto {
    return this.camposProducto[campo];
  }

  protected mostrarCampo(campo: CampoProducto): boolean {
    return this.camposCargados && this.estadoCampo(campo) !== 'OCULTO';
  }

  protected soloLectura(campo: CampoProducto): boolean {
    return this.estadoCampo(campo) === 'SOLO_LECTURA';
  }

  protected setActiveTab(tab: 'register' | 'list'): void {
    if (tab === 'register' && !this.hasPermission('PRODUCTO_CREAR')) {
      return;
    }

    this.activeTab = tab;
    this.limpiarMensajes();

    if (tab === 'list') {
      this.cargarProductos();
    }
  }

  protected registrarProducto(event: SubmitEvent): void {
    event.preventDefault();
    this.limpiarMensajes();

    if (!this.hasPermission('PRODUCTO_CREAR')) {
      this.errorGeneral = 'No tienes permiso para registrar productos.';
      return;
    }

    if (!this.companyId) {
      this.errorGeneral = 'No se encontro la empresa para registrar el producto.';
      return;
    }

    const payload: ProductoAlta = {
      id_subcategoria: Number(this.productoForm.id_subcategoria),
      codigo_barra: this.productoForm.codigo_barra?.trim() || '',
      nombre: this.productoForm.nombre.trim(),
      descripcion: this.productoForm.descripcion?.trim() || '',
      unidad_medida: this.productoForm.unidad_medida.trim(),
      precio: this.estadoCampo('precio') === 'EDITABLE'
        ? Math.round(Number(this.productoForm.precio) * 100) / 100
        : 0,
      costo: this.estadoCampo('costo') === 'EDITABLE' ? this.normalizarCosto(this.productoForm.costo) : null,
      activo: true,
    };

    if (!payload.id_subcategoria || !payload.nombre || !payload.unidad_medida) {
      this.errorGeneral = 'Completa subcategoria, nombre y unidad de medida del producto.';
      return;
    }

    this.guardandoProducto = true;

    const request$ = this.imagenProductoSeleccionada
      ? this.apiService.post<ProductoConCosto, FormData>(
          `/api/empresas/${this.companyId}/productos/con-imagen`,
          this.construirFormDataProducto(payload, this.imagenProductoSeleccionada),
        )
      : this.productService.crearProducto(this.companyId, payload);

    request$.subscribe({
      next: () => {
        this.guardandoProducto = false;
        this.mensajeGeneral = 'Producto registrado correctamente.';
        this.limpiarFormularioProducto();
        this.activeTab = 'list';
        this.cargarProductos();
      },
      error: () => {
        this.guardandoProducto = false;
        this.errorGeneral = 'No se pudo registrar el producto.';
        this.cdr.detectChanges();
      },
    });
  }

  protected cancelarRegistroProducto(): void {
    this.activeTab = 'list';
    this.limpiarMensajes();
    this.limpiarFormularioProducto();
  }

  protected eliminarProducto(producto: ProductoConCosto): void {
    if (!this.camposCargados || this.estadoCampo('eliminar') !== 'EDITABLE' || this.guardandoProducto) {
      return;
    }

    if (!confirm(`¿Eliminar el producto ${producto.nombre}?`)) {
      return;
    }

    this.limpiarMensajes();
    this.guardandoProducto = true;
    this.productService.eliminarProducto(producto.id_producto).subscribe({
      next: () => {
        this.guardandoProducto = false;
        this.mensajeGeneral = 'Producto eliminado correctamente.';
        this.cargarProductos();
      },
      error: () => {
        this.guardandoProducto = false;
        this.errorGeneral = 'No se pudo eliminar el producto.';
        this.cdr.detectChanges();
      },
    });
  }

  protected onImagenProductoSeleccionada(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.imagenProductoSeleccionada = input.files?.[0] ?? null;
  }

  private cargarCamposProducto(): void {
    if (!this.companyId) {
      this.camposProducto = camposProductoPorDefecto();
      this.camposCargados = true;
      return;
    }

    this.companyPermissionsService.obtenerMisCamposProducto(this.companyId).subscribe({
      next: (campos) => {
        this.camposProducto = campos;
        this.camposCargados = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.camposProducto = camposProductoPorDefecto();
        this.camposCargados = true;
        this.cdr.detectChanges();
      },
    });
  }

  private cargarNombreEmpresa(): void {
    if (!this.companyId) {
      return;
    }

    this.companyService.obtenerEmpresa(this.companyId).subscribe({
      next: (empresa) => {
        this.companyName = empresa.nombre;
        this.cdr.detectChanges();
      },
      error: () => {
        this.companyName = 'Empresa';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarDatos(): void {
    this.cargandoDatos = true;
    this.limpiarMensajes();

    if (!this.companyId) {
      this.subcategorias = [];
      this.errorGeneral = 'No se encontro la empresa para cargar subcategorias.';
      this.cargarProductos(() => {
        this.cargandoDatos = false;
      });
      return;
    }

    this.productService.getSubcategorias(this.companyId).subscribe({
      next: (subcategorias) => {
        this.subcategorias = subcategorias;
        this.cargarProductos(() => {
          this.cargandoDatos = false;
        });
      },
      error: () => {
        this.subcategorias = [];
        this.errorGeneral = 'No se pudieron cargar las subcategorias.';
        this.cargarProductos(() => {
          this.cargandoDatos = false;
        });
      },
    });
  }

  private cargarProductos(onDone?: () => void): void {
    if (!this.companyId) {
      this.productos = [];
      this.errorGeneral = 'No se encontro la empresa para cargar productos.';
      onDone?.();
      this.cdr.detectChanges();
      return;
    }

    this.productService.getProductos(this.companyId).subscribe({
      next: (productos) => {
        this.productos = productos;
        this.errorGeneral = '';
        onDone?.();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.productos = [];
        this.errorGeneral = error?.error?.detail ?? 'No se pudieron cargar los productos.';
        onDone?.();
        this.cdr.detectChanges();
      },
    });
  }

  private limpiarFormularioProducto(): void {
    this.productoForm.id_subcategoria = 0;
    this.productoForm.nombre = '';
    this.productoForm.descripcion = '';
    this.productoForm.unidad_medida = '';
    this.productoForm.precio = 0;
    this.productoForm.costo = null;
    this.productoForm.activo = true;
    this.imagenProductoSeleccionada = null;
  }

  private limpiarMensajes(): void {
    this.errorGeneral = '';
    this.mensajeGeneral = '';
  }

  private construirFormDataProducto(payload: ProductoAlta, imagen: File): FormData {
    const formData = new FormData();
    formData.append('id_subcategoria', String(payload.id_subcategoria));
    formData.append('codigo_barra', payload.codigo_barra ?? '');
    formData.append('nombre', payload.nombre);
    formData.append('descripcion', payload.descripcion ?? '');
    formData.append('unidad_medida', payload.unidad_medida);
    formData.append('precio', String(this.estadoCampo('precio') === 'EDITABLE' ? payload.precio : 0));
    if (this.estadoCampo('costo') === 'EDITABLE' && payload.costo != null) {
      formData.append('costo', String(payload.costo));
    }
    formData.append('activo', 'true');
    formData.append('imagen', imagen);
    return formData;
  }

  private normalizarCosto(valor: number | null | undefined): number | null {
    if (valor == null || Number.isNaN(Number(valor))) {
      return null;
    }

    return Math.round(Number(valor) * 100) / 100;
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    const permissions = this.companyPermissionsService.permissions();
    const codes = this.companyPermissionsService.getPermissionCodes();
    if (codes.length === 0) {
      return true;
    }
    return permissions[permission] === true;
  }
}

