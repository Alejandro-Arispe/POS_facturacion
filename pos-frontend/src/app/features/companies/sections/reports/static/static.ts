import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';

import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../core/services/company-permissions.service';
import { ApiService } from '../../../../../core/services/api.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

type StaticReportTab = 'sales' | 'inventory' | 'cash-registers';

interface SalesSummaryBranch {
  id_sucursal: number;
  sucursal: string;
  ventas_dia: {
    total_ventas: number;
    monto_vendido: number;
    ticket_promedio: number;
    productos_vendidos: number;
  };
  top_productos: Array<{
    posicion: number;
    id_producto: number;
    producto: string;
    unidades: number;
  }>;
}

interface SalesSummaryReportResponse {
  id_empresa: number;
  fecha: string;
  sucursales: SalesSummaryBranch[];
  total_empresa: {
    total_ventas: number;
    monto_vendido: number;
    ticket_promedio: number;
    productos_vendidos: number;
  };
}

interface SalesSummaryReportView {
  date: string;
  branches: Array<{
    name: string;
    totalSales: number;
    amount: string;
    averageTicket: string;
    productsSold: number;
    topProducts: Array<{
      rank: number;
      name: string;
      quantity: number;
    }>;
  }>;
  companyTotal: {
    totalSales: number;
    amount: string;
    averageTicket: string;
    productsSold: number;
  };
}

interface SalesDetailsBranch {
  id_sucursal: number;
  sucursal: string;
  ventas: Array<{
    id_venta: number;
    numero_venta: string;
    hora: string;
    cliente: string;
    subtotal: number;
    descuento: number;
    total: number;
  }>;
  resumen_sucursal: {
    total_registros: number;
    total_vendido: number;
  };
}

interface SalesDetailsReportResponse {
  id_empresa: number;
  fecha: string;
  sucursales: SalesDetailsBranch[];
  total_empresa: {
    total_registros: number;
    total_vendido: number;
  };
}

interface SalesDetailsReportView {
  date: string;
  branches: Array<{
    name: string;
    sales: Array<{
      id: string;
      time: string;
      client: string;
      subtotal: string;
      discount: string;
      total: string;
    }>;
    summary: {
      records: number;
      total: string;
    };
  }>;
  companyTotal: {
    records: number;
    total: string;
  };
}

interface InventoryStatusBranch {
  id_sucursal: number;
  sucursal: string;
  productos: Array<{
    id_producto: number;
    producto: string;
    stock_actual: number;
    stock_minimo: number;
    stock_maximo: number | null;
    estado: string;
  }>;
  resumen_sucursal: {
    total_productos: number;
    productos_bajo_stock: number;
    productos_sobre_stock: number;
    productos_agotados: number;
  };
}

interface InventoryStatusReportResponse {
  id_empresa: number;
  fecha: string;
  sucursales: InventoryStatusBranch[];
  total_empresa: {
    total_productos: number;
    productos_bajo_stock: number;
    productos_sobre_stock: number;
    productos_agotados: number;
  };
}

interface InventoryStatusReportView {
  date: string;
  branches: Array<{
    name: string;
    items: Array<{
      product: string;
      actual: number;
      minimum: number;
      maximum: number | string;
      status: string;
    }>;
    summary: {
      totalProducts: number;
      lowStock: number;
      overStock: number;
      outOfStock: number;
    };
  }>;
  companyTotal: {
    totalProducts: number;
    lowStock: number;
    overStock: number;
    outOfStock: number;
  };
}

interface InventoryMovementsBranch {
  id_sucursal: number;
  sucursal: string;
  movimientos: Array<{
    id_movimiento_inventario: number;
    fecha: string;
    tipo_movimiento: string;
    direccion: string;
    producto: string;
    cantidad: number;
  }>;
  resumen_sucursal: {
    total_entradas: number;
    total_salidas: number;
  };
}

interface InventoryMovementsReportResponse {
  id_empresa: number;
  fecha_inicio: string;
  fecha_fin: string;
  sucursales: InventoryMovementsBranch[];
  total_empresa: {
    total_entradas: number;
    total_salidas: number;
  };
}

interface InventoryMovementsReportView {
  period: string;
  branches: Array<{
    name: string;
    movements: Array<{
      date: string;
      type: string;
      product: string;
      quantity: number;
    }>;
    summary: {
      entries: number;
      exits: number;
    };
  }>;
  companyTotal: {
    entries: number;
    exits: number;
  };
}

interface CashSummaryBranch {
  id_sucursal: number;
  sucursal: string;
  cajas: Array<{
    id_caja: number;
    id_caja_sesion: number;
    caja: string;
    estado: string;
    apertura: string;
    cierre: string | null;
  }>;
  resumen_sucursal: {
    total_cajas: number;
    cajas_abiertas: number;
    cajas_cerradas: number;
    ingresos: number;
    egresos: number;
    flujo_neto: number;
  };
}

interface CashSummaryReportResponse {
  id_empresa: number;
  fecha: string;
  sucursales: CashSummaryBranch[];
  total_empresa: {
    total_cajas: number;
    cajas_abiertas: number;
    cajas_cerradas: number;
    ingresos: number;
    egresos: number;
    flujo_neto: number;
  };
}

interface CashSummaryReportView {
  date: string;
  branches: Array<{
    name: string;
    boxes: Array<{
      name: string;
      state: string;
      opening: string;
      closing: string;
    }>;
    summary: {
      totalBoxes: number;
      openBoxes: number;
      closedBoxes: number;
      income: string;
      expenses: string;
      netFlow: string;
    };
  }>;
  companyTotal: {
    totalBoxes: number;
    openBoxes: number;
    closedBoxes: number;
    income: string;
    expenses: string;
    netFlow: string;
  };
}

interface CashMovementsBranch {
  id_sucursal: number;
  sucursal: string;
  movimientos: Array<{
    id_movimiento_caja: number;
    hora: string;
    caja: string;
    tipo: string;
    concepto: string;
    monto: number;
  }>;
  resumen_sucursal: {
    total_movimientos: number;
    total_ingresos: number;
    total_egresos: number;
  };
}

interface CashMovementsReportResponse {
  id_empresa: number;
  fecha: string;
  sucursales: CashMovementsBranch[];
  total_empresa: {
    total_movimientos: number;
    total_ingresos: number;
    total_egresos: number;
  };
}

interface CashMovementsReportView {
  date: string;
  branches: Array<{
    name: string;
    movements: Array<{
      time: string;
      box: string;
      type: string;
      concept: string;
      amount: string;
    }>;
    summary: {
      totalMovements: number;
      totalIncome: string;
      totalExpenses: string;
    };
  }>;
  companyTotal: {
    totalMovements: number;
    totalIncome: string;
    totalExpenses: string;
  };
}

