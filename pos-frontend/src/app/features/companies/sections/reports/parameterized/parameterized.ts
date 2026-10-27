import { watchReadPermissions } from '../../../../../core/services/watch-read-permissions';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';
import { ChangeDetectorRef, Component, ElementRef, OnInit, inject } from '@angular/core';
import { PreferencesService } from '../../../../../core/services/preferences.service';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';
import { Branch, CompanyService, CompanyStaffMember, CashRegisterMovementType } from '../../../../../core/services/company.service';
import { ProductService, TipoVenta, MetodoPago, Producto, TipoMovimiento, CategoriaProducto } from '../../../../../core/services/product.service';
import {
  CashRegisterListResponse,
  CashRegisterResponse,
  CashRegisterService,
} from '../../../../../core/services/cash-register.service';
import { ApiService } from '../../../../../core/services/api.service';

type ParameterizedReportTab = 'sale' | 'inventory' | 'cash';

@Component({
  selector: 'app-parameterized-reports',
  imports: [Navbar, Sidebar, FormsModule],
  templateUrl: './parameterized.html',
  styleUrl: './parameterized.css',
})
export class ParameterizedReports implements OnInit {
  protected readonly permissions=inject(CompanyPermissionsService);
  protected exportError='';
  private readonly preferences = inject(PreferencesService);
  private readonly readSnapshot=watchReadPermissions(() => { this.salesReport=null;this.inventoryReport=null;this.cashReport=null;this.cdr.markForCheck(); });
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly productService = inject(ProductService);
  private readonly cashRegisterService = inject(CashRegisterService);
  private readonly apiService = inject(ApiService);
  private readonly hostElement = inject(ElementRef<HTMLElement>);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected activeTab: ParameterizedReportTab = 'sale';
  protected branchOptions = ['Todas'];
  protected saleTypeOptions = ['Todas'];
  protected paymentMethodOptions = ['Todos'];
  protected productOptions = ['Todos'];
  protected staffOptions = ['Todos'];
  protected inventoryMovementTypeOptions = ['Todos'];
  protected categoryOptions = ['Todas'];
  protected cashOptions = ['Todas'];
  protected cashMovementTypeOptions = ['Todos'];

  private branchesList: Branch[] = [];
  private saleTypesList: TipoVenta[] = [];
  private paymentMethodsList: MetodoPago[] = [];
  private productsList: Producto[] = [];
  private staffList: CompanyStaffMember[] = [];
  private movementTypesList: TipoMovimiento[] = [];
  private categoriesList: CategoriaProducto[] = [];
  private cashRegistersList: CashRegisterResponse[] = [];
  private cashMovementTypesList: CashRegisterMovementType[] = [];

  protected saleFilters = {
    startDate: '01/05/2026',
    endDate: '31/05/2026',
    branch: 'Todas',
    saleType: 'Todas',
    paymentMethod: 'Todos',
    product: 'Todos',
    staff: 'Todos',
  };

  protected salesReport: null | any = null;
  protected loadingSalesReport = false;

  protected inventoryFilters = {
    startDate: '01/05/2026',
    endDate: '31/05/2026',
    branch: 'Todas',
    movementType: 'Todos',
    product: 'Todos',
    category: 'Todas',
  };

  protected inventoryReport: null | any = null;
  protected loadingInventoryReport = false;

  protected cashFilters = {
    startDate: '01/05/2026',
    endDate: '31/05/2026',
    branch: 'Todas',
    cash: 'Todas',
    movementType: 'Todos',
    sessionStatus: 'Todas',
  };

  protected cashReport: null | any = null;
  protected loadingCashReport = false;

  ngOnInit(): void {
    this.loadFilterOptions();
  }

  protected setActiveTab(tab: ParameterizedReportTab): void {
    this.activeTab = tab;
    this.salesReport = null;
  }

  protected clearSaleFilters(): void {
    this.saleFilters = {
      startDate: '01/05/2026',
      endDate: '31/05/2026',
      branch: 'Todas',
      saleType: 'Todas',
      paymentMethod: 'Todos',
      product: 'Todos',
      staff: 'Todos',
    };
    this.salesReport = null;
  }

  protected clearInventoryFilters(): void {
    this.inventoryFilters = {
      startDate: '01/05/2026',
      endDate: '31/05/2026',
      branch: 'Todas',
      movementType: 'Todos',
      product: 'Todos',
      category: 'Todas',
    };
    this.inventoryReport = null;
  }

  protected clearCashFilters(): void {
    this.cashFilters = {
      startDate: '01/05/2026',
      endDate: '31/05/2026',
      branch: 'Todas',
      cash: 'Todas',
      movementType: 'Todos',
      sessionStatus: 'Todas',
    };
    this.cashReport = null;
  }

