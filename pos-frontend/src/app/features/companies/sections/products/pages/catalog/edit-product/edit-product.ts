import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ActualizarProductoRequest,
  ProductService,
  Producto,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-edit-product',
  standalone: true,
  imports: [FormsModule, Navbar, Sidebar, RouterLink],
  templateUrl: './edit-product.html',
  styleUrl: './edit-product.css',
})
export class EditProduct implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly productId = Number(this.route.snapshot.paramMap.get('productId') ?? 0);
  protected cargandoProducto = false;
  protected guardandoProducto = false;
  protected guardandoImagen = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';
  protected imagenActual: string | null = null;
  protected imagenSeleccionada: File | null = null;
  protected subcategorias: SubcategoriaProducto[] = [];

  protected readonly form: ActualizarProductoRequest = {
    id_subcategoria: 0,
    codigo_barra: '',
    nombre: '',
    descripcion: '',
    unidad_medida: '',
    precio: 0,
    activo: true,
  };

  ngOnInit(): void {
    this.cargarSubcategorias();
    this.cargarProducto();
  }

  protected guardarProducto(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.productId) {
      this.errorEdicion = 'No se encontro el producto a editar.';
      return;
    }

    const payload: ActualizarProductoRequest = {
      id_subcategoria: Number(this.form.id_subcategoria),
      codigo_barra: this.form.codigo_barra?.trim(),
      nombre: this.form.nombre?.trim(),
      descripcion: this.form.descripcion?.trim(),
      unidad_medida: this.form.unidad_medida?.trim(),
      precio: this.form.precio,
      activo: this.form.activo,
    };

    if (!payload.id_subcategoria || !payload.nombre || !payload.unidad_medida) {
      this.errorEdicion = 'Completa subcategoria, nombre y unidad de medida del producto.';
      return;
    }

    this.guardandoProducto = true;

    this.productService.actualizarProducto(this.productId, payload).subscribe({
      next: () => {
        if (this.imagenSeleccionada) {
          this.actualizarImagenDespuesDeGuardar();
          return;
        }

        this.guardandoProducto = false;
        this.mensajeEdicion = 'Producto actualizado correctamente.';
        this.cdr.detectChanges();
      },
      error: () => {
        this.guardandoProducto = false;
        this.errorEdicion = 'No se pudo actualizar el producto.';
        this.cdr.detectChanges();
      },
    });
  }

  protected onImagenSeleccionada(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.imagenSeleccionada = input.files?.[0] ?? null;
  }

  protected volverListado(): void {
    void this.router.navigate(['/company', this.companyId, 'products']);
  }

  private cargarProducto(): void {
    this.errorEdicion = '';

    if (!this.productId) {
      this.errorEdicion = 'No se encontro el producto a editar.';
      return;
    }

    this.cargandoProducto = true;

    this.productService.obtenerProducto(this.productId).subscribe({
      next: (producto: Producto) => {
        this.cargandoProducto = false;
        this.imagenActual = producto.imagen ?? null;
        this.form.codigo_barra = producto.codigo_barra ?? '';
        this.form.nombre = producto.nombre;
        this.form.descripcion = producto.descripcion ?? '';
        this.form.id_subcategoria = producto.id_subcategoria ?? 0;
        this.form.unidad_medida = producto.unidad_medida;
        this.form.precio = producto.precio;
        this.form.activo = producto.activo;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoProducto = false;
        this.errorEdicion = 'No se pudieron cargar los datos del producto.';
        this.cdr.detectChanges();
      },
    });
  }

  private actualizarImagenDespuesDeGuardar(): void {
    if (!this.imagenSeleccionada) {
      this.guardandoProducto = false;
      this.cdr.detectChanges();
      return;
    }

    this.guardandoImagen = true;
    this.productService.actualizarImagenProducto(this.productId, this.imagenSeleccionada).subscribe({
      next: (productoActualizado) => {
        this.guardandoImagen = false;
        this.guardandoProducto = false;
        this.imagenSeleccionada = null;
        this.imagenActual = productoActualizado.imagen ?? null;
        this.mensajeEdicion = 'Producto e imagen actualizados correctamente.';
        this.cdr.detectChanges();
      },
      error: () => {
        this.guardandoImagen = false;
        this.guardandoProducto = false;
        this.errorEdicion = 'Se actualizo el producto, pero no se pudo actualizar la imagen.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarSubcategorias(): void {
    if (!this.companyId) {
      this.subcategorias = [];
      return;
    }

    this.productService.getSubcategorias(this.companyId).subscribe({
      next: (subcategorias) => {
        this.subcategorias = subcategorias;
        this.cdr.detectChanges();
      },
      error: () => {
        this.subcategorias = [];
      },
    });
  }

}