@Component({
  selector: 'app-static-reports',
  imports: [CommonModule, Navbar, Sidebar],
  templateUrl: './static.html',
  styleUrl: './static.css',
})
export class StaticReports {
  private readonly route = inject(ActivatedRoute);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly apiService = inject(ApiService);
  private readonly hostElement = inject(ElementRef<HTMLElement>);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  // TODO: Verificar si los reportes estaticos deben filtrarse por sucursal en el backend.
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected activeTab: StaticReportTab = 'sales';
  protected salesReportType: 'none' | 'summary' | 'details' = 'none';
  protected inventoryReportType: 'none' | 'status' | 'movements' = 'none';
  protected cashReportType: 'none' | 'summary' | 'movements' = 'none';
  protected salesSummaryReport: SalesSummaryReportView = this.createEmptySalesSummaryReport();
  protected loadingSalesSummary = false;
  protected salesSummaryError = '';
  protected salesDetailsReport: SalesDetailsReportView = this.createEmptySalesDetailsReport();
  protected loadingSalesDetails = false;
  protected salesDetailsError = '';
  protected inventoryStatusReport: InventoryStatusReportView = this.createEmptyInventoryStatusReport();
  protected loadingInventoryStatus = false;
  protected inventoryStatusError = '';
  protected inventoryMovementsReport: InventoryMovementsReportView = this.createEmptyInventoryMovementsReport();
  protected loadingInventoryMovements = false;
  protected inventoryMovementsError = '';
  protected cashSummaryReport: CashSummaryReportView = this.createEmptyCashSummaryReport();
  protected loadingCashSummary = false;
  protected cashSummaryError = '';
  protected cashMovementsReport: CashMovementsReportView = this.createEmptyCashMovementsReport();
  protected loadingCashMovements = false;
  protected cashMovementsError = '';

  protected readonly salesSummaryReportMock = {
    date: '30/05/2026',
    branches: [
      {
        name: 'Central',
        totalSales: 45,
        amount: 'Bs 12.850',
        averageTicket: 'Bs 285,56',
        productsSold: 120,
        topProducts: [
          { rank: 1, name: 'Coca Cola 2L', quantity: 25 },
          { rank: 2, name: 'Arroz 5kg', quantity: 18 },
          { rank: 3, name: 'Aceite 1L', quantity: 15 },
        ],
      },
      {
        name: 'Norte',
        totalSales: 32,
        amount: 'Bs 8.450',
        averageTicket: 'Bs 264,06',
        productsSold: 87,
        topProducts: [
          { rank: 1, name: 'Coca Cola 2L', quantity: 19 },
          { rank: 2, name: 'Aceite 1L', quantity: 14 },
          { rank: 3, name: 'Azúcar 5kg', quantity: 11 },
        ],
      },
    ],
    companyTotal: {
      totalSales: 77,
      amount: 'Bs 21.300',
      productsSold: 207,
    },
  };

  protected readonly salesDetailsReportMock = {
    date: '30/05/2026',
    branches: [
      {
        name: 'Central',
        sales: [
          { id: '001', time: '08:12', client: 'Juan Pérez', subtotal: 'Bs 150', discount: 'Bs 0', total: 'Bs 150' },
          { id: '002', time: '09:45', client: 'María López', subtotal: 'Bs 300', discount: 'Bs 15', total: 'Bs 285' },
          { id: '003', time: '11:20', client: 'Carlos Gómez', subtotal: 'Bs 420', discount: 'Bs 20', total: 'Bs 400' },
        ],
        summary: {
          records: 45,
          total: 'Bs 12.850',
        },
      },
      {
        name: 'Norte',
        sales: [
          { id: 'A01', time: '08:30', client: 'Ana Ruiz', subtotal: 'Bs 120', discount: 'Bs 0', total: 'Bs 120' },
          { id: 'A02', time: '10:05', client: 'Luis Castillo', subtotal: 'Bs 250', discount: 'Bs 10', total: 'Bs 240' },
          { id: 'A03', time: '14:10', client: 'Patricia Díaz', subtotal: 'Bs 380', discount: 'Bs 30', total: 'Bs 350' },
        ],
        summary: {
          records: 32,
          total: 'Bs 8.450',
        },
      },
    ],
  };

  protected readonly inventoryStatusReportMock = {
    date: '30/05/2026',
    branches: [
      {
        name: 'Central',
        items: [
          { product: 'Coca Cola 2L', actual: 25, minimum: 10, maximum: 20, status: 'Sobre stock' },
          { product: 'Arroz 5kg', actual: 8, minimum: 10, maximum: '-', status: 'Bajo stock' },
          { product: 'Aceite 1L', actual: 0, minimum: 5, maximum: 10, status: 'Agotado' },
          { product: 'Azúcar 5kg', actual: 15, minimum: 8, maximum: 25, status: 'Normal' },
        ],
        summary: {
          totalProducts: 120,
          lowStock: 8,
          overStock: 5,
          outOfStock: 3,
        },
      },
      {
        name: 'Norte',
        items: [
          { product: 'Coca Cola 2L', actual: 25, minimum: 10, maximum: 20, status: 'Sobre stock' },
          { product: 'Arroz 5kg', actual: 8, minimum: 10, maximum: '-', status: 'Bajo stock' },
          { product: 'Aceite 1L', actual: 0, minimum: 5, maximum: 10, status: 'Agotado' },
          { product: 'Azúcar 5kg', actual: 15, minimum: 8, maximum: 25, status: 'Normal' },
        ],
        summary: {
          totalProducts: 95,
          lowStock: 6,
          overStock: 5,
          outOfStock: 1,
        },
      },
    ],
    companyTotal: {
      totalProducts: 215,
      lowStock: 14,
      overStock: 10,
      outOfStock: 4,
    },
  };

  protected readonly inventoryMovementsReportMock = {
    period: '26/05/2026 - 01/06/2026',
    branches: [
      {
        name: 'Central',
        movements: [
          { date: '30/05/2026', type: 'Entrada manual', product: 'Arroz 5kg', quantity: 20 },
          { date: '30/05/2026', type: 'Salida', product: 'Coca Cola 2L', quantity: 10 },
          { date: '29/05/2026', type: 'Ajuste positivo', product: 'Aceite 1L', quantity: 2 },
        ],
        summary: {
          entries: 25,
          exits: 12,
        },
      },
      {
        name: 'Norte',
        movements: [
          { date: '30/05/2026', type: 'Entrada', product: 'Azúcar 5kg', quantity: 15 },
          { date: '29/05/2026', type: 'Salida', product: 'Arroz 5kg', quantity: 8 },
          { date: '28/05/2026', type: 'Ajuste', product: 'Coca Cola 2L', quantity: 1 },
        ],
        summary: {
          entries: 18,
          exits: 10,
        },
      },
    ],
  };

