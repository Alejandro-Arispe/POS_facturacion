import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CurrencySymbolPipe, MoneyPipe } from '../../../../../../../shared/pipes/money.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  CompanyService,
  ClientCategoryResponse,
  CreateClientCategoryResponse,
} from '../../../../../../../core/services/company.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

type ClientCategoryTab = 'register' | 'list';

interface ClientCategoryForm {
  nombre: string;
  descripcion: string;
  plazoCredito: number;
  descuentoBase: number;
  limiteCredito: number;
}

interface ClientCategory {
  id: number;
  nombre: string;
  descripcion: string;
  plazoCredito: number;
  descuentoBase: number;
  limiteCredito: number;
  activo: boolean;
}

@Component({
  selector: 'app-categorias-clientes',
  imports: [MoneyPipe, CurrencySymbolPipe, FormsModule, Navbar, Sidebar, RouterLink],
  templateUrl: './my-categories.html',
  styleUrl: './my-categories.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriasClientes {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = 'Empresa';
  protected activeTab: ClientCategoryTab = 'list';
  protected cargandoCategorias = false;
  protected errorCategorias = '';
  protected cargandoRegistro = false;
  protected errorRegistro = '';
  protected mensajeRegistro = '';

  protected readonly categoryForm: ClientCategoryForm = {
    nombre: '',
    descripcion: '',
    plazoCredito: 0,
    descuentoBase: 0,
    limiteCredito: 0,
  };

  protected categories: ClientCategory[] = [];

  ngOnInit(): void {
    this.cargarCategorias();
  }

  protected setActiveTab(tab: ClientCategoryTab): void {
    if (tab === 'register' && !this.hasPermission('CATEGORIA_CREAR')) {
      return;
    }

    this.activeTab = tab;
    if (tab === 'register') {
      this.errorRegistro = '';
      this.mensajeRegistro = '';
    } else {
      this.errorCategorias = '';
    }
  }

  protected registrarCategoria(event: SubmitEvent): void {
    event.preventDefault();

    this.errorRegistro = '';
    this.mensajeRegistro = '';

    if (!this.hasPermission('CATEGORIA_CREAR')) {
      this.errorRegistro = 'No tienes permiso para registrar categorias.';
      return;
    }

    const nombre = this.categoryForm.nombre.trim();
    if (!nombre) {
      this.errorRegistro = 'Ingresa el nombre de la categoria.';
      return;
    }

    if (!this.companyId) {
      this.errorRegistro = 'No se encontro la empresa para crear la categoria.';
      return;
    }

    this.cargandoRegistro = true;

    this.companyService
      .crearCategoriaCliente(this.companyId, {
        nombre,
        descripcion: this.categoryForm.descripcion.trim(),
        plazo_credito: Number(this.categoryForm.plazoCredito),
        descuento_base: Number(this.categoryForm.descuentoBase),
        limite_credito: Number(this.categoryForm.limiteCredito),
      })
      .pipe(
        finalize(() => {
          this.cargandoRegistro = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (response: CreateClientCategoryResponse) => {
          this.categories = [this.mapCategory(response), ...this.categories];
          this.errorCategorias = '';
          this.categoryForm.nombre = '';
          this.categoryForm.descripcion = '';
          this.categoryForm.plazoCredito = 0;
          this.categoryForm.descuentoBase = 0;
          this.categoryForm.limiteCredito = 0;
          this.mensajeRegistro = 'Categoria creada correctamente.';
          this.activeTab = 'list';
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.errorRegistro = error?.error?.detail ?? 'No se pudo crear la categoria de cliente.';
          this.cdr.detectChanges();
        },
      });
  }

  private cargarCategorias(): void {
    if (!this.companyId) {
      this.errorCategorias = 'No se encontro la empresa para cargar las categorias.';
      return;
    }

    this.errorCategorias = '';
    this.cargandoCategorias = true;

    this.companyService
      .getCategoriasCliente(this.companyId)
      .pipe(
        finalize(() => {
          this.cargandoCategorias = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (categories) => {
          this.categories = categories.map((category) => this.mapExistingCategory(category));
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.categories = [];
          this.errorCategorias = error?.error?.detail ?? 'No se pudieron cargar las categorias de cliente.';
          this.cdr.detectChanges();
        },
      });
  }

  private mapCategory(category: CreateClientCategoryResponse): ClientCategory {
    return {
      id: category.id_categoria_cliente,
      nombre: category.nombre,
      descripcion: category.descripcion,
      plazoCredito: Number(category.plazo_credito ?? 0),
      descuentoBase: Number(category.descuento_base),
      limiteCredito: Number(category.limite_credito),
      activo: category.activo,
    };
  }

  private mapExistingCategory(category: ClientCategoryResponse): ClientCategory {
    return {
      id: category.id_categoria_cliente,
      nombre: category.nombre,
      descripcion: category.descripcion,
      plazoCredito: Number(category.plazo_credito ?? 0),
      descuentoBase: Number(category.descuento_base),
      limiteCredito: Number(category.limite_credito),
      activo: category.activo,
    };
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

