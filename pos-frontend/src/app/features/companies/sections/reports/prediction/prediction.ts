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
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly apiService = inject(ApiService);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
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
}
