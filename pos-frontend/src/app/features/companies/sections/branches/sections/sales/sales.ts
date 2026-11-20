import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CurrencySymbolPipe, MoneyPipe } from '../../../../../../shared/pipes/money.pipe';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  CashRegisterMovementType,
  ClientRole,
  CompanyService,
  ClientCategoryResponse,
} from '../../../../../../core/services/company.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../core/services/company-permissions.service';
import {
  CreateSaleRequest,
  CashRegisterMovementResponse,
  CashRegisterMovementListItem,
  CashRegisterClosingSummaryResponse,
  SaleHistoryResponse,
  SaleHistoryDetailItem,
  CashRegisterService,
} from '../../../../../../core/services/cash-register.service';
import {
  MetodoPago,
  ProductService,
  StockSucursalProducto,
  TipoVenta,
} from '../../../../../../core/services/product.service';
import { ApiService } from '../../../../../../core/services/api.service';
import { OrderService, Order } from '../../../../../../core/services/order.service';
import { apiErrorMessage } from '../../../../../../core/utils/api-error';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { BilleteraConfig, BilleteraService } from '../../../../../../core/services/billetera.service';
import { SecureSrc } from '../../../../../../shared/directives/secure-src';
import { InvoicePrintService } from '../../../../../../core/services/invoice-print.service';
import { TIPOS_DOCUMENTO_SIN } from '../../../../../../core/services/siat.service';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

type SessionViewMode = 'sales' | 'movements';
type SalesTab = 'register' | 'payment' | 'history';
type MovementTab = 'register' | 'list';
type DiscountType = 'percentage' | 'fixed';

interface ProductSearchItem {
  idProducto: number;
  nombre: string;
  unidadMedida: string;
  precio: number;
  stock: number;
  codigo: string;
  codigoBarras: string;
  /** Ruta protegida de la imagen del producto (/media/products/...), si el usuario puede verla. */
  imagen?: string | null;
  /** Confianza de la regla de asociacion (0 a 1) cuando es una sugerencia. */
  confianza?: number | null;
}

/** Sugerencia del backend (JSON en snake_case) basada en reglas de asociacion (HU-26). */
interface RecommendedProductRecord {
  id_producto: number;
  nombre: string;
  unidad_medida?: string | null;
  precio?: number | null;
  stock?: number | null;
  codigo?: string | null;
  codigo_barras?: string | null;
  imagen?: string | null;
  confianza?: number | null;
}

interface RecommendedProductsResponse {
  productos_analizados: number[];
  recomendaciones: RecommendedProductRecord[];
}

interface SaleDetailItem extends ProductSearchItem {
  precioCatalogo?: number;
  cantidad: number;
  descuento: number;
  descuentoTipo: DiscountType;
}

interface ClientOption {
  id: number;
  nombre: string;
  correo: string;
  categoriaId?: number | null;
  /** Saldo a favor por devoluciones (HU-15), usable como medio de pago. */
  saldoAFavor?: number;
}

interface SaleTypeOption {
  id: number;
  nombre: string;
  descripcion: string;
}

interface CashMovementTypeOption {
  id: number;
  nombre: string;
  descripcion: string;
}

interface PaymentMethodOption {
  id: number;
  nombre: string;
  descripcion: string;
}

interface SalePaymentRow {
  id: number;
  metodoPagoId: number | null;
  monto: number | null;
  /** Pago por QR simulado: el cajero confirma que el cliente ya pago. */
  qrConfirmado?: boolean;
  /** Id del cobro QR en la Billetera; se envia al registrar la venta. */
  cobroId?: string;
  /** Pago con tarjeta simulado (HU-13): los datos nunca salen del navegador ni se guardan. */
  tarjeta?: CardForm;
}

interface CardForm {
  numero: string;
  titular: string;
  vencimiento: string;
  cvv: string;
  procesando: boolean;
  error: string;
  /** Codigo de autorizacion devuelto por el adquirente simulado. */
  autorizacion: string | null;
  marca: string;
  ultimos4: string;
}

/** Bancos que operan QR Simple interoperable en Bolivia (simulado). */
const QR_BANCOS = ['Banco Unión', 'Banco Nacional de Bolivia (BNB)', 'Banco Mercantil Santa Cruz', 'Banco de Crédito (BCP)'];

interface CashMovementItem {
  id: number;
  concepto: string;
  monto: number;
  tipoMovimientoId: number;
  tipoMovimiento: string;
  metodoPagoId: number | null;
  metodoPago: string;
  fecha: string;
}

/** Respuesta del reconocimiento por camara (HU-27). */
interface VisionResult {
  estado: 'IDENTIFICADO' | 'COINCIDENCIAS' | 'NO_IDENTIFICADO';
  mensaje: string;
  tiempo_ms?: number;
  coincidencias: Array<{ id_producto: number; nombre: string; confianza: number; motivo?: string }>;
}

interface PostSaleItem {
  idProducto: number;
  descripcion: string;
  disponible: number;
  precio: number;
  cantidad: number;
}

/** Dialogo de postventa: anular la venta completa (HU-14) o devolver productos (HU-15). */
interface PostSaleDialog {
  mode: 'anular' | 'devolver';
  sale: SaleHistoryRow;
  motivo: string;
  reembolso: 'EFECTIVO' | 'SALDO_A_FAVOR';
  items: PostSaleItem[];
  vence: string | null;
  loading: boolean;
  saving: boolean;
  error: string;
}

interface SaleHistoryRow {
  id: number;
  tipoVentaId: number | string;
  fecha: string;
  tipoVenta: string;
  cliente: string;
  clienteId: number | string | null;
  usuarioId: number | string;
  cajaSesionId: number | string;
  total: number;
  estado: string;
  /** Estado SIAT de la factura (PENDIENTE, VALIDADA, OBSERVADA, RECHAZADA, ANULADA, CONTINGENCIA). */
  estadoFactura: string | null;
  metodosPago: string;
  metodosPagoIds: string;
  articulos: number;
  subtotal: number;
  descuentoTotal: number;
  detalles: SaleHistoryDetailItem[];
}

type ProductStockRecord = StockSucursalProducto & {
  codigo?: string | number | null;
  codigo_producto?: string | number | null;
  codigo_barra?: string | number | null;
};