  protected generateSalesReport(after?: () => void, exporting=false): void {
    this.loadingSalesReport = true;
    const normalizeStr = (str: string) =>
      str ? str.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim() : '';

    const selectedBranch = this.saleFilters.branch;
    const branchObj = normalizeStr(selectedBranch) === 'todas'
      ? null
      : this.branchesList.find((b) => normalizeStr(b.nombre) === normalizeStr(selectedBranch));
    const branchId = branchObj ? (branchObj.id_sucursal ?? branchObj.idSucursal ?? branchObj.id) : null;

    const selectedSaleType = this.saleFilters.saleType;
    const saleTypeObj = normalizeStr(selectedSaleType) === 'todas'
      ? null
      : this.saleTypesList.find((t) => normalizeStr(t.nombre) === normalizeStr(selectedSaleType));
    const saleTypeId = saleTypeObj ? saleTypeObj.id_tipo_venta : null;

    const selectedPaymentMethod = this.saleFilters.paymentMethod;
    const paymentMethodObj = normalizeStr(selectedPaymentMethod) === 'todos'
      ? null
      : this.paymentMethodsList.find((m) => normalizeStr(m.nombre) === normalizeStr(selectedPaymentMethod));
    const paymentMethodId = paymentMethodObj ? paymentMethodObj.id_metodo_pago : null;

    const selectedProduct = this.saleFilters.product;
    const productObj = normalizeStr(selectedProduct) === 'todos'
      ? null
      : this.productsList.find((p) => normalizeStr(p.nombre ?? '') === normalizeStr(selectedProduct));
    const productId = productObj ? productObj.id_producto : null;

    const selectedStaff = this.saleFilters.staff;
    const staffObj = normalizeStr(selectedStaff) === 'todos'
      ? null
      : this.staffList.find(
          (s) => normalizeStr(s.usuario.persona?.nombre_completo ?? s.usuario.email) === normalizeStr(selectedStaff)
        );
    const staffId = staffObj ? staffObj.id_usuario : null;

    const parseDateToYYYYMMDD = (dateStr: string): string => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        return dateStr;
      }
      const parts = dateStr.split('/');
      if (parts.length === 3) {
        const day = parts[0].padStart(2, '0');
        const month = parts[1].padStart(2, '0');
        const year = parts[2];
        return `${year}-${month}-${day}`;
      }
      return dateStr;
    };

    const payload = {
      fecha_inicial: parseDateToYYYYMMDD(this.saleFilters.startDate),
      fecha_final: parseDateToYYYYMMDD(this.saleFilters.endDate),
      id_sucursal: branchId ? Number(branchId) : null,
      id_tipo_venta: saleTypeId ? Number(saleTypeId) : null,
      id_metodo_pago: paymentMethodId ? Number(paymentMethodId) : null,
      id_producto: productId ? Number(productId) : null,
      id_usuario: staffId ? Number(staffId) : null,
    };

    const formatCurrency = (v: number) => {
      return this.preferences.formatMoney(v);
    };

    (exporting ? this.apiService.postExport<any,any>(`/api/reportes/${this.companyId}/ventasparametrizado`,payload) : this.apiService.post<any,any>(`/api/reportes/${this.companyId}/ventasparametrizado`,payload))
      .pipe(
        finalize(() => {
          this.loadingSalesReport = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
      next: (response) => {
        const totalSalesObj = response.resumen_gerencial?.find(
          (r: any) =>
            r.indicador?.toLowerCase().includes('total de ventas') ||
            r.indicador?.toLowerCase().includes('ventas realizadas')
        );
        const totalSales = totalSalesObj ? totalSalesObj.valor : 0;

        const totalAmountObj = response.resumen_gerencial?.find(
          (r: any) =>
            r.indicador?.toLowerCase().includes('monto') ||
            r.indicador?.toLowerCase().includes('total vendido')
        );
        const totalAmountVal = totalAmountObj ? totalAmountObj.valor : 0;
        const totalAmount = formatCurrency(totalAmountVal);

        const totalProductsObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('productos')
        );
        const totalProducts = totalProductsObj ? totalProductsObj.valor : 0;

        this.salesReport = {
          company: response.empresa || 'Supermercado La Estrella',
          generatedAt: response.fecha_generacion,
          filters: this.saleFilters,
          summary: {
            totalSales,
            totalAmount,
            totalProducts,
          },
          details: (response.detalle_analitico || []).map((item: any) => ({
            nro: item.numero_venta,
            datetime: item.fecha_hora,
            staff: item.personal,
            type: item.tipo,
            method: item.metodo_pago,
            products: item.productos,
            total: formatCurrency(Number(item.total)),
          })),
        };
        if(after && this.permissions.hasPermission('report:export')) after();
      },
      error: (err) => {
        this.salesReport=null;this.exportError=err?.error?.detail??'No se pudo actualizar el reporte.';
        console.error('Error generating sales report:', err);
      },
    });
  }

  protected generateInventoryReport(after?: () => void, exporting=false): void {
    this.loadingInventoryReport = true;
    const normalizeStr = (str: string) =>
      str ? str.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim() : '';

    const selectedBranch = this.inventoryFilters.branch;
    const branchObj = normalizeStr(selectedBranch) === 'todas'
      ? null
      : this.branchesList.find((b) => normalizeStr(b.nombre) === normalizeStr(selectedBranch));
    const branchId = branchObj ? (branchObj.id_sucursal ?? branchObj.idSucursal ?? branchObj.id) : null;

    const selectedMovementType = this.inventoryFilters.movementType;
    const movementTypeObj = normalizeStr(selectedMovementType) === 'todos'
      ? null
      : this.movementTypesList.find((t) => normalizeStr(t.nombre) === normalizeStr(selectedMovementType));
    const movementTypeId = movementTypeObj ? movementTypeObj.id_tipo_movimiento : null;

    const selectedProduct = this.inventoryFilters.product;
    const productObj = normalizeStr(selectedProduct) === 'todos'
      ? null
      : this.productsList.find((p) => normalizeStr(p.nombre ?? '') === normalizeStr(selectedProduct));
    const productId = productObj ? productObj.id_producto : null;

    const selectedCategory = this.inventoryFilters.category;
    const categoryObj = normalizeStr(selectedCategory) === 'todas'
      ? null
      : this.categoriesList.find((c) => normalizeStr(c.nombre) === normalizeStr(selectedCategory));
    const categoryId = categoryObj ? categoryObj.id_categoria_producto : null;

    const parseDateToYYYYMMDD = (dateStr: string): string => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        return dateStr;
      }
      const parts = dateStr.split('/');
      if (parts.length === 3) {
        const day = parts[0].padStart(2, '0');
        const month = parts[1].padStart(2, '0');
        const year = parts[2];
        return `${year}-${month}-${day}`;
      }
      return dateStr;
    };

    const payload = {
      fecha_inicial: parseDateToYYYYMMDD(this.inventoryFilters.startDate),
      fecha_final: parseDateToYYYYMMDD(this.inventoryFilters.endDate),
      id_sucursal: branchId ? Number(branchId) : null,
      id_tipo_movimiento: movementTypeId ? Number(movementTypeId) : null,
      id_producto: productId ? Number(productId) : null,
      id_categoria_producto: categoryId ? Number(categoryId) : null,
    };

    (exporting ? this.apiService.postExport<any,any>(`/api/reportes/${this.companyId}/inventarioparametrizado`,payload) : this.apiService.post<any,any>(`/api/reportes/${this.companyId}/inventarioparametrizado`,payload))
      .pipe(
        finalize(() => {
          this.loadingInventoryReport = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
      next: (response) => {
        const totalMovementsObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('movimientos')
        );
        const totalMovements = totalMovementsObj ? totalMovementsObj.valor : 0;

        const availableStockObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('disponible')
        );
        const availableStock = availableStockObj ? availableStockObj.valor : 0;

        const lowStockObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('bajo')
        );
        const lowStock = lowStockObj ? lowStockObj.valor : 0;

        const outOfStockObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('agotados')
        );
        const outOfStock = outOfStockObj ? outOfStockObj.valor : 0;

        this.inventoryReport = {
          company: response.empresa || 'Supermercado La Estrella',
          generatedAt: response.fecha_generacion,
          filters: this.inventoryFilters,
          summary: {
            totalMovements,
            availableStock,
            lowStock,
            outOfStock,
          },
          details: (response.detalle_analitico || []).map((item: any) => ({
            nro: item.numero_movimiento,
            datetime: item.fecha_hora,
            product: item.producto,
            category: item.categoria,
            type: item.tipo,
            quantity: item.cantidad,
          })),
        };
        if(after && this.permissions.hasPermission('report:export')) after();
      },
      error: (err) => {
        this.inventoryReport=null;this.exportError=err?.error?.detail??'No se pudo actualizar el reporte.';
        console.error('Error generating inventory report:', err);
      },
    });
  }

  protected generateCashReport(after?: () => void, exporting=false): void {
    this.loadingCashReport = true;
    const normalizeStr = (str: string) =>
      str ? str.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim() : '';

    const selectedBranch = this.cashFilters.branch;
    const branchObj = normalizeStr(selectedBranch) === 'todas'
      ? null
      : this.branchesList.find((b) => normalizeStr(b.nombre) === normalizeStr(selectedBranch));
    const branchId = branchObj ? (branchObj.id_sucursal ?? branchObj.idSucursal ?? branchObj.id) : null;

    const selectedCash = this.cashFilters.cash;
    const cashObj = normalizeStr(selectedCash) === 'todas'
      ? null
      : this.cashRegistersList.find((c) => normalizeStr(c.nombre) === normalizeStr(selectedCash));
    const cashId = cashObj ? cashObj.id_caja : null;

    const selectedMovementType = this.cashFilters.movementType;
    const movementTypeObj = normalizeStr(selectedMovementType) === 'todos'
      ? null
      : this.cashMovementTypesList.find((t) => normalizeStr(t.nombre) === normalizeStr(selectedMovementType));
    const movementTypeId = movementTypeObj ? movementTypeObj.id_tipo_movimiento_caja : null;

    const selectedSessionStatus = this.cashFilters.sessionStatus;
    let sessionStatusPayload: string | null = null;
    if (normalizeStr(selectedSessionStatus) === 'abierta') {
      sessionStatusPayload = 'Abierto';
    } else if (normalizeStr(selectedSessionStatus) === 'cerrada') {
      sessionStatusPayload = 'Cerrado';
    }

    const parseDateToYYYYMMDD = (dateStr: string): string => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        return dateStr;
      }
      const parts = dateStr.split('/');
      if (parts.length === 3) {
        const day = parts[0].padStart(2, '0');
        const month = parts[1].padStart(2, '0');
        const year = parts[2];
        return `${year}-${month}-${day}`;
      }
      return dateStr;
    };

    const payload = {
      fecha_inicial: parseDateToYYYYMMDD(this.cashFilters.startDate),
      fecha_final: parseDateToYYYYMMDD(this.cashFilters.endDate),
      id_sucursal: branchId ? Number(branchId) : null,
      id_caja: cashId ? Number(cashId) : null,
      id_tipo_movimiento_caja: movementTypeId ? Number(movementTypeId) : null,
      estado_sesion: sessionStatusPayload,
    };

    const formatCurrency = (v: number) => {
      return this.preferences.formatMoney(v);
    };

    (exporting ? this.apiService.postExport<any,any>(`/api/reportes/${this.companyId}/cajasparametrizado`,payload) : this.apiService.post<any,any>(`/api/reportes/${this.companyId}/cajasparametrizado`,payload))
      .pipe(
        finalize(() => {
          this.loadingCashReport = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
      next: (response) => {
        const totalSessionsObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('sesiones de caja')
        );
        const totalSessions = totalSessionsObj ? totalSessionsObj.valor : 0;

        const openSessionsObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('abiertas')
        );
        const openSessions = openSessionsObj ? openSessionsObj.valor : 0;

        const closedSessionsObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('cerradas')
        );
        const closedSessions = closedSessionsObj ? closedSessionsObj.valor : 0;

        const totalOpeningAmountObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('aperturas')
        );
        const totalOpeningAmount = totalOpeningAmountObj ? formatCurrency(totalOpeningAmountObj.valor) : formatCurrency(0);

        const expectedTotalAmountObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('esperado')
        );
        const expectedTotalAmount = expectedTotalAmountObj ? formatCurrency(expectedTotalAmountObj.valor) : formatCurrency(0);

        const declaredTotalAmountObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('declarado')
        );
        const declaredTotalAmount = declaredTotalAmountObj ? formatCurrency(declaredTotalAmountObj.valor) : formatCurrency(0);

        const totalDifferenceObj = response.resumen_gerencial?.find(
          (r: any) => r.indicador?.toLowerCase().includes('diferencia')
        );
        const totalDifference = totalDifferenceObj ? formatCurrency(totalDifferenceObj.valor) : formatCurrency(0);

        this.cashReport = {
          company: response.empresa || 'Supermercado La Estrella',
          generatedAt: response.fecha_generacion,
          filters: this.cashFilters,
          summary: {
            totalSessions,
            openSessions,
            closedSessions,
            totalOpeningAmount,
            expectedTotalAmount,
            declaredTotalAmount,
            totalDifference,
          },
          details: (response.detalle_analitico || []).map((item: any) => ({
            nro: item.numero_movimiento,
            datetime: item.fecha_hora,
            cash: item.caja,
            type: item.tipo,
            concept: item.concepto,
            amount: formatCurrency(Number(item.monto)),
          })),
        };
        if(after && this.permissions.hasPermission('report:export')) after();
      },
      error: (err) => {
        this.cashReport=null;this.exportError=err?.error?.detail??'No se pudo actualizar el reporte.';
        console.error('Error generating cash report:', err);
      },
    });
  }

  private refreshCurrentReportResult(): void {
    this.cdr.detectChanges();

    setTimeout(() => {
      this.hostElement.nativeElement
        .querySelector('.report-section')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  private loadFilterOptions(): void {
    if (!this.companyId) {
      return;
    }

    this.companyService.getSucursales(this.companyId).subscribe({
      next: (branches) => {
        this.branchesList = branches;
        this.branchOptions = ['Todas', ...this.uniqueLabels(branches.map((branch) => branch.nombre))];
        this.loadCashOptions(branches);
      },
      error: () => {
        this.branchesList = [];
        this.branchOptions = ['Todas'];
        this.cashOptions = ['Todas'];
      },
    });

    this.productService.getTiposVenta().subscribe({
      next: (types) => {
        this.saleTypesList = types;
        this.saleTypeOptions = ['Todas', ...this.uniqueLabels(types.map((type) => type.nombre))];
      },
      error: () => {
        this.saleTypesList = [];
        this.saleTypeOptions = ['Todas'];
      },
    });

    this.productService.getMetodosPago().subscribe({
      next: (methods) => {
        this.paymentMethodsList = methods;
        this.paymentMethodOptions = ['Todos', ...this.uniqueLabels(methods.map((method) => method.nombre))];
      },
      error: () => {
        this.paymentMethodsList = [];
        this.paymentMethodOptions = ['Todos'];
      },
    });

    this.productService.getProductos(this.companyId).subscribe({
      next: (products) => {
        this.productsList = products;
        this.productOptions = ['Todos', ...this.uniqueLabels(products.map((product) => product.nombre))];
      },
      error: () => {
        this.productsList = [];
        this.productOptions = ['Todos'];
      },
    });

    this.companyService.getPersonalEmpresa(this.companyId).subscribe({
      next: (staff) => {
        this.staffList = staff;
        this.staffOptions = [
          'Todos',
          ...this.uniqueLabels(staff.map((member) => member.usuario.persona?.nombre_completo ?? member.usuario.email)),
        ];
      },
      error: () => {
        this.staffList = [];
        this.staffOptions = ['Todos'];
      },
    });

    this.productService.getTiposMovimiento().subscribe({
      next: (types) => {
        this.movementTypesList = types;
        this.inventoryMovementTypeOptions = ['Todos', ...this.uniqueLabels(types.map((type) => type.nombre))];
      },
      error: () => {
        this.movementTypesList = [];
        this.inventoryMovementTypeOptions = ['Todos'];
      },
    });

    this.productService.getCategorias(this.companyId).subscribe({
      next: (categories) => {
        this.categoriesList = categories;
        this.categoryOptions = ['Todas', ...this.uniqueLabels(categories.map((category) => category.nombre))];
      },
      error: () => {
        this.categoriesList = [];
        this.categoryOptions = ['Todas'];
      },
    });

    this.companyService.getTiposMovimientoCaja().subscribe({
      next: (types) => {
        this.cashMovementTypesList = types;
        this.cashMovementTypeOptions = ['Todos', ...this.uniqueLabels(types.map((type) => type.nombre))];
      },
      error: () => {
        this.cashMovementTypesList = [];
        this.cashMovementTypeOptions = ['Todos'];
      },
    });
  }

  private loadCashOptions(branches: Branch[]): void {
    const branchIds = branches
      .map((branch) => this.getBranchId(branch))
      .filter((id): id is string => id !== null);

    if (branchIds.length === 0) {
      this.cashOptions = ['Todas'];
      return;
    }

    forkJoin(branchIds.map((branchId) => this.cashRegisterService.getCajasSucursal(this.companyId, branchId))).subscribe({
      next: (responses) => {
        const cashRegisters = responses.flatMap((response) => this.normalizeCashRegisterListResponse(response));
        this.cashRegistersList = cashRegisters;
        this.cashOptions = ['Todas', ...this.uniqueLabels(cashRegisters.map((cashRegister) => cashRegister.nombre))];
      },
      error: () => {
        this.cashRegistersList = [];
        this.cashOptions = ['Todas'];
      },
    });
  }

  private normalizeCashRegisterListResponse(response: CashRegisterListResponse): CashRegisterResponse[] {
    if (Array.isArray(response)) {
      return response;
    }

    if (Array.isArray(response.cajas)) {
      return response.cajas;
    }

    if (Array.isArray(response.items)) {
      return response.items;
    }

    if (Array.isArray(response.data)) {
      return response.data;
    }

    if (response.data && Array.isArray(response.data.cajas)) {
      return response.data.cajas;
    }

    if (response.data && Array.isArray(response.data.items)) {
      return response.data.items;
    }

    return [];
  }

  private getBranchId(branch: Branch): string | null {
    const rawId = branch.id_sucursal ?? branch.idSucursal ?? branch.id;
    const parsedId = typeof rawId === 'number' ? rawId : Number(rawId);

    return Number.isFinite(parsedId) ? String(parsedId) : null;
  }

  protected exportSalesReport(format: 'pdf' | 'excel'): void {
    if(!this.permissions.hasPermission('report:export') || this.loadingSalesReport) return;
    this.exportError='';
    this.generateSalesReport(() => {
      if(format==='pdf') this.exportSalesReportPdf();else this.exportSalesReportExcel();
    },true);
  }

  private exportSalesReportPdf(): void {
    const r = this.salesReport;
    const lines: string[] = [
      'REPORTE DE VENTAS PARAMETRIZADO',
      '================================',
      `Empresa: ${r.company}`,
      `Fecha de generacion: ${r.generatedAt}`,
      '',
      'Filtros aplicados:',
      `- Periodo: ${r.filters.startDate} al ${r.filters.endDate}`,
      `- Sucursal: ${r.filters.branch}`,
      `- Tipo de venta: ${r.filters.saleType}`,
      `- Metodo de pago: ${r.filters.paymentMethod}`,
      `- Producto: ${r.filters.product}`,
      `- Personal: ${r.filters.staff}`,
      '',
      'Resumen gerencial:',
      `- Total de ventas realizadas: ${r.summary.totalSales}`,
      `- Monto total vendido: ${r.summary.totalAmount}`,
      `- Productos vendidos: ${r.summary.totalProducts}`,
      '',
      'Detalle Analitico de Ventas:',
      'N. Venta | Fecha y hora | Personal | Tipo | Metodo pago | Productos | Total',
      '--------------------------------------------------------------------------------------------'
    ];

    r.details.forEach((item: any) => {
      lines.push(`${item.nro} | ${item.datetime} | ${item.staff} | ${item.type} | ${item.method} | ${item.products} | ${item.total}`);
    });

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('reporte-ventas')}.pdf`);
  }

  private exportSalesReportExcel(): void {
    const r = this.salesReport;
    const headers = ['N. venta', 'Fecha y hora', 'Personal', 'Tipo', 'Metodo pago', 'Productos', 'Total'];
    const headerCells = headers.map(h => `<th>${this.escapeHtml(h)}</th>`).join('');
    const bodyRows = r.details.map((item: any) => {
      return `<tr>
        <td>${this.escapeHtml(String(item.nro))}</td>
        <td>${this.escapeHtml(String(item.datetime))}</td>
        <td>${this.escapeHtml(String(item.staff))}</td>
        <td>${this.escapeHtml(String(item.type))}</td>
        <td>${this.escapeHtml(String(item.method))}</td>
        <td style="text-align:center;">${this.escapeHtml(String(item.products))}</td>
        <td style="text-align:right;">${this.escapeHtml(String(item.total))}</td>
      </tr>`;
    }).join('');

    const worksheet = this.buildExcelHtml('Reporte de Ventas Parametrizado', r, headerCells, bodyRows, [
      { label: 'Total de ventas realizadas', value: r.summary.totalSales },
      { label: 'Monto total vendido', value: r.summary.totalAmount },
      { label: 'Productos vendidos', value: r.summary.totalProducts }
    ]);

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('reporte-ventas')}.xls`
    );
  }

  protected exportInventoryReport(format: 'pdf' | 'excel'): void {
    if(!this.permissions.hasPermission('report:export') || this.loadingInventoryReport) return;
    this.exportError='';
    this.generateInventoryReport(() => {
      if(format==='pdf') this.exportInventoryReportPdf();else this.exportInventoryReportExcel();
    },true);
  }

  private exportInventoryReportPdf(): void {
    const r = this.inventoryReport;
    const lines: string[] = [
      'REPORTE DE INVENTARIO PARAMETRIZADO',
      '====================================',
      `Empresa: ${r.company}`,
      `Fecha de generacion: ${r.generatedAt}`,
      '',
      'Filtros aplicados:',
      `- Periodo: ${r.filters.startDate} al ${r.filters.endDate}`,
      `- Sucursal: ${r.filters.branch}`,
      `- Tipo de movimiento: ${r.filters.movementType}`,
      `- Producto: ${r.filters.product}`,
      `- Categoria: ${r.filters.category}`,
      '',
      'Resumen gerencial:',
      `- Movimientos registrados: ${r.summary.totalMovements}`,
      `- Productos con stock disponible: ${r.summary.availableStock}`,
      `- Productos con stock bajo: ${r.summary.lowStock}`,
      `- Productos agotados: ${r.summary.outOfStock}`,
      '',
      'Detalle Analitico de Movimientos de Inventario:',
      'N. Movimiento | Fecha y hora | Producto | Categoria | Tipo | Cantidad',
      '--------------------------------------------------------------------------------------------'
    ];

    r.details.forEach((item: any) => {
      lines.push(`${item.nro} | ${item.datetime} | ${item.product} | ${item.category} | ${item.type} | ${item.quantity}`);
    });

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('reporte-inventario')}.pdf`);
  }

  private exportInventoryReportExcel(): void {
    const r = this.inventoryReport;
    const headers = ['N. movimiento', 'Fecha y hora', 'Producto', 'Categoria', 'Tipo', 'Cantidad'];
    const headerCells = headers.map(h => `<th>${this.escapeHtml(h)}</th>`).join('');
    const bodyRows = r.details.map((item: any) => {
      return `<tr>
        <td>${this.escapeHtml(String(item.nro))}</td>
        <td>${this.escapeHtml(String(item.datetime))}</td>
        <td>${this.escapeHtml(String(item.product))}</td>
        <td>${this.escapeHtml(String(item.category))}</td>
        <td>${this.escapeHtml(String(item.type))}</td>
        <td style="text-align:center;">${this.escapeHtml(String(item.quantity))}</td>
      </tr>`;
    }).join('');

    const worksheet = this.buildExcelHtml('Reporte de Inventario Parametrizado', r, headerCells, bodyRows, [
      { label: 'Movimientos registrados', value: r.summary.totalMovements },
      { label: 'Productos con stock disponible', value: r.summary.availableStock },
      { label: 'Productos con stock bajo', value: r.summary.lowStock },
      { label: 'Productos agotados', value: r.summary.outOfStock }
    ]);

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('reporte-inventario')}.xls`
    );
  }

  protected exportCashReport(format: 'pdf' | 'excel'): void {
    if(!this.permissions.hasPermission('report:export') || this.loadingCashReport) return;
    this.exportError='';
    this.generateCashReport(() => {
      if(format==='pdf') this.exportCashReportPdf();else this.exportCashReportExcel();
    },true);
  }

  private exportCashReportPdf(): void {
    const r = this.cashReport;
    const lines: string[] = [
      'REPORTE DE CAJAS PARAMETRIZADO',
      '===============================',
      `Empresa: ${r.company}`,
      `Fecha de generacion: ${r.generatedAt}`,
      '',
      'Filtros aplicados:',
      `- Periodo: ${r.filters.startDate} al ${r.filters.endDate}`,
      `- Sucursal: ${r.filters.branch}`,
      `- Caja: ${r.filters.cash}`,
      `- Tipo de movimiento: ${r.filters.movementType}`,
      `- Estado de sesion: ${r.filters.sessionStatus}`,
      '',
      'Resumen gerencial:',
      `- Sesiones de caja registradas: ${r.summary.totalSessions}`,
      `- Sesiones abiertas: ${r.summary.openSessions}`,
      `- Sesiones cerradas: ${r.summary.closedSessions}`,
      `- Monto total de aperturas: ${r.summary.totalOpeningAmount}`,
      `- Monto total esperado al cierre: ${r.summary.expectedTotalAmount}`,
      `- Monto total declarado al cierre: ${r.summary.declaredTotalAmount}`,
      `- Diferencia total: ${r.summary.totalDifference}`,
      '',
      'Detalle Analitico de Movimientos de Caja:',
      'N. Movimiento | Fecha y hora | Caja | Tipo | Concepto | Monto',
      '--------------------------------------------------------------------------------------------'
    ];

    r.details.forEach((item: any) => {
      lines.push(`${item.nro} | ${item.datetime} | ${item.cash} | ${item.type} | ${item.concept} | ${item.amount}`);
    });

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('reporte-cajas')}.pdf`);
  }

  private exportCashReportExcel(): void {
    const r = this.cashReport;
    const headers = ['N. movimiento', 'Fecha y hora', 'Caja', 'Tipo', 'Concepto', 'Monto'];
    const headerCells = headers.map(h => `<th>${this.escapeHtml(h)}</th>`).join('');
    const bodyRows = r.details.map((item: any) => {
      return `<tr>
        <td>${this.escapeHtml(String(item.nro))}</td>
        <td>${this.escapeHtml(String(item.datetime))}</td>
        <td>${this.escapeHtml(String(item.cash))}</td>
        <td>${this.escapeHtml(String(item.type))}</td>
        <td>${this.escapeHtml(String(item.concept))}</td>
        <td style="text-align:right;">${this.escapeHtml(String(item.amount))}</td>
      </tr>`;
    }).join('');

    const worksheet = this.buildExcelHtml('Reporte de Cajas Parametrizado', r, headerCells, bodyRows, [
      { label: 'Sesiones de caja registradas', value: r.summary.totalSessions },
      { label: 'Sesiones abiertas', value: r.summary.openSessions },
      { label: 'Sesiones cerradas', value: r.summary.closedSessions },
      { label: 'Monto total de aperturas', value: r.summary.totalOpeningAmount },
      { label: 'Monto total esperado al cierre', value: r.summary.expectedTotalAmount },
      { label: 'Monto total declarado al cierre', value: r.summary.declaredTotalAmount },
      { label: 'Diferencia total', value: r.summary.totalDifference }
    ]);

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('reporte-cajas')}.xls`
    );
  }

  private buildExcelHtml(
    title: string,
    report: any,
    headerCells: string,
    bodyRows: string,
    summaryItems: { label: string; value: any }[]
  ): string {
    const filterLabels: Record<string, string> = {
      startDate: 'Fecha Inicial',
      endDate: 'Fecha Final',
      branch: 'Sucursal',
      saleType: 'Tipo de Venta',
      paymentMethod: 'Metodo de Pago',
      product: 'Producto',
      staff: 'Personal',
      movementType: 'Tipo de Movimiento',
      category: 'Categoria',
      cash: 'Caja',
      sessionStatus: 'Estado de Sesi\u00F3n'
    };

    const filterItems = Object.entries(report.filters)
      .map(([key, value]) => {
        const label = filterLabels[key] || key;
        return `<tr><td style="font-weight:bold;">${this.escapeHtml(label)}</td><td>${this.escapeHtml(String(value))}</td></tr>`;
      })
      .join('');

    const summaryRows = summaryItems
      .map(item => `<tr><td>${this.escapeHtml(item.label)}</td><td style="text-align:right;">${this.escapeHtml(String(item.value))}</td></tr>`)
      .join('');

    return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: Arial, sans-serif; }
    table { border-collapse: collapse; font-size: 12px; margin-bottom: 20px; }
    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; }
    th { background: #e2e8f0; font-weight: 700; }
    .title-cell { font-size: 16px; font-weight: bold; padding: 10px 0; }
  </style>
</head>
<body>
  <table>
    <tr><td colspan="4" class="title-cell">${this.escapeHtml(title)}</td></tr>
    <tr><td colspan="4">Empresa: ${this.escapeHtml(report.company)}</td></tr>
    <tr><td colspan="4">Fecha de generacion: ${this.escapeHtml(report.generatedAt)}</td></tr>
  </table>

  <h3>Filtros Aplicados</h3>
  <table>
    <tbody>
      ${filterItems}
    </tbody>
  </table>

  <h3>Resumen Gerencial</h3>
  <table>
    <thead>
      <tr><th>Indicador</th><th>Valor</th></tr>
    </thead>
    <tbody>
      ${summaryRows}
    </tbody>
  </table>

  <h3>Detalle Analitico</h3>
  <table>
    <thead><tr>${headerCells}</tr></thead>
    <tbody>${bodyRows}</tbody>
  </table>
</body>
</html>`;
  }

  private createPdfDocument(lines: string[]): Uint8Array {
    const pageWidth = 595;
    const pageHeight = 842;
    const marginX = 42;
    const startY = 800;
    const lineHeight = 16;
    const maxChars = 92;
    const pages: string[][] = [[]];

    lines.flatMap((line) => this.wrapPdfLine(line, maxChars)).forEach((line) => {
      const currentPage = pages[pages.length - 1];

      if (currentPage.length >= 46) {
        pages.push([]);
      }

      pages[pages.length - 1].push(line);
    });

    const objects: string[] = [
      '<< /Type /Catalog /Pages 2 0 R >>',
      '',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    ];
    const pageObjectNumbers: number[] = [];
    const fontObject = 3;
    const addObject = (value: string): number => {
      objects.push(value);
      return objects.length;
    };

    pages.forEach((pageLines) => {
      const streamLines = ['BT', '/F1 10 Tf'];

      pageLines.forEach((line, index) => {
        if (index === 0) {
          streamLines.push(`${marginX} ${startY} Td (${this.escapePdfText(line)}) Tj`);
        } else {
          streamLines.push(`0 -${lineHeight} Td (${this.escapePdfText(line)}) Tj`);
        }
      });

      streamLines.push('ET');
      const stream = streamLines.join('\n');
      const contentObject = addObject(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
      const pageObject = addObject(
        `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /Font << /F1 ${fontObject} 0 R >> >> /Contents ${contentObject} 0 R >>`,
      );

      pageObjectNumbers.push(pageObject);
    });

    const kids = pageObjectNumbers.map((objectNumber) => `${objectNumber} 0 R`).join(' ');
    objects[1] = `<< /Type /Pages /Kids [${kids}] /Count ${pageObjectNumbers.length} >>`;

    const chunks = ['%PDF-1.4\n'];
    const offsets = [0];

    objects.forEach((object, index) => {
      offsets.push(chunks.join('').length);
      chunks.push(`${index + 1} 0 obj\n${object}\nendobj\n`);
    });

    const xrefOffset = chunks.join('').length;
    chunks.push(`xref\n0 ${objects.length + 1}\n`);
    chunks.push('0000000000 65535 f \n');
    offsets.slice(1).forEach((offset) => chunks.push(`${offset.toString().padStart(10, '0')} 00000 n \n`));
    chunks.push(`trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`);

    return new TextEncoder().encode(chunks.join(''));
  }

  private wrapPdfLine(value: string, maxChars: number): string[] {
    const cleanValue = this.toPdfSafeText(value);

    if (cleanValue.length <= maxChars) {
      return [cleanValue];
    }

    const lines: string[] = [];
    let remaining = cleanValue;

    while (remaining.length > maxChars) {
      const breakpoint = remaining.lastIndexOf(' ', maxChars);
      const index = breakpoint > 20 ? breakpoint : maxChars;
      lines.push(remaining.slice(0, index).trim());
      remaining = remaining.slice(index).trim();
    }

    if (remaining) {
      lines.push(remaining);
    }

    return lines;
  }

  private toPdfSafeText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^\x20-\x7E]/g, '')
      .trim();
  }

  private escapePdfText(value: string): string {
    return this.toPdfSafeText(value).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
  }

  private escapeHtml(value: string): string {
    if(/^[\s\x00-\x1f]*[=+\-@]/.test(value)) value="\'"+value;
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    console.log('downloadBlob: Generando descarga para:', fileName);
    try {
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');

      anchor.href = objectUrl;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);

      setTimeout(() => {
        URL.revokeObjectURL(objectUrl);
      }, 100);
    } catch (e) {
      console.error('Error en downloadBlob:', e);
    }
  }

  private slugify(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .toLowerCase() || 'reporte';
  }

  private uniqueLabels(labels: Array<string | null | undefined>): string[] {
    return Array.from(new Set(labels.map((label) => label?.trim()).filter((label): label is string => Boolean(label))));
  }
}

