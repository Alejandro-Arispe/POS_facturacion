import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/** Uso: {{ 'Texto en español' | t }} o {{ 'Hola {nombre}' | t: { nombre: x } }} */
@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string | null | undefined, params?: Record<string, string | number | null | undefined>): string {
    return key ? this.i18n.t(key, params) : '';
  }
}
