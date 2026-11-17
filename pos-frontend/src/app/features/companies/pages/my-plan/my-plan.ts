import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Company, CompanyService } from '../../../../core/services/company.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';

/** HU-29: vista "Mi Plan" del administrador de empresa (plan activo o último vencido). */
@Component({
  selector: 'app-my-plan',
  imports: [Navbar, RouterLink],
  templateUrl: './my-plan.html',
})
export class MyPlan implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected company: Company | null = null;
  protected plan: { estado: string; fecha_fin: string | null; plan_nombre: string } | null = null;
  protected cargando = true;
  protected error = '';

  ngOnInit(): void {
    const id = String(this.route.snapshot.paramMap.get('empresa') ?? '');
    this.companyService.getMisEmpresas().subscribe({
      next: (companies) => {
        this.company =
          companies.find(
            (c) => String(c.idEmpresa ?? c.id_empresa ?? c.empresa_id ?? c.id ?? '') === id,
          ) ?? null;
        if (!this.company) {
          this.error = 'Empresa no encontrada o no eres su administrador.';
          this.cargando = false;
          this.cdr.markForCheck();
          return;
        }
        this.companyService.getMiPlan(id).subscribe({
          next: (plan) => {
            this.plan = plan ?? null;
            this.cargando = false;
            this.cdr.markForCheck();
          },
          error: () => {
            this.error = 'No se pudo cargar el plan.';
            this.cargando = false;
            this.cdr.markForCheck();
          },
        });
      },
      error: () => {
        this.error = 'No se pudo cargar el plan.';
        this.cargando = false;
        this.cdr.markForCheck();
      },
    });
  }

  protected get vigente(): boolean {
    return (
      this.company?.activo !== false &&
      !!this.plan &&
      ['activo', 'activa'].includes(this.plan.estado.toLowerCase())
    );
  }

  protected get estado(): string {
    if (!this.company) return '';
    if (this.company.activo === false) return 'Suspendida por el administrador del sistema';
    if (!this.plan) return 'Sin plan';
    return this.vigente ? 'Activo' : 'Suspendida (plan vencido)';
  }
}
