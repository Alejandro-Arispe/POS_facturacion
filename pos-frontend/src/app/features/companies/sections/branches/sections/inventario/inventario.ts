import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  MovimientoInventario,
  Producto,
  ProductService,
  StockSucursalProducto,
  TipoMovimiento,
} from '../../../../../../core/services/product.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

type InventoryTab = 'stock' | 'movements';

interface StockItemRow {
  idProducto: number;
  nombre: string;
  cantidad: number;
  stockMin: number | null;
  stockMax: number | null;
}

interface StockEditForm {
  stockMin: number | null;
  stockMax: number | null;
}

interface MovementRow {
  id: number | string;
  idProducto: number;
  producto: string;
  cantidad: number;
  tipoMovimiento: string;
  observacion?: string;
}

@Component({
  selector: 'app-inventario',
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './inventario.html',
  styleUrl: './inventario.css',
})
export class Inventario implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly productService = inject(ProductService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';

  protected activeTab: InventoryTab = this.hasPermission('STOCK_VER') ? 'stock' : 'movements';
  protected showRegisterMovementTab = false;

  protected cargandoStock = false;
  protected cargandoMovimientos = false;
  protected guardandoMovimiento = false;
  protected guardandoStock = false;

  protected errorStock = '';
  protected mensajeStock = '';
  protected errorMovimientos = '';
  protected errorMovimiento = '';
  protected mensajeMovimiento = '';
  protected errorTiposMovimiento = '';

  protected productos: Producto[] = [];
  protected stockItems: StockItemRow[] = [];
  protected movements: MovementRow[] = [];
  protected movementTypes: TipoMovimiento[] = [];

  protected movimientoProductoId: number | null = null;
  protected movimientoCantidad = 1;
  protected movimientoTipoId: number | null = null;
  protected movimientoObservacion = '';
  protected editandoStockProductoId: number | null = null;
  protected stockEditForm: StockEditForm = {
    stockMin: null,
    stockMax: null,
  };

  ngOnInit(): void {
    this.cargarTiposMovimiento();
    this.cargarInventario();
  }

  protected setActiveTab(tab: InventoryTab): void {
    if (tab === 'stock' && !this.hasPermission('STOCK_VER')) {
      return;
    }

    if (tab === 'movements' && !this.hasPermission('MOVIMIENTO_VER')) {
      return;
    }

    this.activeTab = tab;
    this.limpiarMensajesMovimiento();

    if (tab === 'stock') {
      this.showRegisterMovementTab = false;
      this.cargarStock();
      return;
    }

    this.cargarMovimientos();
  }

  protected openRegisterMovementTab(): void {
    if (!this.hasPermission('MOVIMIENTO_REGISTRAR')) {
      this.errorMovimiento = 'No tienes permiso para registrar movimientos.';
      return;
    }

    this.activeTab = 'movements';
    this.showRegisterMovementTab = true;
    this.limpiarMensajesMovimiento();

    if (!this.movimientoProductoId && this.productos.length > 0) {
      this.movimientoProductoId = this.productos[0].id_producto;
    }
  }

  protected closeRegisterMovementTab(): void {
    this.showRegisterMovementTab = false;
    this.limpiarMensajesMovimiento();
  }

  protected registrarMovimiento(event: SubmitEvent): void {
    event.preventDefault();
    this.limpiarMensajesMovimiento();

    if (!this.hasPermission('MOVIMIENTO_REGISTRAR')) {
      this.errorMovimiento = 'No tienes permiso para registrar movimientos.';
      return;
    }

    if (!this.companyId || !this.branchId) {
      this.errorMovimiento = 'No se encontro la empresa o sucursal para registrar el movimiento.';
      return;
    }

    const idProducto = Number(this.movimientoProductoId);
    const cantidad = Number(this.movimientoCantidad);
    const idTipoMovimiento = Number(this.movimientoTipoId);

    if (!idProducto || !cantidad || cantidad <= 0 || !idTipoMovimiento) {
      this.errorMovimiento = 'Completa producto, cantidad y tipo de movimiento.';
      return;
    }

    this.guardandoMovimiento = true;

    this.productService
      .crearMovimientoProducto(this.companyId, this.branchId, {
        id_producto: idProducto,
        cantidad,
        id_tipo_movimiento: idTipoMovimiento,
        observacion: this.movimientoObservacion.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardandoMovimiento = false;
          this.mensajeMovimiento = 'Movimiento registrado correctamente.';
          this.resetMovimientoForm();
          this.showRegisterMovementTab = false;
          this.cargarInventario();
        },
        error: () => {
          this.guardandoMovimiento = false;
          this.errorMovimiento = 'No se pudo registrar el movimiento. Intenta nuevamente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected iniciarEdicionStock(item: StockItemRow): void {
    if (!this.hasPermission('STOCK_CONFIGURAR')) {
      this.errorStock = 'No tienes permiso para configurar stock.';
      return;
    }

    this.limpiarMensajesStock();
    this.editandoStockProductoId = item.idProducto;
    this.stockEditForm = {
      stockMin: item.stockMin,
      stockMax: item.stockMax,
    };
  }

  protected cancelarEdicionStock(): void {
    this.editandoStockProductoId = null;
    this.stockEditForm = {
      stockMin: null,
      stockMax: null,
    };
    this.limpiarMensajesStock();
  }

  protected guardarStock(item: StockItemRow): void {
    this.limpiarMensajesStock();

    if (!this.hasPermission('STOCK_CONFIGURAR')) {
      this.errorStock = 'No tienes permiso para configurar stock.';
      return;
    }

    if (!this.companyId || !this.branchId) {
      this.errorStock = 'No se encontro la empresa o sucursal para actualizar el stock.';
      return;
    }

    const stockMin = this.parseStockValue(this.stockEditForm.stockMin);
    const stockMax = this.parseStockValue(this.stockEditForm.stockMax);

    if (stockMin !== null && stockMin < 0) {
      this.errorStock = 'El stock minimo no puede ser negativo.';
      return;
    }

    if (stockMax !== null && stockMax < 0) {
      this.errorStock = 'El stock maximo no puede ser negativo.';
      return;
    }

    if (stockMin !== null && stockMax !== null && stockMin > stockMax) {
      this.errorStock = 'El stock minimo no puede ser mayor al stock maximo.';
      return;
    }

    this.guardandoStock = true;

    this.productService
      .actualizarStockSucursal(this.companyId, this.branchId, item.idProducto, {
        stock_minimo: stockMin ?? undefined,
        stock_maximo: stockMax ?? undefined,
      })
      .subscribe({
        next: () => {
          this.guardandoStock = false;
          this.mensajeStock = 'Stock minimo y maximo actualizados correctamente.';
          this.editandoStockProductoId = null;
          this.cargarStock();
        },
        error: () => {
          this.guardandoStock = false;
          this.errorStock = 'No se pudo actualizar el stock minimo y maximo.';
          this.cdr.detectChanges();
        },
      });
  }

  protected isStockNearMin(item: StockItemRow): boolean {
    return item.stockMin !== null && item.cantidad <= item.stockMin + 3;
  }

  protected isStockNearMax(item: StockItemRow): boolean {
    return item.stockMax !== null && item.cantidad >= item.stockMax - 3;
  }

  protected isStockWarning(item: StockItemRow): boolean {
    return this.isStockNearMin(item) || this.isStockNearMax(item);
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    const permissions = this.companyPermissionsService.permissions();
    const codes = this.companyPermissionsService.getPermissionCodes();
    if (codes.length === 0) {
      return true;
    }
    return permissions[permission] === true;
  }

  private cargarInventario(): void {
    this.cargarStock();
    this.cargarMovimientos();
  }

  private cargarTiposMovimiento(): void {
    this.errorTiposMovimiento = '';

    this.productService.getTiposMovimiento().subscribe({
      next: (tiposMovimiento) => {
        this.movementTypes = tiposMovimiento;

        if (tiposMovimiento.length > 0) {
          const tipoSeleccionadoExiste = tiposMovimiento.some(
            (tipo) => tipo.id_tipo_movimiento === this.movimientoTipoId,
          );

          if (!tipoSeleccionadoExiste) {
            this.movimientoTipoId = tiposMovimiento[0].id_tipo_movimiento;
          }
        } else {
          this.movimientoTipoId = null;
        }

        this.cdr.detectChanges();
      },
      error: () => {
        this.movementTypes = [];
        this.movimientoTipoId = null;
        this.errorTiposMovimiento = 'No se pudieron cargar los tipos de movimiento.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarStock(): void {
    this.errorStock = '';
    this.mensajeStock = '';
    this.cargandoStock = true;

    if (!this.companyId || !this.branchId) {
      this.productos = [];
      this.stockItems = [];
      this.movimientoProductoId = null;
      this.cargandoStock = false;
      this.errorStock = 'No se encontro la empresa o sucursal para cargar el stock.';
      this.cdr.detectChanges();
      return;
    }

    this.productService.getStockSucursal(this.companyId, this.branchId).subscribe({
      next: (stocks: StockSucursalProducto[]) => {
        this.stockItems = stocks.map((stock) => ({
          idProducto: stock.id_producto,
          nombre: stock.nombre_producto,
          cantidad: Number(stock.cantidad ?? 0),
          stockMin: stock.stock_minimo ?? null,
          stockMax: stock.stock_maximo ?? null,
        }));

        this.productos = stocks.map((stock) => ({
          id_producto: stock.id_producto,
          id_subcategoria: null,
          nombre: stock.nombre_producto,
          descripcion: null,
          unidad_medida: stock.unidad_medida,
          precio: stock.precio,
          imagen: stock.imagen ?? null,
          activo: stock.activo,
        }));

        this.movimientoProductoId = this.movimientoProductoId ?? this.productos[0]?.id_producto ?? null;
        this.cargandoStock = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.productos = [];
        this.stockItems = [];
        this.movimientoProductoId = null;
        this.cargandoStock = false;
        this.errorStock = 'No se pudo cargar el stock de la sucursal.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarMovimientos(): void {
    this.errorMovimientos = '';
    this.cargandoMovimientos = true;

    if (!this.companyId || !this.branchId) {
      this.movements = [];
      this.cargandoMovimientos = false;
      this.errorMovimientos = 'No se encontro la empresa o sucursal para cargar los movimientos.';
      this.cdr.detectChanges();
      return;
    }

    this.productService.getMovimientosSucursal(this.companyId, this.branchId).subscribe({
      next: (movimientos) => {
        this.movements = movimientos.map((movimiento, index) => this.mapMovimiento(movimiento, index)).reverse();
        this.cargandoMovimientos = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.movements = [];
        this.cargandoMovimientos = false;
        this.errorMovimientos = 'No se pudieron cargar los movimientos del inventario.';
        this.cdr.detectChanges();
      },
    });
  }

  private mapMovimiento(movimiento: MovimientoInventario, fallbackIndex: number): MovementRow {
    const tipoMovimientoDesdeRelacion =
      typeof movimiento?.tipo_movimiento === 'string'
        ? movimiento.tipo_movimiento
        : movimiento?.tipo_movimiento?.nombre;

    const tipoMovimientoDesdeAlias =
      typeof movimiento?.tipoMovimiento === 'string'
        ? movimiento.tipoMovimiento
        : movimiento?.tipoMovimiento?.nombre;

    const tipoMovimiento =
      tipoMovimientoDesdeRelacion ??
      tipoMovimientoDesdeAlias ??
      movimiento?.tipo ??
      'Sin tipo';

    return {
      id: movimiento?.id_movimiento ?? movimiento?.id ?? `${movimiento.id_producto}-${fallbackIndex}`,
      idProducto: movimiento.id_producto,
      producto: movimiento?.producto?.nombre ?? `Producto ${movimiento.id_producto}`,
      cantidad: Number(movimiento?.cantidad ?? 0),
      tipoMovimiento: String(tipoMovimiento),
      observacion: movimiento?.observacion ?? '',
    };
  }

  private resetMovimientoForm(): void {
    this.movimientoCantidad = 1;
    this.movimientoTipoId = this.movementTypes[0]?.id_tipo_movimiento ?? null;
    this.movimientoObservacion = '';
    this.movimientoProductoId = this.productos[0]?.id_producto ?? null;
  }

  private limpiarMensajesMovimiento(): void {
    this.errorMovimiento = '';
    this.mensajeMovimiento = '';
  }

  private limpiarMensajesStock(): void {
    this.errorStock = '';
    this.mensajeStock = '';
  }

  private parseStockValue(value: number | null): number | null {
    if (value === null || value === undefined || value === ('' as never)) {
      return null;
    }

    const parsedValue = Number(value);
    return Number.isNaN(parsedValue) ? null : parsedValue;
  }
}

