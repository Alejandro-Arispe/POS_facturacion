import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from './api.service';
import {
  CompanyActiveSubscription,
  CompanyPermission,
} from './company-permissions.service';

export interface Company {
  idEmpresa?: string | number;
  id_empresa?: string | number;
  empresa_id?: string | number;
  id?: string | number;
  nombre: string;
  razon_social: string;
  nit: string;
  correo: string;
  fecha_registro?: string;
  fecha_creacion?: string;
  activo?: boolean;
  suscripcion_activa?: {
    estado: string;
    fecha_fin: string;
    plan_nombre: string;
  } | null;
}

export interface CreateCompanyRequest {
  nombre: string;
  razon_social: string;
  nit: string;
  correo: string;
}

export interface UpdateCompanyRequest extends CreateCompanyRequest {
  activo: boolean;
}

export interface CreateBranchRequest {
  nombre: string;
  direccion: string;
  telefono: string;
  ciudad: string;
}

export interface UpdateBranchRequest extends CreateBranchRequest {
  activo: boolean;
}

export interface Branch {
  id?: string | number;
  idSucursal?: string | number;
  id_sucursal?: string | number;
  nombre: string;
  direccion?: string;
  telefono?: string;
  ciudad?: string;
  activo?: boolean;
  fecha_registro?: string;
}

export interface InviteEmployeeRequest {
  email: string;
  id_sucursales: number[];
  id_rol: number;
}

export interface InviteEmployeeResponse {
  mensaje?: string;
  message?: string;
  email?: string;
  link_invitacion?: string;
  id_sucursales?: number[];
  id_rol?: number;
  [key: string]: unknown;
}

export interface UpdateEmployeeRequest {
  email: string;
  id_sucursales: number[];
  id_rol: number;
  activo: boolean;
}

export interface InviteClientRequest {
  email: string;
}

export interface InviteClientResponse {
  mensaje: string;
  email: string;
  link_invitacion: string;
}

export interface CreateClientCategoryRequest {
  nombre: string;
  descripcion: string;
  plazo_credito: number;
  descuento_base: number;
  limite_credito: number;
}

export interface UpdateClientCategoryRequest {
  nombre: string;
  descripcion: string;
  plazo_credito: number;
  descuento_base: number;
  limite_credito: number;
  activo: boolean;
}

export interface CreateClientCategoryResponse {
  id_categoria_cliente: number;
  id_empresa: number;
  nombre: string;
  descripcion: string;
  plazo_credito: number;
  permite_credito?: boolean;
  descuento_base: string | number;
  limite_credito: string | number;
  activo: boolean;
}

export interface ClientCategoryResponse {
  id_categoria_cliente: number;
  id_empresa: number;
  nombre: string;
  descripcion: string;
  plazo_credito: number;
  permite_credito?: boolean;
  descuento_base: string | number;
  limite_credito: string | number;
  activo: boolean;
}

export interface ClientPerson {
  id_persona: number;
  nombre_completo: string;
  fecha_nacimiento: string;
  genero: string;
  telefono: string;
  documento: string;
}

export interface ClientUser {
  id_usuario: number;
  email: string;
  activo: boolean;
  persona?: ClientPerson;
}

export interface ClientRecord {
  id_cliente: number;
  id_usuario: number;
  id_categoria_cliente: number | null;
  codigo_cliente: string;
  saldo_credito: number | null;
  limite_credito: number | null;
  activo: boolean;
}

export interface ClientRole {
  id_usuario_rol: number;
  id_usuario: number;
  id_rol: number;
  id_empresa: number;
  id_sucursal: number | null;
  activo: boolean;
  usuario: ClientUser;
  cliente: ClientRecord;
}

export interface CreditPaymentMethod {
  id_metodo_pago: number;
  nombre: string;
  descripcion: string | null;
}

export interface CreditPayment {
  id_pago_credito: number;
  id_metodo_pago: number;
  monto_pagado: string | number;
  fecha_pago: string;
  metodo_pago: CreditPaymentMethod;
}

export interface ReceivableSaleProduct {
  id_producto: number;
  nombre: string;
  codigo_barra: string | null;
  unidad_medida: string;
}

export interface ReceivableSaleDetail {
  id_detalle_venta: number;
  id_producto: number;
  cantidad: number;
  precio_unitario: string | number;
  descuento: string | number;
  subtotal: string | number;
  total: string | number;
  descripcion: string | null;
  producto: ReceivableSaleProduct;
}

export interface ReceivableSale {
  id_venta: number;
  id_tipo_venta: number;
  id_cliente: number;
  id_caja_sesion: number;
  id_usuario: number;
  subtotal: string | number;
  descuento_total: string | number;
  total: string | number;
  fecha: string;
  estado: string;
  tipo_venta_nombre: string;
  detalles: ReceivableSaleDetail[];
}

export interface ClientReceivable {
  id_cxc: number;
  id_venta: number;
  monto_credito: string | number;
  saldo_pendiente: string | number;
  fecha_inicio: string;
  fecha_vencimiento: string;
  estado: string;
  venta: ReceivableSale;
  pagos_credito: CreditPayment[];
}

