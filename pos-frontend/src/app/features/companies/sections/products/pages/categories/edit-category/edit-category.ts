import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ActualizarCategoriaRequest,
  ActualizarSubcategoriaRequest,
  CategoriaProducto,
  CrearSubcategoriaRequest,
  ProductService,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-edit-category',
  standalone: true,
  imports: [FormsModule, Navbar, Sidebar, RouterLink],
  templateUrl: './edit-category.html',
  styleUrl: './edit-category.css',
})
export class EditCategory implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly categoryId = Number(this.route.snapshot.paramMap.get('categoryId') ?? 0);
  protected cargandoCategoria = false;
  protected guardandoCategoria = false;
  protected guardandoSubcategoria = false;
  protected guardandoNuevaSubcategoria = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';
  protected subcategorias: SubcategoriaProducto[] = [];
  protected subcategoriaSeleccionada: SubcategoriaProducto | null = null;
  protected subcategoriasNuevasTemporales: CrearSubcategoriaRequest[] = [];

  protected readonly nuevaSubcategoriaForm: CrearSubcategoriaRequest = {
    id_categoria_producto: 0,
    nombre: '',
    descripcion: '',
    activo: true,
  };

  protected readonly form: ActualizarCategoriaRequest = {
    nombre: '',
    descripcion: '',
    activo: true,
  };

  protected readonly subcategoriaForm: ActualizarSubcategoriaRequest = {
    id_categoria_producto: 0,
    nombre: '',
    descripcion: '',
    activo: true,
  };

  ngOnInit(): void {
    this.cargarCategoria();
    this.cargarSubcategorias();
  }

  protected guardarCategoria(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.categoryId) {
      this.errorEdicion = 'No se encontro la categoria a editar.';
      return;
    }

    const payload: ActualizarCategoriaRequest = {
      nombre: this.form.nombre?.trim(),
      descripcion: this.form.descripcion?.trim() ?? '',
      activo: this.form.activo,
    };

    if (!payload.nombre) {
      this.errorEdicion = 'El nombre de la categoria es obligatorio.';
      return;
    }

    this.guardandoCategoria = true;

    this.productService.actualizarCategoria(this.categoryId, payload).subscribe({
      next: () => {
        this.crearSubcategoriasNuevasDespuesDeCategoria();
      },
      error: () => {
        this.guardandoCategoria = false;
        this.errorEdicion = 'No se pudo actualizar la categoria.';
        this.cdr.detectChanges();
      },
    });
  }

  protected seleccionarSubcategoria(subcategoria: SubcategoriaProducto): void {
    this.subcategoriaSeleccionada = subcategoria;
    this.subcategoriaForm.id_categoria_producto = subcategoria.id_categoria_producto;
    this.subcategoriaForm.nombre = subcategoria.nombre;
    this.subcategoriaForm.descripcion = subcategoria.descripcion ?? '';
    this.subcategoriaForm.activo = subcategoria.activo;
  }

  protected agregarNuevaSubcategoriaTemporal(): void {
    if (!this.nuevaSubcategoriaForm.nombre.trim()) {
      this.errorEdicion = 'El nombre de la nueva subcategoria es obligatorio.';
      return;
    }

    this.subcategoriasNuevasTemporales.push({
      id_categoria_producto: this.categoryId,
      nombre: this.nuevaSubcategoriaForm.nombre.trim(),
      descripcion: this.nuevaSubcategoriaForm.descripcion?.trim() ?? '',
      activo: this.nuevaSubcategoriaForm.activo,
    });

    this.nuevaSubcategoriaForm.nombre = '';
    this.nuevaSubcategoriaForm.descripcion = '';
    this.nuevaSubcategoriaForm.activo = true;
    this.limpiarMensajes();
  }

  protected eliminarNuevaSubcategoriaTemporal(index: number): void {
    this.subcategoriasNuevasTemporales.splice(index, 1);
  }

  protected guardarSubcategoria(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.subcategoriaSeleccionada) {
      this.errorEdicion = 'Selecciona una subcategoria para editar.';
      return;
    }

    const payload: ActualizarSubcategoriaRequest = {
      id_categoria_producto: Number(this.subcategoriaForm.id_categoria_producto),
      nombre: this.subcategoriaForm.nombre?.trim(),
      descripcion: this.subcategoriaForm.descripcion?.trim() ?? '',
      activo: this.subcategoriaForm.activo,
    };

    if (!payload.id_categoria_producto || !payload.nombre) {
      this.errorEdicion = 'Completa los datos de la subcategoria.';
      return;
    }

    this.guardandoSubcategoria = true;

    this.productService
      .actualizarSubcategoria(this.subcategoriaSeleccionada.id_subcategoria, payload)
      .subscribe({
        next: () => {
          this.guardandoSubcategoria = false;
          this.subcategoriaSeleccionada = null;
          this.mensajeEdicion = 'Subcategoria actualizada correctamente.';
          this.cargarSubcategorias();
          this.cdr.detectChanges();
        },
        error: () => {
          this.guardandoSubcategoria = false;
          this.errorEdicion = 'No se pudo actualizar la subcategoria.';
          this.cdr.detectChanges();
        },
      });
  }

  protected subcategoriasDeCategoria(): SubcategoriaProducto[] {
    return this.subcategorias.filter((item) => item.id_categoria_producto === this.categoryId);
  }

  protected volverListado(): void {
    void this.router.navigate(['/company', this.companyId, 'products', 'categories']);
  }

  private cargarCategoria(): void {
    this.errorEdicion = '';

    if (!this.categoryId) {
      this.errorEdicion = 'No se encontro la categoria a editar.';
      return;
    }

    this.cargandoCategoria = true;

    this.productService.obtenerCategoria(this.categoryId).subscribe({
      next: (categoria: CategoriaProducto) => {
        this.cargandoCategoria = false;
        this.form.nombre = categoria.nombre;
        this.form.descripcion = categoria.descripcion ?? '';
        this.form.activo = categoria.activo;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoCategoria = false;
        this.errorEdicion = 'No se pudieron cargar los datos de la categoria.';
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

  private crearSubcategoriasNuevasDespuesDeCategoria(): void {
    if (this.subcategoriasNuevasTemporales.length === 0) {
      this.guardandoCategoria = false;
      this.mensajeEdicion = 'Categoria actualizada correctamente.';
      this.cdr.detectChanges();
      return;
    }

    const crearSiguiente = (index: number) => {
      if (index >= this.subcategoriasNuevasTemporales.length) {
        this.guardandoCategoria = false;
        this.mensajeEdicion = 'Categoria y nuevas subcategorias actualizadas correctamente.';
        this.subcategoriasNuevasTemporales = [];
        this.cargarSubcategorias();
        this.cdr.detectChanges();
        return;
      }

      const subcategoria = this.subcategoriasNuevasTemporales[index];
      const payload: CrearSubcategoriaRequest = {
        id_categoria_producto: this.categoryId,
        nombre: subcategoria.nombre.trim(),
        descripcion: subcategoria.descripcion?.trim() ?? '',
        activo: subcategoria.activo,
      };

      this.productService.crearSubcategoria(payload).subscribe({
        next: () => {
          crearSiguiente(index + 1);
        },
        error: () => {
          this.guardandoCategoria = false;
          this.errorEdicion = `Error al crear la subcategoria: ${subcategoria.nombre}`;
          this.cdr.detectChanges();
        },
      });
    };

    crearSiguiente(0);
  }

  protected limpiarMensajes(): void {
    this.errorEdicion = '';
    this.mensajeEdicion = '';
  }
}

