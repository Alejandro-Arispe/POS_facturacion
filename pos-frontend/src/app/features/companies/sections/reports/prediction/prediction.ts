import { exportReportExcel, exportReportPdf, ReportTable } from '../../../../../shared/utils/report-export';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';
import { watchReadPermissions } from '../../../../../core/services/watch-read-permissions';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { Branch, CompanyService } from '../../../../../core/services/company.service';
import { ApiService } from '../../../../../core/services/api.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

interface SupplyPrediction {
  id_producto: number;
  producto: string;
  vendido_ultimos_30_dias: number;
  stock_actual: number;
  promedio_diario: number;
  prediccion_proximos_30_dias: number;
  recomendado_comprar: number;
}

@Component({
  selector: 'app-sales-prediction',
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './prediction.html',
  styleUrl: './prediction.css',
})
export class SalesPrediction implements OnInit {
  private readonly readSnapshot=watchReadPermissions(() => { this.predictionResults=null; });
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly apiService = inject(ApiService);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly permissions = inject(CompanyPermissionsService);
  protected branches: Branch[] = [];
  protected selectedBranchId = '';
  protected loadingBranches = false;
  protected branchesError = '';
  protected predictionResults: SupplyPrediction[] | null = null;
  protected loadingPrediction = false;
  protected predictionError = '';
  protected submitAttempted = false;

  ngOnInit(): void {
    this.loadBranches();
  }

  protected branchId(branch: Branch): string {
    return String(branch.idSucursal ?? branch.id_sucursal ?? branch.id ?? '');
  }

  protected generatePrediction(): void {
    this.submitAttempted = true;
    this.predictionError = '';

    const branchId = Number(this.selectedBranchId);
    if (!this.selectedBranchId || !Number.isInteger(branchId) || branchId <= 0) {
      return;
    }

    this.loadingPrediction = true;
    this.predictionResults = null;

    this.apiService
      .post<SupplyPrediction[], { id_sucursal: number }>(
        '/api/reportes/productos-abastecimiento',
        { id_sucursal: branchId },
      )
      .pipe(finalize(() => (this.loadingPrediction = false)))
      .subscribe({
        next: (results) => {
          this.predictionResults = results;
        },
        error: (error) => {
          this.predictionError =
            error?.error?.detail ?? 'No se pudo generar la predicción de ventas.';
        },
      });
  }

  private loadBranches(): void {
    if (!this.companyId) {
      this.branchesError = 'No se encontró la empresa para cargar sus sucursales.';
      return;
    }

    this.loadingBranches = true;
    this.branchesError = '';

    this.companyService
      .getSucursales(this.companyId)
      .pipe(finalize(() => (this.loadingBranches = false)))
      .subscribe({
        next: (branches) => {
          this.branches = branches.filter((branch) => branch.activo !== false);
        },
        error: () => {
          this.branchesError = 'No se pudieron cargar las sucursales de la empresa.';
        },
      });
  }

  /** HU-24: exporta la prediccion de abastecimiento con la sucursal como filtro. */
  protected exportPrediction(format: 'pdf' | 'excel'): void {
    if (!this.permissions.hasPermission('report:export') || !this.predictionResults) return;
    const branch = this.branches.find((b) => this.branchId(b) === String(this.selectedBranchId));
    const table: ReportTable = {
      title: 'Prediccion de abastecimiento (proximos 30 dias)',
      filters: [`Sucursal: ${branch?.nombre ?? this.selectedBranchId}`],
      headers: ['Producto', 'Vendido 30 d', 'Stock actual', 'Promedio diario', 'Prediccion 30 d', 'Comprar'],
      rows: this.predictionResults.map((p) => [p.producto, p.vendido_ultimos_30_dias, p.stock_actual, p.promedio_diario,
        p.prediccion_proximos_30_dias, p.recomendado_comprar]),
    };
    const file = 'prediccion-abastecimiento';
    if (format === 'pdf') exportReportPdf(table, file); else exportReportExcel(table, file);
  }
}
