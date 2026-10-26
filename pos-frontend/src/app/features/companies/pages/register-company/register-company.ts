import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CompanyService } from '../../../../core/services/company.service';
import { PagoService, CompraPlanDisponible } from '../../../../core/services/pago.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-register-company',
  imports: [FormsModule, Navbar, RouterLink],
  templateUrl: './register-company.html',
  styleUrl: './register-company.css',
})
export class RegisterCompany {
  protected readonly form = { nombre: '', razon_social: '', nit: '', correo: '' };
  protected compras: CompraPlanDisponible[] = [];
  protected compraId: number | null = null;
  protected cargando = false;
  protected cargandoPlanes = true;
  protected error = '';

  constructor(
    private readonly companies: CompanyService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
    pago: PagoService,
    route: ActivatedRoute,
  ) {
    const requested = Number(route.snapshot.queryParamMap.get('compra'));
    pago.getPlanesDisponibles().subscribe({
      next: (compras) => {
        this.compras = compras;
        this.compraId =
          compras.find((c) => c.id_compra_plan === requested)?.id_compra_plan ??
          compras[0]?.id_compra_plan ??
          null;
        this.cargandoPlanes = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.cargandoPlanes = false;
        this.error =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : 'No se pudieron cargar tus planes adquiridos. Intenta nuevamente desde Mis empresas.';
        this.cdr.markForCheck();
      },
    });
  }

  protected registrarEmpresa(event: SubmitEvent): void {
    event.preventDefault();
    if (this.cargando || !this.compraId) return;
    this.error = '';
    this.cargando = true;
    this.companies
      .crearEmpresa({
        nombre: this.form.nombre.trim(),
        razon_social: this.form.razon_social.trim(),
        nit: this.form.nit.trim(),
        correo: this.form.correo.trim(),
        id_compra_plan: this.compraId,
      })
      .subscribe({
        next: (response) => {
          const raw = response as unknown as { empresa?: Record<string, unknown> } & Record<
            string,
            unknown
          >;
          const company = raw.empresa ?? raw;
          const id = Number(company['id_empresa'] ?? company['idEmpresa'] ?? company['id']);
          void this.router.navigate(
            Number.isSafeInteger(id) && id > 0 ? ['/company', id, 'branches'] : ['/my-companies'],
          );
        },
        error: (err) => {
          this.cargando = false;
          this.error =
            typeof err.error?.detail === 'string'
              ? err.error.detail
              : 'No se pudo registrar la empresa. Tu plan sigue disponible.';
          this.cdr.markForCheck();
        },
      });
  }
}
