import { Injectable } from '@angular/core';
import { ApiService } from './api.service';

export type ReportExportFormat = 'excel' | 'pdf';

export interface DynamicReportColumn {
  nombre: string;
  etiqueta?: string;
  tipo?: string;
}

export interface DynamicReportSpecification {
  identificador_plantilla?: string;
  titulo?: string;
  metricas?: string[];
  dimensiones?: string[];
  filtros?: Record<string, string | number | boolean | null>;
  formato?: string;
  solicita_aclaracion?: boolean;
  pregunta?: string | null;
  confianza?: number;
}

export interface DynamicReportResponse {
  id_reporte?: string;
  titulo?: string;
  identificador_plantilla?: string;
  estado?: string;
  especificacion?: DynamicReportSpecification;
  columnas?: DynamicReportColumn[];
  filas?: Array<Record<string, unknown>>;
  agregados?: Record<string, unknown>;
  grafico?: unknown;
  advertencias?: string[];
  fecha_generacion?: string;
}

@Injectable({
  providedIn: 'root',
})
export class DynamicReportService {
  constructor(
    private readonly apiService: ApiService,
  ) {}

  run(companyId: string, prompt: string) {
    const endpoint = `/api/reportes/${companyId}/run`;

    return this.apiService.post<DynamicReportResponse, { prompt: string }>(endpoint, {
      prompt,
    });
  }
}
