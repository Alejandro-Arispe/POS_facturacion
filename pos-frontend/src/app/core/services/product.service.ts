import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from './api.service';

export interface CategoriaProducto {
  id_categoria_producto: number;
  id_empresa?: number;
  nombre: string;
  descripcion?: string | null;
  activo: boolean;
  subcategorias?: SubcategoriaProducto[];
}

export interface SubcategoriaProducto {
  id_subcategoria: number;
  id_categoria_producto: number;
  nombre: string;
  descripcion?: string | null;
  activo: boolean;
}

export interface Producto {
  id_producto: number;
  id_empresa?: number;
  id_subcategoria: number | null;
  codigo_barra?: string | null;
  nombre: string;
  descripcion?: string | null;
  unidad_medida: string;
  precio: number;
  imagen?: string | null;
  activo: boolean;
  subcategoria?: SubcategoriaProducto | null;
}

export interface CrearCategoriaRequest {
  nombre: string;
  descripcion?: string;
  activo: boolean;
}

export interface CrearSubcategoriaRequest {
  id_categoria_producto: number;
  nombre: string;
  descripcion?: string;
  activo: boolean;
}

export interface CrearProductoRequest {
  id_subcategoria: number;
  codigo_barra?: string;
  nombre: string;
  descripcion?: string;
  unidad_medida: string;
  precio: number;
  activo: boolean;
}

export interface ActualizarCategoriaRequest {
  nombre?: string;
  descripcion?: string;
  activo?: boolean;
}

export interface ActualizarSubcategoriaRequest {
  id_categoria_producto?: number;
  nombre?: string;
  descripcion?: string;
  activo?: boolean;
}

export interface ActualizarProductoRequest {
  id_subcategoria?: number;
  codigo_barra?: string;
  nombre?: string;
  descripcion?: string;
  unidad_medida?: string;
  precio?: number;
  activo?: boolean;
}

export interface StockProducto {
  id_stock: number;
  id_producto: number;
  cantidad: number;
  stock_min?: number;
  stock_max?: number;
  fecha_actualizacion?: string | null;
}

export interface StockSucursalProducto {
  id_stock: number;
  id_producto: number;
  id_sucursal: number;
  cantidad: number;
  stock_minimo: number;
  stock_maximo: number;
  fecha_actualizacion?: string | null;
  nombre_producto: string;
  codigo_barra?: string | null;
  unidad_medida: string;
  precio: number;
  imagen?: string | null;
  activo: boolean;
}

export interface TipoMovimiento {
  id_tipo_movimiento: number;
  nombre: string;
  descripcion?: string | null;
  direccion?: 'ENTRADA' | 'SALIDA' | string;
}

export interface TipoVenta {
  id_tipo_venta: number;
  nombre: string;
  descripcion?: string | null;
}

export interface MetodoPago {
  id_metodo_pago: number;
  nombre: string;
  descripcion?: string | null;
}

export interface MovimientoInventario {
  id_movimiento?: number;
  id?: number;
  id_producto: number;
  cantidad: number;
  observacion?: string | null;
  tipo?: string | null;
  producto?: {
    id_producto?: number;
    nombre?: string | null;
  } | null;
  tipo_movimiento?:
    | {
        id_tipo_movimiento?: number;
        nombre?: string | null;
      }
    | string
    | null;
  tipoMovimiento?:
    | {
        id_tipo_movimiento?: number;
        nombre?: string | null;
      }
    | string
    | null;
}

export interface ActualizarStockRequest {
  cantidad?: number;
  stock_min?: number;
  stock_max?: number;
}

