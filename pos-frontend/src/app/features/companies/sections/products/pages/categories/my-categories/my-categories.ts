import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CompanyService } from '../../../../../../../core/services/company.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../../core/services/company-permissions.service';
import {
  CategoriaProducto,
  CrearCategoriaRequest,
  CrearSubcategoriaRequest,
  ProductService,
  SubcategoriaProducto,
} from '../../../../../../../core/services/product.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-categorias-panel',
  standalone: true,
  imports: [FormsModule, Navbar, RouterLink, Sidebar],
  templateUrl: './my-categories.html',
  styleUrl: './my-categories.css',
})
export class CategoriasPanel implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly productService = inject(ProductService);
  private readonly cdr = inject(ChangeDetectorRef);
  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = 'Empresa';

  protected activeTab: 'list' | 'register' = 'list';

  protected readonly categoriaForm: CrearCategoriaRequest = {
    nombre: '',
    descripcion: '',
    activo: true,
  };

  protected readonly subcategoriaForm: CrearSubcategoriaRequest = {
    id_categoria_producto: 0,
    nombre: '',
    descripcion: '',
    activo: true,
  };

  protected categorias: CategoriaProducto[] = [];
  protected subcategorias: SubcategoriaProducto[] = [];
  protected subcategoriasTemporales: CrearSubcategoriaRequest[] = [];

  protected cargandoDatos = false;
  protected guardandoCategoria = false;

  protected errorGeneral = '';
  protected mensajeGeneral = '';

  ngOnInit(): void {
    this.cargarNombreEmpresa();
    this.cargarDatos();
  }

  protected setActiveTab(tab: 'list' | 'register'): void {
    if (tab === 'register' && !this.hasPermission('CATEGORIA_CREAR')) {
      return;
    }

    this.activeTab = tab;
    this.limpiarMensajes();

    if (tab === 'list') {
      this.cargarDatos();
    }
  }

  protected registrarCategoria(event: SubmitEvent): void {
    event.preventDefault();
    this.limpiarMensajes();

    if (!this.hasPermission('CATEGORIA_CREAR')) {
      this.errorGeneral = 'No tienes permiso para registrar categorias.';
      return;
    }

    if (!this.companyId) {
      this.errorGeneral = 'No se encontro la empresa para registrar la categoria.';
      return;
    }

    const payload: CrearCategoriaRequest = {
      nombre: this.categoriaForm.nombre.trim(),
      descripcion: this.categoriaForm.descripcion?.trim() ?? '',
      activo: this.categoriaForm.activo,
    };

    if (!payload.nombre) {
      this.errorGeneral = 'El nombre de la categoria es obligatorio.';
      return;
    }

    this.guardandoCategoria = true;
    this.productService.crearCategoria(this.companyId, payload).subscribe({
      next: (nuevaCategoria) => {
        this.crearSubcategoriasDespuesDeCategoria(nuevaCategoria.id_categoria_producto);
      },
      error: () => {
        this.guardandoCategoria = false;
        this.errorGeneral = 'No se pudo registrar la categoria.';
        this.cdr.detectChanges();
      },
    });
  }


  protected registrarSubcategoriaTemporal(): void {
    if (!this.hasPermission('CATEGORIA_CREAR')) {
      this.errorGeneral = 'No tienes permiso para registrar subcategorias.';
      return;
    }

    if (!this.subcategoriaForm.nombre.trim()) {
      this.errorGeneral = 'El nombre de la subcategoria es obligatorio.';
      return;
    }

    this.subcategoriasTemporales.push({
      id_categoria_producto: 0,
      nombre: this.subcategoriaForm.nombre.trim(),
      descripcion: this.subcategoriaForm.descripcion?.trim() ?? '',
      activo: this.subcategoriaForm.activo,
    });

    this.subcategoriaForm.nombre = '';
    this.subcategoriaForm.descripcion = '';
    this.subcategoriaForm.activo = true;
    this.limpiarMensajes();
  }

  protected eliminarSubcategoriaTemporal(index: number): void {
    this.subcategoriasTemporales.splice(index, 1);
  }

  private crearSubcategoriasDespuesDeCategoria(idCategoria: number): void {
    if (this.subcategoriasTemporales.length === 0) {
      this.guardandoCategoria = false;
      this.mensajeGeneral = 'Categoria registrada correctamente.';
      this.limpiarFormulariosRegistro();
      this.activeTab = 'list';
      this.cargarDatos();
      return;
    }

    const crearSubcategorias = (index: number) => {
      if (index >= this.subcategoriasTemporales.length) {
        this.guardandoCategoria = false;
        this.mensajeGeneral = 'Categoria y subcategorias registradas correctamente.';
        this.limpiarFormulariosRegistro();
        this.activeTab = 'list';
        this.cargarDatos();
        return;
      }

      const subcategoria = this.subcategoriasTemporales[index];
      const payload: CrearSubcategoriaRequest = {
        id_categoria_producto: idCategoria,
        nombre: subcategoria.nombre.trim(),
        descripcion: subcategoria.descripcion?.trim() ?? '',
        activo: subcategoria.activo,
      };

      this.productService.crearSubcategoria(payload).subscribe({
        next: () => {
          crearSubcategorias(index + 1);
        },
        error: () => {
          this.guardandoCategoria = false;
          this.errorGeneral = `Error al crear la subcategoria: ${subcategoria.nombre}`;
          this.cdr.detectChanges();
        },
      });
    };

    crearSubcategorias(0);
  }

  private cargarDatos(): void {
    this.cargandoDatos = true;
    this.limpiarMensajes();

    this.cargarCategorias();
    this.cargarSubcategorias(() => {
      this.cargandoDatos = false;
    });
  }

  private cargarCategorias(): void {
    if (!this.companyId) {
      this.errorGeneral = 'No se encontro la empresa para cargar categorias.';
      this.categorias = [];
      this.cdr.detectChanges();
      return;
    }

    this.productService.getCategorias(this.companyId).subscribe({
      next: (categorias) => {
        this.categorias = categorias;
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorGeneral = 'No se pudieron cargar las categorias.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarSubcategorias(onDone?: () => void): void {
    if (!this.companyId) {
      this.errorGeneral = 'No se encontro la empresa para cargar subcategorias.';
      this.subcategorias = [];
      onDone?.();
      this.cdr.detectChanges();
      return;
    }

    this.productService.getSubcategorias(this.companyId).subscribe({
      next: (subcategorias) => {
        this.subcategorias = subcategorias;
        onDone?.();
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorGeneral = 'No se pudieron cargar las subcategorias.';
        onDone?.();
        this.cdr.detectChanges();
      },
    });
  }

  private limpiarFormulariosRegistro(): void {
    this.categoriaForm.nombre = '';
    this.categoriaForm.descripcion = '';
    this.categoriaForm.activo = true;
    this.subcategoriaForm.nombre = '';
    this.subcategoriaForm.descripcion = '';
    this.subcategoriaForm.activo = true;
    this.subcategoriasTemporales = [];
  }

  protected limpiarMensajes(): void {
    this.errorGeneral = '';
    this.mensajeGeneral = '';
  }

  private cargarNombreEmpresa(): void {
    if (!this.companyId) {
      return;
    }

    this.companyService.obtenerEmpresa(this.companyId).subscribe({
      next: (empresa) => {
        this.companyName = empresa.nombre;
        this.cdr.detectChanges();
      },
      error: () => {
        this.companyName = 'Empresa';
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
}

