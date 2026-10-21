import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../../shared/pipes/money.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ClientReceivable,
  ClientRole,
  CompanyService,
} from '../../../../../../core/services/company.service';
import {
  MetodoPago,
  ProductService,
} from '../../../../../../core/services/product.service';
import {
  CashRegisterService,
  RegisterCreditPaymentRequest,
} from '../../../../../../core/services/cash-register.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

type CreditTab = 'receivables' | 'payment';

interface CreditClient {
  id: number;
  name: string;
  document: string;
  code: string;
  balance: number;
  active: boolean;
}

interface PaymentMethodOption {
  id: number;
  name: string;
}

interface CreditPaymentRow {
  id: number;
  paymentMethodId: number | null;
  amount: number | null;
}

@Component({
  selector: 'app-credit-collections',
  imports: [MoneyPipe, FormsModule, Navbar, Sidebar],
  templateUrl: './credit_collections.html',
  styleUrl: './credit_collections.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreditCollections implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly productService = inject(ProductService);
  private readonly cashRegisterService = inject(CashRegisterService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected readonly cashRegisterId = this.route.snapshot.paramMap.get('cashRegisterId') ?? '';
  protected readonly cashRegisterSessionId = this.route.snapshot.queryParamMap.get('sessionId') ?? '';

  protected searchTerm = '';
  protected activeTab: CreditTab = 'receivables';
  protected loadingClients = false;
  protected loadingPaymentMethods = false;
  protected savingPayment = false;
  protected clientsError = '';
  protected clients: CreditClient[] = [];
  protected selectedClient: CreditClient | null = null;
  protected receivables: ClientReceivable[] = [];
  protected loadingReceivables = false;
  protected receivablesError = '';
  protected paymentError = '';
  protected paymentMessage = '';
  protected selectedReceivable: ClientReceivable | null = null;
  protected paymentMethods: PaymentMethodOption[] = [];
  protected paymentRows: CreditPaymentRow[] = [];
  protected nextPaymentRowId = 1;
  private receivablesRequestId = 0;

  ngOnInit(): void {
    this.loadClients();
    this.loadPaymentMethods();
  }

  protected get filteredClients(): CreditClient[] {
    const term = this.normalize(this.searchTerm);

    if (!term) {
      return this.clients;
    }

    return this.clients.filter((client) =>
      [client.name, client.document, client.code]
        .some((value) => this.normalize(value).includes(term)),
    );
  }

  protected selectClient(client: CreditClient): void {
    this.selectedClient = client;
    this.closePaymentTab();
    this.loadReceivables(client.id);
  }

  protected openPaymentTab(receivable: ClientReceivable): void {
    this.selectedReceivable = receivable;
    this.paymentError = '';
    this.paymentMessage = '';
    this.paymentRows = [
      this.createPaymentRow(undefined, this.normalizeMoney(receivable.saldo_pendiente)),
    ];
    this.activeTab = 'payment';
  }

  protected closePaymentTab(): void {
    this.activeTab = 'receivables';
    this.selectedReceivable = null;
    this.paymentRows = [];
    this.paymentError = '';
  }

  protected updatePaymentAmount(row: CreditPaymentRow, value: number | string | null): void {
    row.amount = this.normalizeMoney(value);
  }

  protected get remainingPaymentAmount(): number {
    const balance = this.normalizeMoney(this.selectedReceivable?.saldo_pendiente ?? 0);
    const assigned = this.paymentRows.reduce(
      (total, row) => total + this.normalizeMoney(row.amount),
      0,
    );
    return this.roundCurrency(Math.max(balance - assigned, 0));
  }

  protected trackPaymentRow(_index: number, row: CreditPaymentRow): number {
    return row.id;
  }

  protected registerCreditPayment(): void {
    this.paymentError = '';
    this.paymentMessage = '';

    if (!this.cashRegisterSessionId) {
      this.paymentError = 'No se encontro la sesion de caja para registrar el pago.';
      return;
    }

    if (!this.selectedReceivable) {
      this.paymentError = 'No se encontro la cuenta por cobrar seleccionada.';
      return;
    }

    const payments = this.paymentRows.map((row) => ({
      id_metodo_pago: row.paymentMethodId,
      monto_pagado: this.normalizeMoney(row.amount),
    }));

    if (payments.length === 0) {
      this.paymentError = 'Agrega al menos una forma de pago.';
      return;
    }

    if (payments.some((payment) => payment.id_metodo_pago === null)) {
      this.paymentError = 'Selecciona un metodo de pago en cada fila.';
      return;
    }

    if (payments.some((payment) => payment.monto_pagado <= 0)) {
      this.paymentError = 'Ingresa un monto mayor a 0 en cada fila.';
      return;
    }

    const totalPayment = this.roundCurrency(
      payments.reduce((total, payment) => total + payment.monto_pagado, 0),
    );
    const pendingBalance = this.normalizeMoney(this.selectedReceivable.saldo_pendiente);

    if (totalPayment > pendingBalance) {
      this.paymentError =
        `El total pagado no puede superar el saldo pendiente de Bs ${this.formatCurrency(pendingBalance)}.`;
      return;
    }

    const payload: RegisterCreditPaymentRequest = {
      id_cxc: this.selectedReceivable.id_cxc,
      pagos_credito: payments.map((payment) => ({
        id_metodo_pago: Number(payment.id_metodo_pago),
        monto_pagado: payment.monto_pagado.toFixed(2),
      })),
    };

    const selectedClientId = this.selectedClient?.id;
    this.savingPayment = true;

    this.cashRegisterService
      .registrarPagoCreditoSesionCaja(this.cashRegisterSessionId, payload)
      .pipe(
        finalize(() => {
          this.savingPayment = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          this.closePaymentTab();
          this.paymentMessage = 'Pago de credito registrado correctamente.';

          if (selectedClientId) {
            this.loadReceivables(selectedClientId);
          }
        },
        error: (error) => {
          this.paymentError =
            error?.error?.detail ?? 'No se pudo registrar el pago de credito.';
        },
      });
  }

  protected trackClient(_index: number, client: CreditClient): number {
    return client.id;
  }

  protected formatCurrency(value: string | number): string {
    return new Intl.NumberFormat('es-BO', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value) || 0);
  }

  protected formatDate(value: string): string {
    if (!value) {
      return '-';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('es-BO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }

  protected trackReceivable(_index: number, receivable: ClientReceivable): number {
    return receivable.id_cxc;
  }

  protected getStatusClass(status: string): string {
    const normalizedStatus = this.normalize(status);

    if (['pagado', 'cancelado'].includes(normalizedStatus)) {
      return 'status-paid';
    }

    if (['vencido', 'atrasado'].includes(normalizedStatus)) {
      return 'status-overdue';
    }

    return 'status-pending';
  }

  private loadReceivables(clientId: number): void {
    const requestId = ++this.receivablesRequestId;
    this.receivables = [];
    this.receivablesError = '';
    this.loadingReceivables = true;

    this.companyService.getCuentasPorCobrarCliente(this.companyId, clientId).subscribe({
      next: (receivables) => {
        if (requestId !== this.receivablesRequestId) {
          return;
        }

        this.receivables = Array.isArray(receivables)
          ? receivables.filter((receivable) => this.normalize(receivable.estado) !== 'pagada')
          : [];

        const totalPending = this.roundCurrency(
          this.receivables.reduce((sum, r) => sum + this.normalizeMoney(r.saldo_pendiente), 0)
        );
        if (this.selectedClient && this.selectedClient.id === clientId) {
          this.selectedClient.balance = totalPending;
        }
        const clientItem = this.clients.find((c) => c.id === clientId);
        if (clientItem) {
          clientItem.balance = totalPending;
        }

        this.loadingReceivables = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        if (requestId !== this.receivablesRequestId) {
          return;
        }

        this.receivables = [];
        this.loadingReceivables = false;
        this.receivablesError =
          error?.error?.detail ?? 'No se pudieron cargar las cuentas por cobrar del cliente.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadClients(): void {
    if (!this.companyId) {
      this.clientsError = 'No se encontro la empresa para cargar los clientes.';
      return;
    }

    this.loadingClients = true;
    this.clientsError = '';

    this.companyService
      .getClientesEmpresa(this.companyId)
      .pipe(
        finalize(() => {
          this.loadingClients = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (clients) => {
          this.clients = clients
            .map((client) => this.mapClient(client))
            .filter((client) => client.active)
            .sort((first, second) => second.balance - first.balance);
          this.selectedClient = null;
          this.receivables = [];
        },
        error: (error) => {
          this.clients = [];
          this.selectedClient = null;
          this.receivables = [];
          this.clientsError = error?.error?.detail ?? 'No se pudieron cargar los clientes.';
        },
      });
  }

  private loadPaymentMethods(): void {
    this.loadingPaymentMethods = true;

    this.productService.getMetodosPago().subscribe({
      next: (methods) => {
        this.paymentMethods = methods.map((method) => this.mapPaymentMethod(method));
        this.paymentRows = this.paymentRows.map((row) => ({
          ...row,
          paymentMethodId:
            row.paymentMethodId ?? this.getFirstAvailablePaymentMethodId(row.id),
        }));
        this.loadingPaymentMethods = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.paymentMethods = [];
        this.loadingPaymentMethods = false;
        this.cdr.detectChanges();
      },
    });
  }

  private createPaymentRow(
    id = this.nextPaymentRowId++,
    amount: number | null = null,
  ): CreditPaymentRow {
    return {
      id,
      paymentMethodId: this.getFirstAvailablePaymentMethodId(id),
      amount,
    };
  }

  private getFirstAvailablePaymentMethodId(rowId?: number): number | null {
    const usedMethodIds = new Set(
      this.paymentRows
        .filter((row) => row.id !== rowId)
        .map((row) => row.paymentMethodId)
        .filter((id): id is number => id !== null),
    );

    return this.paymentMethods.find((method) => !usedMethodIds.has(method.id))?.id ?? null;
  }

  private mapPaymentMethod(method: MetodoPago): PaymentMethodOption {
    return {
      id: method.id_metodo_pago,
      name: method.nombre,
    };
  }

  private mapClient(client: ClientRole): CreditClient {
    return {
      id: client.cliente.id_cliente,
      name: client.usuario.persona?.nombre_completo ?? 'Sin nombre',
      document: client.usuario.persona?.documento ?? '',
      code: client.cliente.codigo_cliente ?? '',
      balance: Math.max(0, Number(client.cliente.saldo_credito ?? 0)),
      active: client.cliente.activo,
    };
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase();
  }

  private normalizeMoney(value: string | number | null): number {
    const amount = Number(value ?? 0);
    return this.roundCurrency(Number.isFinite(amount) ? Math.max(amount, 0) : 0);
  }

  private roundCurrency(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
  }
}
