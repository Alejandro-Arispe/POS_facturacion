import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { ApiService } from '../../../../../core/services/api.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

interface RespaldoResumen {
  archivo: string;
  fecha: string;
}

interface RespaldoResultado {
  archivo: string;
  fecha?: string;
  id_empresa: number;
  tablas: string[];
  incluye_ventas: boolean;
  mensaje: string;
}

@Component({
  selector: 'app-company-backup',
  standalone: true,
  imports: [Navbar, Sidebar],
  templateUrl: './backup.html',
  styleUrl: './backup.css',
})
export class CompanyBackup implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected copias: RespaldoResumen[] = [];
  protected cargando = false;
  protected creando = false;
  protected restaurando = '';
  protected error = '';
  protected aviso = '';

  ngOnInit(): void {
    if (!this.companyId) {
      this.error = 'No se encontró la empresa en la ruta.';
      return;
    }
    this.cargar();
  }

  protected crear(): void {
    this.creando = true;
    this.error = '';
    this.aviso = '';
    this.api
      .post<RespaldoResultado, Record<string, never>>(this.ruta(), {})
      .subscribe({
        next: (respuesta) => {
          this.aviso = respuesta.mensaje || 'Copia creada.';
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

  private ruta(): string {
    return `/api/empresas/${this.companyId}/respaldos`;
  }

  private mensajeError(err: unknown, fallback: string): string {
    const detalle = (err as { error?: { detail?: unknown } })?.error?.detail;
    return typeof detalle === 'string' && detalle.trim() ? detalle : fallback;
  }
}
