import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { TranslatePipe } from '../../../../core/i18n/translate.pipe';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Navbar } from '../../../../shared/components/navbar/navbar';

import { Company, CompanyService } from '../../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../../core/services/company-permissions.service';
import { AuthService } from '../../../../core/services/auth.service';
import { PlanSelectorModalComponent } from '../../sections/payments/plan-selector-modal/plan-selector-modal';
import { PagoService, CompraPlanDisponible } from '../../../../core/services/pago.service';

@Component({
  selector: 'app-my-companies',
  standalone: true,
  imports: [TranslatePipe, Navbar, RouterLink, PlanSelectorModalComponent],
  templateUrl: './my-companies.html',
  styleUrl: './my-companies.css',
})
export class MyCompanies implements OnInit {
  protected avisoPlan = '';
  protected planesDisponibles: CompraPlanDisponible[] = [];
  protected companies: Company[] = [];
  protected cargandoEmpresas = false;
  protected errorEmpresas = '';
  protected empresaEntrandoId: string | number | null = null;
  protected empresaSeleccionadaId: number | null = null;
  protected readonly esSuperAdmin = inject(AuthService).isSuperAdmin();

  constructor(
    private readonly companyService: CompanyService,
    private readonly companyPermissionsService: CompanyPermissionsService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
    route: ActivatedRoute,
    private readonly pago: PagoService,
  ) {
    if (route.snapshot.queryParamMap.has('activar'))
      this.avisoPlan =
        'Activa un plan para acceder a tu empresa. Puedes elegir el gratuito sin tarjeta.';
    if (route.snapshot.queryParamMap.has('ampliar'))
      this.avisoPlan = 'La función elegida requiere un plan superior.';
  }

  abrirModalPago(idEmpresa: string | number): void {
    this.empresaSeleccionadaId =
      typeof idEmpresa === 'string' ? parseInt(idEmpresa, 10) : idEmpresa;
  }

  cerrarModalPago(): void {
    this.empresaSeleccionadaId = null;
  }

  ngOnInit(): void {
    this.cargarMisEmpresas();
    this.pago.getPlanesDisponibles().subscribe({
      next: (compras) => {
        this.planesDisponibles = compras;
        this.cdr.markForCheck();
      },
      error: () => {
        this.cdr.markForCheck();
      },
    });
  }

  protected obtenerIdEmpresa(company: Company): string | number {
    return (
      company.idEmpresa ?? company.id_empresa ?? company.empresa_id ?? company.id ?? company.nit
    );
  }

  protected tienePlanActivo(company: Company): boolean {
    const estado = company.suscripcion_activa?.estado?.toLowerCase();

    return estado === 'activa' || estado === 'activo';
  }

  protected entrarEmpresa(company: Company): void {
    const idEmpresa = this.obtenerIdEmpresa(company);

    this.empresaEntrandoId = idEmpresa;
    this.errorEmpresas = '';

    this.companyService.getMisPermisos(idEmpresa).subscribe({
      next: (response) => {
        this.companyPermissionsService.savePermissions(response.permisos);
        this.companyPermissionsService.saveActiveSubscription(response.suscripcion_activa);
        this.empresaEntrandoId = null;
        this.cdr.detectChanges();
        void this.router.navigate(['/company', idEmpresa, 'branches']);
      },
      error: () => {
        this.companyPermissionsService.clearPermissions();
        this.empresaEntrandoId = null;
        this.errorEmpresas = 'No se pudieron cargar tus permisos. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarMisEmpresas(): void {
    this.cargandoEmpresas = true;
    this.errorEmpresas = '';

    this.companyService.getMisEmpresas().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.cargandoEmpresas = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.companies = [];
        this.cargandoEmpresas = false;
        this.errorEmpresas = 'No se pudieron cargar tus empresas. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }
}