@Component({
  selector: 'app-sales',
  imports: [MoneyPipe, CurrencySymbolPipe, CommonModule, FormsModule, Navbar, Sidebar, SecureSrc],
  templateUrl: './sales.html',
  styleUrl: './sales.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})

export class Sales implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly apiService = inject(ApiService);
  private readonly companyService = inject(CompanyService);
  private readonly cashRegisterService = inject(CashRegisterService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly orderService = inject(OrderService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected readonly cashRegisterId = this.route.snapshot.paramMap.get('cashRegisterId') ?? '';
  protected readonly maxPaymentRows = 3;
  protected cashRegisterSessionId = this.route.snapshot.queryParamMap.get('sessionId') ?? '';

  protected viewMode: SessionViewMode = this.resolveViewMode(this.route.snapshot.queryParamMap.get('section'));
  protected sidebarActiveItemLabel = this.viewMode === 'movements' ? 'Movimientos' : 'Ventas';

  protected activeTab: SalesTab = this.hasPermission('sale:create') ? 'register' : 'history';
  protected movementActiveTab: MovementTab = this.hasPermission('cash_movement:create') ? 'register' : 'list';
  protected searchTerm = '';
  protected selectedClientId: number | null = null;
  protected selectedSaleTypeId: number | null = null;
  protected onlineInvoice = false;
  // Datos del comprador para la factura SIAT; si se dejan vacíos se usan los del cliente.
  protected readonly tiposDocumento = TIPOS_DOCUMENTO_SIN;
  protected invoiceDocType = 1;
  protected invoiceDocNumber = '';
  protected invoiceBusinessName = '';
  protected lastInvoiceId: number | null = null;
  private readonly invoicePrint = inject(InvoicePrintService);
  protected saleDiscount = 0;
  protected movementConcept = '';
  protected movementAmount: number | null = null;
  protected selectedMovementTypeId: number | null = null;
  protected selectedPaymentMethodId: number | null = null;
  protected showPaymentTab = false;
  protected nextPaymentRowId = 1;

  protected loadingRegisterData = false;
  protected loadingSaleTypes = false;
  protected loadingMovementTypes = false;
  protected loadingPaymentMethods = false;
  protected loadingMovementItems = false;
  protected loadingSalesHistory = false;
  protected loadingRecommendedProducts = false;
  protected chargingSale = false;
  protected savingMovement = false;
  protected registerError = '';
  protected registerMessage = '';
  protected recommendedProductsError = '';
  protected movementError = '';
  protected movementMessage = '';
  protected salesHistoryError = '';

  protected isLoadOrderModalOpen = false;
  protected loadingOrders = false;
  protected availableOrders: Order[] = [];
  protected loadedOrderId: number | null = null;
  /** Pedido pendiente de cargar cuando termine el stock de sucursal. */
  private pendingPedidoId: number | null = null;
  private registerDataReady = false;
  /** Catálogo completo (incluye stock 0) para cargar pedidos sin perder líneas. */
  private catalogProducts: ProductSearchItem[] = [];
  /** Cliente del pedido; se reaplica cuando llega la lista de clientes (el select no lo borre). */
  private pendingOrderClientId: number | null = null;

  protected products: ProductSearchItem[] = [];
  protected recommendedProducts: ProductSearchItem[] = [];
  protected clients: ClientOption[] = [];
  protected saleTypes: SaleTypeOption[] = [];
  protected saleDetails: SaleDetailItem[] = [];
  protected salePaymentRows: SalePaymentRow[] = [];
  protected salesHistory: SaleHistoryRow[] = [];
  protected movementTypes: CashMovementTypeOption[] = [];
  protected paymentMethods: PaymentMethodOption[] = [];
  protected movementItems: CashMovementItem[] = [];

  // HU-11: Historial de compras de clientes y detalle de venta
  protected historySelectedClientId: number | string = 'all';
  protected selectedSaleDetail: SaleHistoryRow | null = null;
  protected postSale: PostSaleDialog | null = null;
  protected postSaleMessage = '';
  protected isSaleDetailModalOpen = false;

  // HU: Movimiento de caja en PDV (Botones Ingreso y Egreso con caja abierta)
  protected isQuickMovementModalOpen = false;
  protected quickMovementTipo: 'INGRESO' | 'EGRESO' = 'INGRESO';
  protected quickMovementMonto: number | null = null;
  protected quickMovementConcepto = '';
  protected quickMovementMetodoPagoId: number | null = null;
  protected savingQuickMovement = false;
  protected quickMovementError = '';
  protected efectivoEsperadoCaja = 0;

  protected get efectivoEsperadoProyectado(): number {
    const monto = Number(this.quickMovementMonto) || 0;
    if (this.quickMovementTipo === 'INGRESO') {
      return this.roundCurrency(this.efectivoEsperadoCaja + monto);
    } else {
      return this.roundCurrency(this.efectivoEsperadoCaja - monto);
    }
  }

  protected cargarEfectivoEsperado(): void {
    if (!this.cashRegisterSessionId) return;
    this.cashRegisterService.getResumenCierreCajaSesion(this.cashRegisterSessionId).subscribe({
      next: (res: CashRegisterClosingSummaryResponse) => {
        const efectivo = res.resumen_por_metodo_pago?.find(
          (m) => m.metodo_pago?.toUpperCase() === 'EFECTIVO',
        );
        this.efectivoEsperadoCaja = efectivo?.monto_esperado ?? res.monto_esperado_total ?? 0;
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  protected abrirModalMovimientoRapido(tipo: 'INGRESO' | 'EGRESO'): void {
    if (!this.hasPermission('cash_movement:create')) return;
    this.quickMovementTipo = tipo;
    this.quickMovementMonto = null;
    this.quickMovementConcepto = '';
    this.quickMovementError = '';

    if (this.movementTypes.length === 0) {
      this.loadMovementTypes();
    }
    if (this.paymentMethods.length === 0) {
      this.loadPaymentMethods();
    }

    const efectivoMetodo = this.paymentMethods.find((m) => m.nombre.toUpperCase() === 'EFECTIVO');
    this.quickMovementMetodoPagoId = efectivoMetodo ? efectivoMetodo.id : (this.paymentMethods[0]?.id ?? null);

    this.isQuickMovementModalOpen = true;
    this.cdr.markForCheck();
  }

  protected cerrarModalMovimientoRapido(): void {
    if (this.savingQuickMovement) return;
    this.isQuickMovementModalOpen = false;
    this.quickMovementError = '';
  }

  protected confirmarMovimientoRapido(event: Event): void {
    event.preventDefault();
    this.quickMovementError = '';
    if (!this.hasPermission('cash_movement:create')) { this.quickMovementError = 'No tienes permiso para registrar movimientos de caja.'; return; }

    if (!this.quickMovementMonto || this.quickMovementMonto <= 0) {
      this.quickMovementError = 'Ingresa un monto valido mayor a 0.';
      return;
    }

    const concepto = this.quickMovementConcepto.trim();
    if (!concepto) {
      this.quickMovementError = 'Ingresa una descripcion o motivo para el movimiento.';
      return;
    }

    if (!this.cashRegisterSessionId) {
      this.quickMovementError = 'No se encontro la sesion de caja activa.';
      return;
    }

    const tipo = this.movementTypes.find((t) => t.nombre.toUpperCase() === this.quickMovementTipo);
    const idTipo = tipo ? tipo.id : (this.quickMovementTipo === 'INGRESO' ? 2 : 3);

    let idMetodo = this.quickMovementMetodoPagoId;
    if (!idMetodo) {
      const efectivo = this.paymentMethods.find((m) => m.nombre.toUpperCase() === 'EFECTIVO');
      idMetodo = efectivo ? efectivo.id : (this.paymentMethods[0]?.id ?? 1);
    }

    this.savingQuickMovement = true;
    this.cashRegisterService
      .crearMovimientoCajaSesion(this.cashRegisterSessionId, {
        id_tipo_movimiento_caja: idTipo,
        id_metodo_pago: idMetodo,
        monto: Number(this.quickMovementMonto),
        concepto: `${this.quickMovementTipo}: ${concepto}`,
      })
      .pipe(
        finalize(() => {
          this.savingQuickMovement = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.isQuickMovementModalOpen = false;
          this.registerMessage = `Movimiento de ${this.quickMovementTipo} por Bs. ${Number(this.quickMovementMonto).toFixed(2)} registrado correctamente.`;
          this.cargarEfectivoEsperado();
          this.loadMovementItems();
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.quickMovementError = err?.error?.detail ?? 'No se pudo registrar el movimiento de caja.';
          this.cdr.markForCheck();
        },
      });
  }

  protected get filteredSalesHistory(): SaleHistoryRow[] {
    return this.salesHistory.filter((sale) => {
      // Excluir ventas anuladas o canceladas (regla HU: únicamente ventas completadas/pagadas)
      const estadoNorm = (sale.estado || '').toUpperCase();
      if (estadoNorm.includes('ANULAD') || estadoNorm.includes('CANCELAD')) {
        return false;
      }

      // Filtro por cliente
      if (this.historySelectedClientId !== 'all') {
        const targetId = Number(this.historySelectedClientId);
        if (Number(sale.clienteId) !== targetId) {
          return false;
        }
      }

      return true;
    });
  }

  // --- Postventa: anulacion (HU-14) y devolucion (HU-15) ------------------------------------------
  protected canPostSale(sale: SaleHistoryRow): boolean {
    return this.hasPermission('sale:void') && (sale.estado || '').toUpperCase() !== 'DEVUELTA';
  }

  protected openAnnulSale(sale: SaleHistoryRow): void {
    this.postSaleMessage = '';
    this.postSale = { mode: 'anular', sale, motivo: '', reembolso: 'EFECTIVO', items: [], vence: null,
      loading: false, saving: false, error: '' };
  }

  protected openReturnSale(sale: SaleHistoryRow): void {
    this.postSaleMessage = '';
    const dialog: PostSaleDialog = { mode: 'devolver', sale, motivo: '', reembolso: 'EFECTIVO',
      items: [], vence: null, loading: true, saving: false, error: '' };
    this.postSale = dialog;
    this.apiService.get<{ vence: string; productos: Array<{ id_producto: number; descripcion: string; cantidad_disponible: number; precio_unitario_neto: number }> }>(
      `/api/ventas/${sale.id}/devoluciones`,
    ).subscribe({
      next: (resumen) => {
        dialog.vence = resumen.vence;
        dialog.items = resumen.productos
          .filter((p) => p.cantidad_disponible > 0)
          .map((p) => ({
            idProducto: p.id_producto,
            descripcion: sale.detalles.find((d) => Number(d.id_producto) === p.id_producto)?.producto?.nombre ?? p.descripcion ?? `Producto #${p.id_producto}`,
            disponible: p.cantidad_disponible,
            precio: Number(p.precio_unitario_neto),
            cantidad: 0,
          }));
        dialog.loading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        dialog.loading = false;
        dialog.error = error?.error?.detail ?? 'No se pudo cargar la venta para la devolución.';
        this.cdr.detectChanges();
      },
    });
  }

  protected get postSaleReturnTotal(): number {
    return this.roundCurrency((this.postSale?.items ?? []).reduce((acc, i) => acc + i.precio * (Number(i.cantidad) || 0), 0));
  }

  protected returnAll(): void {
    this.postSale?.items.forEach((i) => (i.cantidad = i.disponible));
  }

  protected closePostSale(): void {
    if (!this.postSale?.saving) this.postSale = null;
  }

  protected confirmPostSale(): void {
    const dialog = this.postSale;
    if (!dialog || dialog.saving) return;
    dialog.error = '';
    if (dialog.motivo.trim().length < 5) {
      dialog.error = 'Escribe el motivo (al menos 5 caracteres).';
      return;
    }
    const items = dialog.items
      .filter((i) => Number(i.cantidad) > 0)
      .map((i) => ({ id_producto: i.idProducto, cantidad: Math.min(Number(i.cantidad), i.disponible) }));
    if (dialog.mode === 'devolver' && items.length === 0) {
      dialog.error = 'Indica al menos un producto y la cantidad a devolver.';
      return;
    }
    dialog.saving = true;
    const request$ = dialog.mode === 'anular'
      ? this.apiService.post<{ mensaje: string }, { motivo: string }>(`/api/ventas/${dialog.sale.id}/anular`, { motivo: dialog.motivo.trim() })
      : this.apiService.post<{ mensaje: string }, unknown>(`/api/ventas/${dialog.sale.id}/devoluciones`, {
          items, tipo_reembolso: dialog.reembolso, motivo: dialog.motivo.trim(),
        });
    request$.subscribe({
      next: (response) => {
        this.postSale = null;
        this.postSaleMessage = response.mensaje;
        this.loadSalesHistory();
        this.loadRegisterData();
        this.cargarEfectivoEsperado();
        this.cdr.detectChanges();
      },
      error: (error) => {
        dialog.saving = false;
        dialog.error = error?.error?.detail ?? 'No se pudo completar la operación.';
        this.cdr.detectChanges();
      },
    });
  }

  protected openSaleDetailModal(sale: SaleHistoryRow): void {
    this.selectedSaleDetail = sale;
    this.isSaleDetailModalOpen = true;
  }

  protected closeSaleDetailModal(): void {
    this.selectedSaleDetail = null;
    this.isSaleDetailModalOpen = false;
  }

  ngOnInit(): void {
    this.loadBilleteraConfig();
    // Nombre del comercio para el QR de cobro.
    this.companyService.getMisEmpresas().subscribe({
      next: (companies) => {
        const company = companies.find((c) => String(c.idEmpresa ?? c.id_empresa ?? c.id) === this.companyId);
        this.companyName = company?.nombre ?? '';
      },
      error: () => (this.companyName = ''),
    });
    this.validarSesionActiva();

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (params.get('sessionId')) {
        this.cashRegisterSessionId = params.get('sessionId') ?? '';
      }
      const nextMode = this.resolveViewMode(params.get('section'));

      if (nextMode !== this.viewMode) {
        this.viewMode = nextMode;
        this.sidebarActiveItemLabel = nextMode === 'movements' ? 'Movimientos' : 'Ventas';
        this.resetViewState();

        if (nextMode === 'sales') {
          this.loadSalesViewData();
        } else {
          this.loadMovementTypes();
          this.loadPaymentMethods();
        }

        this.cdr.detectChanges();
      }

      // Pedido desde bandeja: esperar a que el stock esté cargado (evita carrito vacío).
      const pedidoIdParam = params.get('pedido_id');
      if (pedidoIdParam) {
        this.pendingPedidoId = parseInt(pedidoIdParam, 10);
        this.tryLoadPendingOrder();
      }
    });

    if (this.viewMode === 'sales') {
      this.loadSalesViewData();
    } else {
      this.loadMovementTypes();
      this.loadPaymentMethods();
      this.loadMovementItems();
    }
  }

  private validarSesionActiva(): void {
    if (!this.companyId || !this.branchId || !this.cashRegisterId) return;

    this.cashRegisterService.getCajasSucursal(this.companyId, this.branchId).subscribe({
      next: (response) => {
        const cajas = Array.isArray(response) ? response : ((response as any).cajas || (response as any).items || (response as any).data?.cajas || (response as any).data?.items || []);
        const caja = cajas.find((c: any) => String(c.id_caja) === this.cashRegisterId || String(c.id) === this.cashRegisterId);
        
        if (caja && caja.estado_operativo === 'abierta_por_mi' && caja.sesion_activa) {
          this.cashRegisterSessionId = String(caja.sesion_activa.id_caja_sesion);
          this.cargarEfectivoEsperado();
          
          // Actualizar URL sin recargar para mantener sessionId
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { sessionId: this.cashRegisterSessionId },
            queryParamsHandling: 'merge',
            replaceUrl: true
          });
        } else if (this.cashRegisterSessionId) {
          // Si ya contamos con sessionId en queryParams, permitir continuar operando en POS
          this.cargarEfectivoEsperado();
          return;
        } else {
          this.router.navigate(['/company', this.companyId, 'branch', this.branchId, 'cash-register']);
        }
      },
      error: () => {
        this.router.navigate(['/company', this.companyId, 'branch', this.branchId, 'cash-register']);
      }
    });
  }

  protected setActiveTab(tab: SalesTab): void {
    if (tab === 'register' && !this.hasPermission('sale:create')) {
      return;
    }

    if (tab !== 'payment') {
      this.closePaymentTab();
    }

    this.activeTab = tab;

    if (tab === 'history') {
      this.loadSalesHistory();
    }
  }

  protected setMovementTab(tab: MovementTab): void {
    if (tab === 'register' && !this.hasPermission('cash_movement:create')) {
      return;
    }

    if (tab === 'list' && !this.hasPermission('cash_movement:read')) {
      return;
    }

    this.movementActiveTab = tab;
    this.limpiarMensajesMovimientoCaja();

    if (tab === 'list') {
      this.loadMovementItems();
    }
  }

  protected get filteredProducts(): ProductSearchItem[] {
    const term = this.normalizeSearchTerm(this.searchTerm);

    if (!term) {
      return this.products.slice(0, 8);
    }

    return this.products
      .filter((product) => this.productMatchesSearch(product, term))
      .slice(0, 8);
  }

  protected get visibleRecommendedProducts(): ProductSearchItem[] {
    return this.saleDetails.length === 0 ? this.products : this.recommendedProducts;
  }

  protected isCreditSaleType(saleType: SaleTypeOption): boolean {
    return this.normalizeSearchTerm(saleType.nombre) === 'credito';
  }

  protected isSaleTypeDisabled(saleType: SaleTypeOption): boolean {
    return this.isCreditSaleType(saleType) && !this.selectedClientId;
  }

  protected get selectedSaleTypeIsCredit(): boolean {
    const saleType = this.saleTypes.find((type) => type.id === this.selectedSaleTypeId);
    return Boolean(saleType && this.isCreditSaleType(saleType));
  }

  protected get subtotal(): number {
    return this.roundCurrency(
      this.saleDetails.reduce((total, item) => total + this.effectivePrice(item) * item.cantidad, 0),
    );
  }

  protected get detailDiscountTotal(): number {
    return this.roundCurrency(
      this.saleDetails.reduce((total, item) => total + this.getItemDiscountAmount(item), 0),
    );
  }

  protected get discountTotal(): number {
    return this.roundCurrency(Math.min(this.subtotal, this.detailDiscountTotal + (this.canDiscount ? this.normalizeMoney(this.saleDiscount) : 0)));
  }

  protected get total(): number {
    return this.roundCurrency(Math.max(this.subtotal - this.discountTotal, 0));
  }

  protected addProduct(product: ProductSearchItem): void {
    if (!this.hasPermission('sale:create')) {
      this.registerError = 'No tienes permiso para registrar ventas.';
      return;
    }

    this.registerError = '';
    this.registerFieldErrors = [];
    this.registerMessage = '';

    // HU-34: el stock real es el de la sucursal (las recomendaciones pueden no traerlo).
    const branchStock =
      this.products.find((p) => p.idProducto === product.idProducto)?.stock ?? product.stock;
    if (branchStock <= 0) {
      this.registerError = 'Stock insuficiente';
      return;
    }

    const existingItem = this.saleDetails.find((item) => item.idProducto === product.idProducto);

    if (existingItem) {
      this.updateQuantity(existingItem, existingItem.cantidad + 1);
      this.loadRecommendedProducts();
      return;
    }

    const clientDiscount = this.getClientDiscountPercentage();

    this.saleDetails = [
      ...this.saleDetails,
      {
        ...product,
        precioCatalogo: product.precio,
        cantidad: 1,
        descuento: clientDiscount > 0 ? clientDiscount : 0,
        descuentoTipo: clientDiscount > 0 ? 'percentage' : 'fixed',
      },
    ];
    this.loadRecommendedProducts();
  }

  protected scannerOpen = false;
  private scanStream: MediaStream | null = null;
  private scanTimer = 0;

  protected async toggleScanner(): Promise<void> {
    if (this.scannerOpen) {
      this.stopScanner();
      return;
    }
    const Detector = (window as unknown as { BarcodeDetector?: new (opts: { formats: string[] }) => { detect: (source: HTMLVideoElement) => Promise<Array<{ rawValue?: string }>> } }).BarcodeDetector;
    if (!Detector || !navigator.mediaDevices?.getUserMedia) {
      this.registerError = 'Este navegador no lee códigos con la cámara. Usa Chrome o un lector USB.';
      return;
    }
    this.scannerOpen = true;
    this.registerError = '';
    this.registerFieldErrors = [];
    setTimeout(() => void this.startScanner(Detector), 50);
  }

  private async startScanner(Detector: new (opts: { formats: string[] }) => { detect: (source: HTMLVideoElement) => Promise<Array<{ rawValue?: string }>> }): Promise<void> {
    const video = document.getElementById('barcodeVideo') as HTMLVideoElement | null;
    if (!video) {
      this.stopScanner();
      return;
    }
    try {
      this.scanStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      video.srcObject = this.scanStream;
      await video.play();
      const detector = new Detector({ formats: ['ean_13', 'ean_8', 'code_128', 'qr_code', 'upc_a'] });
      this.scanTimer = window.setInterval(async () => {
        try {
          const codes = await detector.detect(video);
          const value = codes[0]?.rawValue?.trim();
          if (value) {
            this.searchTerm = value;
            this.stopScanner();
            setTimeout(() => {
              this.addFromSearch();
              this.cdr.markForCheck();
            }, 50);
          }
        } catch {
          // un frame ilegible no corta el escaneo
        }
      }, 400);
    } catch {
      this.registerError = 'No se pudo abrir la cámara.';
      this.stopScanner();
    }
  }

  // --- HU-27: reconocimiento de productos por camara (Gemini) -----------------------------------
  protected visionOpen = false;
  protected visionBusy = false;
  protected visionError = '';
  protected visionResult: VisionResult | null = null;
  private visionStream: MediaStream | null = null;

  protected async openVision(): Promise<void> {
    if (this.scannerOpen) this.stopScanner();
    this.visionOpen = true;
    this.visionError = '';
    this.visionResult = null;
    if (!navigator.mediaDevices?.getUserMedia) {
      this.visionError = 'Este navegador no permite usar la cámara.';
      return;
    }
    try {
      this.visionStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      setTimeout(() => {
        const video = document.getElementById('visionVideo') as HTMLVideoElement | null;
        if (video) { video.srcObject = this.visionStream; void video.play(); }
      }, 50);
    } catch {
      this.visionError = 'No se pudo acceder a la cámara. Revisa los permisos del navegador.';
    }
    this.cdr.markForCheck();
  }

  protected closeVision(): void {
    this.visionStream?.getTracks().forEach((track) => track.stop());
    this.visionStream = null;
    this.visionOpen = false;
    this.visionBusy = false;
  }

  /** Captura un cuadro del video, lo envia al backend (Gemini) y agrega el producto si se reconoce. */
  protected captureVision(): void {
    const video = document.getElementById('visionVideo') as HTMLVideoElement | null;
    if (!video || !video.videoWidth || this.visionBusy) return;
    const canvas = document.createElement('canvas');
    const scale = Math.min(1, 960 / video.videoWidth);
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height);
    this.visionBusy = true;
    this.visionError = '';
    this.visionResult = null;
    canvas.toBlob((blob) => {
      if (!blob) { this.visionBusy = false; this.visionError = 'No se pudo capturar la foto.'; this.cdr.markForCheck(); return; }
      const form = new FormData();
      form.append('imagen', blob, 'captura.jpg');
      this.apiService
        .post<VisionResult, FormData>(
          `/api/empresas/${this.companyId}/sucursales/${this.branchId}/productos/reconocer`, form)
        .subscribe({
          next: (result) => {
            this.visionBusy = false;
            this.visionResult = result;
            if (result.estado === 'IDENTIFICADO' && result.coincidencias[0]) this.addRecognized(result.coincidencias[0].id_producto);
            this.cdr.markForCheck();
          },
          error: (error) => {
            this.visionBusy = false;
            this.visionError = error?.error?.detail ?? 'No se pudo reconocer el producto.';
            this.cdr.markForCheck();
          },
        });
    }, 'image/jpeg', 0.85);
  }

  protected addRecognized(idProducto: number): void {
    const product = this.products.find((p) => p.idProducto === idProducto);
    if (!product) {
      this.visionError = 'El producto reconocido no tiene stock en esta sucursal.';
      return;
    }
    this.addProduct(product);
  }

  private stopScanner(): void {
    window.clearInterval(this.scanTimer);
    this.scanStream?.getTracks().forEach((track) => track.stop());
    this.scanStream = null;
    this.scannerOpen = false;
  }

  protected onSearchEnter(event: Event): void {
    event.preventDefault();
    this.addFromSearch();
  }

  /** HU-34: código de barras exacto → ese producto; 1 coincidencia → agrega; 0 → "Producto no encontrado". */
  private addFromSearch(): void {
    const term = this.normalizeSearchTerm(this.searchTerm);
    if (!term) {
      return;
    }
    const exact = this.products.find(
      (p) => !!p.codigoBarras && this.normalizeSearchTerm(p.codigoBarras) === term,
    );
    if (exact) {
      this.addProduct(exact);
      this.searchTerm = '';
      return;
    }
    const matches = this.filteredProducts;
    if (matches.length === 1) {
      this.addProduct(matches[0]);
    } else if (matches.length === 0) {
      this.registerError = 'Producto no encontrado';
    }
  }

  protected updateQuantity(item: SaleDetailItem, value: number | string): void {
    const quantity = Math.max(1, Math.floor(Number(value) || 1));

    if (item.stock > 0 && quantity > item.stock) {
      item.cantidad = item.stock;
      this.registerError = `Solo hay ${item.stock} unidades disponibles de ${item.nombre}.`;
      return;
    }

    item.cantidad = quantity;
    this.updateItemDiscount(item, item.descuento);
    this.registerError = '';
    this.registerFieldErrors = [];
  }

  protected updateItemDiscount(item: SaleDetailItem, value: number | string): void {
    if (!this.canDiscount) {
      item.descuento = 0;
      item.descuentoTipo = 'fixed';
      return;
    }

    const discount = this.normalizeMoney(value);
    item.descuento = item.descuentoTipo === 'percentage'
      ? Math.min(discount, 100)
      : Math.min(discount, this.getItemSubtotal(item));
  }

  protected updateItemDiscountType(item: SaleDetailItem, type: DiscountType): void {
    if (!this.canDiscount) {
      item.descuentoTipo = 'fixed';
      item.descuento = 0;
      return;
    }

    item.descuentoTipo = type;
    this.updateItemDiscount(item, item.descuento);
  }

  protected getItemDiscountAmount(item: SaleDetailItem): number {
    const itemSubtotal = this.getItemSubtotal(item);
    // El descuento de la categoría del cliente (HU-08) se aplica aunque el usuario no pueda dar descuentos manuales.
    if (!this.canDiscount) return this.roundCurrency(itemSubtotal * this.getClientDiscountPercentage() / 100);

    if (item.descuentoTipo === 'percentage') {
      return this.roundCurrency(itemSubtotal * Math.min(this.normalizeMoney(item.descuento), 100) / 100);
    }

    return this.roundCurrency(Math.min(this.normalizeMoney(item.descuento), itemSubtotal));
  }

  protected getItemLineTotal(item: SaleDetailItem): number {
    return this.roundCurrency(Math.max(this.getItemSubtotal(item) - this.getItemDiscountAmount(item), 0));
  }

  protected removeProduct(item: SaleDetailItem): void {
    this.saleDetails = this.saleDetails.filter((detail) => detail.idProducto !== item.idProducto);
    this.loadRecommendedProducts();
  }

  protected cobrar(): void {
    this.registerError = '';
    this.registerFieldErrors = [];
    this.registerMessage = '';

    if (!this.hasPermission('sale:create')) {
      this.registerError = 'No tienes permiso para registrar ventas.';
      return;
    }

    if (this.saleDetails.length === 0) {
      this.registerError = 'Agrega al menos un producto al detalle de venta.';
      return;
    }

    if (this.onlineInvoice && !this.selectedClientId) {
      this.onlineInvoice = false;
      this.registerError = 'Selecciona un cliente para emitir una factura en linea.';
      return;
    }

    if (this.isSelectedSaleTypeCreditWithoutClient()) {
      this.registerError = 'Para una venta a credito debes seleccionar un cliente diferente de Consumidor final.';
      this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
      return;
    }

    if (this.selectedSaleTypeIsCredit && this.exceedsSelectedClientCreditLimit() && !this.creditAuthorizationReady) {
      this.creditAuthorizationOpen = true;
      this.registerError = `La venta supera el limite de credito (Bs ${this.formatCurrency(this.getSelectedClientCreditLimit())}). Requiere autorizacion de un administrador.`;
      return;
    }

    if (this.selectedSaleTypeIsCredit) {
      this.registrarVentaCredito();
      return;
    }

    this.openPaymentTab();
  }

  protected addPaymentRow(): void {
    if (!this.canAddPaymentRow) {
      return;
    }

    this.salePaymentRows = [
      ...this.salePaymentRows,
      this.createPaymentRow(undefined, this.getRemainingPaymentAmount()),
    ];
  }

  protected removePaymentRow(row: SalePaymentRow): void {
    if (row.cobroId) this.discardQr(row);
    if (this.salePaymentRows.length === 1) {
      this.salePaymentRows = [this.createPaymentRow(this.salePaymentRows[0]?.id)];
      return;
    }

    this.salePaymentRows = this.salePaymentRows.filter((paymentRow) => paymentRow.id !== row.id);
  }

  protected trackPaymentRow(_: number, row: SalePaymentRow): number {
    return row.id;
  }

  protected get canAddPaymentRow(): boolean {
    return this.salePaymentRows.length < this.maxPaymentRows && this.salePaymentRows.length < this.paymentMethods.length;
  }

  protected isPaymentMethodDisabled(paymentRow: SalePaymentRow, method: PaymentMethodOption): boolean {
    if (this.isSaldoAFavorMethod(method) && this.selectedClientSaldoAFavor <= 0) return true;
    return this.salePaymentRows.some((row) => row.id !== paymentRow.id && row.metodoPagoId === method.id);
  }

  protected isSaldoAFavorMethod(method: PaymentMethodOption): boolean {
    return method.nombre.trim().toUpperCase() === 'SALDO A FAVOR';
  }

  protected get selectedClientSaldoAFavor(): number {
    return this.clients.find((c) => c.id === this.selectedClientId)?.saldoAFavor ?? 0;
  }

  protected updatePaymentAmount(row: SalePaymentRow, value: number | string | null): void {
    row.monto = this.normalizeMoney(value);
    // Con la billetera, el QR se emite por un monto fijo: si cambia, se cancela y se genera otro.
    if (this.billeteraConfig.qr_habilitado && row.cobroId && !row.qrConfirmado) this.discardQr(row);
    row.qrConfirmado = false;
    // Un cambio de monto invalida la autorizacion de tarjeta ya obtenida.
    if (row.tarjeta?.autorizacion) row.tarjeta = this.emptyCard();
  }

  // --- Pago por QR (simulado) -------------------------------------------------------------------
  protected readonly qrImages = new Map<number, string>();
  /** Datos visibles del cobro QR por fila: banco, referencia y vencimiento. */
  protected readonly qrInfo = new Map<number, { banco: string; referencia: string; vence: Date }>();
  protected qrBanco = QR_BANCOS[0];
  protected readonly qrBancos = QR_BANCOS;

  // --- Pasarela QR de la Billetera ---------------------------------------------------------------
  private readonly billetera = inject(BilleteraService);
  /** Si el servidor no tiene la billetera configurada se usan el QR y la tarjeta simulados. */
  protected billeteraConfig: BilleteraConfig = { qr_habilitado: false, tarjeta_habilitada: false };
  protected readonly qrBusy = new Set<number>();
  private readonly qrPollers = new Map<number, ReturnType<typeof setInterval>>();
  private billeteraCleanup = this.destroyRef.onDestroy(() => {
    this.qrPollers.forEach((timer) => clearInterval(timer));
    this.qrPollers.clear();
  });

  private loadBilleteraConfig(): void {
    this.billetera.config().subscribe({
      next: (config) => {
        this.billeteraConfig = config;
        this.cdr.markForCheck();
      },
      error: () => (this.billeteraConfig = { qr_habilitado: false, tarjeta_habilitada: false }),
    });
  }

  /** Crea el cobro en la billetera y consulta su estado cada 2 s hasta que se pague o venza. */
  private createWalletQr(row: SalePaymentRow): void {
    this.discardQr(row);
    const monto = this.normalizeMoney(row.monto);
    if (!(monto > 0) || !this.companyId) return;
    this.qrBusy.add(row.id);
    this.billetera
      .crearCobro(Number(this.companyId), this.branchId ? Number(this.branchId) : null, monto, this.companyName || 'Venta POS')
      .subscribe({
        next: (cobro) => {
          this.qrBusy.delete(row.id);
          row.cobroId = cobro.id;
          this.qrImages.set(row.id, cobro.qr_imagen);
          this.qrInfo.set(row.id, { banco: 'Billetera', referencia: cobro.referencia, vence: new Date(cobro.vence) });
          this.qrPollers.set(row.id, setInterval(() => this.checkWalletQr(row), 2000));
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.qrBusy.delete(row.id);
          this.registerError = error?.error?.detail ?? 'No se pudo crear el cobro QR en la billetera.';
          this.cdr.markForCheck();
        },
      });
  }

  private checkWalletQr(row: SalePaymentRow): void {
    if (!row.cobroId) return this.stopQrPolling(row);
    this.billetera.estado(row.cobroId).subscribe({
      next: (estado) => {
        if (estado.estado === 'PAID') {
          row.qrConfirmado = true;
          this.qrPayer.set(row.id, estado.pagado_por);
          this.stopQrPolling(row);
        } else if (estado.estado === 'EXPIRED' || estado.estado === 'CANCELLED') {
          this.stopQrPolling(row);
          this.qrImages.delete(row.id);
          row.cobroId = undefined;
          this.registerError = 'El QR venció o se canceló. Genera uno nuevo.';
        }
        this.cdr.markForCheck();
      },
      error: () => {
        /* Error transitorio de red: se reintenta en el siguiente ciclo. */
      },
    });
  }

  /** Quien pago cada QR (nombre que informa la billetera). */
  protected readonly qrPayer = new Map<number, string>();

  private stopQrPolling(row: SalePaymentRow): void {
    clearInterval(this.qrPollers.get(row.id));
    this.qrPollers.delete(row.id);
  }

  /** Cancela en la billetera un QR pendiente (monto cambiado, otro metodo o venta cancelada). */
  private discardQr(row: SalePaymentRow): void {
    this.stopQrPolling(row);
    if (row.cobroId && !row.qrConfirmado) this.billetera.cancelar(row.cobroId).subscribe({ error: () => undefined });
    row.cobroId = undefined;
    this.qrImages.delete(row.id);
    this.qrInfo.delete(row.id);
    this.qrPayer.delete(row.id);
  }

  protected isQrRow(row: SalePaymentRow): boolean {
    const method = this.paymentMethods.find((m) => m.id === row.metodoPagoId);
    return method?.nombre.trim().toUpperCase() === 'QR';
  }

  protected changePaymentMethod(row: SalePaymentRow, methodId: number | null): void {
    if (row.cobroId) this.discardQr(row);
    row.metodoPagoId = methodId;
    row.qrConfirmado = false;
    row.tarjeta = this.isCardRow(row) ? this.emptyCard() : undefined;
    if (this.isQrRow(row)) void this.generateQr(row);
  }

  // --- Pago con tarjeta simulado (HU-13) --------------------------------------------------------
  protected isCardRow(row: SalePaymentRow): boolean {
    const method = this.paymentMethods.find((m) => m.id === row.metodoPagoId);
    return method?.nombre.trim().toUpperCase() === 'TARJETA';
  }

  private emptyCard(): CardForm {
    return { numero: '', titular: '', vencimiento: '', cvv: '', procesando: false, error: '', autorizacion: null, marca: '', ultimos4: '' };
  }

  protected cardBrand(numero: string): string {
    const n = numero.replace(/\D/g, '');
    if (/^4/.test(n)) return 'Visa';
    if (/^(5[1-5]|2[2-7])/.test(n)) return 'Mastercard';
    if (/^3[47]/.test(n)) return 'American Express';
    return '';
  }

  protected formatCardNumber(card: CardForm, value: string): void {
    const digits = value.replace(/\D/g, '').slice(0, 16);
    card.numero = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
    card.marca = this.cardBrand(digits);
    card.error = '';
  }

  /** Simula la autorizacion del adquirente (ATC/Linkser): valida Luhn, vencimiento y CVV. */
  protected authorizeCard(row: SalePaymentRow): void {
    const card = row.tarjeta;
    if (!card || card.procesando) return;
    const digits = card.numero.replace(/\D/g, '');
    const [mm, yy] = card.vencimiento.split('/').map((v) => Number(v));
    const now = new Date();
    const expired = !mm || !yy || mm < 1 || mm > 12 || new Date(2000 + yy, mm, 1) <= new Date(now.getFullYear(), now.getMonth(), 1);
    if (digits.length < 15 || !this.luhn(digits)) card.error = 'Número de tarjeta inválido.';
    else if (!card.titular.trim()) card.error = 'Ingresa el nombre del titular.';
    else if (expired) card.error = 'Fecha de vencimiento inválida o tarjeta vencida (MM/AA).';
    else if (!/^\d{3,4}$/.test(card.cvv)) card.error = 'CVV inválido.';
    else if (!(Number(row.monto) > 0)) card.error = 'Ingresa el monto a cobrar con tarjeta.';
    if (card.error) return;
    card.procesando = true;
    if (this.billeteraConfig.tarjeta_habilitada) {
      // Autorizacion real con la tarjeta virtual de la Billetera (los datos solo pasan por el backend).
      this.billetera
        .autorizarTarjeta({
          id_empresa: Number(this.companyId), id_sucursal: this.branchId ? Number(this.branchId) : null,
          numero: digits, mes: mm, anio: 2000 + yy, cvv: card.cvv, titular: card.titular.trim(),
          monto: this.normalizeMoney(row.monto), comercio: this.companyName || 'POS',
        })
        .subscribe({
          next: (r) => {
            card.procesando = false;
            if (r.status === 'APPROVED') {
              card.autorizacion = r.approvalCode ?? '';
              card.ultimos4 = r.cardLast4 ?? digits.slice(-4);
              card.marca = this.cardBrand(digits) || 'Tarjeta';
              card.numero = '•••• •••• •••• ' + card.ultimos4;
              card.cvv = '';
            } else {
              card.error = r.message || 'Transacción rechazada.';
            }
            this.cdr.markForCheck();
          },
          error: (error) => {
            card.procesando = false;
            card.error = error?.error?.detail ?? 'No se pudo autorizar la tarjeta.';
            this.cdr.markForCheck();
          },
        });
      return;
    }
    // Tarjetas de prueba que terminan en 0002 simulan un rechazo del banco emisor.
    setTimeout(() => {
      card.procesando = false;
      if (digits.endsWith('0002')) {
        card.error = 'Transacción rechazada por el banco emisor (fondos insuficientes).';
      } else {
        card.autorizacion = String(Math.floor(100000 + Math.random() * 900000));
        card.ultimos4 = digits.slice(-4);
        card.marca = this.cardBrand(digits) || 'Tarjeta';
        // No se conservan los datos sensibles despues de autorizar.
        card.numero = '•••• •••• •••• ' + card.ultimos4;
        card.cvv = '';
      }
      this.cdr.markForCheck();
    }, 1200);
  }

  private luhn(digits: string): boolean {
    let sum = 0;
    for (let i = 0; i < digits.length; i++) {
      let d = Number(digits[digits.length - 1 - i]);
      if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9; }
      sum += d;
    }
    return sum % 10 === 0;
  }

  protected confirmQrPayment(row: SalePaymentRow): void {
    row.qrConfirmado = true;
    this.registerError = '';
    this.registerFieldErrors = [];
  }

  /**
   * Genera un QR Simple interoperable (simulado): no se conecta a ningun banco. Lleva banco, comercio,
   * monto, moneda y referencia, con vigencia de 5 minutos como los QR de cobro en Bolivia.
   */
  protected async generateQr(row: SalePaymentRow): Promise<void> {
    if (this.billeteraConfig.qr_habilitado) return this.createWalletQr(row);
    // qrcode se carga solo cuando se usa para no engordar el paquete inicial.
    const QRCode = (await import('qrcode')).default;
    const referencia = `QRS-${Date.now().toString(36).toUpperCase()}`;
    const vence = new Date(Date.now() + 5 * 60 * 1000);
    const payload = JSON.stringify({
      tipo: 'QR_SIMPLE', banco: this.qrBanco, comercio: this.companyName || 'Comercio',
      monto: this.normalizeMoney(row.monto), moneda: 'BOB', referencia, vence: vence.toISOString(),
    });
    this.qrInfo.set(row.id, { banco: this.qrBanco, referencia, vence });
    this.qrImages.set(row.id, await QRCode.toDataURL(payload, { margin: 1, width: 220 }));
    this.cdr.markForCheck();
  }

  // HU-09: autorizacion de un administrador cuando la venta a credito excede el limite.
  protected creditAuthorizationOpen = false;
  protected creditAuth = { email: '', contrasena: '', motivo: '' };

  protected get creditAuthorizationReady(): boolean {
    return this.creditAuthorizationOpen && !!this.creditAuth.email.trim() && !!this.creditAuth.contrasena && !!this.creditAuth.motivo.trim();
  }

  protected cancelCreditAuthorization(): void {
    this.creditAuthorizationOpen = false;
    this.creditAuth = { email: '', contrasena: '', motivo: '' };
  }

  // HU-17: errores por campo de la validacion previa de la factura (respuesta { detail, errores }).
  protected registerFieldErrors: string[] = [];
  private static readonly FIELD_LABELS: Record<string, string> = {
    nit_emisor: 'NIT de la empresa',
    codigo_tipo_documento: 'Documento del comprador',
    monto_total: 'Total',
    iva: 'IVA 13%',
    cuis: 'CUIS',
    cufd: 'CUFD',
  };

  private fieldErrors(error: unknown): string[] {
    const errores = (error as { error?: { errores?: Record<string, string> } })?.error?.errores;
    if (!errores || typeof errores !== 'object') return [];
    return Object.entries(errores).map(([field, message]) => {
      const label = field.endsWith('.codigo_producto_sin') ? 'Codigo SIN del producto' : (Sales.FIELD_LABELS[field] ?? field);
      return `${label}: ${message}`;
    });
  }

  private registrarVentaCredito(): void {
    if (!this.cashRegisterSessionId) {
      this.registerError = 'No se encontro la sesion de caja para registrar la venta.';
      return;
    }

    if (!this.selectedSaleTypeId) {
      this.registerError = 'Selecciona un tipo de venta.';
      return;
    }

    const payload: CreateSaleRequest = {
      id_tipo_venta: this.selectedSaleTypeId,
      id_cliente: this.selectedClientId,
      id_metodo_pago: null,
      id_pedido: this.loadedOrderId,
      factura_linea: this.onlineInvoice && this.hasPermission("invoice:issue"),
      ...this.invoiceBuyer(),
      subtotal: this.subtotal,
      descuento_total: this.discountTotal,
      total: this.total,
      estado: 'Pendiente',
      detalles: this.buildSaleDetailPayload(),
      autorizacion_credito: this.creditAuthorizationReady
        ? { email: this.creditAuth.email.trim(), contrasena: this.creditAuth.contrasena, motivo: this.creditAuth.motivo.trim() }
        : null,
    };

    this.chargingSale = true;

    this.cashRegisterService.registrarVentaSesionCaja(this.cashRegisterSessionId, payload).subscribe({
      next: (response) => {
        this.cancelCreditAuthorization();
        this.registerMessage = `Venta a credito registrada correctamente con el ID ${response.id_venta}.`;
        this.lastInvoiceId = response.id_factura ?? null;
        if (this.lastInvoiceId) this.registerMessage += ' Factura emitida (SIAT simulado).';
        this.resetSaleForm();
        this.closePaymentTab();
        this.activeTab = 'register';
        this.chargingSale = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.chargingSale = false;
        this.registerError = apiErrorMessage(error, 'No se pudo registrar la venta a credito.');
        this.registerFieldErrors = this.fieldErrors(error);
        this.cdr.detectChanges();
      },
    });
  }

  protected registrarPago(): void {
    this.registerError = '';
    this.registerFieldErrors = [];

    if (!this.hasPermission('sale:create')) {
      this.registerError = 'No tienes permiso para registrar ventas.';
      return;
    }

    if (!this.cashRegisterSessionId) {
      this.registerError = 'No se encontro la sesion de caja para registrar la venta.';
      return;
    }

    if (this.saleDetails.length === 0) {
      this.registerError = 'Agrega al menos un producto al detalle de venta.';
      return;
    }

    if (!this.selectedSaleTypeId) {
      this.registerError = 'Selecciona un tipo de venta.';
      return;
    }

    if (this.isSelectedSaleTypeCreditWithoutClient()) {
      this.registerError = 'Para una venta a credito debes seleccionar un cliente diferente de Consumidor final.';
      this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
      return;
    }

    if (this.selectedSaleTypeIsCredit && this.exceedsSelectedClientCreditLimit() && !this.creditAuthorizationReady) {
      this.creditAuthorizationOpen = true;
      this.registerError = `La venta supera el limite de credito (Bs ${this.formatCurrency(this.getSelectedClientCreditLimit())}). Requiere autorizacion de un administrador.`;
      return;
    }

    if (this.salePaymentRows.some((row) => this.isQrRow(row) && !row.qrConfirmado)) {
      this.registerError = 'Confirma el pago por QR cuando el cliente haya escaneado el codigo.';
      return;
    }

    if (this.salePaymentRows.some((row) => this.isCardRow(row) && !row.tarjeta?.autorizacion)) {
      this.registerError = 'Autoriza el cobro con tarjeta antes de registrar la venta.';
      return;
    }

    const paymentRows = this.salePaymentRows
      .map((row) => ({
        id_metodo_pago: row.metodoPagoId ?? null,
        monto: this.normalizeMoney(row.monto),
        referencia_billetera: this.isQrRow(row) ? row.cobroId : undefined,
      }))
      .filter((row) => row.id_metodo_pago !== null || row.monto > 0);

    const paymentRow = paymentRows[0];
    const idMetodoPago = paymentRow?.id_metodo_pago ?? null;

    if (!idMetodoPago) {
      this.registerError = 'Selecciona un metodo de pago.';
      return;
    }

    if (paymentRows.some((row) => !row.id_metodo_pago)) {
      this.registerError = 'Selecciona un metodo de pago en cada registro de pago.';
      return;
    }

    if (paymentRows.some((row) => row.monto <= 0)) {
      this.registerError = 'Ingresa un monto mayor a 0 en cada registro de pago.';
      return;
    }

    const paymentMethodIds = paymentRows.map((row) => row.id_metodo_pago);
    const uniquePaymentMethodIds = new Set(paymentMethodIds);

    if (uniquePaymentMethodIds.size !== paymentMethodIds.length) {
      this.registerError = 'No puedes repetir el mismo metodo de pago.';
      return;
    }

    const paymentTotal = this.roundCurrency(paymentRows.reduce((sum, row) => sum + row.monto, 0));

    if (paymentTotal !== this.total) {
      this.registerError = `La suma de los pagos debe ser igual al total: Bs ${this.formatCurrency(this.total)}.`;
      return;
    }

    const payload: CreateSaleRequest = {
      id_tipo_venta: this.selectedSaleTypeId,
      id_cliente: this.selectedClientId,
      id_metodo_pago: idMetodoPago,
      factura_linea: this.onlineInvoice && this.hasPermission("invoice:issue"),
      ...this.invoiceBuyer(),
      subtotal: this.subtotal,
      descuento_total: this.discountTotal,
      total: this.total,
      estado: 'Pendiente',
      pagos: paymentRows.map((row) => ({
        id_metodo_pago: Number(row.id_metodo_pago),
        monto: row.monto,
        ...(row.referencia_billetera ? { referencia_billetera: row.referencia_billetera } : {}),
      })),
      detalles: this.buildSaleDetailPayload(),
      id_pedido: this.loadedOrderId,
    };

    this.chargingSale = true;

    this.cashRegisterService.registrarVentaSesionCaja(this.cashRegisterSessionId, payload).subscribe({
      next: (response) => {
        this.registerMessage = `Venta registrada correctamente con el ID ${response.id_venta}.`;
        this.lastInvoiceId = response.id_factura ?? null;
        if (this.lastInvoiceId) this.registerMessage += ' Factura emitida (SIAT simulado).';
        this.resetSaleForm();
        this.closePaymentTab();
        this.activeTab = 'register';
        this.chargingSale = false;
        this.cargarEfectivoEsperado();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.chargingSale = false;
        this.registerError = apiErrorMessage(error, 'No se pudo registrar la venta.');
        this.registerFieldErrors = this.fieldErrors(error);
        this.cdr.detectChanges();
      },
    });
  }

  protected cancelarPago(): void {
    this.salePaymentRows.filter((row) => row.cobroId).forEach((row) => this.discardQr(row));
    this.registerError = '';
    this.registerFieldErrors = [];
    this.closePaymentTab();
    this.activeTab = 'register';
  }

  private openPaymentTab(): void {
    this.showPaymentTab = true;
    this.salePaymentRows = [this.createPaymentRow(undefined, this.total)];
    this.activeTab = 'payment';
  }

  private closePaymentTab(): void {
    this.showPaymentTab = false;
    this.salePaymentRows = [];
  }

  private resetSaleForm(): void {
    this.saleDetails = [];
    this.recommendedProducts = [];
    this.recommendedProductsError = '';
    this.loadingRecommendedProducts = false;
    this.searchTerm = '';
    this.selectedClientId = null;
    this.pendingOrderClientId = null;
    this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
    this.onlineInvoice = false;
    this.invoiceDocType = 1;
    this.invoiceDocNumber = '';
    this.invoiceBusinessName = '';
    this.saleDiscount = 0;
    this.loadedOrderId = null;
  }

  private invoiceBuyer(): Partial<CreateSaleRequest> {
    if (!this.onlineInvoice) return {};
    return {
      codigo_tipo_documento: this.invoiceDocType,
      numero_documento: this.invoiceDocNumber.trim() || null,
      razon_social: this.invoiceBusinessName.trim() || null,
    };
  }

  protected printLastInvoice(): void {
    if (!this.lastInvoiceId) return;
    this.invoicePrint.print(this.lastInvoiceId).catch((error) => {
      this.registerError = error?.error?.detail ?? error?.message ?? 'No se pudo generar la factura.';
      this.cdr.detectChanges();
    });
  }

  private createPaymentRow(id = this.nextPaymentRowId++, monto: number | null = null): SalePaymentRow {
    return {
      id,
      metodoPagoId: this.getFirstAvailablePaymentMethodId(id),
      monto,
    };
  }

  private buildSaleDetailPayload(): CreateSaleRequest['detalles'] {
    return this.saleDetails.map((item) => ({
      id_producto: item.idProducto,
      cantidad: item.cantidad,
      precio_unitario: this.effectivePrice(item),
      descuento: this.getItemDiscountAmount(item),
      subtotal: this.getItemLineTotal(item),
      descripcion: 'Venta de mostrador',
    }));
  }

  private getFirstAvailablePaymentMethodId(rowId?: number): number | null {
    const usedMethodIds = new Set(
      this.salePaymentRows
        .filter((row) => row.id !== rowId)
        .map((row) => row.metodoPagoId)
        .filter((id): id is number => id !== null),
    );

    return this.paymentMethods.find((method) => !usedMethodIds.has(method.id))?.id ?? null;
  }

  private getRemainingPaymentAmount(): number {
    const paid = this.salePaymentRows.reduce((sum, row) => sum + this.normalizeMoney(row.monto), 0);
    return this.roundCurrency(Math.max(this.total - paid, 0));
  }

  protected formatCurrency(value: number): string {
    return this.roundCurrency(value).toFixed(2);
  }

  protected trackProduct(_: number, product: ProductSearchItem): number {
    return product.idProducto;
  }

  private loadRecommendedProducts(): void {
    const productIds = this.saleDetails.map((item) => item.idProducto);

    this.recommendedProductsError = '';

    if (productIds.length === 0) {
      this.recommendedProducts = [];
      this.loadingRecommendedProducts = false;
      this.cdr.detectChanges();
      return;
    }

    this.loadingRecommendedProducts = true;
    this.recommendedProducts = [];
    this.cdr.detectChanges();

    this.apiService
      .post<RecommendedProductsResponse, { id_productos: number[] }>('/api/reportes/recomendar-productos', {
        id_productos: productIds,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          // Solo productos con stock en esta sucursal, maximo 3 (HU-26).
          this.recommendedProducts = (response.recomendaciones || [])
            .map((product) => this.mapRecommendedProduct(product))
            .filter((product) => product.stock > 0)
            .slice(0, 3);
          this.loadingRecommendedProducts = false;
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.recommendedProducts = [];
          this.loadingRecommendedProducts = false;
          this.recommendedProductsError = error?.error?.detail ?? 'No se pudieron cargar los productos recomendados.';
          this.cdr.detectChanges();
        },
      });
  }

  protected trackDetail(_: number, item: SaleDetailItem): number {
    return item.idProducto;
  }

  protected trackClient(_: number, client: ClientOption): number {
    return client.id;
  }

  protected trackMovementType(_: number, movementType: CashMovementTypeOption): number {
    return movementType.id;
  }

  protected trackMovementItem(_: number, movementItem: CashMovementItem): number {
    return movementItem.id;
  }

  protected registrarMovimientoCaja(event: SubmitEvent): void {
    event.preventDefault();
    this.limpiarMensajesMovimientoCaja();

    if (!this.hasPermission('cash_movement:create')) {
      this.movementError = 'No tienes permiso para registrar movimientos de caja.';
      return;
    }

    const concepto = this.movementConcept.trim();
    const monto = Number(this.movementAmount);
    const tipoMovimientoId = Number(this.selectedMovementTypeId);
    const metodoPagoId = Number(this.selectedPaymentMethodId);

    if (!concepto || !monto || monto <= 0 || !tipoMovimientoId || !metodoPagoId) {
      this.movementError = 'Completa concepto, monto, tipo de movimiento y metodo de pago.';
      return;
    }

    if (!this.cashRegisterSessionId) {
      this.movementError = 'No se encontro la sesion de caja para registrar el movimiento.';
      return;
    }

    const tipoMovimiento = this.movementTypes.find((movementType) => movementType.id === tipoMovimientoId);

    this.savingMovement = true;

    this.cashRegisterService
      .crearMovimientoCajaSesion(this.cashRegisterSessionId, {
        concepto,
        monto: this.roundCurrency(monto),
        id_tipo_movimiento_caja: tipoMovimientoId,
        id_metodo_pago: metodoPagoId,
      })
      .subscribe({
        next: () => {
          this.movementMessage = 'Movimiento de caja registrado correctamente.';
          this.movementConcept = '';
          this.movementAmount = null;
          this.selectedMovementTypeId = this.movementTypes[0]?.id ?? null;
          this.movementActiveTab = 'list';
          this.savingMovement = false;
          this.loadMovementItems();
          this.cargarEfectivoEsperado();
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.savingMovement = false;
          this.movementError = error?.error?.detail ?? 'No se pudo registrar el movimiento de caja.';
          this.cdr.detectChanges();
        },
      });
  }

  private loadSalesViewData(): void {
    if (this.hasPermission("sale:create")) this.loadRegisterData();
    this.loadSaleTypes();
    this.loadPaymentMethods();
    this.loadMovementTypes();
    this.cargarEfectivoEsperado();

    if (this.activeTab === 'history') {
      this.loadSalesHistory();
    }
  }

  private loadRegisterData(): void {
    if(!this.companyPermissionsService.hasField('product','read','precio') || !this.companyPermissionsService.hasField('product','read','nombre')) {
      this.products=[];
      this.registerDataReady = true;
      this.registerError='La búsqueda de ventas requiere acceso al nombre y precio de los productos.';
      return;
    }
    this.loadingRegisterData = true;
    this.registerDataReady = false;
    this.registerError = '';
    this.registerFieldErrors = [];

    if (!this.companyId || !this.branchId) {
      this.loadingRegisterData = false;
      this.registerDataReady = true;
      this.registerError = 'No se encontro la empresa o sucursal para cargar ventas.';
      return;
    }

    this.registerDataReady = false;
    this.productService.getStockSucursal(this.companyId, this.branchId).subscribe({
      next: (stock) => {
        this.catalogProducts = stock
          .filter(item => typeof item.precio==='number' && typeof item.nombre_producto==='string')
          .map((item) => this.mapStockProduct(item));
        this.products = this.catalogProducts.filter((product) => product.stock > 0);
        this.loadingRegisterData = false;
        this.registerDataReady = true;
        this.loadClients();
        this.tryLoadPendingOrder();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.catalogProducts = [];
        this.products = [];
        this.loadingRegisterData = false;
        this.registerDataReady = true;
        this.registerError = apiErrorMessage(error, 'No se pudieron cargar los productos de la sucursal.');
        this.tryLoadPendingOrder();
        this.cdr.detectChanges();
      },
    });
  }

  private tryLoadPendingOrder(): void {
    if (this.pendingPedidoId == null || this.loadingRegisterData || !this.registerDataReady) return;
    if (!this.companyId || !this.branchId) return;

    const orderId = this.pendingPedidoId;
    this.pendingPedidoId = null;
    this.orderService
      .getOrderForPOS(parseInt(this.companyId, 10), parseInt(this.branchId, 10), orderId)
      .subscribe({
        next: (order) => this.loadOrderIntoCart(order),
        error: (err) => {
          console.error('Error loading order from queryParam', err);
          this.registerError =
            err?.error?.detail ?? `No se pudo cargar el pedido #${orderId} en el POS.`;
          this.cdr.detectChanges();
        },
      });
  }

  private loadClients(): void {
    if (!this.companyId || !this.hasPermission("client:read")) {
      this.clients = [];
      return;
    }

    this.companyService.getClientesEmpresa(this.companyId).subscribe({
      next: (clients) => {
        this.clients = clients.map((client) => this.mapClient(client));
        this.reapplyPendingOrderClient();
        this.cdr.detectChanges();
      },
      error: () => {
        this.clients = [];
        this.reapplyPendingOrderClient();
        this.cdr.detectChanges();
      },
    });
  }

  private reapplyPendingOrderClient(): void {
    if (this.pendingOrderClientId == null) return;
    this.selectedClientId = this.pendingOrderClientId;
    this.onClientSelected(this.pendingOrderClientId);
  }

  private loadSaleTypes(): void {
    this.loadingSaleTypes = true;

    this.productService.getTiposVenta().subscribe({
      next: (saleTypes) => {
        this.saleTypes = saleTypes.map((saleType) => this.mapSaleType(saleType));
        this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
        this.loadingSaleTypes = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.saleTypes = [];
        this.selectedSaleTypeId = null;
        this.loadingSaleTypes = false;
        this.cdr.detectChanges();
      },
    });
  }

  private loadMovementTypes(): void {
    if (!this.hasPermission("cash_movement:create")) return;
    this.loadingMovementTypes = true;
    this.movementError = '';

    this.companyService.getTiposMovimientoCaja().subscribe({
      next: (types) => {
        this.movementTypes = types.filter((type) => ["INGRESO","EGRESO","AJUSTE_POSITIVO","AJUSTE_NEGATIVO"].includes(type.nombre.toUpperCase())).map((type) => this.mapMovementType(type));
        this.selectedMovementTypeId = this.movementTypes[0]?.id ?? null;
        this.loadingMovementTypes = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.movementTypes = [];
        this.selectedMovementTypeId = null;
        this.loadingMovementTypes = false;
        this.movementError = 'No se pudieron cargar los tipos de movimiento de caja.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadMovementItems(): void {
    if (!this.hasPermission("cash_movement:read")) { this.movementItems=[]; return; }
    if (!this.cashRegisterSessionId) {
      this.movementItems = [];
      this.loadingMovementItems = false;
      this.movementError = 'No se encontro la sesion de caja para cargar movimientos.';
      this.cdr.detectChanges();
      return;
    }

    this.loadingMovementItems = true;

    this.cashRegisterService.getMovimientosCajaSesion(this.cashRegisterSessionId).subscribe({
      next: (movements) => {
        this.movementItems = movements.map((movement) => this.mapCashMovement(movement));
        this.loadingMovementItems = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.movementItems = [];
        this.loadingMovementItems = false;
        this.movementError = error?.error?.detail ?? 'No se pudieron cargar los movimientos de caja.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadPaymentMethods(): void {
    this.loadingPaymentMethods = true;

    this.productService.getMetodosPago().subscribe({
      next: (methods) => {
        this.paymentMethods = methods.map((method) => this.mapPaymentMethod(method));
        this.selectedPaymentMethodId = this.paymentMethods[0]?.id ?? null;
        if (this.salePaymentRows.length > 0) {
          this.salePaymentRows = this.salePaymentRows.map((row) => ({
            ...row,
            metodoPagoId: row.metodoPagoId ?? this.getFirstAvailablePaymentMethodId(row.id),
          }));
        }
        this.loadingPaymentMethods = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.paymentMethods = [];
        this.selectedPaymentMethodId = null;
        this.loadingPaymentMethods = false;
        this.cdr.detectChanges();
      },
    });
  }

  private loadSalesHistory(): void {
    if (!this.hasPermission("sale:read")) { this.salesHistory=[]; return; }
    if (!this.cashRegisterSessionId) {
      this.salesHistory = [];
      this.loadingSalesHistory = false;
      this.salesHistoryError = 'No se encontro la sesion de caja para cargar el historial de ventas.';
      this.cdr.detectChanges();
      return;
    }

    this.loadingSalesHistory = true;
    this.salesHistoryError = '';

    this.cashRegisterService.getVentasSesionCaja(this.cashRegisterSessionId).subscribe({
      next: (sales) => {
        this.salesHistory = sales.map((sale) => this.mapSaleHistory(sale)).reverse();
        this.loadingSalesHistory = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.salesHistory = [];
        this.loadingSalesHistory = false;
        this.salesHistoryError = error?.error?.detail ?? 'No se pudo cargar el historial de ventas.';
        this.cdr.detectChanges();
      },
    });
  }

  private mapStockProduct(item: ProductStockRecord): ProductSearchItem {
    return {
      idProducto: item.id_producto,
      nombre: item.nombre_producto ?? `Producto #${item.id_producto}`,
      unidadMedida: item.unidad_medida ?? '—',
      precio: Number(item.precio ?? 0),
      stock: Number(item.cantidad ?? 0),
      codigo: this.stringifyOptional(item.codigo ?? item.codigo_producto ?? item.id_producto),
      codigoBarras: this.stringifyOptional(item.codigo_barra),
      imagen: item.imagen ?? null,
    };
  }

  private mapRecommendedProduct(product: RecommendedProductRecord): ProductSearchItem {
    const id = Number(product.id_producto);
    // El stock y la imagen se toman de la sucursal actual cuando el producto ya esta cargado.
    const local = this.products.find((p) => p.idProducto === id);
    return {
      idProducto: id,
      nombre: product.nombre ?? 'Producto sin nombre',
      unidadMedida: product.unidad_medida ?? local?.unidadMedida ?? 'unidad',
      precio: Number(product.precio ?? local?.precio ?? 0),
      stock: local ? local.stock : Number(product.stock ?? 0),
      codigo: this.stringifyOptional(product.codigo ?? id),
      codigoBarras: this.stringifyOptional(product.codigo_barras),
      imagen: product.imagen ?? local?.imagen ?? null,
      confianza: product.confianza ?? null,
    };
  }

  private mapClient(client: ClientRole): ClientOption {
    return {
      id: client.cliente.id_cliente,
      nombre: client.usuario.persona?.nombre_completo ?? 'Sin nombre',
      correo: client.usuario.email,
      categoriaId: client.cliente?.id_categoria_cliente ?? null,
      saldoAFavor: Number((client.cliente as { saldo_a_favor?: number } | undefined)?.saldo_a_favor ?? 0),
    };
  }

  protected selectedClientCategory: ClientCategoryResponse | null = null;

  protected onClientSelected(clientId: number | null): void {
    this.selectedClientCategory = null;

    if (!clientId) {
      this.onlineInvoice = false;
    }

    this.ensureSelectedSaleTypeIsAllowed();

    if (!clientId) {
      this.cdr.detectChanges();
      return;
    }

    const client = this.clients.find((c) => c.id === clientId);
    const categoriaId = client?.categoriaId ?? null;

    if (!categoriaId) {
      this.cdr.detectChanges();
      return;
    }

    // Fetch categories and find the matching one
    this.companyService.getCategoriasCliente(this.companyId).subscribe({
      next: (categories) => {
        if (this.selectedClientId !== clientId) {
          return;
        }

        const found = categories.find((cat) => cat.id_categoria_cliente === categoriaId) ?? null;
        this.selectedClientCategory = found;

        // If a category with a base discount exists, apply it to current sale details
        const pct = this.getClientDiscountPercentage();
        if (pct > 0) {
          this.applyClientDiscountToSaleDetails(pct);
        }

        this.cdr.detectChanges();
      },
      error: () => {
        this.selectedClientCategory = null;
        this.cdr.detectChanges();
      },
    });
  }

  private getClientDiscountPercentage(): number {
    const raw = this.selectedClientCategory?.descuento_base ?? null;
    const value = Number(raw ?? 0);
    return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
  }

  private getSelectedClientCreditLimit(): number {
    const value = Number(this.selectedClientCategory?.limite_credito ?? 0);
    return Number.isFinite(value) ? Math.max(0, value) : 0;
  }

  private exceedsSelectedClientCreditLimit(): boolean {
    const creditLimit = this.getSelectedClientCreditLimit();
    return creditLimit > 0 && this.total > creditLimit;
  }

  private ensureSelectedSaleTypeIsAllowed(): void {
    if (this.isSelectedSaleTypeCreditWithoutClient()) {
      this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
    }
  }

  private isSelectedSaleTypeCreditWithoutClient(): boolean {
    const saleType = this.saleTypes.find((type) => type.id === this.selectedSaleTypeId);
    return Boolean(saleType && this.isSaleTypeDisabled(saleType));
  }

  private getDefaultAllowedSaleTypeId(): number | null {
    const firstAllowed = this.saleTypes.find((saleType) => !this.isSaleTypeDisabled(saleType));
    return firstAllowed?.id ?? null;
  }

  private applyClientDiscountToSaleDetails(percentage: number): void {
    if (!percentage || percentage <= 0) return;

    this.saleDetails = this.saleDetails.map((item) => {
      const updated: SaleDetailItem = { ...item };
      updated.descuentoTipo = 'percentage';
      // set descuento as percentage number (bounded by updateItemDiscount logic below)
      updated.descuento = percentage;
      // ensure discount value is normalized
      this.updateItemDiscount(updated, updated.descuento);
      return updated;
    });
  }

  private mapSaleType(saleType: TipoVenta): SaleTypeOption {
    return {
      id: saleType.id_tipo_venta,
      nombre: saleType.nombre,
      descripcion: saleType.descripcion ?? '',
    };
  }

  private mapMovementType(movementType: CashRegisterMovementType): CashMovementTypeOption {
    return {
      id: movementType.id_tipo_movimiento_caja,
      nombre: movementType.nombre,
      descripcion: movementType.descripcion ?? '',
    };
  }

  private mapPaymentMethod(method: MetodoPago): PaymentMethodOption {
    return {
      id: method.id_metodo_pago,
      nombre: method.nombre,
      descripcion: method.descripcion ?? '',
    };
  }

  private mapCashMovement(
    movement: CashRegisterMovementResponse | CashRegisterMovementListItem,
    defaultTipoMovimiento?: string,
    defaultMetodoPago?: string,
  ): CashMovementItem {
    const tipoMovimientoId = movement.id_tipo_movimiento_caja;
    const metodoPagoId = movement.id_metodo_pago;

    return {
      id: movement.id_movimiento_caja,
      concepto: movement.concepto,
      monto: Number(movement.monto ?? 0),
      tipoMovimientoId,
      tipoMovimiento:
        defaultTipoMovimiento ??
        this.movementTypes.find((type) => type.id === tipoMovimientoId)?.nombre ??
        `Tipo ${tipoMovimientoId}`,
      metodoPagoId,
      metodoPago:
        defaultMetodoPago ??
        (metodoPagoId
          ? this.paymentMethods.find((method) => method.id === metodoPagoId)?.nombre ?? `Metodo ${metodoPagoId}`
          : 'Sin metodo'),
      fecha: this.formatMovementDate(movement.fecha),
    };
  }

  private mapSaleHistory(sale: SaleHistoryResponse): SaleHistoryRow {
    const rawSale = sale as SaleHistoryResponse & {
      tipo_venta?: { nombre?: string };
      tipoVenta?: { nombre?: string };
      cliente?: { nombre?: string };
    };

    const salePayments = sale.pagos ?? [];
    const saleDetails = sale.detalles ?? [];

    const paymentLabels = salePayments.length > 0
      ? salePayments
          .map((payment) => {
            const methodName =
              payment.metodo_pago?.nombre ??
              this.paymentMethods.find((method) => method.id === payment.id_metodo_pago)?.nombre;
            const amount = this.formatCurrency(Number(payment.monto ?? 0));
            return `${methodName ?? `Metodo ${payment.id_metodo_pago}`}: Bs ${amount}`;
          })
          .join(' | ')
      : 'Sin pagos';

    const tipoVentaId =
      sale.id_tipo_venta ??
      (sale as unknown as Record<string, number | string | undefined>)['id_tipo_venta'] ??
      (sale as unknown as Record<string, number | string | undefined>)['idTipoVenta'] ??
      'Sin tipo';

    const saleTypeLabel =
      sale.tipo_venta_nombre ??
      rawSale.tipo_venta?.nombre ??
      rawSale.tipoVenta?.nombre ??
      this.saleTypes.find((type) => type.id === Number(tipoVentaId))?.nombre ??
      `Tipo ${tipoVentaId}`;

    const matchedClient = this.clients.find((c) => c.id === Number(sale.id_cliente));
    const client = matchedClient
      ? matchedClient.nombre
      : (sale.id_cliente ? `Cliente #${sale.id_cliente}` : 'Consumidor final');

    const paymentMethodIds = salePayments.length > 0
      ? salePayments.map((payment) => String(payment.id_metodo_pago)).join(' | ')
      : 'Sin metodo';

    return {
      id: sale.id_venta,
      tipoVentaId,
      fecha: this.formatDateOnly(sale.fecha),
      tipoVenta: saleTypeLabel,
      cliente: client,
      clienteId: sale.id_cliente ?? null,
      usuarioId: sale.id_usuario,
      cajaSesionId: sale.id_caja_sesion,
      total: Number(sale.total ?? 0),
      estado: sale.estado,
      estadoFactura: sale.estado_siat ?? null,
      metodosPago: paymentLabels,
      metodosPagoIds: paymentMethodIds,
      articulos: saleDetails.reduce((total, detail) => total + Number(detail.cantidad ?? 0), 0),
      subtotal: Number(sale.subtotal ?? 0),
      descuentoTotal: Number(sale.descuento_total ?? 0),
      detalles: saleDetails,
    };
  }

  private productMatchesSearch(product: ProductSearchItem, term: string): boolean {
    const normalizedTerm = this.normalizeSearchTerm(term);
    const normalizedBarcodeTerm = this.normalizeBarcode(normalizedTerm);
    const searchable = [
      product.nombre,
      product.codigo,
      product.codigoBarras,
      String(product.idProducto),
    ]
      .map((value) => this.normalizeSearchTerm(value))
      .join(' ');

    if (searchable.includes(normalizedTerm)) {
      return true;
    }

    if (!normalizedBarcodeTerm) {
      return false;
    }

    return this.normalizeBarcode(searchable).includes(normalizedBarcodeTerm);
  }

  private normalizeSearchTerm(value: string): string {
    return value.trim().toLowerCase();
  }

  private normalizeBarcode(value: string): string {
    return value.replace(/\D+/g, '');
  }

  private normalizeMoney(value: number | string | null): number {
    return Math.max(0, this.roundCurrency(Number(value) || 0));
  }

  private getItemSubtotal(item: SaleDetailItem): number {
    return this.roundCurrency(this.effectivePrice(item) * item.cantidad);
  }

  private roundCurrency(value: number): number {
    return Math.round(value * 100) / 100;
  }

  private formatMovementDate(date: string): string {
    const parsedDate = new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
      return date;
    }

    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(parsedDate);
  }

  private formatDateOnly(date: string): string {
    // If the API returns an ISO-like date string without timezone (e.g. "2026-05-24T18:30:00"),
    // extract the date part directly to avoid timezone surprises and always show dd/mm/yyyy.
    const isoMatch = String(date).match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (isoMatch) {
      const [, year, month, day] = isoMatch;
      return `${day}/${month}/${year}`;
    }

    const parsedDate = new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
      return date;
    }

    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(parsedDate);
  }

  private resolveViewMode(section: string | null): SessionViewMode {
    return section === 'movimientos' ? 'movements' : 'sales';
  }

  private resetViewState(): void {
    this.clearSalesMessages();
    this.limpiarMensajesMovimientoCaja();
    this.activeTab = this.hasPermission('sale:create') ? 'register' : 'history';
    this.movementActiveTab = this.hasPermission('cash_movement:create') ? 'register' : 'list';
    this.closePaymentTab();
  }

  private clearSalesMessages(): void {
    this.registerError = '';
    this.registerFieldErrors = [];
    this.registerMessage = '';
  }

  private limpiarMensajesMovimientoCaja(): void {
    this.movementError = '';
    this.movementMessage = '';
  }

  private stringifyOptional(value: string | number | null | undefined): string {
    return value === null || value === undefined ? '' : String(value);
  }

  protected get canSetSalePrice(): boolean {
    return this.companyPermissionsService.hasField('sale', 'create', 'precio_unitario');
  }
  protected effectivePrice(item: SaleDetailItem): number {
    return this.canSetSalePrice ? item.precio : (item.precioCatalogo ?? item.precio);
  }
  protected updateItemPrice(item: SaleDetailItem, value: number | string | null): void {
    if (!this.canSetSalePrice) return;
    const price=Number(value);
    if (value===null || value==='' || !Number.isFinite(price) || price<0 || price>9999999999.99) {
      this.registerError='Ingresa un precio unitario válido.'; return;
    }
    item.precio=this.roundCurrency(price);
    this.updateItemDiscount(item,item.descuento);
    this.registerError='';
  }

  protected get canDiscount(): boolean {
    return this.companyPermissionsService.hasField('sale', 'create', 'descuento') && this.hasPermission('sale:discount');
  }

  protected hasPermission(permission: string): boolean {
    return this.companyPermissionsService.permissions()[permission] === true;
  }

  protected openLoadOrderModal(): void {
    this.isLoadOrderModalOpen = true;
    this.loadingOrders = true;
    this.availableOrders = [];

    const companyIdNum = parseInt(this.companyId, 10);
    this.orderService.getOrdersByCompany(companyIdNum).subscribe({
      next: (orders) => {
        // Filter orders for this branch that are ready or in prep
        const branchIdNum = parseInt(this.branchId, 10);
        // Backend solo convierte pedidos en listo_para_recoger.
        this.availableOrders = orders.filter(
          (o) =>
            o.id_sucursal === branchIdNum &&
            o.estado === 'listo_para_recoger' &&
            !o.id_venta,
        );
        this.loadingOrders = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading orders', err);
        this.loadingOrders = false;
        this.cdr.detectChanges();
      }
    });
  }

  protected closeLoadOrderModal(): void {
    this.isLoadOrderModalOpen = false;
  }

  protected loadOrderIntoCart(order: Order): void {
    if (order.estado !== 'listo_para_recoger') {
      this.registerError = `El pedido #${order.id_pedido} debe estar en "listo_para_recoger" (estado: ${order.estado}).`;
      this.closeLoadOrderModal();
      this.cdr.detectChanges();
      return;
    }
    if (order.id_venta) {
      this.registerError = `El pedido #${order.id_pedido} ya fue convertido en venta.`;
      this.closeLoadOrderModal();
      this.cdr.detectChanges();
      return;
    }
    if (this.loadingRegisterData) {
      this.pendingPedidoId = order.id_pedido;
      this.registerMessage = `Cargando productos para el pedido #${order.id_pedido}…`;
      this.closeLoadOrderModal();
      this.cdr.detectChanges();
      return;
    }
    if (this.catalogProducts.length === 0 && this.products.length === 0) {
      this.registerError =
        `No hay productos en esta sucursal para cargar el pedido #${order.id_pedido}.`;
      this.closeLoadOrderModal();
      this.cdr.detectChanges();
      return;
    }

    this.resetSaleForm();
    this.loadedOrderId = order.id_pedido;
    // Pedido de app → emitir factura SIAT al cobrar (si el cajero tiene permiso).
    this.onlineInvoice = this.hasPermission('invoice:issue');
    this.pendingOrderClientId = order.id_cliente ?? null;
    this.selectedClientId = this.pendingOrderClientId;
    if (this.pendingOrderClientId != null) {
      this.onClientSelected(this.pendingOrderClientId);
    }

    const missing: number[] = [];
    for (const detalle of order.detalles) {
      const productFound =
        this.catalogProducts.find((p) => p.idProducto === detalle.id_producto) ??
        this.products.find((p) => p.idProducto === detalle.id_producto);
      if (productFound) {
        this.upsertOrderLine(productFound, detalle.cantidad);
      } else {
        missing.push(detalle.id_producto);
      }
    }

    if (missing.length) {
      this.registerError =
        `Pedido #${order.id_pedido}: no se encontraron producto(s) ${missing.join(', ')}. ` +
        'El carrito quedó incompleto; no se podrá facturar hasta igualar el pedido.';
    } else {
      this.registerMessage = `Pedido #${order.id_pedido} cargado. Cliente y líneas del pedido aplicados.`;
      this.registerError = '';
    }

    this.closeLoadOrderModal();
    this.cdr.detectChanges();
  }

  /** Carga líneas de pedido sin el bloqueo de stock de addProduct (el backend valida al vender). */
  private upsertOrderLine(product: ProductSearchItem, cantidad: number): void {
    const qty = Math.max(1, Math.floor(cantidad) || 1);
    const existing = this.saleDetails.find((item) => item.idProducto === product.idProducto);
    const clientDiscount = this.getClientDiscountPercentage();
    if (existing) {
      existing.cantidad = qty;
      return;
    }
    this.saleDetails = [
      ...this.saleDetails,
      {
        ...product,
        precioCatalogo: product.precio,
        cantidad: qty,
        descuento: clientDiscount > 0 ? clientDiscount : 0,
        descuentoTipo: clientDiscount > 0 ? 'percentage' : 'fixed',
      },
    ];
  }
}