export interface UpdateClientRequest {
  id_categoria_cliente: number;
  codigo_cliente: string;
  activo: boolean;
}

export interface UpdateClientResponse {
  id_cliente: number;
  id_usuario: number;
  id_categoria_cliente: number;
  codigo_cliente: string;
  saldo_credito: number;
  limite_credito: number;
  activo: boolean;
}

export interface EmployeeRole {
  id_usuario_rol: number;
  id_usuario: number;
  id_rol: number;
  id_empresa: number;
  id_sucursal: number;
  activo: boolean;
  usuario: {
    id_usuario: number;
    email: string;
    activo: boolean;
    persona?: {
      id_persona: number;
      nombre_completo: string;
      fecha_nacimiento: string;
      genero: string;
      telefono: string;
      documento: string;
    };
  };
}

export interface EmployeeRelation {
  id_usuario_rol: number;
  id_rol: number;
  id_empresa: number;
  id_sucursal: number;
  activo: boolean;
}

export interface CompanyStaffMember {
  id_usuario: number;
  usuario: EmployeeRole['usuario'];
  relaciones: EmployeeRelation[];
}

export interface CashRegisterMovementType {
  id_tipo_movimiento_caja: number;
  nombre: string;
  descripcion?: string | null;
}

export interface PermissionByModulePermission {
  id_permiso: number;
  codigo: string;
  nombre: string;
}

export interface PermissionModuleWithPermissions {
  id_modulo: number;
  codigo: string;
  nombre: string;
  permisos: PermissionByModulePermission[];
}

export interface RoleListItem {
  id_rol: number;
  nombre: string;
  tipo: string;
  descripcion: string;
  activo: boolean;
}

export interface RolePermissionDetail {
  id_rol_permiso: number;
  id_rol: number;
  id_permiso: number;
  activo: boolean;
}

export interface RoleDetailResponse extends RoleListItem {
  rol_permisos: RolePermissionDetail[];
}

export interface CreateRoleRequest {
  nombre: string;
  permiso_ids: number[];
}

export interface CreateRoleResponse {
  rol: RoleListItem;
  permiso_ids: number[];
}

export interface UpdateRoleRequest {
  nombre: string;
  permiso_ids: number[];
  activo: boolean;
}

export interface CompanyAccessResponse {
  permisos: CompanyPermission[];
  suscripcion_activa: CompanyActiveSubscription | null;
}

@Injectable({
  providedIn: 'root',
})
export class CompanyService {
  constructor(private readonly apiService: ApiService) {}

  getMisEmpresas(): Observable<Company[]> {
    return this.apiService.get<Company[]>('/api/empresas/mis-empresas');
  }

  getMisPermisos(idEmpresa: string | number): Observable<CompanyAccessResponse> {
    return this.apiService.get<CompanyAccessResponse>(`/api/empresas/${idEmpresa}/mis-permisos`);
  }

  getPermisosPorModulo(): Observable<PermissionModuleWithPermissions[]> {
    return this.apiService.get<PermissionModuleWithPermissions[]>('/api/empresas/permisos-por-modulo');
  }

  getRoles(idEmpresa: string | number): Observable<RoleListItem[]> {
    return this.apiService.get<RoleListItem[]>(`/api/roles/empresa/${idEmpresa}`);
  }

  getRoleById(idRol: string | number): Observable<RoleDetailResponse> {
    return this.apiService.get<RoleDetailResponse>(`/api/roles/${idRol}`);
  }

  actualizarRol(idRol: string | number, payload: UpdateRoleRequest): Observable<RoleDetailResponse> {
    return this.apiService.put<RoleDetailResponse, UpdateRoleRequest>(`/api/roles/${idRol}`, payload);
  }

  crearRol(idEmpresa: string | number, payload: CreateRoleRequest): Observable<CreateRoleResponse> {
    return this.apiService.post<CreateRoleResponse, CreateRoleRequest>(`/api/roles/empresa/${idEmpresa}`, payload);
  }

  obtenerEmpresa(idEmpresa: string): Observable<Company> {
    return this.apiService.get<Company>(`/api/empresas/${idEmpresa}`);
  }

  crearEmpresa(payload: CreateCompanyRequest): Observable<Company> {
    return this.apiService.post<Company, CreateCompanyRequest>('/api/empresas/crear', payload);
  }

  actualizarEmpresa(idEmpresa: string, payload: UpdateCompanyRequest): Observable<Company> {
    return this.apiService.put<Company, UpdateCompanyRequest>(
      `/api/empresas/${idEmpresa}`,
      payload,
    );
  }

  crearSucursal(idEmpresa: string, payload: CreateBranchRequest): Observable<Branch> {
    return this.apiService.post<Branch, CreateBranchRequest>(
      `/api/empresas/${idEmpresa}/sucursales`,
      payload,
    );
  }

  getSucursales(idEmpresa: string): Observable<Branch[]> {
    return this.apiService.get<Branch[]>(`/api/empresas/${idEmpresa}/sucursales`);
  }

