import { Pipe, PipeTransform, inject } from '@angular/core';
import { PreferencesService } from '../../core/services/preferences.service';

/** Formatea un monto con la moneda y el idioma configurados para la empresa: {{ total | money }} */
@Pipe({ name: 'money', standalone: true, pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly preferences = inject(PreferencesService);

  transform(value: number | string | null | undefined, withSymbol = true): string {
    return this.preferences.formatMoney(value, withSymbol);
  }
}

/** Simbolo de la moneda configurada, para etiquetas como "Monto ({{ '' | currencySymbol }})". */
@Pipe({ name: 'currencySymbol', standalone: true, pure: false })
export class CurrencySymbolPipe implements PipeTransform {
  private readonly preferences = inject(PreferencesService);

  transform(_value?: unknown): string {
    return this.preferences.currency().symbol;
  }
}
