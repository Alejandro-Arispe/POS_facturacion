import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../../../shared/pipes/money.pipe';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ProductService,
  Producto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-view-product',
  standalone: true,
  imports: [MoneyPipe, Navbar, Sidebar, RouterLink],
  templateUrl: './view-product.html',
  styleUrl: './view-product.css',
})
export class ViewProduct implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly productService = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly productId = Number(this.route.snapshot.paramMap.get('productId') ?? 0);
  protected cargandoProducto = false;
  protected errorProducto = '';
  protected producto: Producto | null = null;
  protected readonly apiBaseUrl = 'http://localhost:8000';


  ngOnInit(): void {
    this.cargarProducto();
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