  obtenerSucursal(idEmpresa: string, idSucursal: string): Observable<Branch> {
    return this.apiService.get<Branch>(`/api/empresas/${idEmpresa}/sucursales/${idSucursal}`);
  }

  actualizarSucursal(idSucursal: string, payload: UpdateBranchRequest): Observable<Branch> {
    return this.apiService.put<Branch, UpdateBranchRequest>(
      `/api/sucursales/${idSucursal}`,
      payload,
    );
  }

  invitarEmpleado(
    idEmpresa: string,
    payload: InviteEmployeeRequest,
  ): Observable<InviteEmployeeResponse> {
    return this.apiService.post<InviteEmployeeResponse, InviteEmployeeRequest>(
      `/api/empresas/${idEmpresa}/invitar-empleado`,
      payload,
    );
  }

  invitarCliente(idEmpresa: string, payload: InviteClientRequest): Observable<InviteClientResponse> {
    return this.apiService.post<InviteClientResponse, InviteClientRequest>(
      `/api/empresas/${idEmpresa}/invitar-cliente`,
      payload,
    );
  }

  getClientesEmpresa(idEmpresa: string): Observable<ClientRole[]> {
    return this.apiService.get<ClientRole[]>(`/api/empresas/${idEmpresa}/clientes`);
  }

  getCuentasPorCobrarCliente(
    idEmpresa: string | number,
    idCliente: string | number,
  ): Observable<ClientReceivable[]> {
    return this.apiService.get<ClientReceivable[]>(
      `/api/empresas/${idEmpresa}/clientes/${idCliente}/cuentas-por-cobrar`,
    );
  }

  actualizarClienteEmpresa(
    idEmpresa: string,
    idCliente: string | number,
    payload: UpdateClientRequest,
  ): Observable<UpdateClientResponse> {
    return this.apiService.put<UpdateClientResponse, UpdateClientRequest>(
      `/api/empresas/${idEmpresa}/clientes/${idCliente}`,
      payload,
    );
  }

  getCategoriasCliente(idEmpresa: string): Observable<ClientCategoryResponse[]> {
    return this.apiService.get<ClientCategoryResponse[]>(`/api/categorias-cliente/${idEmpresa}`);
  }

  crearCategoriaCliente(
    idEmpresa: string,
    payload: CreateClientCategoryRequest,
  ): Observable<CreateClientCategoryResponse> {
    return this.apiService.post<CreateClientCategoryResponse, CreateClientCategoryRequest>(
      `/api/categorias-cliente/${idEmpresa}`,
      payload,
    );
  }

  actualizarCategoriaCliente(
    idEmpresa: string,
    idCategoriaCliente: string | number,
    payload: UpdateClientCategoryRequest,
  ): Observable<CreateClientCategoryResponse> {
    return this.apiService.put<CreateClientCategoryResponse, UpdateClientCategoryRequest>(
      `/api/empresas/${idEmpresa}/categorias-cliente/${idCategoriaCliente}`,
      payload,
    );
  }

  getEmpleadosSucursal(idEmpresa: string, idSucursal: string): Observable<EmployeeRole[]> {
    return this.apiService.get<EmployeeRole[]>(
      `/api/empresas/${idEmpresa}/sucursales/${idSucursal}/empleados`,
    );
  }

  getPersonalEmpresa(idEmpresa: string): Observable<CompanyStaffMember[]> {
    return this.apiService.get<CompanyStaffMember[]>(`/api/empresas/${idEmpresa}/personal`);
  }

  editarPersonalEmpresa(
    idEmpresa: string,
    payload: UpdateEmployeeRequest,
  ): Observable<EmployeeRole[]> {
    return this.apiService.put<EmployeeRole[], UpdateEmployeeRequest>(
      `/api/empresas/${idEmpresa}/editarpersonal`,
      payload,
    );
  }

  getTiposMovimientoCaja(): Observable<CashRegisterMovementType[]> {
    return this.apiService.get<CashRegisterMovementType[]>('/api/empresas/tipos-movimiento-caja');
  }

  getConfiguracionSistema(idEmpresa: string | number): Observable<SystemConfiguration> {
    return this.apiService.get<SystemConfiguration>(`/api/configuracion-sistema/empresas/${idEmpresa}`);
  }

  actualizarConfiguracionSistema(idEmpresa: string | number, payload: SystemConfiguration): Observable<SystemConfiguration> {
    return this.apiService.put<SystemConfiguration, SystemConfiguration>(`/api/configuracion-sistema/empresas/${idEmpresa}`, payload);
  }
}

export interface SystemConfiguration {
  id_configuracion?: number;
  id_empresa?: number;
  tema: string;
  idioma: string;
  zona_horaria: string;
  moneda: string;
  activar_notificaciones_push: boolean;
  activar_sonido: boolean;
  activar_vibracion: boolean;
  confirmar_antes_de_eliminar: boolean;
  cerrar_sesion_por_inactividad: boolean;
  minutos_inactividad: number;
  imprimir_automaticamente: boolean;
  numero_copias: number;
  tamano_ticket: string;
  color_primario?: string | null;
}
