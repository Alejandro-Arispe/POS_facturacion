import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ClientCategoryResponse,
  CompanyService,
  UpdateClientCategoryRequest,
} from '../../../../../../../core/services/company.service';
import { Navbar } from '../../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-edit-client-category',
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './edit-category.html',
  styleUrl: './edit-category.css',
})
export class EditClientCategory implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly categoryId = Number(this.route.snapshot.paramMap.get('categoryId') ?? 0);
  protected cargandoCategoria = false;
  protected guardandoCategoria = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';

  protected readonly form: UpdateClientCategoryRequest = {
    nombre: '',
    descripcion: '',
    plazo_credito: 0,
    descuento_base: 0,
    limite_credito: 0,
    activo: true,
  };

  ngOnInit(): void {
    this.cargarCategoria();
  }

  protected guardarCategoria(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.companyId) {
      this.errorEdicion = 'No se encontro la empresa para actualizar la categoria.';
      return;
    }

    if (!this.categoryId) {
      this.errorEdicion = 'No se encontro la categoria a editar.';
      return;
    }

    const nombre = this.form.nombre.trim();
    if (!nombre) {
      this.errorEdicion = 'El nombre de la categoria es obligatorio.';
      return;
    }

    const payload: UpdateClientCategoryRequest = {
      nombre,
      descripcion: this.form.descripcion.trim(),
      plazo_credito: Number(this.form.plazo_credito),
      descuento_base: Number(this.form.descuento_base),
      limite_credito: Number(this.form.limite_credito),
      activo: this.form.activo,
    };

    this.guardandoCategoria = true;

    this.companyService
      .actualizarCategoriaCliente(this.companyId, this.categoryId, payload)
      .pipe(
        finalize(() => {
          this.guardandoCategoria = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          this.mensajeEdicion = 'Categoria actualizada correctamente.';
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.errorEdicion = error?.error?.detail ?? 'No se pudo actualizar la categoria de cliente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected volverListado(): void {
    void this.router.navigate(['/company', this.companyId, 'clients', 'categories']);
  }

  private cargarCategoria(): void {
    this.errorEdicion = '';

    if (!this.companyId) {
      this.errorEdicion = 'No se encontro la empresa para cargar la categoria.';
      return;
    }

    if (!this.categoryId) {
      this.errorEdicion = 'No se encontro la categoria a editar.';
      return;
    }

    this.cargandoCategoria = true;

    this.companyService.getCategoriasCliente(this.companyId).subscribe({
      next: (categorias) => {
        const categoria = categorias.find((item) => item.id_categoria_cliente === this.categoryId);

        this.cargandoCategoria = false;

        if (!categoria) {
          this.errorEdicion = 'Categoria no encontrada para la empresa.';
          this.cdr.detectChanges();
          return;
        }

        this.form.nombre = categoria.nombre;
        this.form.descripcion = categoria.descripcion ?? '';
        this.form.plazo_credito = Number(categoria.plazo_credito ?? 0);
        this.form.descuento_base = Number(categoria.descuento_base);
        this.form.limite_credito = Number(categoria.limite_credito);
        this.form.activo = categoria.activo;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.cargandoCategoria = false;
        this.errorEdicion = error?.error?.detail ?? 'No se pudieron cargar los datos de la categoria.';
        this.cdr.detectChanges();
      },
    });
  }
}

