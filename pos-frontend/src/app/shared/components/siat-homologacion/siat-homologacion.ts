import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import {
  SiatCodes,
  SiatService,
  SinProductoServicio,
  SinUnidadMedida,
} from '../../../core/services/siat.service';

/** Bloque "Homologación Impuestos Nacionales (SIAT)" del formulario de productos. */
@Component({
  selector: 'app-siat-homologacion',
  standalone: true,
  imports: [FormsModule],
  template: `
    <fieldset class="siat-block grid gap-4 rounded-xl border border-line p-4">
      <legend class="px-1 text-sm font-semibold text-ink">
        Homologación Impuestos Nacionales (SIAT)
      </legend>
      @if (error) {
        <p class="feedback feedback-error">{{ error }}</p>
      }
      <div class="grid gap-4 sm:grid-cols-2">
        <label class="form-group">
          <span class="label">Código de Producto/Servicio SIN *</span>
          <select
            class="input"
            name="codigo_producto_sin"
            [ngModel]="codes.codigo_producto_sin ?? null"
            (ngModelChange)="set('codigo_producto_sin', $event)"
            [disabled]="disabled"
          >
            <option [ngValue]="null">Selecciona un código</option>
            @for (p of productos; track p.codigo_producto_sin) {
              <option [ngValue]="p.codigo_producto_sin">
                {{ p.codigo_producto_sin }} - {{ p.descripcion_producto_sin }} (Act. {{ p.codigo_actividad }})
              </option>
            }
          </select>
        </label>
        <label class="form-group">
          <span class="label">Unidad de Medida SIN *</span>
          <select
            class="input"
            name="codigo_unidad_medida_sin"
            [ngModel]="codes.codigo_unidad_medida_sin ?? null"
            (ngModelChange)="set('codigo_unidad_medida_sin', $event)"
            [disabled]="disabled"
          >
            <option [ngValue]="null">Selecciona una unidad</option>
            @for (u of unidades; track u.codigo_unidad_sin) {
              <option [ngValue]="u.codigo_unidad_sin">{{ u.codigo_unidad_sin }} - {{ u.descripcion }}</option>
            }
          </select>
        </label>
      </div>
    </fieldset>
  `,
})
export class SiatHomologacion implements OnInit {
  private readonly siat = inject(SiatService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input() codes: SiatCodes = {};
  @Input() disabled = false;
  @Output() codesChange = new EventEmitter<SiatCodes>();

  protected productos: SinProductoServicio[] = [];
  protected unidades: SinUnidadMedida[] = [];
  protected error = '';

  ngOnInit(): void {
    forkJoin([this.siat.productosServicios(), this.siat.unidadesMedida()]).subscribe({
      next: ([productos, unidades]) => {
        this.productos = productos;
        this.unidades = unidades;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'No se pudieron cargar los catálogos del SIN.';
        this.cdr.detectChanges();
      },
    });
  }

  protected set(field: keyof SiatCodes, value: number | null): void {
    this.codesChange.emit({ ...this.codes, [field]: value });
  }
}
