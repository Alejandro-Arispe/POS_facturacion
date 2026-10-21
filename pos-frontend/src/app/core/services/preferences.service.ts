import { Injectable, inject, signal } from '@angular/core';
import { I18nService } from '../i18n/i18n.service';
import { ThemeService } from './theme.service';
import { CompanyService } from './company.service';

export interface CurrencyInfo {
  code: string;
  label: string;
  symbol: string;
}

export const SUPPORTED_CURRENCIES: CurrencyInfo[] = [
  { code: 'BOB', label: 'Boliviano', symbol: 'Bs' },
  { code: 'USD', label: 'Dólar estadounidense', symbol: '$' },
  { code: 'EUR', label: 'Euro', symbol: '€' },
  { code: 'PEN', label: 'Sol peruano', symbol: 'S/' },
  { code: 'ARS', label: 'Peso argentino', symbol: 'AR$' },
  { code: 'CLP', label: 'Peso chileno', symbol: 'CLP$' },
  { code: 'MXN', label: 'Peso mexicano', symbol: 'MX$' },
  { code: 'BRL', label: 'Real brasileño', symbol: 'R$' },
];

export const ACCENT_PRESETS = ['#047857', '#2563eb', '#7c3aed', '#db2777', '#ea580c', '#0f766e', '#334155'];

const DEFAULT_ACCENT = '#047857';
const CURRENCY_KEY = 'pos-currency';
const ACCENT_KEY = 'pos-accent';

/**
 * Preferencias de interfaz de la empresa activa (idioma, moneda, tema y color de acento).
 * Se cargan desde la configuracion del sistema al entrar a la empresa y se recuerdan en el navegador.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly i18n = inject(I18nService);
  private readonly theme = inject(ThemeService);
  private readonly companyService = inject(CompanyService);

  private readonly currencySignal = signal<CurrencyInfo>(this.findCurrency(this.read(CURRENCY_KEY)));
  readonly currency = this.currencySignal.asReadonly();
  private loadedCompanyId = '';

  /** Aplica lo guardado en el navegador al arrancar la aplicacion. */
  initialize(): void {
    this.applyAccent(this.read(ACCENT_KEY) ?? DEFAULT_ACCENT);
  }

  /** Carga la configuracion de la empresa una sola vez por empresa visitada. */
  ensureLoaded(companyId: string | number | null | undefined): void {
    const id = companyId ? String(companyId) : '';
    if (!id || id === this.loadedCompanyId) {
      return;
    }
    this.loadedCompanyId = id;
    this.companyService.getConfiguracionSistema(id).subscribe({
      next: (config) => this.apply(config),
      error: () => {
        this.loadedCompanyId = '';
      },
    });
  }

  apply(config: { idioma?: string; moneda?: string; tema?: string; color_primario?: string | null }): void {
    if (config.idioma) this.i18n.setLanguage(config.idioma);
    if (config.tema) this.theme.setTheme(config.tema);
    if (config.moneda) this.setCurrency(config.moneda);
    this.applyAccent(config.color_primario || DEFAULT_ACCENT);
  }

  setCurrency(code: string): void {
    const currency = this.findCurrency(code);
    this.currencySignal.set(currency);
    this.write(CURRENCY_KEY, currency.code);
  }

  formatMoney(value: number | string | null | undefined, withSymbol = true): string {
    const amount = Number(value ?? 0);
    const formatted = new Intl.NumberFormat(this.i18n.locale(), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number.isFinite(amount) ? amount : 0);
    return withSymbol ? `${this.currencySignal().symbol} ${formatted}` : formatted;
  }

  applyAccent(color: string): void {
    const accent = /^#[0-9a-f]{6}$/i.test(color) ? color.toLowerCase() : DEFAULT_ACCENT;
    const darker = this.shade(accent, -0.18);
    const lighter = this.shade(accent, 0.12);
    const [r, g, b] = this.rgb(accent);
    const vars: Record<string, string> = {
      '--color-accent-green': accent,
      '--color-accent-mint': lighter,
      '--color-success': lighter,
      '--color-success-hover': darker,
      '--color-tab-active-text': accent,
      '--color-table-header-text': darker,
      '--color-border-glow': `rgba(${r}, ${g}, ${b}, 0.35)`,
      '--color-table-row-hover': `rgba(${r}, ${g}, ${b}, 0.05)`,
    };
    for (const target of [document.documentElement, document.body]) {
      for (const [name, value] of Object.entries(vars)) {
        target.style.setProperty(name, value);
      }
    }
    this.write(ACCENT_KEY, accent);
  }

  private findCurrency(code: string | null | undefined): CurrencyInfo {
    return SUPPORTED_CURRENCIES.find((c) => c.code === (code ?? '').toUpperCase()) ?? SUPPORTED_CURRENCIES[0];
  }

  private rgb(hex: string): [number, number, number] {
    const n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  private shade(hex: string, amount: number): string {
    const channels = this.rgb(hex).map((c) => {
      const target = amount < 0 ? 0 : 255;
      return Math.round(c + (target - c) * Math.abs(amount));
    });
    return '#' + channels.map((c) => c.toString(16).padStart(2, '0')).join('');
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // sin almacenamiento: la preferencia vale solo para esta sesion
    }
  }
}
