import { Injectable, signal } from '@angular/core';
import { TRANSLATIONS } from './translations';

export type AppLanguage = 'es' | 'en' | 'pt';

export const SUPPORTED_LANGUAGES: Array<{ code: AppLanguage; label: string }> = [
  { code: 'es', label: 'Español' },
  { code: 'en', label: 'English' },
  { code: 'pt', label: 'Português' },
];

const STORAGE_KEY = 'pos-lang';

/**
 * Traduccion de la interfaz. Las claves son el texto original en español, asi las pantallas
 * siguen siendo legibles y cualquier texto sin traducir cae de forma segura al español.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly languageSignal = signal<AppLanguage>(this.readStoredLanguage());
  readonly language = this.languageSignal.asReadonly();

  constructor() {
    document.documentElement.lang = this.languageSignal();
  }

  setLanguage(language: string | null | undefined): void {
    const next = this.normalize(language);
    this.languageSignal.set(next);
    document.documentElement.lang = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // almacenamiento no disponible: el idioma se mantiene solo en memoria
    }
  }

  t(key: string, params?: Record<string, string | number | null | undefined>): string {
    const language = this.languageSignal();
    const text = language === 'es' ? key : (TRANSLATIONS[language]?.[key] ?? key);
    if (!params) {
      return text;
    }
    return text.replace(/\{(\w+)\}/g, (_match, name: string) => String(params[name] ?? ''));
  }

  /** Locale de Intl para formatear numeros y fechas segun el idioma activo. */
  locale(): string {
    switch (this.languageSignal()) {
      case 'en':
        return 'en-US';
      case 'pt':
        return 'pt-BR';
      default:
        return 'es-BO';
    }
  }

  private readStoredLanguage(): AppLanguage {
    try {
      return this.normalize(localStorage.getItem(STORAGE_KEY));
    } catch {
      return 'es';
    }
  }

  private normalize(language: string | null | undefined): AppLanguage {
    const code = (language ?? '').toLowerCase().slice(0, 2);
    return code === 'en' || code === 'pt' ? code : 'es';
  }
}
