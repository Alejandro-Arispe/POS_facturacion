import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CurrencySymbolPipe } from '../../../../../../shared/pipes/money.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  CashRegisterListResponse,
  CashRegisterResponse,
  CashRegisterService,
} from '../../../../../../core/services/cash-register.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

type CashRegisterTab = 'list' | 'register' | 'edit';

interface CashRegisterItem {
  id: number;
  codigo: string;
  nombre: string;
  fechaCreacion: string;
  activo: boolean;
  estadoOperativo: 'inactiva' | 'disponible' | 'abierta_por_mi' | 'en_uso';
  actionLabel: 'Abrir caja' | 'Ingresar al POS' | 'Acción deshabilitada' | 'Caja inactiva';
  sessionId: number | null;
}

interface CashRegisterForm {
  codigo: string;
  nombre: string;
  activo: boolean;
}

@Component({
  selector: 'app-cash-register',
  imports: [CurrencySymbolPipe, FormsModule, Navbar, RouterLink, Sidebar],
  templateUrl: './cash_register.html',
  styleUrl: './cash_register.css',
})
export class CashRegister implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cashRegisterService = inject(CashRegisterService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';

  protected activeTab: CashRegisterTab = 'list';
  protected mensaje = '';
  protected error = '';
  protected cargandoCajas = false;
  protected cargandoRegistro = false;
  protected cargandoEdicion = false;
  protected editingCashRegisterId: number | null = null;

  // HU: Apertura de caja - Modal de monto inicial y validación de sesión
  protected isModalAperturaOpen = false;
  protected selectedCajaParaAbrir: CashRegisterItem | null = null;
  protected montoInicialApertura: number | null = 0;
  protected notaApertura = '';
  protected abriendoCajaModal = false;
  protected errorAperturaModal = '';

  protected get miCajaAbierta(): CashRegisterItem | undefined {
    return this.cashRegisters.find((c) => c.estadoOperativo === 'abierta_por_mi');
  }

  protected registerForm: Omit<CashRegisterForm, 'activo'> = {
    codigo: '',
    nombre: '',
  };

  protected editForm: CashRegisterForm = {
    codigo: '',
    nombre: '',
    activo: true,
  };

  protected cashRegisters: CashRegisterItem[] = [];



  ngOnInit(): void {
    this.cargarCajas();
  }

  protected setActiveTab(tab: CashRegisterTab): void {
    if (tab === 'register' && !this.hasPermission('CAJA_CREAR')) {
      return;
    }

    if (tab === 'edit' && (this.editingCashRegisterId === null || !this.hasPermission('CAJA_EDITAR'))) {
      return;
    }

    this.activeTab = tab;
    this.clearMessages();
  }

  private cargarCajas(): void {
    this.error = '';

    if (!this.mensaje) {
      this.mensaje = '';
    }

    if (!this.companyId || !this.branchId) {
      this.error = 'No se encontro la empresa o sucursal para cargar las cajas.';
      return;
    }

    this.cargandoCajas = true;

    this.cashRegisterService.getCajasSucursal(this.companyId, this.branchId)
      .pipe(
        finalize(() => {
          this.cargandoCajas = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
      next: (response) => {
        const cashRegisters = this.normalizeCashRegisterListResponse(response);

        this.cashRegisters = cashRegisters.map((cashRegister) =>
          this.mapCashRegisterResponse(cashRegister),
        );
      },
      error: () => {
        this.cashRegisters = [];
        this.error = 'No se pudieron cargar las cajas registradoras.';
      },
    });
  }

  protected abrirModalApertura(caja: CashRegisterItem): void {
    this.selectedCajaParaAbrir = caja;
    this.montoInicialApertura = 0;
    this.notaApertura = 'Apertura de caja';
    this.errorAperturaModal = '';
    this.isModalAperturaOpen = true;
  }

  protected cerrarModalApertura(): void {
    if (this.abriendoCajaModal) {
      return;
    }
    this.isModalAperturaOpen = false;
    this.selectedCajaParaAbrir = null;
    this.errorAperturaModal = '';
  }

  protected confirmarAperturaCaja(event: SubmitEvent): void {
    event.preventDefault();
    this.errorAperturaModal = '';

    if (
      this.montoInicialApertura === null ||
      this.montoInicialApertura === undefined ||
      Number.isNaN(this.montoInicialApertura) ||
      this.montoInicialApertura < 0
    ) {
      this.errorAperturaModal = 'Ingresa un monto inicial valido (mayor o igual a 0).';
      return;
    }

    if (!this.selectedCajaParaAbrir) {
      this.errorAperturaModal = 'No se ha seleccionado una caja para abrir.';
      return;
    }

    const cajaId = this.selectedCajaParaAbrir.id;
    this.abriendoCajaModal = true;

    this.cashRegisterService
      .abrirSesionCaja(String(cajaId), {
        monto_inicial: Number(this.montoInicialApertura),
        nota: this.notaApertura.trim() || 'Apertura de caja',
      })
      .pipe(
        finalize(() => {
          this.abriendoCajaModal = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (response) => {
          this.isModalAperturaOpen = false;
          void this.router.navigate(
            [
              '/company',
              this.companyId,
              'branch',
              this.branchId,
              'cash-register',
              cajaId,
              'sales',
            ],
            {
              queryParams: { sessionId: response.id_caja_sesion },
            },
          );
        },
        error: (error) => {
          this.errorAperturaModal =
            error?.error?.detail ??
            'No se pudo abrir la caja. Verifica que no tengas otra sesión abierta.';
        },
      });
  }

  protected registrarCaja(event: SubmitEvent): void {
    event.preventDefault();
    this.clearMessages();

    if (!this.hasPermission('CAJA_EDITAR')) {
      this.error = 'No tienes permiso para registrar cajas.';
      return;
    }

    const codigo = this.registerForm.codigo.trim();
    const nombre = this.registerForm.nombre.trim();

    if (!codigo || !nombre) {
      this.error = 'Completa el codigo y nombre de la caja.';
      return;
    }

    if (!this.branchId) {
      this.error = 'No se encontro la sucursal para registrar la caja.';
      return;
    }

    if (this.cashRegisters.some((cashRegister) => cashRegister.codigo.toLowerCase() === codigo.toLowerCase())) {
      this.error = 'Ya existe una caja con ese codigo.';
      return;
    }

    this.cargandoRegistro = true;

    this.cashRegisterService
      .crearCaja(this.branchId, { nombre, codigo })
      .pipe(
        finalize(() => {
          this.cargandoRegistro = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
      next: (cashRegister) => {
        this.cargandoRegistro = false;
        const registeredCashRegister = cashRegister ?? ({} as CashRegisterResponse);

        this.cashRegisters = [
          ...this.cashRegisters,
          this.mapCashRegisterResponse({
            ...registeredCashRegister,
            nombre: registeredCashRegister.nombre ?? nombre,
            codigo: registeredCashRegister.codigo ?? codigo,
          }),
        ];
        this.registerForm = {
          codigo: '',
          nombre: '',
        };
        this.mensaje = 'Caja registrada correctamente.';
        this.activeTab = 'list';
        this.cdr.detectChanges();
      },
      error: (error: { error?: { detail?: string } }) => {
        this.cargandoRegistro = false;
        this.error = error?.error?.detail ?? 'No se pudo registrar la caja. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  protected abrirEdicion(cashRegister: CashRegisterItem): void {
    if (!this.hasPermission('CAJA_EDITAR')) {
      this.error = 'No tienes permiso para editar cajas.';
      return;
    }

    this.clearMessages();
    this.editingCashRegisterId = cashRegister.id;
    this.editForm = {
      codigo: cashRegister.codigo,
      nombre: cashRegister.nombre,
      activo: cashRegister.activo,
    };
    this.activeTab = 'edit';
  }

  protected cancelarEdicion(): void {
    this.clearMessages();
    this.editingCashRegisterId = null;
    this.editForm = {
      codigo: '',
      nombre: '',
      activo: true,
    };
    this.activeTab = 'list';
  }

  protected guardarCambios(event: SubmitEvent): void {
    event.preventDefault();
    this.clearMessages();

    if (!this.hasPermission('CAJA_EDITAR')) {
      this.error = 'No tienes permiso para editar cajas.';
      return;
    }

    const codigo = this.editForm.codigo.trim();
    const nombre = this.editForm.nombre.trim();

    if (this.editingCashRegisterId === null) {
      this.error = 'Selecciona una caja para editar.';
      return;
    }

    if (!this.companyId || !this.branchId) {
      this.error = 'No se encontro la empresa o sucursal para actualizar la caja.';
      return;
    }

    if (!codigo || !nombre) {
      this.error = 'Completa el codigo y nombre de la caja.';
      return;
    }

    const codigoRepetido = this.cashRegisters.some(
      (cashRegister) =>
        cashRegister.id !== this.editingCashRegisterId &&
        cashRegister.codigo.toLowerCase() === codigo.toLowerCase(),
    );

    if (codigoRepetido) {
      this.error = 'Ya existe otra caja con ese codigo.';
      return;
    }

    const editingCashRegisterId = this.editingCashRegisterId;

    this.cargandoEdicion = true;

    this.cashRegisterService
      .actualizarCaja(this.companyId, this.branchId, editingCashRegisterId, {
        nombre,
        codigo,
        activo: this.editForm.activo,
      })
      .pipe(
        finalize(() => {
          this.cargandoEdicion = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (cashRegister) => {
          this.cargandoEdicion = false;
          const updatedCashRegister = this.mapCashRegisterResponse({
            ...cashRegister,
            id_caja: cashRegister.id_caja ?? editingCashRegisterId,
            nombre: cashRegister.nombre ?? nombre,
            codigo: cashRegister.codigo ?? codigo,
            activo: cashRegister.activo ?? this.editForm.activo,
          });

          this.cashRegisters = this.cashRegisters.map((currentCashRegister) =>
            currentCashRegister.id === editingCashRegisterId
              ? updatedCashRegister
              : currentCashRegister,
          );

          this.editingCashRegisterId = null;
          this.activeTab = 'list';
          this.mensaje = 'Caja actualizada correctamente.';
          this.cdr.detectChanges();
        },
        error: () => {
          this.cargandoEdicion = false;
          this.error = 'No se pudo actualizar la caja. Intenta nuevamente.';
          this.cdr.detectChanges();
        },
      });
  }

  private getNextId(): number {
    return Math.max(0, ...this.cashRegisters.map((cashRegister) => cashRegister.id)) + 1;
  }

  private mapCashRegisterResponse(cashRegister: CashRegisterResponse): CashRegisterItem {
    const id = cashRegister.id_caja ?? cashRegister.id ?? this.getNextId();
    let estadoOperativo = cashRegister.estado_operativo;
    let sessionId: number | null = cashRegister.sesion_activa?.id_caja_sesion ?? null;

    if (!estadoOperativo) {
      const policyKey = `cash-register-open-policy:${this.companyId}:${this.branchId}`;
      const rawPolicy = sessionStorage.getItem(policyKey);
      if (rawPolicy) {
        try {
          const parsed = JSON.parse(rawPolicy);
          if (parsed.allowedCashRegisterId === id && parsed.sessionId) {
            estadoOperativo = 'abierta_por_mi';
            sessionId = parsed.sessionId;
          } else if (parsed.blockedCashRegisterIds?.includes(id)) {
            estadoOperativo = 'en_uso';
          }
        } catch {}
      }
    }

    if (!estadoOperativo) {
      estadoOperativo = cashRegister.activo ? 'disponible' : 'inactiva';
    }
    
    let actionLabel: CashRegisterItem['actionLabel'] = 'Abrir caja';
    
    if (estadoOperativo === 'inactiva') {
      actionLabel = 'Caja inactiva';
    } else if (estadoOperativo === 'disponible') {
      actionLabel = 'Abrir caja';
    } else if (estadoOperativo === 'abierta_por_mi') {
      actionLabel = 'Ingresar al POS';
    } else if (estadoOperativo === 'en_uso') {
      actionLabel = 'Acción deshabilitada';
    }

    return {
      id,
      codigo: cashRegister.codigo,
      nombre: cashRegister.nombre,
      fechaCreacion: this.formatDate(cashRegister.fecha_creacion ?? cashRegister.fecha_registro),
      activo: cashRegister.activo ?? true,
      estadoOperativo,
      actionLabel,
      sessionId,
    };
  }

  private normalizeCashRegisterListResponse(response: CashRegisterListResponse): CashRegisterResponse[] {
    if (Array.isArray(response)) {
      return response;
    }

    if (Array.isArray(response.cajas)) {
      return response.cajas;
    }

    if (Array.isArray(response.items)) {
      return response.items;
    }

    if (Array.isArray(response.data)) {
      return response.data;
    }

    if (response.data && Array.isArray(response.data.cajas)) {
      return response.data.cajas;
    }

    if (response.data && Array.isArray(response.data.items)) {
      return response.data.items;
    }

    return [];
  }


  private formatDate(date?: string): string {
    if (!date) {
      return this.getCurrentDate();
    }

    const parsedDate = new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
      return date;
    }

    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(parsedDate);
  }

  private getCurrentDate(): string {
    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(new Date());
  }

  private clearMessages(): void {
    this.error = '';
    this.mensaje = '';
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    const permissions = this.companyPermissionsService.permissions();
    const codes = this.companyPermissionsService.getPermissionCodes();
    if (codes.length === 0) {
      return true;
    }
    return permissions[permission] === true;
  }
}

