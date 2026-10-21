import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CategoriaProducto,
  ProductService,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-view-category',
  standalone: true,
  imports: [Navbar, Sidebar, RouterLink],
  templateUrl: './view-category.html',
  styleUrl: './view-category.css',
})
export class ViewCategory implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly productService = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly categoryId = Number(this.route.snapshot.paramMap.get('categoryId') ?? 0);
  protected cargandoCategoria = false;
  protected errorCategoria = '';
  protected categoria: CategoriaProducto | null = null;
  protected subcategorias: SubcategoriaProducto[] = [];

  ngOnInit(): void {
    this.cargarCategoria();
    this.cargarSubcategorias();
  }

  protected subcategoriasDeCategoria(): SubcategoriaProducto[] {
    if (!this.categoryId) {
      return [];
    }
    return this.subcategorias.filter((item) => item.id_categoria_producto === this.categoryId);
  }

  private cargarCategoria(): void {
    this.errorCategoria = '';

    if (!this.categoryId) {
      this.errorCategoria = 'No se encontro la categoria solicitada.';
      return;
    }

    this.cargandoCategoria = true;

    this.productService.obtenerCategoria(this.categoryId).subscribe({
      next: (categoria) => {
        this.categoria = categoria;
        this.cargandoCategoria = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoCategoria = false;
        this.errorCategoria = 'No se pudieron cargar los datos de la categoria.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarSubcategorias(): void {
    if (!this.companyId) {
      this.subcategorias = [];
      this.cdr.detectChanges();
      return;
    }

    this.productService.getSubcategorias(this.companyId).subscribe({
      next: (subcategorias) => {
        this.subcategorias = subcategorias;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cdr.detectChanges();
      },
    });
  }
}

