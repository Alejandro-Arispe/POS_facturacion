import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export type QbeFieldType = 'TEXTO' | 'NUMERO' | 'FECHA' | 'BOOLEANO';

export interface QbeField {
  codigo: string;
  etiqueta: string;
  tipo: QbeFieldType;
  operadores: string[];
}

export interface QbeSource {
  codigo: string;
  nombre: string;
  descripcion: string;
  campos: QbeField[];
  campos_por_defecto: string[];
}

export interface QbeCondition {
  campo: string;
  operador: string;
  valor: string;
  valor2?: string;
}

export interface QbeAggregate {
  campo: string | null;
  funcion: 'SUMA' | 'CONTEO' | 'PROMEDIO' | 'MAXIMO' | 'MINIMO';
}

export interface QbeQuery {
  fuente: string;
  campos: string[];
  condiciones: QbeCondition[];
  agrupar: string[];
  agregados: QbeAggregate[];
  orden: { campo: string; direccion: 'asc' | 'desc' } | null;
  limite: number;
}

export interface QbeResult {
  columnas: Array<{ codigo: string; etiqueta: string; tipo: QbeFieldType }>;
  filas: unknown[][];
  total_filas: number;
  truncado: boolean;
  descripcion: string;
}

@Injectable({ providedIn: 'root' })
export class QbeService {
  private readonly api = inject(ApiService);

  sources(companyId: string | number): Observable<QbeSource[]> {
    return this.api.get<QbeSource[]>(`/api/reportes/${companyId}/qbe/fuentes`);
  }

  run(companyId: string | number, query: QbeQuery, exporting=false): Observable<QbeResult> {
    return exporting ? this.api.postExport<QbeResult,QbeQuery>(`/api/reportes/${companyId}/qbe/ejecutar`,query) : this.api.post<QbeResult, QbeQuery>(`/api/reportes/${companyId}/qbe/ejecutar`, query);
  }
}
