import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { CompanyService, SystemConfiguration } from '../../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../shared/components/sidebar/sidebar';
import { ThemeService } from '../../../../core/services/theme.service';
import {
  ACCENT_PRESETS,
  PreferencesService,
  SUPPORTED_CURRENCIES,
} from '../../../../core/services/preferences.service';
import { I18nService, SUPPORTED_LANGUAGES } from '../../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, Navbar, Sidebar, TranslatePipe],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly themeService = inject(ThemeService);
  private readonly preferencesService = inject(PreferencesService);
  private readonly i18n = inject(I18nService);

  protected readonly currencies = SUPPORTED_CURRENCIES;
  protected readonly languages = SUPPORTED_LANGUAGES;
  protected readonly accentPresets = ACCENT_PRESETS;

  protected activeTab: 'general' | 'appearance' | 'notifications' | 'printing' | 'security' = 'general';
  protected companyId = '';
  protected companyName = 'Cargando...';
  
  protected config: SystemConfiguration = {
    tema: 'claro',
    idioma: 'es',
    zona_horaria: 'America/La_Paz',
    moneda: 'BOB',
    activar_notificaciones_push: true,
    activar_sonido: true,
    activar_vibracion: true,
    confirmar_antes_de_eliminar: true,
    cerrar_sesion_por_inactividad: false,
    minutos_inactividad: 15,
    imprimir_automaticamente: false,
    numero_copias: 1,
    tamano_ticket: '80mm',
    color_primario: '#047857',
  };

  protected loading = false;
  protected saving = false;
  protected errorMsg = '';
  protected successMsg = '';

  ngOnInit(): void {
    // Try to get from query params, otherwise from active subscription (context)
    this.companyId = this.route.snapshot.queryParamMap.get('companyId') || 
                      String(this.companyPermissionsService.activeSubscription()?.id_empresa || '');

    if (!this.companyId) {
      this.errorMsg = 'No se ha detectado el contexto de la empresa actual.';
      return;
    }

    this.loadCompanyDetails();
    this.loadSettings();
  }

  setTab(tab: 'general' | 'appearance' | 'notifications' | 'printing' | 'security'): void {
    this.activeTab = tab;
    this.successMsg = '';
    this.errorMsg = '';
  }

  loadCompanyDetails(): void {
    this.companyService.obtenerEmpresa(this.companyId).subscribe({
      next: (comp) => {
        this.companyName = comp.nombre;
        this.cdr.detectChanges();
      },
      error: () => {
        this.companyName = 'Mi Empresa';
        this.cdr.detectChanges();
      }
    });
  }

  loadSettings(): void {
    this.loading = true;
    this.companyService.getConfiguracionSistema(this.companyId).subscribe({
      next: (data) => {
        this.config = { ...data };
        this.preferencesService.apply(this.config);
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorMsg = 'Error al cargar la configuración del sistema.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  saveSettings(): void {
    this.saving = true;
    this.errorMsg = '';
    this.successMsg = '';

    this.companyService.actualizarConfiguracionSistema(this.companyId, this.config).subscribe({
      next: (data) => {
        this.config = { ...data };
        this.preferencesService.apply(this.config);
        this.successMsg = this.i18n.t('Configuración guardada exitosamente.');
        this.saving = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.errorMsg = err?.error?.detail ?? this.i18n.t('No se pudo guardar la configuración.');
        this.saving = false;
        this.cdr.detectChanges();
      }
    });
  }

  previewLanguage(language: string): void {
    this.i18n.setLanguage(language);
  }

  previewAccent(color: string): void {
    this.config.color_primario = color;
    this.preferencesService.applyAccent(color);
  }

  applyTheme(tema: string): void {
    this.themeService.setTheme(tema);
  }
}
