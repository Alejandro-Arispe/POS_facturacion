import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CompanyPermissionsService } from '../../../../../../../core/services/company-permissions.service';
import {
  ProductService,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import {
  ProductField,
  ProductValues,
  productPayload,
} from '../../../../../../../core/services/product-fields';
import { ProductFieldEditor } from '../../../../../../../shared/components/product-field-editor/product-field-editor';
import { SiatHomologacion } from '../../../../../../../shared/components/siat-homologacion/siat-homologacion';
import { SiatCodes } from '../../../../../../../core/services/siat.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-edit-product',
  standalone: true,
  imports: [Navbar, Sidebar, RouterLink, ProductFieldEditor, SiatHomologacion],
  templateUrl: './edit-product.html',
  styleUrl: './edit-product.css',
})
export class EditProduct implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ProductService);
  protected readonly permissions = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);
  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly productId = Number(this.route.snapshot.paramMap.get('productId'));
  protected values: ProductValues = {};
  protected categories: SubcategoriaProducto[] = [];
  protected readonly touched = new Set<ProductField>();
  protected image: File | null = null;
  protected siat: SiatCodes = {};
  protected siatTouched = false;
  protected loading = true;
  protected saving = false;
  protected error = '';
  protected success = '';
  protected get blocked() {
    return !this.permissions.hasPermission('product:update') || this.permissions.contextError();
  }
  ngOnInit() {
    if (!this.permissions.hasPermission('product:read')) this.loading = false;
    else
      this.api.obtenerProducto(this.productId).subscribe({
        next: (p) => {
          this.values = { ...p };
          this.siat = siatDe(p);
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: (e) => {
          this.error = e?.error?.detail ?? 'No se pudo cargar el producto.';
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
    this.api.getSubcategorias(this.companyId).subscribe({
      next: (c) => {
        this.categories = c;
        this.cdr.detectChanges();
      },
      error: () => {
        this.categories = [];
      },
    });
  }
  protected change(event: { field: ProductField; value: string | number | boolean | null }) {
    this.values[event.field] = event.value;
    this.touched.add(event.field);
    this.success = '';
  }
  protected save(event: Event) {
    event.preventDefault();
    if (this.blocked || this.loading || this.saving || this.error) return;
    const payload = productPayload(this.values, this.touched, 'update', (a, f) =>
      this.permissions.hasField('product', a, f),
    );
    if (
      this.touched.has('imagen') &&
      this.permissions.hasField('product', 'update', 'imagen') &&
      this.values.imagen === null
    )
      payload.imagen = null;
    if (this.siatTouched) {
      if (!this.siat.codigo_producto_sin || !this.siat.codigo_unidad_medida_sin) {
        this.error = 'Completa la homologación SIAT: código de producto y unidad de medida SIN.';
        return;
      }
      Object.assign(payload, this.siat);
    }
    if (!Object.keys(payload).length && !this.image) {
      this.success = 'No hay cambios para guardar.';
      return;
    }
    if (this.image && !this.permissions.hasField('product', 'update', 'imagen')) {
      this.error = 'Tu permiso para modificar imágenes cambió.';
      return;
    }
    this.saving = true;
    this.success = '';
    const request = this.image
      ? this.api.actualizarProductoConImagen(this.productId, payload, this.image)
      : this.api.actualizarProducto(this.productId, payload);
    request.subscribe({
      next: (p) => {
        this.values = { ...p };
        this.siat = siatDe(p);
        this.siatTouched = false;
        this.image = null;
        this.touched.clear();
        this.saving = false;
        this.success = 'Producto actualizado.';
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.detail ?? 'No se pudo actualizar el producto.';
        this.cdr.detectChanges();
      },
    });
  }
}

function siatDe(p: SiatCodes): SiatCodes {
  return {
    codigo_producto_sin: p.codigo_producto_sin ?? null,
    codigo_unidad_medida_sin: p.codigo_unidad_medida_sin ?? null,
  };
}
