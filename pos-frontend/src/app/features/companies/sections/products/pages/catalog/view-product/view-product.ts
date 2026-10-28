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

@Component({
  selector: 'app-view-product',
  standalone: true,
  imports: [SecureSrc, Navbar, Sidebar, RouterLink, MoneyPipe],
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
  ngOnInit() {
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