  protected readonly cashSummaryReportMock = {
    date: '30/05/2026',
    branches: [
      {
        name: 'Central',
        boxes: [
          { name: 'Caja Principal', state: 'Cerrada', opening: '08:00', closing: '18:00' },
          { name: 'Caja Secundaria', state: 'Abierta', opening: '09:00', closing: '--' },
        ],
        summary: {
          totalBoxes: 2,
          openBoxes: 1,
          closedBoxes: 1,
          income: 'Bs 15.200',
          expenses: 'Bs 1.250',
          netFlow: 'Bs 13.950',
        },
      },
      {
        name: 'Norte',
        boxes: [
          { name: 'Caja Principal', state: 'Cerrada', opening: '08:30', closing: '17:30' },
        ],
        summary: {
          totalBoxes: 1,
          openBoxes: 0,
          closedBoxes: 1,
          income: 'Bs 8.400',
          expenses: 'Bs 600',
          netFlow: 'Bs 7.800',
        },
      },
    ],
    companyTotal: {
      totalBoxes: 3,
      openBoxes: 1,
      closedBoxes: 2,
      income: 'Bs 23.600',
      expenses: 'Bs 1.850',
      netFlow: 'Bs 21.750',
    },
  };

  protected readonly cashMovementsReportMock = {
    date: '30/05/2026',
    branches: [
      {
        name: 'Central',
        movements: [
          { time: '08:00', box: 'Caja Principal', type: 'Apertura', concept: 'Fondo inicial', amount: 'Bs 500' },
          { time: '10:30', box: 'Caja Principal', type: 'Ingreso', concept: 'Venta contado', amount: 'Bs 350' },
          { time: '12:15', box: 'Caja Principal', type: 'Egreso', concept: 'Compra insumos', amount: 'Bs 120' },
          { time: '18:00', box: 'Caja Principal', type: 'Cierre', concept: 'Cierre de caja', amount: 'Bs 0' },
        ],
        summary: {
          totalMovements: 25,
          totalIncome: 'Bs 15.200',
          totalExpenses: 'Bs 1.250',
        },
      },
      {
        name: 'Norte',
        movements: [
          { time: '08:30', box: 'Caja Principal', type: 'Apertura', concept: 'Fondo inicial', amount: 'Bs 500' },
          { time: '11:00', box: 'Caja Principal', type: 'Ingreso', concept: 'Venta contado', amount: 'Bs 420' },
          { time: '14:00', box: 'Caja Principal', type: 'Egreso', concept: 'Transporte', amount: 'Bs 80' },
        ],
        summary: {
          totalMovements: 14,
          totalIncome: 'Bs 8.400',
          totalExpenses: 'Bs 600',
        },
      },
    ],
  };

  protected showSalesReport(type: 'summary' | 'details'): void {
    this.salesReportType = type;

    if (type === 'summary') {
      this.loadSalesSummaryReport();
    } else {
      this.loadSalesDetailsReport();
    }
  }

  protected showInventoryReport(type: 'status' | 'movements'): void {
    this.inventoryReportType = type;

    if (type === 'status') {
      this.loadInventoryStatusReport();
    } else {
      this.loadInventoryMovementsReport();
    }
  }

  protected showCashReport(type: 'summary' | 'movements'): void {
    this.cashReportType = type;

    if (type === 'summary') {
      this.loadCashSummaryReport();
    } else {
      this.loadCashMovementsReport();
    }
  }

  protected setActiveTab(tab: StaticReportTab): void {
    this.activeTab = tab;
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    return this.companyPermissionsService.permissions()[permission] === true;
  }

  protected hasSalesSummaryData(): boolean {
    const report = this.salesSummaryReport;

    return (
      report.branches.length > 0 ||
      report.companyTotal.totalSales > 0 ||
      report.companyTotal.productsSold > 0
    );
  }

  protected hasSalesDetailsData(): boolean {
    const report = this.salesDetailsReport;

    return (
      report.branches.some((branch) => branch.sales.length > 0) ||
      report.companyTotal.records > 0
    );
  }

  protected hasInventoryStatusData(): boolean {
    const report = this.inventoryStatusReport;

    return (
      report.branches.some((branch) => branch.items.length > 0) ||
      report.companyTotal.totalProducts > 0
    );
  }

  protected hasInventoryMovementsData(): boolean {
    const report = this.inventoryMovementsReport;

    return (
      report.branches.some((branch) => branch.movements.length > 0) ||
      report.companyTotal.entries > 0 ||
      report.companyTotal.exits > 0
    );
  }

  protected hasCashSummaryData(): boolean {
    const report = this.cashSummaryReport;

    return (
      report.branches.some((branch) => branch.boxes.length > 0) ||
      report.companyTotal.totalBoxes > 0 ||
      report.companyTotal.openBoxes > 0 ||
      report.companyTotal.closedBoxes > 0
    );
  }

  protected hasCashMovementsData(): boolean {
    const report = this.cashMovementsReport;

    return (
      report.branches.some((branch) => branch.movements.length > 0) ||
      report.companyTotal.totalMovements > 0
    );
  }

  protected formatCurrency(value: number): string {
    return new Intl.NumberFormat('es-BO', {
      style: 'currency',
      currency: 'BOB',
      minimumFractionDigits: 2,
    }).format(value ?? 0);
  }

  protected formatReportDate(value: string): string {
    const date = new Date(`${value}T00:00:00`);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  }

