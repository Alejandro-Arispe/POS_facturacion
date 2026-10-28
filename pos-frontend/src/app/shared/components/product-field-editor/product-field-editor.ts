import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompanyPermissionsService } from '../../../core/services/company-permissions.service';
import { PRODUCT_FIELDS, ProductField, ProductValues } from '../../../core/services/product-fields';
import { SubcategoriaProducto } from '../../../core/services/product.service';

@Component({
  selector: 'app-product-field-editor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="grid gap-5 sm:grid-cols-2">
      @for (field of fields; track field.key) {
        @if (visible(field.key)) {
          <div class="grid content-start gap-2" [class.sm:col-span-2]="field.kind === 'textarea'">
            <label
              class="text-sm font-semibold text-ink"
              [for]="'product-' + action + '-' + field.key"
            >
              {{ field.label }}
              @if (required(field.key)) {
                <span class="text-muted" aria-hidden="true">*</span>
              }
            </label>
            @if (!writable(field.key)) {
              <output
                class="rounded-lg border border-line bg-canvas px-3 py-2.5 text-sm text-muted"
                [id]="'product-' + action + '-' + field.key"
                >{{ display(field.key) }}</output
              >
              <span class="text-xs text-muted">Solo lectura</span>
            } @else {
              @switch (field.kind) {
                @case ('category') {
                  <select
                    class="input"
                    [id]="id(field.key)"
                    [name]="id(field.key)"
                    [ngModel]="inputValue(field.key) ?? null"
                    [ngModelOptions]="{ standalone: true }"
                    [required]="required(field.key)"
                    [disabled]="disabled"
                    (ngModelChange)="change(field.key, $event)"
                  >
                    <option [ngValue]="null">Selecciona una subcategoría</option>
                    @for (category of categories; track category.id_subcategoria) {
                      <option [ngValue]="category.id_subcategoria">{{ category.nombre }}</option>
                    }
                  </select>
                }
                @case ('boolean') {
                  <label class="flex min-h-11 items-center gap-3 text-sm"
                    ><input
                      type="checkbox"
                      class="size-4 accent-brand"
                      [id]="id(field.key)"
                      [checked]="inputValue(field.key) === true"
                      [disabled]="disabled"
                      (change)="change(field.key, $any($event.target).checked)"
                    />Activo</label
                  >
                }
                @case ('money') {
                  <input
                    class="input"
                    type="number"
                    min="0"
                    max="9999999999.99"
                    step="0.01"
                    [id]="id(field.key)"
                    [name]="id(field.key)"
                    [ngModel]="inputValue(field.key) ?? null"
                    [ngModelOptions]="{ standalone: true }"
                    [required]="required(field.key)"
                    [disabled]="disabled"
                    (ngModelChange)="change(field.key, $event)"
                  />
                }
                @case ('textarea') {
                  <textarea
                    class="input min-h-24"
                    maxlength="10000"
                    rows="3"
                    [id]="id(field.key)"
                    [name]="id(field.key)"
                    [ngModel]="inputValue(field.key) ?? ''"
                    [ngModelOptions]="{ standalone: true }"
                    [disabled]="disabled"
                    (ngModelChange)="change(field.key, $event)"
                  ></textarea>
                }
                @case ('image') {
                  <input
                    class="input"
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    [id]="id(field.key)"
                    [disabled]="disabled"
                    (change)="selectImage($event)"
                  />
                  @if (action === 'update' && canRead(field.key) && values.imagen) {
                    <button
                      class="justify-self-start text-sm font-medium text-brand underline"
                      type="button"
                      [disabled]="disabled"
                      (click)="change('imagen', null); imageChange.emit(null)"
                    >
                      Retirar imagen actual
                    </button>
                  }
                }
                @default {
                  <input
                    class="input"
                    [id]="id(field.key)"
                    [name]="id(field.key)"
                    [ngModel]="inputValue(field.key) ?? ''"
                    [ngModelOptions]="{ standalone: true }"
                    [required]="required(field.key)"
                    [maxlength]="
                      field.key === 'nombre' ? 150 : field.key === 'unidad_medida' ? 50 : 100
                    "
                    [disabled]="disabled"
                    (ngModelChange)="change(field.key, $event)"
                  />
                }
              }
              @if (action === 'update' && !canRead(field.key)) {
                <p class="text-xs text-muted">
                  El valor actual está restringido. Puedes establecer uno nuevo.
                </p>
              }
            }
          </div>
        }
      }
    </div>
  `,
})
export class ProductFieldEditor {
  private readonly permissions = inject(CompanyPermissionsService);
  @Input() action: 'create' | 'update' = 'create';
  @Input() values: ProductValues = {};
  @Input() categories: SubcategoriaProducto[] = [];
  @Input() disabled = false;
  @Output() fieldChange = new EventEmitter<{
    field: ProductField;
    value: string | number | boolean | null;
  }>();
  @Output() imageChange = new EventEmitter<File | null>();
  readonly fields = PRODUCT_FIELDS;
  private readonly ownChanges = new Map<ProductField, string | number | boolean | null>();
  inputValue(field: ProductField) {
    return this.action === 'create' || this.canRead(field)
      ? this.values[field]
      : this.ownChanges.get(field);
  }
  id(field: string) {
    return 'product-' + this.action + '-' + field;
  }
  canRead(field: ProductField) {
    return this.permissions.hasField('product', 'read', field);
  }
  writable(field: ProductField) {
    return this.permissions.hasField('product', this.action, field);
  }
  visible(field: ProductField) {
    return this.action === 'create'
      ? this.writable(field)
      : this.writable(field) || this.canRead(field);
  }
  required(field: ProductField) {
    return (
      this.action === 'create' &&
      ['nombre', 'id_subcategoria', 'unidad_medida', 'precio'].includes(field)
    );
  }
  change(field: ProductField, value: string | number | boolean | null) {
    if (this.writable(field) && !this.disabled) {
      this.ownChanges.set(field, value);
      this.fieldChange.emit({ field, value });
    }
  }
  selectImage(event: Event) {
    if (this.writable('imagen'))
      this.imageChange.emit((event.target as HTMLInputElement).files?.[0] ?? null);
  }
  display(field: ProductField): string {
    if (!this.canRead(field)) return 'Restringido';
    if (field === 'id_subcategoria')
      return this.categories.find((c) => c.id_subcategoria === this.values[field])?.nombre ?? '—';
    if (field === 'activo')
      return this.values[field] == null ? '—' : this.values[field] ? 'Activo' : 'Inactivo';
    return this.values[field] == null ? '—' : String(this.values[field]);
  }
}
