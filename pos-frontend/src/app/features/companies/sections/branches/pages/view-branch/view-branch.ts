import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Branch, CompanyService } from '../../../../../../core/services/company.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-view-branch',
  imports: [Navbar, Sidebar, RouterLink],
  templateUrl: './view-branch.html',
  styleUrl: './view-branch.css',
})
export class ViewBranch implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected cargandoSucursal = false;
  protected errorSucursal = '';
  protected branch: Branch = {
    nombre: '',
    direccion: '',
    telefono: '',
    ciudad: '',
    activo: true,
    fecha_registro: '',
  };

  ngOnInit(): void {
    this.cargarSucursal();
  }

  private cargarSucursal(): void {
    this.errorSucursal = '';

    if (!this.companyId || !this.branchId) {
      this.errorSucursal = 'No se encontro la sucursal solicitada.';
      return;
    }

    this.cargandoSucursal = true;

    this.companyService.obtenerSucursal(this.companyId, this.branchId).subscribe({
      next: (branch) => {
        this.branch = branch;
        this.cargandoSucursal = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoSucursal = false;
        this.errorSucursal = 'No se pudieron cargar los datos de la sucursal.';
        this.cdr.detectChanges();
      },
    });
  }
}