  private loadSalesSummaryReport(): void {
    if (!this.companyId || this.loadingSalesSummary) {
      return;
    }

    this.loadingSalesSummary = true;
    this.salesSummaryError = '';
    this.salesSummaryReport = this.createEmptySalesSummaryReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/resumenventas`)
      .pipe(
        finalize(() => {
          this.loadingSalesSummary = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original resumen de ventas:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<SalesSummaryReportResponse>(report);

            console.log('[Reportes] Respuesta procesada resumen de ventas:', unwrapped);

            this.salesSummaryReport = this.mapSalesSummaryReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar el resumen de ventas:', error);
            this.salesSummaryReport = this.createEmptySalesSummaryReport();
            this.salesSummaryError = 'El servidor respondio, pero los datos del resumen de ventas no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP resumen de ventas:', error);
          this.salesSummaryReport = this.createEmptySalesSummaryReport();
          this.salesSummaryError = 'No se pudo generar el resumen de ventas. Intenta nuevamente.';
        },
      });
  }

  private loadSalesDetailsReport(): void {
    if (!this.companyId || this.loadingSalesDetails) {
      return;
    }

    this.loadingSalesDetails = true;
    this.salesDetailsError = '';
    this.salesDetailsReport = this.createEmptySalesDetailsReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/detallesventas`)
      .pipe(
        finalize(() => {
          this.loadingSalesDetails = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original detalle de ventas:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<SalesDetailsReportResponse>(report);

            console.log('[Reportes] Respuesta procesada detalle de ventas:', unwrapped);

            this.salesDetailsReport = this.mapSalesDetailsReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar el detalle de ventas:', error);
            this.salesDetailsReport = this.createEmptySalesDetailsReport();
            this.salesDetailsError = 'El servidor respondio, pero los datos del detalle de ventas no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP detalle de ventas:', error);
          this.salesDetailsReport = this.createEmptySalesDetailsReport();
          this.salesDetailsError = 'No se pudo generar el detalle de ventas. Intenta nuevamente.';
        },
      });
  }

  private loadInventoryStatusReport(): void {
    if (!this.companyId || this.loadingInventoryStatus) {
      return;
    }

    this.loadingInventoryStatus = true;
    this.inventoryStatusError = '';
    this.inventoryStatusReport = this.createEmptyInventoryStatusReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/estadoinventario`)
      .pipe(
        finalize(() => {
          this.loadingInventoryStatus = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original estado de inventario:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<InventoryStatusReportResponse>(report);

            console.log('[Reportes] Respuesta procesada estado de inventario:', unwrapped);

            this.inventoryStatusReport = this.mapInventoryStatusReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar el estado de inventario:', error);
            this.inventoryStatusReport = this.createEmptyInventoryStatusReport();
            this.inventoryStatusError = 'El servidor respondio, pero los datos del estado de inventario no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP estado de inventario:', error);
          this.inventoryStatusReport = this.createEmptyInventoryStatusReport();
          this.inventoryStatusError = 'No se pudo generar el estado de inventario. Intenta nuevamente.';
        },
      });
  }

  private loadInventoryMovementsReport(): void {
    if (!this.companyId || this.loadingInventoryMovements) {
      return;
    }

    this.loadingInventoryMovements = true;
    this.inventoryMovementsError = '';
    this.inventoryMovementsReport = this.createEmptyInventoryMovementsReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/movimientosinventario`)
      .pipe(
        finalize(() => {
          this.loadingInventoryMovements = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original movimientos de inventario:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<InventoryMovementsReportResponse>(report);

            console.log('[Reportes] Respuesta procesada movimientos de inventario:', unwrapped);

            this.inventoryMovementsReport = this.mapInventoryMovementsReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar los movimientos de inventario:', error);
            this.inventoryMovementsReport = this.createEmptyInventoryMovementsReport();
            this.inventoryMovementsError = 'El servidor respondio, pero los datos de movimientos de inventario no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP movimientos de inventario:', error);
          this.inventoryMovementsReport = this.createEmptyInventoryMovementsReport();
          this.inventoryMovementsError = 'No se pudieron generar los movimientos de inventario. Intenta nuevamente.';
        },
      });
  }

  private loadCashSummaryReport(): void {
    if (!this.companyId || this.loadingCashSummary) {
      return;
    }

    this.loadingCashSummary = true;
    this.cashSummaryError = '';
    this.cashSummaryReport = this.createEmptyCashSummaryReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/resumencajas`)
      .pipe(
        finalize(() => {
          this.loadingCashSummary = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original resumen de cajas:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<CashSummaryReportResponse>(report);

            console.log('[Reportes] Respuesta procesada resumen de cajas:', unwrapped);

            this.cashSummaryReport = this.mapCashSummaryReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar el resumen de cajas:', error);
            this.cashSummaryReport = this.createEmptyCashSummaryReport();
            this.cashSummaryError = 'El servidor respondio, pero los datos del resumen de cajas no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP resumen de cajas:', error);
          this.cashSummaryReport = this.createEmptyCashSummaryReport();
          this.cashSummaryError = 'No se pudo generar el resumen de cajas. Intenta nuevamente.';
        },
      });
  }

  private loadCashMovementsReport(): void {
    if (!this.companyId || this.loadingCashMovements) {
      return;
    }

    this.loadingCashMovements = true;
    this.cashMovementsError = '';
    this.cashMovementsReport = this.createEmptyCashMovementsReport();

    this.apiService
      .get<unknown>(`/api/reportes/${this.companyId}/movimientoscaja`)
      .pipe(
        finalize(() => {
          this.loadingCashMovements = false;
          this.refreshCurrentReportResult();
        }),
      )
      .subscribe({
        next: (report) => {
          console.log('[Reportes] Respuesta original movimientos de caja:', report);

          try {
            const unwrapped = this.unwrapStaticReportResponse<CashMovementsReportResponse>(report);

            console.log('[Reportes] Respuesta procesada movimientos de caja:', unwrapped);

            this.cashMovementsReport = this.mapCashMovementsReport(unwrapped);
          } catch (error) {
            console.error('[Reportes] Error al procesar los movimientos de caja:', error);
            this.cashMovementsReport = this.createEmptyCashMovementsReport();
            this.cashMovementsError = 'El servidor respondio, pero los datos de movimientos de caja no tienen el formato esperado.';
          }
        },
        error: (error) => {
          console.error('[Reportes] Error HTTP movimientos de caja:', error);
          this.cashMovementsReport = this.createEmptyCashMovementsReport();
          this.cashMovementsError = 'No se pudieron generar los movimientos de caja. Intenta nuevamente.';
        },
      });
  }

  private refreshCurrentReportResult(): void {
    this.cdr.detectChanges();

    setTimeout(() => {
      this.hostElement.nativeElement
        .querySelector('.report-result')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  private unwrapStaticReportResponse<TReport>(response: unknown): TReport {
    let current = response;

    for (let level = 0; level < 3; level++) {
      if (!current || typeof current !== 'object') {
        return {} as TReport;
      }

      const record = current as Record<string, unknown>;
      let nestedValue: unknown;

      for (const key of ['data', 'datos', 'reporte', 'report', 'resultado', 'result']) {
        const value = record[key];

        if (value && typeof value === 'object') {
          nestedValue = value;
          break;
        }
      }

      if (!nestedValue) {
        return current as TReport;
      }

      current = nestedValue;
    }

    return current as TReport;
  }

  private mapSalesSummaryReport(report: SalesSummaryReportResponse): SalesSummaryReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_ventas: 0,
      monto_vendido: 0,
      ticket_promedio: 0,
      productos_vendidos: 0,
    };

    return {
      date: this.formatReportDate(report.fecha),
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        totalSales: branch.ventas_dia?.total_ventas ?? 0,
        amount: this.formatCurrency(branch.ventas_dia?.monto_vendido ?? 0),
        averageTicket: this.formatCurrency(branch.ventas_dia?.ticket_promedio ?? 0),
        productsSold: branch.ventas_dia?.productos_vendidos ?? 0,
        topProducts: (branch.top_productos ?? []).map((product) => ({
          rank: product.posicion,
          name: product.producto,
          quantity: product.unidades,
        })),
      })),
      companyTotal: {
        totalSales: totalEmpresa.total_ventas,
        amount: this.formatCurrency(totalEmpresa.monto_vendido),
        averageTicket: this.formatCurrency(totalEmpresa.ticket_promedio),
        productsSold: totalEmpresa.productos_vendidos,
      },
    };
  }

  private createEmptySalesSummaryReport(): SalesSummaryReportView {
    return {
      date: '',
      branches: [],
      companyTotal: {
        totalSales: 0,
        amount: this.formatCurrency(0),
        averageTicket: this.formatCurrency(0),
        productsSold: 0,
      },
    };
  }

  private mapSalesDetailsReport(report: SalesDetailsReportResponse): SalesDetailsReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_registros: 0,
      total_vendido: 0,
    };

    return {
      date: this.formatReportDate(report.fecha),
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        sales: (branch.ventas ?? []).map((sale) => ({
          id: sale.numero_venta,
          time: sale.hora,
          client: sale.cliente,
          subtotal: this.formatCurrency(sale.subtotal),
          discount: this.formatCurrency(sale.descuento),
          total: this.formatCurrency(sale.total),
        })),
        summary: {
          records: branch.resumen_sucursal?.total_registros ?? 0,
          total: this.formatCurrency(branch.resumen_sucursal?.total_vendido ?? 0),
        },
      })),
      companyTotal: {
        records: totalEmpresa.total_registros,
        total: this.formatCurrency(totalEmpresa.total_vendido),
      },
    };
  }

  private createEmptySalesDetailsReport(): SalesDetailsReportView {
    return {
      date: '',
      branches: [],
      companyTotal: {
        records: 0,
        total: this.formatCurrency(0),
      },
    };
  }

  private mapInventoryStatusReport(report: InventoryStatusReportResponse): InventoryStatusReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_productos: 0,
      productos_bajo_stock: 0,
      productos_sobre_stock: 0,
      productos_agotados: 0,
    };

    return {
      date: this.formatReportDate(report.fecha),
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        items: (branch.productos ?? []).map((product) => ({
          product: product.producto,
          actual: product.stock_actual,
          minimum: product.stock_minimo,
          maximum: product.stock_maximo ?? '-',
          status: product.estado,
        })),
        summary: {
          totalProducts: branch.resumen_sucursal?.total_productos ?? 0,
          lowStock: branch.resumen_sucursal?.productos_bajo_stock ?? 0,
          overStock: branch.resumen_sucursal?.productos_sobre_stock ?? 0,
          outOfStock: branch.resumen_sucursal?.productos_agotados ?? 0,
        },
      })),
      companyTotal: {
        totalProducts: totalEmpresa.total_productos,
        lowStock: totalEmpresa.productos_bajo_stock,
        overStock: totalEmpresa.productos_sobre_stock,
        outOfStock: totalEmpresa.productos_agotados,
      },
    };
  }

  private createEmptyInventoryStatusReport(): InventoryStatusReportView {
    return {
      date: '',
      branches: [],
      companyTotal: {
        totalProducts: 0,
        lowStock: 0,
        overStock: 0,
        outOfStock: 0,
      },
    };
  }

  private mapInventoryMovementsReport(report: InventoryMovementsReportResponse): InventoryMovementsReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_entradas: 0,
      total_salidas: 0,
    };

    return {
      period: `${this.formatReportDate(report.fecha_inicio)} - ${this.formatReportDate(report.fecha_fin)}`,
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        movements: (branch.movimientos ?? []).map((movement) => ({
          date: this.formatReportDate(movement.fecha),
          type: movement.tipo_movimiento,
          product: movement.producto,
          quantity: movement.cantidad,
        })),
        summary: {
          entries: branch.resumen_sucursal?.total_entradas ?? 0,
          exits: branch.resumen_sucursal?.total_salidas ?? 0,
        },
      })),
      companyTotal: {
        entries: totalEmpresa.total_entradas,
        exits: totalEmpresa.total_salidas,
      },
    };
  }

  private createEmptyInventoryMovementsReport(): InventoryMovementsReportView {
    return {
      period: '',
      branches: [],
      companyTotal: {
        entries: 0,
        exits: 0,
      },
    };
  }

  private mapCashSummaryReport(report: CashSummaryReportResponse): CashSummaryReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_cajas: 0,
      cajas_abiertas: 0,
      cajas_cerradas: 0,
      ingresos: 0,
      egresos: 0,
      flujo_neto: 0,
    };

    return {
      date: this.formatReportDate(report.fecha),
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        boxes: (branch.cajas ?? []).map((box) => ({
          name: box.caja,
          state: box.estado,
          opening: box.apertura,
          closing: box.cierre ?? '--',
        })),
        summary: {
          totalBoxes: branch.resumen_sucursal?.total_cajas ?? 0,
          openBoxes: branch.resumen_sucursal?.cajas_abiertas ?? 0,
          closedBoxes: branch.resumen_sucursal?.cajas_cerradas ?? 0,
          income: this.formatCurrency(branch.resumen_sucursal?.ingresos ?? 0),
          expenses: this.formatCurrency(branch.resumen_sucursal?.egresos ?? 0),
          netFlow: this.formatCurrency(branch.resumen_sucursal?.flujo_neto ?? 0),
        },
      })),
      companyTotal: {
        totalBoxes: totalEmpresa.total_cajas,
        openBoxes: totalEmpresa.cajas_abiertas,
        closedBoxes: totalEmpresa.cajas_cerradas,
        income: this.formatCurrency(totalEmpresa.ingresos),
        expenses: this.formatCurrency(totalEmpresa.egresos),
        netFlow: this.formatCurrency(totalEmpresa.flujo_neto),
      },
    };
  }

  private createEmptyCashSummaryReport(): CashSummaryReportView {
    return {
      date: '',
      branches: [],
      companyTotal: {
        totalBoxes: 0,
        openBoxes: 0,
        closedBoxes: 0,
        income: this.formatCurrency(0),
        expenses: this.formatCurrency(0),
        netFlow: this.formatCurrency(0),
      },
    };
  }

  private mapCashMovementsReport(report: CashMovementsReportResponse): CashMovementsReportView {
    const totalEmpresa = report.total_empresa ?? {
      total_movimientos: 0,
      total_ingresos: 0,
      total_egresos: 0,
    };

    return {
      date: this.formatReportDate(report.fecha),
      branches: (report.sucursales ?? []).map((branch) => ({
        name: branch.sucursal,
        movements: (branch.movimientos ?? []).map((movement) => ({
          time: movement.hora,
          box: movement.caja,
          type: movement.tipo,
          concept: movement.concepto,
          amount: this.formatCurrency(movement.monto),
        })),
        summary: {
          totalMovements: branch.resumen_sucursal?.total_movimientos ?? 0,
          totalIncome: this.formatCurrency(branch.resumen_sucursal?.total_ingresos ?? 0),
          totalExpenses: this.formatCurrency(branch.resumen_sucursal?.total_egresos ?? 0),
        },
      })),
      companyTotal: {
        totalMovements: totalEmpresa.total_movimientos,
        totalIncome: this.formatCurrency(totalEmpresa.total_ingresos),
        totalExpenses: this.formatCurrency(totalEmpresa.total_egresos),
      },
    };
  }

  private createEmptyCashMovementsReport(): CashMovementsReportView {
    return {
      date: '',
      branches: [],
      companyTotal: {
        totalMovements: 0,
        totalIncome: this.formatCurrency(0),
        totalExpenses: this.formatCurrency(0),
      },
    };
  }

  // --- Export Actions ---

  protected exportSalesSummaryReport(format: 'pdf' | 'excel'): void {
    console.log('exportSalesSummaryReport clicked with format:', format);
    if (!this.salesSummaryReport) {
      console.warn('exportSalesSummaryReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportSalesSummaryReportPdf();
    } else {
      this.exportSalesSummaryReportExcel();
    }
  }

  private exportSalesSummaryReportPdf(): void {
    const r = this.salesSummaryReport;
    const lines: string[] = [
      'REPORTE ESTATICO: RESUMEN DE VENTAS',
      '===================================',
      `Fecha: ${r.date}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push(`- Ventas del dia: ${branch.totalSales}`);
      lines.push(`- Monto vendido: ${branch.amount}`);
      lines.push(`- Ticket promedio: ${branch.averageTicket}`);
      lines.push(`- Productos vendidos: ${branch.productsSold}`);
      lines.push('');
      lines.push('  TOP PRODUCTOS:');
      branch.topProducts.forEach((p) => {
        lines.push(`  ${p.rank}. ${p.name} (${p.quantity} unid.)`);
      });
      lines.push('----------------------------------------');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total de ventas: ${r.companyTotal.totalSales}`);
    lines.push(`- Monto vendido: ${r.companyTotal.amount}`);
    lines.push(`- Ticket promedio: ${r.companyTotal.averageTicket}`);
    lines.push(`- Productos vendidos: ${r.companyTotal.productsSold}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('resumen-ventas')}.pdf`);
  }

  private exportSalesSummaryReportExcel(): void {
    const r = this.salesSummaryReport;
    const branchRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.totalSales}</td>
        <td style="text-align:right;">${this.escapeHtml(b.amount)}</td>
        <td style="text-align:right;">${this.escapeHtml(b.averageTicket)}</td>
        <td style="text-align:right;">${b.productsSold}</td>
      </tr>
    `).join('');

    const topProductsRows = r.branches.flatMap((b) => 
      b.topProducts.map((p) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td style="text-align:center;">${p.rank}</td>
          <td>${this.escapeHtml(p.name)}</td>
          <td style="text-align:right;">${p.quantity}</td>
        </tr>
      `)
    ).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="5" class="title-cell">Reporte Estatico: Resumen de Ventas</td></tr>
    <tr><td colspan="5">Fecha: ${this.escapeHtml(r.date)}</td></tr>
  </table>

  <h3>Resumen por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Ventas</th>
        <th>Monto Vendido</th>
        <th>Ticket Promedio</th>
        <th>Productos Vendidos</th>
      </tr>
    </thead>
    <tbody>
      ${branchRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.totalSales}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.amount)}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.averageTicket)}</td>
        <td style="text-align:right;">${r.companyTotal.productsSold}</td>
      </tr>
    </tbody>
  </table>

  <h3>Top Productos por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Posicion</th>
        <th>Producto</th>
        <th>Unidades</th>
      </tr>
    </thead>
    <tbody>
      ${topProductsRows}
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('resumen-ventas')}.xls`
    );
  }

  protected exportSalesDetailsReport(format: 'pdf' | 'excel'): void {
    console.log('exportSalesDetailsReport clicked with format:', format);
    if (!this.salesDetailsReport) {
      console.warn('exportSalesDetailsReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportSalesDetailsReportPdf();
    } else {
      this.exportSalesDetailsReportExcel();
    }
  }

  private exportSalesDetailsReportPdf(): void {
    const r = this.salesDetailsReport;
    const lines: string[] = [
      'REPORTE ESTATICO: DETALLE DE VENTAS',
      '===================================',
      `Fecha: ${r.date}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push('N. Venta | Hora | Cliente | Subtotal | Descuento | Total');
      lines.push('--------------------------------------------------------------------------------------------');
      branch.sales.forEach((s) => {
        lines.push(`${s.id} | ${s.time} | ${s.client} | ${s.subtotal} | ${s.discount} | ${s.total}`);
      });
      lines.push('');
      lines.push(`Resumen ${branch.name}:`);
      lines.push(`- Total registros: ${branch.summary.records}`);
      lines.push(`- Total vendido: ${branch.summary.total}`);
      lines.push('============================================================================================');
      lines.push('');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total registros: ${r.companyTotal.records}`);
    lines.push(`- Total vendido: ${r.companyTotal.total}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('detalle-ventas')}.pdf`);
  }

  private exportSalesDetailsReportExcel(): void {
    const r = this.salesDetailsReport;
    const rows = r.branches.flatMap((b) => 
      b.sales.map((s) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td>${this.escapeHtml(s.id)}</td>
          <td>${this.escapeHtml(s.time)}</td>
          <td>${this.escapeHtml(s.client)}</td>
          <td style="text-align:right;">${this.escapeHtml(s.subtotal)}</td>
          <td style="text-align:right;">${this.escapeHtml(s.discount)}</td>
          <td style="text-align:right;">${this.escapeHtml(s.total)}</td>
        </tr>
      `)
    ).join('');

    const branchSummaryRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.summary.records}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.total)}</td>
      </tr>
    `).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="7" class="title-cell">Reporte Estatico: Detalle de Ventas</td></tr>
    <tr><td colspan="7">Fecha: ${this.escapeHtml(r.date)}</td></tr>
  </table>

  <h3>Detalle Analitico de Ventas</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>N. Venta</th>
        <th>Hora</th>
        <th>Cliente</th>
        <th>Subtotal</th>
        <th>Descuento</th>
        <th>Total</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <h3>Resumen por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Registros</th>
        <th>Total Vendido</th>
      </tr>
    </thead>
    <tbody>
      ${branchSummaryRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.records}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.total)}</td>
      </tr>
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('detalle-ventas')}.xls`
    );
  }

  protected exportInventoryStatusReport(format: 'pdf' | 'excel'): void {
    console.log('exportInventoryStatusReport clicked with format:', format);
    if (!this.inventoryStatusReport) {
      console.warn('exportInventoryStatusReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportInventoryStatusReportPdf();
    } else {
      this.exportInventoryStatusReportExcel();
    }
  }

  private exportInventoryStatusReportPdf(): void {
    const r = this.inventoryStatusReport;
    const lines: string[] = [
      'REPORTE ESTATICO: ESTADO DE INVENTARIO ACTUAL',
      '=============================================',
      `Fecha: ${r.date}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push('Producto | Stock Actual | Stock Minimo | Stock Maximo | Estado');
      lines.push('--------------------------------------------------------------------------------------------');
      branch.items.forEach((item) => {
        lines.push(`${item.product} | ${item.actual} | ${item.minimum} | ${item.maximum} | ${item.status}`);
      });
      lines.push('');
      lines.push(`Resumen ${branch.name}:`);
      lines.push(`- Total productos: ${branch.summary.totalProducts}`);
      lines.push(`- Productos con stock bajo: ${branch.summary.lowStock}`);
      lines.push(`- Productos con sobre stock: ${branch.summary.overStock}`);
      lines.push(`- Productos agotados: ${branch.summary.outOfStock}`);
      lines.push('============================================================================================');
      lines.push('');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total productos: ${r.companyTotal.totalProducts}`);
    lines.push(`- Productos con stock bajo: ${r.companyTotal.lowStock}`);
    lines.push(`- Productos con sobre stock: ${r.companyTotal.overStock}`);
    lines.push(`- Productos agotados: ${r.companyTotal.outOfStock}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('estado-inventario')}.pdf`);
  }

  private exportInventoryStatusReportExcel(): void {
    const r = this.inventoryStatusReport;
    const rows = r.branches.flatMap((b) => 
      b.items.map((i) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td>${this.escapeHtml(i.product)}</td>
          <td style="text-align:right;">${i.actual}</td>
          <td style="text-align:right;">${i.minimum}</td>
          <td style="text-align:right;">${this.escapeHtml(String(i.maximum))}</td>
          <td>${this.escapeHtml(i.status)}</td>
        </tr>
      `)
    ).join('');

    const summaryRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.summary.totalProducts}</td>
        <td style="text-align:right;">${b.summary.lowStock}</td>
        <td style="text-align:right;">${b.summary.overStock}</td>
        <td style="text-align:right;">${b.summary.outOfStock}</td>
      </tr>
    `).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="6" class="title-cell">Reporte Estatico: Estado de Inventario Actual</td></tr>
    <tr><td colspan="6">Fecha: ${this.escapeHtml(r.date)}</td></tr>
  </table>

  <h3>Detalle de Inventario por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Producto</th>
        <th>Stock Actual</th>
        <th>Stock Minimo</th>
        <th>Stock Maximo</th>
        <th>Estado</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <h3>Resumen de Inventario</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Productos</th>
        <th>Bajo Stock</th>
        <th>Sobre Stock</th>
        <th>Agotados</th>
      </tr>
    </thead>
    <tbody>
      ${summaryRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.totalProducts}</td>
        <td style="text-align:right;">${r.companyTotal.lowStock}</td>
        <td style="text-align:right;">${r.companyTotal.overStock}</td>
        <td style="text-align:right;">${r.companyTotal.outOfStock}</td>
      </tr>
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('estado-inventario')}.xls`
    );
  }

  protected exportInventoryMovementsReport(format: 'pdf' | 'excel'): void {
    console.log('exportInventoryMovementsReport clicked with format:', format);
    if (!this.inventoryMovementsReport) {
      console.warn('exportInventoryMovementsReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportInventoryMovementsReportPdf();
    } else {
      this.exportInventoryMovementsReportExcel();
    }
  }

  private exportInventoryMovementsReportPdf(): void {
    const r = this.inventoryMovementsReport;
    const lines: string[] = [
      'REPORTE ESTATICO: MOVIMIENTOS DE INVENTARIO',
      '==========================================',
      `Periodo: ${r.period}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push('Fecha | Tipo Movimiento | Producto | Cantidad');
      lines.push('--------------------------------------------------------------------------------------------');
      branch.movements.forEach((m) => {
        lines.push(`${m.date} | ${m.type} | ${m.product} | ${m.quantity}`);
      });
      lines.push('');
      lines.push(`Resumen ${branch.name}:`);
      lines.push(`- Total entradas: ${branch.summary.entries}`);
      lines.push(`- Total salidas: ${branch.summary.exits}`);
      lines.push('============================================================================================');
      lines.push('');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total entradas: ${r.companyTotal.entries}`);
    lines.push(`- Total salidas: ${r.companyTotal.exits}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('movimientos-inventario')}.pdf`);
  }

  private exportInventoryMovementsReportExcel(): void {
    const r = this.inventoryMovementsReport;
    const rows = r.branches.flatMap((b) => 
      b.movements.map((m) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td>${this.escapeHtml(m.date)}</td>
          <td>${this.escapeHtml(m.type)}</td>
          <td>${this.escapeHtml(m.product)}</td>
          <td style="text-align:right;">${m.quantity}</td>
        </tr>
      `)
    ).join('');

    const summaryRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.summary.entries}</td>
        <td style="text-align:right;">${b.summary.exits}</td>
      </tr>
    `).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="5" class="title-cell">Reporte Estatico: Movimientos de Inventario</td></tr>
    <tr><td colspan="5">Periodo: ${this.escapeHtml(r.period)}</td></tr>
  </table>

  <h3>Detalle de Movimientos por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Fecha</th>
        <th>Tipo Movimiento</th>
        <th>Producto</th>
        <th>Cantidad</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <h3>Resumen de Movimientos</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Entradas</th>
        <th>Total Salidas</th>
      </tr>
    </thead>
    <tbody>
      ${summaryRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.entries}</td>
        <td style="text-align:right;">${r.companyTotal.exits}</td>
      </tr>
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('movimientos-inventario')}.xls`
    );
  }

  protected exportCashSummaryReport(format: 'pdf' | 'excel'): void {
    console.log('exportCashSummaryReport clicked with format:', format);
    if (!this.cashSummaryReport) {
      console.warn('exportCashSummaryReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportCashSummaryReportPdf();
    } else {
      this.exportCashSummaryReportExcel();
    }
  }

  private exportCashSummaryReportPdf(): void {
    const r = this.cashSummaryReport;
    const lines: string[] = [
      'REPORTE ESTATICO: RESUMEN DE SESIONES DE CAJA',
      '============================================',
      `Fecha: ${r.date}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push('Caja | Estado | Apertura | Cierre');
      lines.push('--------------------------------------------------------------------------------------------');
      branch.boxes.forEach((box) => {
        lines.push(`${box.name} | ${box.state} | ${box.opening} | ${box.closing}`);
      });
      lines.push('');
      lines.push(`Resumen ${branch.name}:`);
      lines.push(`- Total cajas: ${branch.summary.totalBoxes}`);
      lines.push(`- Cajas abiertas: ${branch.summary.openBoxes}`);
      lines.push(`- Cajas cerradas: ${branch.summary.closedBoxes}`);
      lines.push(`- Ingresos: ${branch.summary.income}`);
      lines.push(`- Egresos: ${branch.summary.expenses}`);
      lines.push(`- Flujo neto: ${branch.summary.netFlow}`);
      lines.push('============================================================================================');
      lines.push('');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total cajas: ${r.companyTotal.totalBoxes}`);
    lines.push(`- Cajas abiertas: ${r.companyTotal.openBoxes}`);
    lines.push(`- Cajas cerradas: ${r.companyTotal.closedBoxes}`);
    lines.push(`- Ingresos: ${r.companyTotal.income}`);
    lines.push(`- Egresos: ${r.companyTotal.expenses}`);
    lines.push(`- Flujo neto: ${r.companyTotal.netFlow}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('resumen-cajas')}.pdf`);
  }

  private exportCashSummaryReportExcel(): void {
    const r = this.cashSummaryReport;
    const rows = r.branches.flatMap((b) => 
      b.boxes.map((box) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td>${this.escapeHtml(box.name)}</td>
          <td>${this.escapeHtml(box.state)}</td>
          <td>${this.escapeHtml(box.opening)}</td>
          <td>${this.escapeHtml(box.closing)}</td>
        </tr>
      `)
    ).join('');

    const summaryRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.summary.totalBoxes}</td>
        <td style="text-align:right;">${b.summary.openBoxes}</td>
        <td style="text-align:right;">${b.summary.closedBoxes}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.income)}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.expenses)}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.netFlow)}</td>
      </tr>
    `).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="5" class="title-cell">Reporte Estatico: Resumen de Cajas</td></tr>
    <tr><td colspan="5">Fecha: ${this.escapeHtml(r.date)}</td></tr>
  </table>

  <h3>Estado de Cajas por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Caja</th>
        <th>Estado</th>
        <th>Apertura</th>
        <th>Cierre</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <h3>Resumen Financiero de Cajas</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Cajas</th>
        <th>Abiertas</th>
        <th>Cerradas</th>
        <th>Ingresos</th>
        <th>Egresos</th>
        <th>Flujo Neto</th>
      </tr>
    </thead>
    <tbody>
      ${summaryRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.totalBoxes}</td>
        <td style="text-align:right;">${r.companyTotal.openBoxes}</td>
        <td style="text-align:right;">${r.companyTotal.closedBoxes}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.income)}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.expenses)}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.netFlow)}</td>
      </tr>
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('resumen-cajas')}.xls`
    );
  }

  protected exportCashMovementsReport(format: 'pdf' | 'excel'): void {
    console.log('exportCashMovementsReport clicked with format:', format);
    if (!this.cashMovementsReport) {
      console.warn('exportCashMovementsReport: data is null');
      return;
    }
    if (format === 'pdf') {
      this.exportCashMovementsReportPdf();
    } else {
      this.exportCashMovementsReportExcel();
    }
  }

  private exportCashMovementsReportPdf(): void {
    const r = this.cashMovementsReport;
    const lines: string[] = [
      'REPORTE ESTATICO: MOVIMIENTOS DE CAJA',
      '=====================================',
      `Fecha: ${r.date}`,
      ''
    ];

    r.branches.forEach((branch) => {
      lines.push(`SUCURSAL: ${branch.name}`);
      lines.push('Hora | Caja | Tipo | Concepto | Monto');
      lines.push('--------------------------------------------------------------------------------------------');
      branch.movements.forEach((m) => {
        lines.push(`${m.time} | ${m.box} | ${m.type} | ${m.concept} | ${m.amount}`);
      });
      lines.push('');
      lines.push(`Resumen ${branch.name}:`);
      lines.push(`- Total movimientos: ${branch.summary.totalMovements}`);
      lines.push(`- Total ingresos: ${branch.summary.totalIncome}`);
      lines.push(`- Total egresos: ${branch.summary.totalExpenses}`);
      lines.push('============================================================================================');
      lines.push('');
    });

    lines.push('TOTAL EMPRESA');
    lines.push(`- Total movimientos: ${r.companyTotal.totalMovements}`);
    lines.push(`- Total ingresos: ${r.companyTotal.totalIncome}`);
    lines.push(`- Total egresos: ${r.companyTotal.totalExpenses}`);

    const pdf = this.createPdfDocument(lines);
    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify('movimientos-caja')}.pdf`);
  }

  private exportCashMovementsReportExcel(): void {
    const r = this.cashMovementsReport;
    const rows = r.branches.flatMap((b) => 
      b.movements.map((m) => `
        <tr>
          <td>${this.escapeHtml(b.name)}</td>
          <td>${this.escapeHtml(m.time)}</td>
          <td>${this.escapeHtml(m.box)}</td>
          <td>${this.escapeHtml(m.type)}</td>
          <td>${this.escapeHtml(m.concept)}</td>
          <td style="text-align:right;">${this.escapeHtml(m.amount)}</td>
        </tr>
      `)
    ).join('');

    const summaryRows = r.branches.map((b) => `
      <tr>
        <td>${this.escapeHtml(b.name)}</td>
        <td style="text-align:right;">${b.summary.totalMovements}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.totalIncome)}</td>
        <td style="text-align:right;">${this.escapeHtml(b.summary.totalExpenses)}</td>
      </tr>
    `).join('');

    const worksheet = `<!doctype html>
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
    <tr><td colspan="6" class="title-cell">Reporte Estatico: Movimientos de Caja</td></tr>
    <tr><td colspan="6">Fecha: ${this.escapeHtml(r.date)}</td></tr>
  </table>

  <h3>Detalle de Movimientos por Sucursal</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Hora</th>
        <th>Caja</th>
        <th>Tipo</th>
        <th>Concepto</th>
        <th>Monto</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <h3>Resumen de Movimientos</h3>
  <table>
    <thead>
      <tr>
        <th>Sucursal</th>
        <th>Total Movimientos</th>
        <th>Total Ingresos</th>
        <th>Total Egresos</th>
      </tr>
    </thead>
    <tbody>
      ${summaryRows}
      <tr style="font-weight:bold; background:#f1f5f9;">
        <td>TOTAL EMPRESA</td>
        <td style="text-align:right;">${r.companyTotal.totalMovements}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.totalIncome)}</td>
        <td style="text-align:right;">${this.escapeHtml(r.companyTotal.totalExpenses)}</td>
      </tr>
    </tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify('movimientos-caja')}.xls`
    );
  }

  // --- PDF & Excel Helpers ---

  private buildExcelHtml(
    title: string,
    report: any,
    headerCells: string,
    bodyRows: string,
    summaryItems: { label: string; value: any }[]
  ): string {
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
    <tr><td colspan="4">Fecha: ${this.escapeHtml(report.date || report.period || '')}</td></tr>
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
}