export interface ActualizarStockSucursalRequest {
  stock_minimo?: number;
  stock_maximo?: number;
}

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  constructor(private readonly apiService: ApiService) {}

  getCategorias(idEmpresa: string): Observable<CategoriaProducto[]> {
    return this.apiService.get<CategoriaProducto[]>(
      `/api/empresas/${idEmpresa}/categorias-con-subcategorias`,
    );
  }

  crearCategoria(idEmpresa: string, payload: CrearCategoriaRequest): Observable<CategoriaProducto> {
    return this.apiService.post<CategoriaProducto, CrearCategoriaRequest>(
      `/api/empresas/${idEmpresa}/categorias`,
      payload,
    );
  }

  obtenerCategoria(idCategoriaProducto: number): Observable<CategoriaProducto> {
    return this.apiService.get<CategoriaProducto>(`/api/productos/categorias/${idCategoriaProducto}`);
  }

  actualizarCategoria(
    idCategoriaProducto: number,
    payload: ActualizarCategoriaRequest,
  ): Observable<CategoriaProducto> {
    return this.apiService.put<CategoriaProducto, ActualizarCategoriaRequest>(
      `/api/productos/categorias/${idCategoriaProducto}`,
      payload,
    );
  }

  eliminarCategoria(idCategoriaProducto: number): Observable<{ mensaje: string }> {
    return this.apiService.delete<{ mensaje: string }>(`/api/productos/categorias/${idCategoriaProducto}`);
  }

  getSubcategorias(idEmpresa: string): Observable<SubcategoriaProducto[]> {
    return this.apiService.get<SubcategoriaProducto[]>(`/api/empresas/${idEmpresa}/subcategorias`);
  }

  crearSubcategoria(payload: CrearSubcategoriaRequest): Observable<SubcategoriaProducto> {
    return this.apiService.post<SubcategoriaProducto, CrearSubcategoriaRequest>(
      '/api/productos/subcategorias',
      payload,
    );
  }

  obtenerSubcategoria(idSubcategoria: number): Observable<SubcategoriaProducto> {
    return this.apiService.get<SubcategoriaProducto>(`/api/productos/subcategorias/${idSubcategoria}`);
  }

  actualizarSubcategoria(
    idSubcategoria: number,
    payload: ActualizarSubcategoriaRequest,
  ): Observable<SubcategoriaProducto> {
    return this.apiService.put<SubcategoriaProducto, ActualizarSubcategoriaRequest>(
      `/api/productos/subcategorias/${idSubcategoria}`,
      payload,
    );
  }

  getProductos(idEmpresa: string): Observable<Producto[]> {
    return this.apiService.get<Producto[]>(`/api/empresas/${idEmpresa}/productos`);
  }

  crearProducto(idEmpresa: string, payload: CrearProductoRequest): Observable<Producto> {
    return this.apiService.post<Producto, CrearProductoRequest>(`/api/empresas/${idEmpresa}/productos`, payload);
  }

  obtenerProducto(idProducto: number): Observable<Producto> {
    return this.apiService.get<Producto>(`/api/productos/${idProducto}`);
  }

  actualizarProducto(idProducto: number, payload: ActualizarProductoRequest): Observable<Producto> {
    return this.apiService.put<Producto, ActualizarProductoRequest>(
      `/api/productos/${idProducto}`,
      payload,
    );
  }

  eliminarProducto(idProducto: number): Observable<{ mensaje: string }> {
    return this.apiService.delete<{ mensaje: string }>(`/api/productos/${idProducto}`);
  }

  actualizarImagenProducto(idProducto: number, imagen: File): Observable<Producto> {
    const formData = new FormData();
    formData.append('imagen', imagen);
    return this.apiService.put<Producto, FormData>(`/api/productos/${idProducto}/imagen`, formData);
  }

  crearProductoConImagen(idEmpresa: string, payload: CrearProductoRequest, imagen: File): Observable<Producto> {
    const formData = new FormData();
    formData.append('id_subcategoria', String(payload.id_subcategoria));
    formData.append('codigo_barra', payload.codigo_barra ?? '');
    formData.append('nombre', payload.nombre);
    formData.append('descripcion', payload.descripcion ?? '');
    formData.append('unidad_medida', payload.unidad_medida);
    formData.append('precio', String(payload.precio));
    formData.append('activo', String(payload.activo));
    formData.append('imagen', imagen);

    return this.apiService.post<Producto, FormData>(`/api/empresas/${idEmpresa}/productos/con-imagen`, formData);
  }


  actualizarStock(idProducto: number, payload: ActualizarStockRequest): Observable<StockProducto> {
    return this.apiService.put<StockProducto, ActualizarStockRequest>(
      `/api/productos/${idProducto}/stock`,
      payload,
    );
  }

  getStockProducto(idProducto: number): Observable<StockProducto> {
    return this.apiService.get<StockProducto>(`/api/productos/${idProducto}/stock`);
  }

  getStockSucursal(idEmpresa: string, idSucursal: string): Observable<StockSucursalProducto[]> {
    return this.apiService.get<StockSucursalProducto[]>(
      `/api/inventario/empresas/${idEmpresa}/sucursales/${idSucursal}/stock`,
    );
  }

  actualizarStockSucursal(
    idEmpresa: string,
    idSucursal: string,
    idProducto: number,
    payload: ActualizarStockSucursalRequest,
  ): Observable<StockSucursalProducto> {
    return this.apiService.put<StockSucursalProducto, ActualizarStockSucursalRequest>(
      `/api/inventario/empresas/${idEmpresa}/sucursales/${idSucursal}/stock/${idProducto}`,
      payload,
    );
  }

  getTiposMovimiento(): Observable<TipoMovimiento[]> {
    return this.apiService.get<TipoMovimiento[]>('/api/inventario/tipos-movimiento');
  }

  getTiposVenta(): Observable<TipoVenta[]> {
    return this.apiService.get<TipoVenta[]>('/api/ventas/tipos-venta');
  }

  getMetodosPago(): Observable<MetodoPago[]> {
    return this.apiService.get<MetodoPago[]>('/api/ventas/metodos-pago');
  }

  getMovimientosSucursal(idEmpresa: string, idSucursal: string): Observable<MovimientoInventario[]> {
    return this.apiService.get<MovimientoInventario[]>(
      `/api/inventario/empresas/${idEmpresa}/sucursales/${idSucursal}/movimientos`,
    );
  }

  crearMovimientoProducto(
    idEmpresa: string,
    idSucursal: string,
    payload: { id_producto: number; id_tipo_movimiento: number; cantidad: number; observacion?: string },
  ): Observable<any> {
    return this.apiService.post<any, typeof payload>(
      `/api/inventario/empresas/${idEmpresa}/sucursales/${idSucursal}/movimientos`,
      payload,
    );
  }

  getResumenInventario(): Observable<any> {
    return this.apiService.get<any>('/api/inventario/estado');
  }

}
