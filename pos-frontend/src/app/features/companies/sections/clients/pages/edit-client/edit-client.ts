import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ClientCategoryResponse,
  ClientRole,
  CompanyService,
  UpdateClientRequest,
} from '../../../../../../core/services/company.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

interface ClientEditForm {
  id_categoria_cliente: number | null;
  codigo_cliente: string;
  activo: boolean;
}

interface ClientCategoryOption {
  id_categoria_cliente: number;
  nombre: string;
  descripcion: string;
  permite_credito: boolean;
  activo: boolean;
}

@Component({
  selector: 'app-edit-client',
  standalone: true,
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './edit-client.html',
  styleUrls: ['./edit-client.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditClient implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly clientId = Number(this.route.snapshot.paramMap.get('clientId') ?? 0);

  protected cargandoDatos = false;
  protected guardandoCliente = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';

  protected readonly form: ClientEditForm = {
    id_categoria_cliente: null,
    codigo_cliente: '',
    activo: true,
  };

  protected clientName = 'Cliente';
  protected categories: ClientCategoryOption[] = [];

  ngOnInit(): void {
    this.cargarDatos();
  }


  protected guardarCliente(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.companyId) {
      this.errorEdicion = 'No se encontro la empresa para actualizar el cliente.';
      return;
    }

    if (!this.clientId) {
      this.errorEdicion = 'No se encontro el cliente a editar.';
      return;
    }

    if (!this.form.id_categoria_cliente) {
      this.errorEdicion = 'Selecciona una categoria de cliente.';
      return;
    }

    const payload: UpdateClientRequest = {
      id_categoria_cliente: Number(this.form.id_categoria_cliente),
      codigo_cliente: this.form.codigo_cliente.trim(),
      activo: this.form.activo,
    };

    if (!payload.codigo_cliente) {
      this.errorEdicion = 'Ingresa el codigo del cliente.';
      return;
    }

    this.guardandoCliente = true;

    this.companyService
      .actualizarClienteEmpresa(this.companyId, this.clientId, payload)
      .pipe(
        finalize(() => {
          this.guardandoCliente = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          this.mensajeEdicion = 'Cliente actualizado correctamente.';
          this.cdr.detectChanges();
        },
        error: (error: { error?: { detail?: string } }) => {
          this.errorEdicion = error?.error?.detail ?? 'No se pudo actualizar el cliente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected volverListado(): void {
    void this.router.navigate(['/company', this.companyId, 'clients']);
  }

  protected trackCategory(_: number, category: ClientCategoryOption): number {
    return category.id_categoria_cliente;
  }

  private cargarDatos(): void {
    this.errorEdicion = '';

    if (!this.companyId) {
      this.errorEdicion = 'No se encontro la empresa para cargar el cliente.';
      return;
    }

    if (!this.clientId) {
      this.errorEdicion = 'No se encontro el cliente a editar.';
      return;
    }

    this.cargandoDatos = true;

    this.companyService.getClientesEmpresa(this.companyId).subscribe({
      next: (clientes: ClientRole[]) => {
        const cliente = clientes.find((item) => item.cliente.id_cliente === this.clientId);

        if (!cliente) {
          this.cargandoDatos = false;
          this.errorEdicion = 'Cliente no encontrado para la empresa.';
          this.cdr.detectChanges();
          return;
        }

        this.clientName = cliente.usuario.persona?.nombre_completo ?? 'Cliente';
        this.form.id_categoria_cliente = cliente.cliente.id_categoria_cliente;
        this.form.codigo_cliente = cliente.cliente.codigo_cliente;
        this.form.activo = cliente.cliente.activo;

        this.companyService.getCategoriasCliente(this.companyId).subscribe({
          next: (categorias: ClientCategoryResponse[]) => {
            this.cargandoDatos = false;
            this.setCategories(categorias);
            this.cdr.detectChanges();
          },
          error: (error: { error?: { detail?: string } }) => {
            this.cargandoDatos = false;
            this.errorEdicion = error?.error?.detail ?? 'No se pudieron cargar las categorias de cliente.';
            this.cdr.detectChanges();
          },
        });
      },
      error: (error: { error?: { detail?: string } }) => {
        this.cargandoDatos = false;
        this.errorEdicion = error?.error?.detail ?? 'No se pudieron cargar los datos del cliente.';
        this.cdr.detectChanges();
      },
    });
  }

  private setCategories(categorias: ClientCategoryResponse[]): void {
    this.categories = categorias.map((category) => this.mapCategory(category));
  }

  private mapCategory(category: ClientCategoryResponse): ClientCategoryOption {
    return {
      id_categoria_cliente: category.id_categoria_cliente,
      nombre: category.nombre,
      descripcion: category.descripcion ?? '',
      permite_credito: Number(category.plazo_credito ?? 0) > 0,
      activo: category.activo,
    };
  }
}

