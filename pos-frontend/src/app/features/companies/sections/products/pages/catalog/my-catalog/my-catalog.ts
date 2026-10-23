import { ChangeDetectorRef, Component, HostListener, OnInit, inject } from '@angular/core';
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
import { Icon } from '../../../../../../../shared/components/icon/icon';
import {
  CatalogColumn,
  CatalogQuery,
  ColumnKey,
  catalogCsv,
  pageNumbers,
  queryProducts,
} from './catalog-table';

type ProductoConCosto = Producto & { costo?: number | null };
type ProductoAlta = CrearProductoRequest & { costo: number | null };

@Component({
  selector: 'app-productos-catalog',
  standalone: true,
  imports: [MoneyPipe, FormsModule, Navbar, RouterLink, Sidebar, Icon],
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
  protected activeTab: 'register' | 'list' = 'list';
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

  protected cargandoProductos = false;
  protected errorCargaProductos = '';
  protected search = '';
  protected status: CatalogQuery['status'] = 'all';
  protected subcategory = '';
  protected sortKey: ColumnKey = 'nombre';
  protected sortDirection: 'asc' | 'desc' = 'asc';
  protected filtersOpen = false;
  protected columnsOpen = false;
  protected page = 1;
  protected pageSize = 8;
  protected readonly pageSizes = [8, 16, 24, 50];
  protected readonly selectedIds = new Set<number>();
  protected readonly failedImages = new Set<number>();
  protected activeProduct: ProductoConCosto | null = null;
  protected actionPosition = { top: 0, left: 0 };
  protected readonly columnVisibility: Record<ColumnKey, boolean> = {
    id_producto: true,
    nombre: true,
    codigo_barra: false,
    subcategoria: true,
    unidad_medida: true,
    precio: true,
    costo: true,
    activo: true,
  };

  private readonly columns: CatalogColumn[] = [
    { key: 'id_producto', label: 'ID' },
    { key: 'nombre', label: 'Producto' },
    { key: 'codigo_barra', label: 'Código de barras' },
    { key: 'subcategoria', label: 'Subcategoría' },
    { key: 'unidad_medida', label: 'Unidad' },
    { key: 'precio', label: 'Precio' },
    { key: 'costo', label: 'Costo' },
    { key: 'activo', label: 'Estado' },
  ];

  protected get availableColumns(): CatalogColumn[] {
    return this.columns.filter(
      (column) =>
        (column.key !== 'precio' && column.key !== 'costo') || this.mostrarCampo(column.key),
    );
  }

  protected get visibleColumns(): CatalogColumn[] {
    return this.availableColumns.filter((column) => this.columnVisibility[column.key]);
  }

  protected get filteredProducts(): ProductoConCosto[] {
    return queryProducts(this.productos, {
      search: this.search,
      status: this.status,
      subcategory: this.subcategory,
      sort: this.sortKey,
      direction: this.sortDirection,
    });
  }

  protected get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredProducts.length / this.pageSize));
  }
  protected get currentPage(): number {
    return Math.min(this.page, this.totalPages);
  }
  protected get pageProducts(): ProductoConCosto[] {
    return this.filteredProducts.slice(
      (this.currentPage - 1) * this.pageSize,
      this.currentPage * this.pageSize,
    );
  }
  protected get pagination(): Array<number | '…'> {
    return pageNumbers(this.currentPage, this.totalPages);
  }
  protected get firstRecord(): number {
    return this.filteredProducts.length ? (this.currentPage - 1) * this.pageSize + 1 : 0;
  }
  protected get lastRecord(): number {
    return Math.min(this.currentPage * this.pageSize, this.filteredProducts.length);
  }
  protected get activeFilterCount(): number {
    return Number(this.status !== 'all') + Number(Boolean(this.subcategory));
  }
  protected get hasFilters(): boolean {
    return Boolean(this.search.trim() || this.activeFilterCount);
  }
  protected get allPageSelected(): boolean {
    return (
      this.pageProducts.length > 0 &&
      this.pageProducts.every((product) => this.selectedIds.has(product.id_producto))
    );
  }
  protected get somePageSelected(): boolean {
    return (
      !this.allPageSelected &&
      this.pageProducts.some((product) => this.selectedIds.has(product.id_producto))
    );
  }

  protected updateQuery(): void {
    this.page = 1;
    this.selectedIds.clear();
    this.activeProduct = null;
  }

  protected clearFilters(): void {
    this.search = '';
    this.status = 'all';
    this.subcategory = '';
    this.updateQuery();
  }

  protected sortBy(key: ColumnKey): void {
    if (this.sortKey === key) this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    else {
      this.sortKey = key;
      this.sortDirection = 'asc';
    }
    this.page = 1;
  }

  protected changePage(value: number | '…'): void {
    if (typeof value === 'number' && value >= 1 && value <= this.totalPages) {
      this.page = value;
      this.activeProduct = null;
    }
  }

  protected changePageSize(value: number): void {
    if (this.pageSizes.includes(Number(value))) {
      this.pageSize = Number(value);
      this.page = 1;
    }
  }

  protected toggleColumn(key: ColumnKey): void {
    if (key !== 'nombre') this.columnVisibility[key] = !this.columnVisibility[key];
  }

  protected toggleProduct(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  protected togglePageSelection(): void {
    const remove = this.allPageSelected;
    for (const product of this.pageProducts) {
      if (remove) this.selectedIds.delete(product.id_producto);
      else this.selectedIds.add(product.id_producto);
    }
  }

  protected toggleActions(product: ProductoConCosto, event: MouseEvent): void {
    if (this.activeProduct?.id_producto === product.id_producto) {
      this.activeProduct = null;
      return;
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.actionPosition = {
      left: Math.max(8, Math.min(rect.right - 184, window.innerWidth - 192)),
      top: window.innerHeight - rect.bottom < 150 ? Math.max(8, rect.top - 144) : rect.bottom + 6,
    };
    this.activeProduct = product;
    this.columnsOpen = false;
    requestAnimationFrame(() =>
      document
        .querySelector<HTMLElement>('#product-actions a, #product-actions button:not(:disabled)')
        ?.focus(),
    );
  }

  @HostListener('document:click', ['$event'])
  protected closeOutside(event: MouseEvent): void {
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.closest('[data-product-actions]')) this.activeProduct = null;
    if (!target?.closest('[data-column-picker]')) this.columnsOpen = false;
  }

  @HostListener('document:keydown.escape')
  protected closeMenus(): void {
    if (this.activeProduct)
      document.getElementById('product-action-' + this.activeProduct.id_producto)?.focus();
    else if (this.columnsOpen) document.getElementById('column-picker-trigger')?.focus();
    this.activeProduct = null;
    this.columnsOpen = false;
  }

  @HostListener('window:resize')
  @HostListener('window:scroll')
  protected closeActions(): void {
    this.activeProduct = null;
  }

  protected imageUrl(product: ProductoConCosto): string {
    const path = product.imagen ?? '';
    return /^https?:\/\//i.test(path)
      ? path
      : this.apiBaseUrl.replace(/\/$/, '') + '/' + path.replace(/^\//, '');
  }

  protected exportProducts(): void {
    if (!this.camposCargados || this.cargandoProductos || this.cargandoDatos) return;
    const rows = this.filteredProducts.filter(
      (product) => !this.selectedIds.size || this.selectedIds.has(product.id_producto),
    );
    if (!rows.length) return;
    const blob = new Blob([catalogCsv(rows, this.visibleColumns)], {
      type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'productos-empresa-' + this.companyId + '.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  protected reloadProducts(): void {
    this.cargarProductos();
  }

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
    this.activeProduct = null;
    this.columnsOpen = false;
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
      precio:
        this.estadoCampo('precio') === 'EDITABLE'
          ? Math.round(Number(this.productoForm.precio) * 100) / 100
          : 0,
      costo:
        this.estadoCampo('costo') === 'EDITABLE'
          ? this.normalizarCosto(this.productoForm.costo)
          : null,
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
        this.clearFilters();
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
    if (
      !this.camposCargados ||
      this.estadoCampo('eliminar') !== 'EDITABLE' ||
      this.guardandoProducto
    ) {
      return;
    }

    if (!confirm(`¿Eliminar el producto ${producto.nombre}?`)) {
      return;
    }

    this.activeProduct = null;

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
    this.cargandoProductos = true;
    this.errorCargaProductos = '';
    if (!this.companyId) {
      this.productos = [];
      this.errorCargaProductos = 'No se encontró la empresa para cargar productos.';
      this.cargandoProductos = false;
      onDone?.();
      this.cdr.detectChanges();
      return;
    }

    this.productService.getProductos(this.companyId).subscribe({
      next: (productos) => {
        this.productos = productos;
        this.cargandoProductos = false;
        this.failedImages.clear();
        const ids = new Set(productos.map((product) => product.id_producto));
        for (const id of this.selectedIds) if (!ids.has(id)) this.selectedIds.delete(id);
        this.page = this.currentPage;
        this.errorGeneral = '';
        onDone?.();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.productos = [];
        this.cargandoProductos = false;
        this.selectedIds.clear();
        this.errorCargaProductos = error?.error?.detail ?? 'No se pudieron cargar los productos.';
        onDone?.();
        this.cdr.detectChanges();
      },
    });
  }

  private limpiarFormularioProducto(): void {
    this.productoForm.id_subcategoria = 0;
    this.productoForm.codigo_barra = '';
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
    formData.append(
      'precio',
      String(this.estadoCampo('precio') === 'EDITABLE' ? payload.precio : 0),
    );
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
