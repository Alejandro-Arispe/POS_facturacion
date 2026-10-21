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
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
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
}

interface RecommendedProductsResponse {
  productos_analizados: number[];
  recomendaciones: ProductSearchItem[];
}

interface SaleDetailItem extends ProductSearchItem {
  cantidad: number;
  descuento: number;
  descuentoTipo: DiscountType;
}

interface ClientOption {
  id: number;
  nombre: string;
  correo: string;
  categoriaId?: number | null;
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
}

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
  imports: [MoneyPipe, CurrencySymbolPipe, CommonModule, FormsModule, Navbar, Sidebar],
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
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected readonly cashRegisterId = this.route.snapshot.paramMap.get('cashRegisterId') ?? '';
  protected readonly maxPaymentRows = 3;
  protected cashRegisterSessionId = this.route.snapshot.queryParamMap.get('sessionId') ?? '';

  protected viewMode: SessionViewMode = this.resolveViewMode(this.route.snapshot.queryParamMap.get('section'));
  protected sidebarActiveItemLabel = this.viewMode === 'movements' ? 'Movimientos' : 'Ventas';

  protected activeTab: SalesTab = this.hasPermission('VENTA_CREAR') ? 'register' : 'history';
  protected movementActiveTab: MovementTab = this.hasPermission('MOVIMIENTO_REGISTRAR') ? 'register' : 'list';
  protected searchTerm = '';
  protected selectedClientId: number | null = null;
  protected selectedSaleTypeId: number | null = null;
  protected onlineInvoice = false;
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

  protected openSaleDetailModal(sale: SaleHistoryRow): void {
    this.selectedSaleDetail = sale;
    this.isSaleDetailModalOpen = true;
  }

  protected closeSaleDetailModal(): void {
    this.selectedSaleDetail = null;
    this.isSaleDetailModalOpen = false;
  }

  ngOnInit(): void {
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

      // Check if pedido_id was passed
      const pedidoIdParam = params.get('pedido_id');
      if (pedidoIdParam) {
        const orderIdNum = parseInt(pedidoIdParam, 10);
        this.orderService.getOrderForPOS(parseInt(this.companyId, 10), parseInt(this.branchId, 10), orderIdNum).subscribe({
          next: (order) => {
            this.loadOrderIntoCart(order);
          },
          error: (err) => {
            console.error('Error loading order from queryParam', err);
          }
        });
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
    if (tab === 'register' && !this.hasPermission('VENTA_CREAR')) {
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
    if (tab === 'register' && !this.hasPermission('MOVIMIENTO_REGISTRAR')) {
      return;
    }

    if (tab === 'list' && !this.hasPermission('MOVIMIENTO_VER')) {
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
      this.saleDetails.reduce((total, item) => total + item.precio * item.cantidad, 0),
    );
  }

  protected get detailDiscountTotal(): number {
    return this.roundCurrency(
      this.saleDetails.reduce((total, item) => total + this.getItemDiscountAmount(item), 0),
    );
  }

  protected get discountTotal(): number {
    return this.roundCurrency(Math.min(this.subtotal, this.detailDiscountTotal + this.normalizeMoney(this.saleDiscount)));
  }

  protected get total(): number {
    return this.roundCurrency(Math.max(this.subtotal - this.discountTotal, 0));
  }

  protected addProduct(product: ProductSearchItem): void {
    if (!this.hasPermission('VENTA_CREAR')) {
      this.registerError = 'No tienes permiso para registrar ventas.';
      return;
    }

    this.registerError = '';
    this.registerMessage = '';

    const existingItem = this.saleDetails.find((item) => item.idProducto === product.idProducto);

    if (existingItem) {
      this.updateQuantity(existingItem, existingItem.cantidad + 1);
      this.loadRecommendedProducts();
      return;
    }

    const clientDiscount = this.hasPermission('VENTA_DESCUENTO') ? this.getClientDiscountPercentage() : 0;

    this.saleDetails = [
      ...this.saleDetails,
      {
        ...product,
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
              if (this.filteredProducts.length === 1) {
                this.addProduct(this.filteredProducts[0]);
              }
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

  private stopScanner(): void {
    window.clearInterval(this.scanTimer);
    this.scanStream?.getTracks().forEach((track) => track.stop());
    this.scanStream = null;
    this.scannerOpen = false;
  }

  protected onSearchEnter(event: Event): void {
    event.preventDefault();

    if (this.filteredProducts.length === 1) {
      this.addProduct(this.filteredProducts[0]);
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
  }

  protected updateItemDiscount(item: SaleDetailItem, value: number | string): void {
    if (!this.hasPermission('VENTA_DESCUENTO')) {
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
    if (!this.hasPermission('VENTA_DESCUENTO')) {
      item.descuentoTipo = 'fixed';
      item.descuento = 0;
      return;
    }

    item.descuentoTipo = type;
    this.updateItemDiscount(item, item.descuento);
  }

  protected getItemDiscountAmount(item: SaleDetailItem): number {
    const itemSubtotal = this.getItemSubtotal(item);

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
    this.registerMessage = '';

    if (!this.hasPermission('VENTA_CREAR')) {
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

    if (this.selectedSaleTypeIsCredit && this.exceedsSelectedClientCreditLimit()) {
      this.registerError = `El total de la venta no puede superar el limite de credito de la categoria: Bs ${this.formatCurrency(this.getSelectedClientCreditLimit())}.`;
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
    return this.salePaymentRows.some((row) => row.id !== paymentRow.id && row.metodoPagoId === method.id);
  }

  protected updatePaymentAmount(row: SalePaymentRow, value: number | string | null): void {
    row.monto = this.normalizeMoney(value);
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
      factura_linea: this.onlineInvoice,
      subtotal: this.subtotal,
      descuento_total: this.discountTotal,
      total: this.total,
      estado: 'Pendiente',
      detalles: this.buildSaleDetailPayload(),
    };

    this.chargingSale = true;

    this.cashRegisterService.registrarVentaSesionCaja(this.cashRegisterSessionId, payload).subscribe({
      next: (response) => {
        this.registerMessage = `Venta a credito registrada correctamente con el ID ${response.id_venta}.`;
        this.resetSaleForm();
        this.closePaymentTab();
        this.activeTab = 'register';
        this.chargingSale = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.chargingSale = false;
        this.registerError = error?.error?.detail ?? 'No se pudo registrar la venta a credito.';
        this.cdr.detectChanges();
      },
    });
  }

  protected registrarPago(): void {
    this.registerError = '';

    if (!this.hasPermission('VENTA_CREAR')) {
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

    if (this.selectedSaleTypeIsCredit && this.exceedsSelectedClientCreditLimit()) {
      this.registerError = `El total de la venta no puede superar el limite de credito de la categoria: Bs ${this.formatCurrency(this.getSelectedClientCreditLimit())}.`;
      return;
    }

    const paymentRows = this.salePaymentRows
      .map((row) => ({
        id_metodo_pago: row.metodoPagoId ?? null,
        monto: this.normalizeMoney(row.monto),
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
      factura_linea: this.onlineInvoice,
      subtotal: this.subtotal,
      descuento_total: this.discountTotal,
      total: this.total,
      estado: 'Pendiente',
      pagos: paymentRows.map((row) => ({
        id_metodo_pago: Number(row.id_metodo_pago),
        monto: row.monto,
      })),
      detalles: this.buildSaleDetailPayload(),
      id_pedido: this.loadedOrderId,
    };

    this.chargingSale = true;

    this.cashRegisterService.registrarVentaSesionCaja(this.cashRegisterSessionId, payload).subscribe({
      next: (response) => {
        this.registerMessage = `Venta registrada correctamente con el ID ${response.id_venta}.`;
        this.resetSaleForm();
        this.closePaymentTab();
        this.activeTab = 'register';
        this.chargingSale = false;
        this.cargarEfectivoEsperado();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.chargingSale = false;
        this.registerError = error?.error?.detail ?? 'No se pudo registrar la venta.';
        this.cdr.detectChanges();
      },
    });
  }

  protected cancelarPago(): void {
    this.registerError = '';
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
    this.selectedSaleTypeId = this.getDefaultAllowedSaleTypeId();
    this.onlineInvoice = false;
    this.saleDiscount = 0;
    this.loadedOrderId = null;
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
      precio_unitario: item.precio,
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
          this.recommendedProducts = (response.recomendaciones || []).map((product) => this.mapRecommendedProduct(product));
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

    if (!this.hasPermission('MOVIMIENTO_REGISTRAR')) {
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
    this.loadRegisterData();
    this.loadSaleTypes();
    this.loadPaymentMethods();
    this.loadMovementTypes();
    this.cargarEfectivoEsperado();

    if (this.activeTab === 'history') {
      this.loadSalesHistory();
    }
  }

  private loadRegisterData(): void {
    this.loadingRegisterData = true;
    this.registerError = '';

    if (!this.companyId || !this.branchId) {
      this.loadingRegisterData = false;
      this.registerError = 'No se encontro la empresa o sucursal para cargar ventas.';
      return;
    }

    this.productService.getStockSucursal(this.companyId, this.branchId).subscribe({
      next: (stock) => {
        this.products = stock
          .map((item) => this.mapStockProduct(item))
          .filter((product) => product.stock > 0);
        this.loadingRegisterData = false;
        this.loadClients();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.products = [];
        this.loadingRegisterData = false;
        this.registerError = error?.error?.detail ?? 'No se pudieron cargar los productos de la sucursal.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadClients(): void {
    if (!this.companyId) {
      this.clients = [];
      return;
    }

    this.companyService.getClientesEmpresa(this.companyId).subscribe({
      next: (clients) => {
        this.clients = clients.map((client) => this.mapClient(client));
        this.cdr.detectChanges();
      },
      error: () => {
        this.clients = [];
        this.cdr.detectChanges();
      },
    });
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
    this.loadingMovementTypes = true;
    this.movementError = '';

    this.companyService.getTiposMovimientoCaja().subscribe({
      next: (types) => {
        this.movementTypes = types.map((type) => this.mapMovementType(type));
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
      nombre: item.nombre_producto,
      unidadMedida: item.unidad_medida,
      precio: Number(item.precio ?? 0),
      stock: Number(item.cantidad ?? 0),
      codigo: this.stringifyOptional(item.codigo ?? item.codigo_producto ?? item.id_producto),
      codigoBarras: this.stringifyOptional(item.codigo_barra),
    };
  }

  private mapRecommendedProduct(product: ProductSearchItem): ProductSearchItem {
    return {
      idProducto: Number(product.idProducto),
      nombre: product.nombre ?? 'Producto sin nombre',
      unidadMedida: product.unidadMedida ?? 'unidad',
      precio: Number(product.precio ?? 0),
      stock: Number(product.stock ?? 0),
      codigo: this.stringifyOptional(product.codigo ?? product.idProducto),
      codigoBarras: this.stringifyOptional(product.codigoBarras),
    };
  }

  private mapClient(client: ClientRole): ClientOption {
    return {
      id: client.cliente.id_cliente,
      nombre: client.usuario.persona?.nombre_completo ?? 'Sin nombre',
      correo: client.usuario.email,
      categoriaId: client.cliente?.id_categoria_cliente ?? null,
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
        if (pct > 0 && this.hasPermission('VENTA_DESCUENTO')) {
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
    return this.roundCurrency(item.precio * item.cantidad);
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
    this.activeTab = this.hasPermission('VENTA_CREAR') ? 'register' : 'history';
    this.movementActiveTab = this.hasPermission('MOVIMIENTO_REGISTRAR') ? 'register' : 'list';
    this.closePaymentTab();
  }

  private clearSalesMessages(): void {
    this.registerError = '';
    this.registerMessage = '';
  }

  private limpiarMensajesMovimientoCaja(): void {
    this.movementError = '';
    this.movementMessage = '';
  }

  private stringifyOptional(value: string | number | null | undefined): string {
    return value === null || value === undefined ? '' : String(value);
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
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
        this.availableOrders = orders.filter(
          (o) => o.id_sucursal === branchIdNum && (o.estado === 'listo_para_recoger' || o.estado === 'en_preparacion' || o.estado === 'confirmado')
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
    this.resetSaleForm();
    this.loadedOrderId = order.id_pedido;
    if (order.id_cliente) {
      this.selectedClientId = order.id_cliente;
    }
    
    // Add each detail to the cart if the product exists in this branch's stock
    for (const detalle of order.detalles) {
      const productFound = this.products.find(p => p.idProducto === detalle.id_producto);
      if (productFound) {
        // We simulate adding the product
        this.addProduct(productFound);
        // We set the correct quantity
        const addedItem = this.saleDetails.find(d => d.idProducto === detalle.id_producto);
        if (addedItem) {
          // Adjust quantity
          addedItem.cantidad = detalle.cantidad;
        }
      }
    }
    
    this.closeLoadOrderModal();
    this.cdr.detectChanges();
  }
}
