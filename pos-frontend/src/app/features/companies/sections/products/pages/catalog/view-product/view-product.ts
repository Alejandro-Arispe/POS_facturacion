import { SecureSrc } from '../../../../../../../shared/directives/secure-src';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CompanyPermissionsService } from '../../../../../../../core/services/company-permissions.service';
import { ProductService, Producto } from '../../../../../../../core/services/product.service';
import { PRODUCT_FIELDS, ProductField } from '../../../../../../../core/services/product-fields';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';
import { MoneyPipe } from '../../../../../../../shared/pipes/money.pipe';
import { environments } from '../../../../../../../environments/environments';
import { FormsModule } from '@angular/forms';
import { CompanyService } from '../../../../../../../core/services/company.service';

@Component({
  selector: 'app-view-product',
  standalone: true,
  imports: [SecureSrc, Navbar, Sidebar, RouterLink, MoneyPipe, FormsModule],
  templateUrl: './view-product.html',
  styleUrl: './view-product.css',
})
export class ViewProduct implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);
  protected readonly permissions = inject(CompanyPermissionsService);
  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly productId = Number(this.route.snapshot.paramMap.get('productId'));
  protected readonly fields = PRODUCT_FIELDS;
  protected product: Producto | null = null;
  protected loading = true;
  protected deleting = false;
  protected error = '';
  protected canRead(field: ProductField) {
    return this.permissions.hasField('product', 'read', field);
  }
  protected imageUrl(path: string) {
    return path.startsWith('http') ? path : environments.apiBaseUrl + path;
  }
  protected value(field: ProductField) {
    if (!this.product || !this.canRead(field)) return null;
    return this.product[field] ?? null;
  }
  /** HU-07: stock mínimo por sucursal desde la ficha (reusa PUT /api/inventario/.../stock/{idProducto}). */
  private readonly companies = inject(CompanyService);
  protected sucursales: { id: number; nombre: string }[] = [];
  protected idSucursalMin: number | null = null;
  protected stockMinimo: number | null = null;
  private stockMaximo: number | null = null;
  protected minMessage = '';
  protected savingMin = false;
  protected loadMin() {
    this.minMessage = '';
    this.stockMinimo = null;
    this.stockMaximo = null;
    if (!this.idSucursalMin) return;
    this.api.getStockSucursal(this.companyId, String(this.idSucursalMin)).subscribe({
      next: (rows) => {
        const row = rows.find((r) => Number(r.id_producto) === this.productId);
        this.stockMinimo = row?.stock_minimo ?? 0;
        this.stockMaximo = row?.stock_maximo ?? null;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.minMessage = e?.error?.detail ?? 'No se pudo cargar el stock.';
        this.cdr.detectChanges();
      },
    });
  }
  protected saveMin() {
    const min = Number(this.stockMinimo);
    if (!this.idSucursalMin || !Number.isInteger(min) || min < 0) {
      this.minMessage = 'Ingresa un stock mínimo entero no negativo.';
      return;
    }
    const max = this.stockMaximo != null && this.stockMaximo >= min ? this.stockMaximo : undefined;
    this.savingMin = true;
    this.api
      .actualizarStockSucursal(this.companyId, String(this.idSucursalMin), this.productId, {
        stock_minimo: min,
        ...(max != null ? { stock_maximo: max } : {}),
      })
      .subscribe({
        next: () => {
          this.savingMin = false;
          this.minMessage = 'Stock mínimo actualizado.';
          this.cdr.detectChanges();
        },
        error: (e) => {
          this.savingMin = false;
          this.minMessage = e?.error?.detail ?? 'No se pudo actualizar el stock mínimo.';
          this.cdr.detectChanges();
        },
      });
  }
  ngOnInit() {
    if (this.permissions.hasPermission('inventory:configure')) {
      this.companies.getSucursales(this.companyId).subscribe({
        next: (branches) => {
          this.sucursales = branches
            .map((b) => ({ id: Number(b.id_sucursal ?? b.idSucursal ?? b.id), nombre: b.nombre }))
            .filter((b) => b.id > 0);
          this.cdr.detectChanges();
        },
      });
    }
    this.api.obtenerProducto(this.productId).subscribe({
      next: (p) => {
        this.product = p;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.error?.detail ?? 'No se pudo cargar el producto.';
        this.cdr.detectChanges();
      },
    });
  }
  protected remove() {
    if (
      !this.permissions.hasPermission('product:delete') ||
      this.deleting ||
      !confirm('¿Eliminar el producto #' + this.productId + '?')
    )
      return;
    this.deleting = true;
    this.api.eliminarProducto(this.productId).subscribe({
      next: () => void this.router.navigate(['/company', this.companyId, 'products']),
      error: (e) => {
        this.deleting = false;
        this.error = e?.error?.detail ?? 'No se pudo eliminar el producto.';
        this.cdr.detectChanges();
      },
    });
  }
}
