import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { ApiService } from '../../../../../core/services/api.service';
import { Branch, CompanyService } from '../../../../../core/services/company.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

interface RespaldoResumen {
  archivo: string;
  fecha: string;
  alcance?: string;
  id_sucursal?: number;
  nombre_sucursal?: string;
  tamano_bytes?: number;
}

interface RespaldoResultado {
  archivo: string;
  fecha?: string;
  id_empresa: number;
  id_sucursal?: number;
  alcance?: string;
  tablas: string[];
  incluye_ventas: boolean;
  mensaje: string;
}

interface ConfiguracionRespaldoDTO {
  id_configuracion_respaldo?: number;
  id_empresa?: number;
  activo: boolean;
  frecuencia: string;
  hora: string;
  alcance: string;
  id_sucursal?: number | null;
  email_destino?: string | null;
  enviar_email: boolean;
  ultimo_respaldo_exitoso?: string | null;
  plan_actual?: string;
  permite_automatico: boolean;
  permite_cloud_email: boolean;
}

@Component({
  selector: 'app-company-backup',
  standalone: true,
  imports: [CommonModule, FormsModule, Navbar, Sidebar],
  templateUrl: './backup.html',
  styleUrl: './backup.css',
})
export class CompanyBackup implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected copias: RespaldoResumen[] = [];
  protected sucursales: Branch[] = [];

  // Estado para creación manual
  protected creando = false;
  protected alcanceManual: 'EMPRESA_COMPLETA' | 'SUCURSAL' = 'EMPRESA_COMPLETA';
  protected sucursalManualId: number | null = null;

  // Estado para configuración automática
  protected config: ConfiguracionRespaldoDTO = {
    activo: false,
    frecuencia: 'DIARIO',
    hora: '02:00',
    alcance: 'EMPRESA_COMPLETA',
    id_sucursal: null,
    email_destino: '',
    enviar_email: false,
    permite_automatico: false,
    permite_cloud_email: false,
  };
  protected cargandoConfig = false;
  protected guardandoConfig = false;

  // Estado para restauración y archivos
  protected restaurando = '';
  protected subiendoArchivo = false;
  protected archivoSeleccionado: File | null = null;

  // Mensajes y carga general
  protected cargando = false;
  protected error = '';
  protected aviso = '';

  ngOnInit(): void {
    if (!this.companyId) {
      this.error = 'No se encontró la empresa en la ruta.';
      return;
    }
    this.cargar();
    this.cargarSucursales();
    this.cargarConfiguracion();
  }

  protected crear(): void {
    this.creando = true;
    this.error = '';
    this.aviso = '';

    const payload = {
      id_sucursal: this.alcanceManual === 'SUCURSAL' ? this.sucursalManualId : null,
    };

    this.api
      .post<RespaldoResultado, typeof payload>(this.ruta(), payload)
      .subscribe({
        next: (respuesta) => {
          this.aviso = respuesta.mensaje || 'Copia de seguridad creada exitosamente.';
          this.creando = false;
          this.cdr.detectChanges();
          this.cargar();
        },
        error: (err) => {
          this.error = this.mensajeError(err, 'No se pudo crear la copia.');
          this.creando = false;
          this.cdr.detectChanges();
        },
      });
  }

  protected guardarConfig(): void {
    this.guardandoConfig = true;
    this.error = '';
    this.aviso = '';

    const payload = {
      activo: this.config.activo,
      frecuencia: this.config.frecuencia,
      hora: this.config.hora,
      alcance: this.config.alcance,
      id_sucursal: this.config.alcance === 'SUCURSAL' ? this.config.id_sucursal : null,
      email_destino: this.config.email_destino,
      enviar_email: this.config.enviar_email,
    };

    this.api
      .put<ConfiguracionRespaldoDTO, typeof payload>(`${this.ruta()}/configuracion`, payload)
      .subscribe({
        next: (actualizada) => {
          this.config = { ...this.config, ...actualizada };
          this.aviso = 'Configuración de respaldos automáticos actualizada.';
          this.guardandoConfig = false;
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.error = this.mensajeError(err, 'No se pudo guardar la configuración.');
          this.guardandoConfig = false;
          this.cdr.detectChanges();
        },
      });
  }

  protected onArchivoSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.archivoSeleccionado = input.files[0];
    } else {
      this.archivoSeleccionado = null;
    }
  }

  protected restaurarArchivoSubido(): void {
    if (!this.archivoSeleccionado) {
      this.error = 'Selecciona un archivo JSON primero.';
      return;
    }
    const confirmar = confirm(
      `¿Deseas restaurar la empresa a partir del archivo "${this.archivoSeleccionado.name}"? Los datos actuales del catálogo, sucursales y roles serán reemplazados.`,
    );
    if (!confirmar) return;

    this.subiendoArchivo = true;
    this.error = '';
    this.aviso = '';

    const formData = new FormData();
    formData.append('archivo', this.archivoSeleccionado);

    const url = `${this.api.getBaseUrl()}/api/empresas/${this.companyId}/respaldos/restaurar-archivo`;
    this.http.post<RespaldoResultado>(url, formData).subscribe({
      next: (res) => {
        this.aviso = res.mensaje || 'Respaldo restaurado exitosamente desde archivo.';
        this.subiendoArchivo = false;
        this.archivoSeleccionado = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = this.mensajeError(err, 'No se pudo restaurar el archivo.');
        this.subiendoArchivo = false;
        this.cdr.detectChanges();
      },
    });
  }

  protected restaurar(archivo: string): void {
    const confirmar = confirm(
      'Se reemplazarán los datos de ESTA empresa: catálogo, sucursales, configuración, roles personalizados y suscripción. Las ventas no se modifican y las demás empresas no se tocan. ¿Continuar?',
    );
    if (!confirmar) {
      return;
    }
    this.restaurando = archivo;
    this.error = '';
    this.aviso = '';
    this.api
      .post<RespaldoResultado, Record<string, never>>(
        `${this.ruta()}/${encodeURIComponent(archivo)}/restaurar`,
        {},
      )
      .subscribe({
        next: (respuesta) => {
          this.aviso = respuesta.mensaje || 'Datos de esta empresa restaurados.';
          this.restaurando = '';
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.error = this.mensajeError(err, 'No se pudo restaurar la copia.');
          this.restaurando = '';
          this.cdr.detectChanges();
        },
      });
  }

  protected descargar(archivo: string): void {
    const url = `${this.api.getBaseUrl()}/api/empresas/${this.companyId}/respaldos/${encodeURIComponent(archivo)}`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const enlace = document.createElement('a');
        const objectUrl = URL.createObjectURL(blob);
        enlace.href = objectUrl;
        enlace.download = archivo;
        enlace.click();
        URL.revokeObjectURL(objectUrl);
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'No se pudo descargar la copia.';
        this.cdr.detectChanges();
      },
    });
  }

  protected fechaLegible(fecha: string): string {
    if (!fecha) {
      return '';
    }
    return fecha.replace('T', ' ').slice(0, 16);
  }

  protected formatearTamano(bytes?: number): string {
    if (!bytes || bytes === 0) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  private cargar(): void {
    this.cargando = true;
    this.api.get<RespaldoResumen[]>(this.ruta()).subscribe({
      next: (copias) => {
        this.copias = copias ?? [];
        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = this.mensajeError(err, 'No se pudieron cargar las copias.');
        this.cargando = false;
        this.cdr.detectChanges();
      },
    });
  }

  private cargarSucursales(): void {
    this.companyService.getSucursales(this.companyId).subscribe({
      next: (sucursales: Branch[]) => {
        this.sucursales = sucursales ?? [];
        if (this.sucursales.length > 0) {
          const firstId = this.sucursales[0].id ?? this.sucursales[0].idSucursal ?? this.sucursales[0].id_sucursal;
          this.sucursalManualId = firstId ? Number(firstId) : null;
        }
        this.cdr.detectChanges();
      },
      error: () => {
        // Silencioso si no hay sucursales o permisos
      },
    });
  }

  private cargarConfiguracion(): void {
    this.cargandoConfig = true;
    this.api.get<ConfiguracionRespaldoDTO>(`${this.ruta()}/configuracion`).subscribe({
      next: (cfg) => {
        if (cfg) {
          this.config = {
            ...this.config,
            ...cfg,
            activo: Boolean(cfg.activo),
            frecuencia: cfg.frecuencia || 'DIARIO',
            hora: cfg.hora || '02:00',
            alcance: cfg.alcance || 'EMPRESA_COMPLETA',
            enviar_email: Boolean(cfg.enviar_email),
          };
        }
        this.cargandoConfig = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoConfig = false;
        this.cdr.detectChanges();
      },
    });
  }

  private ruta(): string {
    return `/api/empresas/${this.companyId}/respaldos`;
  }

  private mensajeError(err: unknown, fallback: string): string {
    const detalle = (err as { error?: { detail?: unknown } })?.error?.detail;
    return typeof detalle === 'string' && detalle.trim() ? detalle : fallback;
  }
}
