import {
  Component,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  inject,
} from '@angular/core';
import { NavigationEnd, Params, Router, RouterLink } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { CompanyPermissionsService } from '../../../core/services/company-permissions.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SidebarStateService } from './sidebar-state.service';
import { Icon, IconName } from '../icon/icon';

export type SidebarContext = 'company' | 'branch' | 'cash_register';

export type SidebarItem = {
  label: string;
  link?: string | unknown[];
  queryParams?: Params;
  active?: boolean;
  expanded?: boolean;
  children?: SidebarItem[];
  permission?: string | string[];
};

type SidebarResolvedContext = {
  context: SidebarContext;
  companyId: string;
  branchId: string;
  cashRegisterId: string;
  cashRegisterSessionId: string;
  activeItemLabel: string;
  permissions: string[];
};

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, TranslatePipe, Icon],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class SidebarComponent implements OnChanges, OnInit, OnDestroy {
  private readonly router = inject(Router);
  protected readonly sidebarState = inject(SidebarStateService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly preferencesService = inject(PreferencesService);
  private readonly navigationSubscription = new Subscription();
  private currentStateKey = '';

  @Input() context: SidebarContext = 'company';
  @Input() companyId = '';
  @Input() branchId = '';
  @Input() cashRegisterId = '';
  @Input() cashRegisterSessionId = '';
  @Input() activeItemLabel = '';
  @Input() permissions: string[] = [];

  openGroups: Record<string, boolean> = {};

  ngOnInit(): void {
    this.navigationSubscription.add(
      this.router.events
        .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
        .subscribe(() => {
          this.sidebarState.closeMobile();
          this.syncOpenGroups();
        }),
    );

    this.syncOpenGroups();
  }

  ngOnDestroy(): void {
    this.navigationSubscription.unsubscribe();
    this.sidebarState.closeMobile();
  }

  @HostListener('document:keydown', ['$event'])
  protected handleKeyboard(event: KeyboardEvent): void {
    if (!this.sidebarState.mobileOpen()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.sidebarState.closeMobile(true);
    }
    if (event.key === 'Tab') {
      const elements = Array.from(
        document.querySelectorAll<HTMLElement>(
          '#workspace-sidebar a[href], #workspace-sidebar button:not(:disabled)',
        ),
      ).filter((element) => element.getClientRects().length > 0);
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    }
  }

  protected iconFor(label: string): IconName {
    const normalized = this.removeDiacritics(label).toLowerCase();
    const icons: Record<string, IconName> = {
      panel: 'dashboard',
      sucursales: 'building',
      usuarios: 'staff',
      clientes: 'users',
      productos: 'package',
      pedidos: 'orders',
      reportes: 'chart',
      configuracion: 'settings',
      bitacora: 'clipboard',
      cajas: 'wallet',
      inventario: 'boxes',
      ventas: 'receipt',
      'cobro de creditos': 'banknote',
      movimientos: 'chart',
    };
    return icons[normalized] ?? 'clipboard';
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.preferencesService.ensureLoaded(this.companyId);
    this.syncOpenGroups();
  }

  toggleGroup(label: string): void {
    const nextState = !this.isGroupOpenByLabel(label);
    this.openGroups[label] = nextState;
    this.sidebarState.setGroupState(this.getStateKey(), label, nextState);
  }

  isGroupOpen(item: SidebarItem): boolean {
    return this.isGroupOpenByLabel(item.label);
  }

  get displayedItems(): SidebarItem[] {
    return this.decorateItems(this.buildItems(), this.resolveContext());
  }

  get backButtonLabel(): string {
    switch (this.resolveContext().context) {
      case 'cash_register':
        return 'Volver a Cajas';
      case 'branch':
        return 'Volver a Mis Sucursales';
      default:
        return 'Volver a Mis Empresas';
    }
  }

  get backButtonLink(): string[] {
    const context = this.resolveContext();

    if (context.context === 'cash_register') {
      return context.companyId && context.branchId
        ? ['/company', context.companyId, 'branch', context.branchId, 'cash-register']
        : ['/my-companies'];
    }

    if (context.context === 'branch') {
      return context.companyId ? ['/company', context.companyId, 'branches'] : ['/my-companies'];
    }

    return ['/my-companies'];
  }

  private buildItems(): SidebarItem[] {
    const context = this.resolveContext();

    switch (context.context) {
      case 'branch':
        return this.buildBranchItems(context);
      case 'cash_register':
        return this.buildCashRegisterItems(context);
      default:
        return this.buildCompanyItems(context);
    }
  }

  private buildCompanyItems(context: SidebarResolvedContext): SidebarItem[] {
    const reportItems = this.buildReportItems(context);

    return [
      {
        label: 'Panel',
        link: ['/company', context.companyId, 'dashboard'],
        permission: 'DASHBOARD_VER',
      },
      {
        label: 'Sucursales',
        link: ['/company', context.companyId, 'branches'],
        permission: 'SUCURSAL_VER',
      },
      {
        label: 'Usuarios',
        permission: ['USUARIO_VER', 'ROL_VER'],
        children: [
          {
            label: 'Personal',
            link: ['/company', context.companyId, 'users', 'staff'],
            permission: 'USUARIO_VER',
          },
          {
            label: 'Roles',
            link: ['/company', context.companyId, 'users', 'rols'],
            permission: 'ROL_VER',
          },
        ],
      },
      {
        label: 'Clientes',
        permission: ['CLIENTE_VER', 'CATEGORIA_VER'],
        children: [
          {
            label: 'Agenda',
            link: ['/company', context.companyId, 'clients'],
            permission: 'CLIENTE_VER',
          },
          {
            label: 'Categorias',
            link: ['/company', context.companyId, 'clients', 'categories'],
            permission: 'CATEGORIA_VER',
          },
        ],
      },
      {
        label: 'Productos',
        permission: ['PRODUCTO_VER', 'CATEGORIA_VER'],
        children: [
          {
            label: 'Catalogo',
            link: ['/company', context.companyId, 'products'],
            permission: 'PRODUCTO_VER',
          },
          {
            label: 'Categoria',
            link: ['/company', context.companyId, 'products', 'categories'],
            permission: 'CATEGORIA_VER',
          },
        ],
      },
      {
        label: 'Pedidos',
        link: ['/company', context.companyId, 'orders'],
        permission: 'PEDIDO_VER',
      },
      ...(reportItems.length > 0
        ? [
            {
              label: 'Reportes',
              link: reportItems[0].link,
              permission: 'REPORTE_GENERAR',
              children: reportItems,
            },
          ]
        : []),
      {
        label: 'Configuración',
        link: ['/administrator/settings'],
        queryParams: { companyId: context.companyId },
        permission: 'EMPRESA_EDITAR',
      },
      {
        label: 'Bitácora',
        link: ['/company', context.companyId, 'audit-log'],
        permission: 'BITACORA_VER',
      },
    ];
  }

  private buildReportItems(context: SidebarResolvedContext): SidebarItem[] {
    const enabledReports = this.getEnabledReportTypes();
    const reportDefinitions = [
      {
        key: 'static',
        label: 'Estaticos',
        link: ['/company', context.companyId, 'reports', 'static'],
        permission: 'REPORTE_GENERAR',
      },
      {
        key: 'parameterized',
        label: 'Parametrizados',
        link: ['/company', context.companyId, 'reports', 'parameterized'],
        permission: 'REPORTE_GENERAR',
      },
      {
        key: 'analytics',
        label: 'Análisis inteligente',
        link: ['/company', context.companyId, 'reports', 'analytics'],
        permission: 'DASHBOARD_VER',
      },
      {
        key: 'qbe',
        label: 'Consulta QBE',
        link: ['/company', context.companyId, 'reports', 'qbe'],
        permission: 'REPORTE_GENERAR',
      },
      {
        key: 'dynamic',
        label: 'Dinamicos',
        link: ['/company', context.companyId, 'reports', 'dynamic'],
        permission: 'REPORTE_GENERAR',
      },
      {
        key: 'prediction',
        label: 'Predicción',
        link: ['/company', context.companyId, 'reports', 'prediction'],
        permission: 'REPORTE_GENERAR',
      },
      {
        key: 'invoices',
        label: 'Facturas',
        link: ['/company', context.companyId, 'reports', 'invoices'],
        permission: 'REPORTE_GENERAR',
      },
    ];

    return reportDefinitions.filter(
      (report) =>
        ['analytics', 'qbe', 'prediction', 'invoices'].includes(report.key) ||
        enabledReports.has(report.key),
    );
  }

  private getEnabledReportTypes(): Set<string> {
    const reportConfiguration = this.companyPermissionsService.planConfiguration().reportes;
    const normalizedConfiguration = reportConfiguration.trim().toLowerCase();

    if (!normalizedConfiguration) {
      return new Set(['static', 'parameterized', 'dynamic', 'prediction']);
    }

    const tokens = normalizedConfiguration
      .split(/[^a-z0-9áéíóúñ]+/i)
      .map((token) => token.trim().toLowerCase())
      .filter(Boolean);

    const reportTypes = new Set<string>();

    for (const token of tokens) {
      const normalizedToken = this.removeDiacritics(token);

      if (['estatico', 'estaticos', 'static', 'statico', 'staticos'].includes(normalizedToken)) {
        reportTypes.add('static');
      }

      if (
        ['parametrizado', 'parametrizados', 'parameterized', 'parameterised'].includes(
          normalizedToken,
        )
      ) {
        reportTypes.add('parameterized');
      }

      if (['dinamico', 'dinamicos', 'dynamic'].includes(normalizedToken)) {
        reportTypes.add('dynamic');
      }
    }

    return reportTypes;
  }

  private removeDiacritics(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  private buildBranchItems(context: SidebarResolvedContext): SidebarItem[] {
    return [
      {
        label: 'Cajas',
        link: ['/company', context.companyId, 'branch', context.branchId, 'cash-register'],
        permission: 'CAJA_VER',
      },
      {
        label: 'Inventario',
        link: ['/company', context.companyId, 'branch', context.branchId, 'inventario'],
        permission: ['STOCK_VER', 'MOVIMIENTO_VER'],
      },
    ];
  }

  private buildCashRegisterItems(context: SidebarResolvedContext): SidebarItem[] {
    const cashRegisterPath = [
      '/company',
      context.companyId,
      'branch',
      context.branchId,
      'cash-register',
      context.cashRegisterId,
    ];

    return [
      {
        label: 'Ventas',
        link: [...cashRegisterPath, 'sales'],
        permission: 'VENTA_VER',
        queryParams: {
          section: 'sales',
          sessionId: context.cashRegisterSessionId,
        },
      },
      {
        label: 'Cobro de Creditos',
        link: [...cashRegisterPath, 'credit-collections'],
        permission: 'VENTA_VER',
        queryParams: {
          sessionId: context.cashRegisterSessionId,
        },
      },
      {
        label: 'Movimientos',
        link: [...cashRegisterPath, 'sales'],
        permission: 'MOVIMIENTO_VER',
        queryParams: {
          section: 'movimientos',
          sessionId: context.cashRegisterSessionId,
        },
      },
    ];
  }

  private decorateItems(items: SidebarItem[], context: SidebarResolvedContext): SidebarItem[] {
    return items
      .map((item) => this.decorateItem(item, context))
      .filter((item): item is SidebarItem => Boolean(item));
  }

  private decorateItem(
    item: SidebarItem,
    context: SidebarResolvedContext,
  ): SidebarItem | undefined {
    if (!this.hasPermission(item, context.permissions)) {
      return undefined;
    }

    const children = item.children?.length ? this.decorateItems(item.children, context) : undefined;
    const hasActiveChild = children?.some((child) => child.active) ?? false;
    const isActive = this.isItemActive(item, hasActiveChild);

    return {
      ...item,
      active: item.active === true || isActive || hasActiveChild,
      expanded: item.expanded,
      children,
    };
  }

  private isItemActive(item: SidebarItem, hasActiveChild = false): boolean {
    if (item.active) {
      return true;
    }

    if (hasActiveChild) {
      return true;
    }

    if (
      item.link &&
      this.isLinkActive(item.link, item.queryParams, Boolean(item.children?.length))
    ) {
      return true;
    }

    return false;
  }

  private isLinkActive(
    link: string | unknown[],
    queryParams?: Params,
    allowDescendants = false,
  ): boolean {
    const currentPath = this.normalizeUrl(this.router.url);
    const linkPath = this.normalizeUrl(
      this.router.serializeUrl(this.router.createUrlTree(this.normalizeCommands(link))),
    );

    const matchesPath = allowDescendants
      ? currentPath === linkPath || currentPath.startsWith(`${linkPath}/`)
      : currentPath === linkPath;

    if (!matchesPath) {
      return false;
    }

    if (!queryParams) {
      return true;
    }

    const currentQueryParams = this.router.parseUrl(this.router.url).queryParams;
    return Object.entries(queryParams).every(
      ([key, value]) => currentQueryParams[key] === String(value),
    );
  }

  private normalizeCommands(link: string | unknown[]): unknown[] {
    return Array.isArray(link) ? link : [link];
  }

  private normalizeUrl(url: string): string {
    return url.split('?')[0].split('#')[0].replace(/\/+$/, '');
  }

  private hasPermission(item: SidebarItem, permissions: string[]): boolean {
    if (!item.permission) {
      return true;
    }

    if (!permissions || permissions.length === 0) {
      return false;
    }

    const requiredPermissions = Array.isArray(item.permission)
      ? item.permission
      : [item.permission];
    return requiredPermissions.some((permission) => permissions.includes(permission));
  }

  private resolveContext(): SidebarResolvedContext {
    return {
      context: this.context,
      companyId: this.companyId || this.getCompanyIdFromUrl(),
      branchId: this.branchId || this.getBranchIdFromUrl(),
      cashRegisterId: this.cashRegisterId || this.getCashRegisterIdFromUrl(),
      cashRegisterSessionId: this.cashRegisterSessionId,
      activeItemLabel: this.activeItemLabel,
      permissions:
        this.permissions.length > 0
          ? this.permissions
          : this.companyPermissionsService.getPermissionCodes(),
    };
  }

  private getCompanyIdFromUrl(): string {
    const match = this.router.url.match(/\/company\/([^/]+)/);
    return match?.[1] ?? '';
  }

  private getBranchIdFromUrl(): string {
    const match = this.router.url.match(/\/branch\/([^/]+)/);
    return match?.[1] ?? '';
  }

  private getCashRegisterIdFromUrl(): string {
    const match = this.router.url.match(/\/cash[-_]register\/([^/]+)/);
    return match?.[1] ?? '';
  }

  private getStateKey(): string {
    const context = this.resolveContext();
    return [context.context, context.companyId, context.branchId, context.cashRegisterId].join(':');
  }

  private isGroupOpenByLabel(label: string): boolean {
    return this.openGroups[label] ?? false;
  }

  private syncOpenGroups(): void {
    const stateKey = this.getStateKey();

    if (stateKey !== this.currentStateKey) {
      this.currentStateKey = stateKey;
      this.openGroups = this.sidebarState.getState(stateKey);
    }

    for (const item of this.displayedItems) {
      if (!item.children?.length) {
        continue;
      }

      this.sidebarState.ensureGroupState(stateKey, item.label, this.shouldOpenGroupByDefault(item));
      this.openGroups[item.label] = this.sidebarState.getGroupState(stateKey, item.label) ?? false;
    }
  }

  private shouldOpenGroupByDefault(item: SidebarItem): boolean {
    if (item.expanded === true) {
      return true;
    }

    return item.children?.some((child) => child.active) ?? false;
  }
}

export { SidebarComponent as Sidebar };
