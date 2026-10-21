import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../../../shared/pipes/money.pipe';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CampoProducto,
  CamposProducto,
  CompanyPermissionCode,
  CompanyPermissionsService,
  EstadoCampoProducto,
  camposProductoPorDefecto,
} from '../../../../../../../core/services/company-permissions.service';
import {
  ProductService,
  Producto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';
import { environments } from '../../../../../../../environments/environments';

type ProductoConCosto = Producto & { costo?: number | null };

@Component({
  selector: 'app-view-product',
  standalone: true,
  imports: [MoneyPipe, Navbar, Sidebar, RouterLink],
  templateUrl: './view-product.html',
  styleUrl: './view-product.css',
})
export class ViewProduct implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly productId = Number(this.route.snapshot.paramMap.get('productId') ?? 0);
  protected cargandoProducto = false;
  protected eliminando = false;
  protected errorProducto = '';
  protected producto: ProductoConCosto | null = null;
  protected camposProducto: CamposProducto = camposProductoPorDefecto();
  protected camposCargados = false;
  protected readonly apiBaseUrl = environments.apiBaseUrl;

  ngOnInit(): void {
    this.cargarCamposProducto();
    this.cargarProducto();
  }

  protected estadoCampo(campo: CampoProducto): EstadoCampoProducto {
    return this.camposProducto[campo];
  }

  protected mostrarCampo(campo: CampoProducto): boolean {
    return this.camposCargados && this.estadoCampo(campo) !== 'OCULTO';
  }

  protected soloLectura(campo: CampoProducto): boolean {
    return this.estadoCampo(campo) === 'SOLO_LECTURA';
  }

  protected eliminarProducto(): void {
    if (!this.producto || !this.camposCargados || this.estadoCampo('eliminar') !== 'EDITABLE' || this.eliminando) {
      return;
    }

    if (!confirm(`¿Eliminar el producto ${this.producto.nombre}?`)) {
      return;
    }

    this.eliminando = true;
    this.errorProducto = '';
    this.productService.eliminarProducto(this.productId).subscribe({
      next: () => {
        void this.router.navigate(['/company', this.companyId, 'products']);
      },
      error: () => {
        this.eliminando = false;
        this.errorProducto = 'No se pudo eliminar el producto.';
        this.cdr.detectChanges();
      },
    });
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    const permissions = this.companyPermissionsService.permissions();
    const codes = this.companyPermissionsService.getPermissionCodes();
    if (codes.length === 0) {
      return true;
    }
    return permissions[permission] === true;
  }

  private cargarCamposProducto(): void {
    if (!this.companyId) {
      this.camposProducto = camposProductoPorDefecto();
      this.camposCargados = true;
      return;
    }

    this.companyPermissionsService.obtenerMisCamposProducto(this.companyId).subscribe({
      next: (campos) => {
        this.camposProducto = campos;
        this.camposCargados = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.camposProducto = camposProductoPorDefecto();
        this.camposCargados = true;
        this.cdr.detectChanges();
      },
    });
  }

  private cargarProducto(): void {
    this.errorProducto = '';

    if (!this.productId) {
      this.errorProducto = 'No se encontro el producto solicitado.';
      return;
    }

    this.cargandoProducto = true;

    this.productService.obtenerProducto(this.productId).subscribe({
      next: (producto) => {
        this.producto = producto;
        this.cargandoProducto = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoProducto = false;
        this.errorProducto = 'No se pudieron cargar los datos del producto.';
        this.cdr.detectChanges();
      },
    });
  }
}
